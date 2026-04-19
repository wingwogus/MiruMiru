import Foundation

struct LoginRequest: Codable, Equatable {
    let email: String
    let password: String
}

struct ReissueRequest: Codable, Equatable {
    let accessToken: String
    let refreshToken: String
}

struct SendEmailCodeRequest: Codable, Equatable {
    let email: String
}

struct VerifyEmailCodeRequest: Codable, Equatable {
    let email: String
    let code: String
}

struct NicknameVerifyRequest: Codable, Equatable {
    let nickname: String
}

struct MajorListRequest: Codable, Equatable {
    let email: String
}

struct SignUpRequest: Codable, Equatable {
    let email: String
    let password: String
    let nickname: String
    let majorId: Int64
    var avatar: String? = nil
}

struct TokenPair: Codable, Equatable {
    let accessToken: String
    let refreshToken: String
}

struct MajorOption: Codable, Equatable {
    let majorId: Int64
    let code: String
    let name: String
}

struct APIResponseEnvelope<T: Decodable>: Decodable {
    let success: Bool
    let data: T?
    let error: APIErrorPayload?
}

struct APIErrorPayload: Decodable, Equatable {
    let code: String
    let message: String
    let detail: APIErrorDetail?
}

enum APIErrorDetail: Decodable, Equatable {
    case validation(field: String, reason: String, rejectedValue: String?)
    case message(String)
    case none

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()

        if let value = try? container.decode(String.self) {
            self = .message(value)
            return
        }

        if let object = try? container.decode(ValidationErrorDetail.self) {
            self = .validation(
                field: object.field,
                reason: object.reason,
                rejectedValue: object.rejectedValue
            )
            return
        }

        self = .none
    }
}

struct ValidationErrorDetail: Decodable, Equatable {
    let field: String
    let reason: String
    let rejectedValue: String?
}

struct EmptyPayload: Decodable, Equatable {}

protocol AuthClientProtocol: Sendable {
    func login(email: String, password: String) async throws -> TokenPair
    func reissue(accessToken: String, refreshToken: String) async throws -> TokenPair
    func sendEmailCode(email: String) async throws
    func verifyEmailCode(email: String, code: String) async throws
    func fetchMajors(email: String) async throws -> [MajorOption]
    func verifyNickname(nickname: String) async throws
    func signUp(email: String, password: String, nickname: String, majorId: Int64) async throws
    func validateRestoredSession(accessToken: String) async throws
}
