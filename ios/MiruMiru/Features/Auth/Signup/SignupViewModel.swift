import Foundation

@MainActor
final class SignupViewModel: ObservableObject {
    @Published private(set) var step: SignupStep = .verifyEmail
    @Published var email = ""
    @Published var verificationCode = ""
    @Published var nickname = ""
    @Published var password = ""
    @Published var confirmPassword = ""
    @Published var isPasswordVisible = false
    @Published var isConfirmPasswordVisible = false
    @Published private(set) var majorOptions: [SignupMajorOption] = []
    @Published var selectedMajorID: Int64?
    @Published private(set) var resendRemainingSeconds = 0
    @Published private(set) var hasSentCode = false
    @Published private(set) var completionRequest: SignupCompletionRequest?
    @Published var emailError: String?
    @Published var codeError: String?
    @Published var nicknameError: String?
    @Published var majorError: String?
    @Published var passwordError: String?
    @Published var confirmPasswordError: String?
    @Published var generalError: String?

    private let client: SignupClientProtocol
    private let resendInterval: Int
    private let sleep: @Sendable (UInt64) async throws -> Void
    private var verifiedEmail: String?
    private(set) var isSendingCode = false
    private(set) var isVerifyingCode = false
    private(set) var isLoadingMajors = false
    private(set) var isSubmitting = false
    private var resendTask: Task<Void, Never>?

    init(
        client: SignupClientProtocol,
        resendInterval: Int = 180,
        sleep: @escaping @Sendable (UInt64) async throws -> Void = { nanoseconds in
            try await Task.sleep(nanoseconds: nanoseconds)
        }
    ) {
        self.client = client
        self.resendInterval = resendInterval
        self.sleep = sleep
    }

    deinit {
        resendTask?.cancel()
    }

    var canResend: Bool {
        hasSentCode && resendRemainingSeconds == 0 && isSendingCode == false
    }

    var resendLabel: String {
        let displaySeconds = resendRemainingSeconds > 0 ? resendRemainingSeconds : resendInterval
        let minutes = displaySeconds / 60
        let seconds = displaySeconds % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }

    var isEmailVerified: Bool {
        verifiedEmail == normalizedEmail
    }

    var primaryButtonTitle: String {
        step.copy.actionTitle
    }

    var isPrimaryButtonDisabled: Bool {
        switch step {
        case .verifyEmail:
            return isVerifyingCode || email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || verificationCode.count != 6
        case .profileDetails:
            return nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || selectedMajorID == nil || isLoadingMajors
        case .createPassword:
            return password.isEmpty || confirmPassword.isEmpty || isSubmitting
        }
    }

    func sendCode() async {
        clearErrors()

        guard validateEmail() else { return }

        isSendingCode = true
        defer { isSendingCode = false }

        do {
            verifiedEmail = nil
            try await client.sendEmailCode(email: normalizedEmail)
            hasSentCode = true
            startResendCountdown()
        } catch {
            apply(error)
        }
    }

    func verifyEmailAndContinue() async {
        clearErrors()

        guard validateEmail() else { return }
        guard validateVerificationCode() else { return }

        isVerifyingCode = true
        defer { isVerifyingCode = false }

        do {
            try await client.verifyEmailCode(email: normalizedEmail, code: verificationCode)
            verifiedEmail = normalizedEmail
            step = .profileDetails
            await loadMajors()
        } catch {
            apply(error)
        }
    }

    func continueFromProfile() {
        clearErrors()

        guard validateProfileDetails() else { return }

        step = .createPassword
    }

    func completeSignup() async {
        clearErrors()

        guard validatePasswordStep() else { return }
        guard isEmailVerified else {
            generalError = "Verify your school email before completing sign up."
            step = .verifyEmail
            return
        }

        isSubmitting = true
        defer { isSubmitting = false }

        do {
            try await client.verifyNickname(nickname.trimmingCharacters(in: .whitespacesAndNewlines))
            try await client.signUp(
                email: normalizedEmail,
                password: password,
                nickname: nickname.trimmingCharacters(in: .whitespacesAndNewlines),
                majorId: selectedMajorID ?? 0
            )
            completionRequest = SignupCompletionRequest(
                email: normalizedEmail,
                bannerMessage: "Your account was created. Log in to continue."
            )
        } catch {
            apply(error)
        }
    }

    func goBack() {
        clearErrors()

        switch step {
        case .verifyEmail:
            break
        case .profileDetails:
            step = .verifyEmail
        case .createPassword:
            step = .profileDetails
        }
    }

