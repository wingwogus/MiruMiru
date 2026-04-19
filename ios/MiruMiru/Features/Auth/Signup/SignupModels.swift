import Foundation

struct SignupMajorOption: Identifiable, Equatable, Sendable {
    let id: Int64
    let code: String
    let name: String
}

enum SignupStep: Int, CaseIterable, Equatable {
    case verifyEmail
    case profileDetails
    case createPassword

    var title: String {
        switch self {
        case .verifyEmail:
            return "Verify School Email"
        case .profileDetails:
            return "Set Up Profile"
        case .createPassword:
            return "Create Password"
        }
    }
}

struct SignupCompletionRequest: Equatable {
    let email: String
    let bannerMessage: String
}

protocol SignupClientProtocol: Sendable {
    func sendEmailCode(email: String) async throws
    func verifyEmailCode(email: String, code: String) async throws
    func fetchMajors(email: String) async throws -> [SignupMajorOption]
    func verifyNickname(_ nickname: String) async throws
    func signUp(email: String, password: String, nickname: String, majorId: Int64) async throws
}

struct AuthSignupClientAdapter: SignupClientProtocol {
    private let client: AuthClientProtocol

    init(client: AuthClientProtocol) {
        self.client = client
    }

    func sendEmailCode(email: String) async throws {
        try await client.sendEmailCode(email: email)
    }

    func verifyEmailCode(email: String, code: String) async throws {
        try await client.verifyEmailCode(email: email, code: code)
    }

    func fetchMajors(email: String) async throws -> [SignupMajorOption] {
        try await client.fetchMajors(email: email).map {
            SignupMajorOption(id: $0.majorId, code: $0.code, name: $0.name)
        }
    }

    func verifyNickname(_ nickname: String) async throws {
        try await client.verifyNickname(nickname: nickname)
    }

    func signUp(email: String, password: String, nickname: String, majorId: Int64) async throws {
        try await client.signUp(email: email, password: password, nickname: nickname, majorId: majorId)
    }
}

struct SignupScreenCopy {
    let heading: String
    let body: String
    let actionTitle: String
}

extension SignupStep {
    var copy: SignupScreenCopy {
        switch self {
        case .verifyEmail:
            return SignupScreenCopy(
                heading: "Access Campus Features",
                body: "To use the board and campus services, please verify your university email address.",
                actionTitle: "Complete Verification"
            )
        case .profileDetails:
            return SignupScreenCopy(
                heading: "Set Up Your Profile",
                body: "Choose your nickname and major so we can tailor your campus experience.",
                actionTitle: "Continue"
            )
        case .createPassword:
            return SignupScreenCopy(
                heading: "Set Your Password",
                body: "Choose a strong password to protect your account.",
                actionTitle: "Complete Sign Up"
            )
        }
    }
}

enum SignupFeatureError: Error, Equatable {
    case unavailable
}

struct UnavailableSignupClient: SignupClientProtocol {
    func sendEmailCode(email: String) async throws {
        throw SignupFeatureError.unavailable
    }

    func verifyEmailCode(email: String, code: String) async throws {
        throw SignupFeatureError.unavailable
    }

    func fetchMajors(email: String) async throws -> [SignupMajorOption] {
        throw SignupFeatureError.unavailable
    }

    func verifyNickname(_ nickname: String) async throws {
        throw SignupFeatureError.unavailable
    }

    func signUp(email: String, password: String, nickname: String, majorId: Int64) async throws {
        throw SignupFeatureError.unavailable
    }
}
