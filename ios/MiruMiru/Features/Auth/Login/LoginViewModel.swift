import Foundation

@MainActor
final class LoginViewModel: ObservableObject {
    @Published var email = ""
    @Published var password = ""
    @Published var isPasswordVisible = false
    @Published var emailError: String?
    @Published var passwordError: String?
    @Published var generalError: String?

    private let session: AppSession
    private let environment: AppEnvironment

    init(
        session: AppSession,
        initialEmail: String = "",
        environment: AppEnvironment = .live()
    ) {
        self.session = session
        self.environment = environment
        self.email = initialEmail
    }

    var isLoading: Bool {
        session.state == .submitting
    }

    var sessionBanner: String? {
        session.bannerMessage
    }

    var isButtonDisabled: Bool {
        email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
        password.isEmpty ||
        isLoading
    }

    func handleAppear() {
        if session.state == .invalidSession {
            session.markInvalidSessionHandled()
        }
    }

    func dismissBanner() {
        session.dismissBanner()
    }

    func submit() async {
        guard validateInputs() else { return }

        generalError = nil

        do {
            try await session.login(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
        } catch let error as AuthError {
            apply(authError: error)
        } catch {
            generalError = "Something went wrong. Please try again."
        }
    }

    @discardableResult
    func validateInputs() -> Bool {
        emailError = nil
        passwordError = nil
        generalError = nil

        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)

        if trimmedEmail.isEmpty {
            emailError = "Please enter your email address."
        } else if trimmedEmail.contains("@") == false || trimmedEmail.contains(".") == false {
            emailError = "Enter a valid email address."
        } else if environment.enforcesAcademicSuffixValidation && trimmedEmail.lowercased().hasSuffix(".ac.jp") == false {
            emailError = "Use your university email ending in .ac.jp."
        }

        if password.isEmpty {
            passwordError = "Please enter your password."
        }

        return emailError == nil && passwordError == nil
    }

    private func apply(authError: AuthError) {
        switch authError {
        case let .fieldValidation(field, reason):
            if field == "email" {
                emailError = reason
            } else if field == "password" {
                passwordError = reason
            } else {
                generalError = reason
            }
        case .invalidCredentials:
            generalError = "Invalid email or password."
        case .invalidSession:
            generalError = "Your session expired. Please log in again."
        case .sessionPersistenceFailure:
            generalError = "Login succeeded, but we couldn't save your session on this device. Please try again."
        case .network:
            generalError = "Unable to reach the server. Please try again."
        case .duplicateEmail,
             .duplicateNickname,
             .emailNotVerified,
             .authCodeNotFound,
             .authCodeMismatch,
             .unregisteredUniversity,
             .invalidMajorSelection:
            generalError = "We couldn't complete login right now. Please try again."
        case .unexpected:
            generalError = "Something went wrong. Please try again."
        }
    }
}