    func consumeCompletionRequest() {
        completionRequest = nil
    }

    private var normalizedEmail: String {
        email.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func loadMajors() async {
        isLoadingMajors = true
        defer { isLoadingMajors = false }

        do {
            majorOptions = try await client.fetchMajors(email: normalizedEmail)
            if majorOptions.contains(where: { $0.id == selectedMajorID }) == false {
                selectedMajorID = majorOptions.first?.id
            }
        } catch {
            majorOptions = []
            selectedMajorID = nil
            apply(error)
        }
    }

    private func clearErrors() {
        emailError = nil
        codeError = nil
        nicknameError = nil
        majorError = nil
        passwordError = nil
        confirmPasswordError = nil
        generalError = nil
    }

    @discardableResult
    private func validateEmail() -> Bool {
        if normalizedEmail.isEmpty {
            emailError = "Please enter your school email."
        } else if normalizedEmail.contains("@") == false || normalizedEmail.contains(".") == false {
            emailError = "Enter a valid school email."
        } else if normalizedEmail.lowercased().hasSuffix(".ac.jp") == false {
            emailError = "Use your university email ending in .ac.jp."
        }

        return emailError == nil
    }

    @discardableResult
    private func validateVerificationCode() -> Bool {
        if verificationCode.count != 6 {
            codeError = "Enter the 6-digit verification code."
        } else if verificationCode.allSatisfy(\.isNumber) == false {
            codeError = "Verification code must contain only numbers."
        }

        return codeError == nil
    }

    @discardableResult
    private func validateProfileDetails() -> Bool {
        if nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            nicknameError = "Please enter your nickname."
        }

        if selectedMajorID == nil {
            majorError = "Please choose your major."
        }

        return nicknameError == nil && majorError == nil
    }

    @discardableResult
    private func validatePasswordStep() -> Bool {
        guard validateProfileDetails() else { return false }

        if password.isEmpty {
            passwordError = "Please enter your password."
        }

        if confirmPassword.isEmpty {
            confirmPasswordError = "Please re-enter your password."
        } else if password.isEmpty == false && password != confirmPassword {
            confirmPasswordError = "Passwords do not match."
        }

        return passwordError == nil && confirmPasswordError == nil
    }

    private func startResendCountdown() {
        resendTask?.cancel()
        resendRemainingSeconds = resendInterval

        resendTask = Task { [weak self] in
            guard let self else { return }

            while Task.isCancelled == false && self.resendRemainingSeconds > 0 {
                try? await self.sleep(1_000_000_000)
                guard Task.isCancelled == false else { return }
                await MainActor.run {
                    self.resendRemainingSeconds = max(0, self.resendRemainingSeconds - 1)
                }
            }
        }
    }

    private func apply(_ error: Error) {
        if let authError = error as? AuthError {
            switch authError {
            case .fieldValidation(let field, let reason):
                switch field {
                case "email":
                    emailError = reason
                case "code":
                    codeError = reason
                case "nickname":
                    nicknameError = reason
                    step = .profileDetails
                case "majorId":
                    majorError = reason
                    step = .profileDetails
                case "password":
                    passwordError = reason
                default:
                    generalError = reason
                }
            case .duplicateEmail:
                emailError = "An account with this email already exists."
                step = .verifyEmail
            case .duplicateNickname:
                nicknameError = "This nickname is already taken."
                step = .profileDetails
            case .emailNotVerified:
                generalError = "Verify your school email before continuing."
                step = .verifyEmail
            case .authCodeNotFound:
                codeError = "Request a new verification code and try again."
            case .authCodeMismatch:
                codeError = "The verification code is incorrect."
            case .unregisteredUniversity:
                emailError = "Your university email domain isn't supported yet."
                step = .verifyEmail
            case .invalidMajorSelection:
                majorError = "Choose a valid major for your university."
                step = .profileDetails
            case .network:
                generalError = "Unable to reach the server. Please try again."
            case .unexpected:
                generalError = "Something went wrong. Please try again."
            case .invalidCredentials, .invalidSession, .sessionPersistenceFailure:
                generalError = "We couldn't complete sign up right now."
            }
            return
        }

        if let signupError = error as? SignupFeatureError, signupError == .unavailable {
            generalError = "Sign up service is not connected yet."
            return
        }

        generalError = "Something went wrong. Please try again."
    }
}
