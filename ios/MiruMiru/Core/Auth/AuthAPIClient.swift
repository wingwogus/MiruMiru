import Foundation

final class AuthAPIClient: AuthClientProtocol, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder: JSONEncoder

    init(
        apiClient: APIClient,
        encoder: JSONEncoder = JSONEncoder()
        ) {
        self.apiClient = apiClient
        self.encoder = encoder
    }

    func login(email: String, password: String) async throws -> TokenPair {
        let request = LoginRequest(email: email, password: password)
        return try await requestTokenPair(
            path: "/api/v1/auth/login",
            body: try encoder.encode(request),
            context: .login
        )
    }

    func reissue(accessToken: String, refreshToken: String) async throws -> TokenPair {
        let request = ReissueRequest(accessToken: accessToken, refreshToken: refreshToken)
        return try await requestTokenPair(
            path: "/api/v1/auth/reissue",
            body: try encoder.encode(request),
            context: .reissue
        )
    }

    func sendEmailCode(email: String) async throws {
        let request = SendEmailCodeRequest(email: email)
        try await performVoidRequest(
            path: "/api/v1/auth/email/send-code",
            body: try encoder.encode(request)
        )
    }

    func verifyEmailCode(email: String, code: String) async throws {
        let request = VerifyEmailCodeRequest(email: email, code: code)
        try await performVoidRequest(
            path: "/api/v1/auth/email/verify-code",
            body: try encoder.encode(request)
        )
    }

    func fetchMajors(email: String) async throws -> [MajorOption] {
        let encodedEmail = email.addingPercentEncoding(withAllowedCharacters: .authQueryAllowed) ?? email
        return try await requestPayload(
            path: "/api/v1/auth/majors?email=\(encodedEmail)",
            context: .signup
        )
    }

    func verifyNickname(nickname: String) async throws {
        let request = NicknameVerifyRequest(nickname: nickname)
        try await performVoidRequest(
            path: "/api/v1/auth/verify-nickname",
            body: try encoder.encode(request)
        )
    }

    func signUp(email: String, password: String, nickname: String, majorId: Int64) async throws {
        let request = SignUpRequest(
            email: email,
            password: password,
            nickname: nickname,
            majorId: majorId
        )
        try await performVoidRequest(
            path: "/api/v1/auth/signup",
            body: try encoder.encode(request)
        )
    }

    func validateRestoredSession(accessToken: String) async throws {
        do {
            _ = try await apiClient.send(
                path: "/api/v1/test/me",
                method: .get,
                accessToken: accessToken
            )
        } catch let error as APIClientError {
            throw AuthErrorMapper.map(apiError: error, context: .restoreProbe)
        }
    }

    private func requestTokenPair(path: String, body: Data, context: AuthFailureContext) async throws -> TokenPair {
        do {
            let (data, _) = try await apiClient.send(
                path: path,
                method: .post,
                body: body
            )
            let envelope = try apiClient.decode(APIResponseEnvelope<TokenPair>.self, from: data)

            guard let tokenPair = envelope.data, envelope.success else {
                throw APIClientError.invalidResponse
            }

            return tokenPair
        } catch let error as APIClientError {
            throw AuthErrorMapper.map(apiError: error, context: context)
        }
    }

    private func requestPayload<Response: Decodable>(
        path: String,
        context: AuthFailureContext
    ) async throws -> Response {
        do {
            let (data, _) = try await apiClient.send(
                path: path,
                method: .get
            )
            let envelope = try apiClient.decode(APIResponseEnvelope<Response>.self, from: data)

            guard let payload = envelope.data, envelope.success else {
                throw APIClientError.invalidResponse
            }

            return payload
        } catch let error as APIClientError {
            throw AuthErrorMapper.map(apiError: error, context: context)
        }
    }

    private func performVoidRequest(path: String, body: Data) async throws {
        do {
            let (data, _) = try await apiClient.send(
                path: path,
                method: .post,
                body: body
            )
            let envelope = try apiClient.decode(APIResponseEnvelope<EmptyPayload>.self, from: data)

            guard envelope.success else {
                throw APIClientError.invalidResponse
            }
        } catch let error as APIClientError {
            throw AuthErrorMapper.map(apiError: error, context: .signup)
        }
    }
}

private extension CharacterSet {
    static let authQueryAllowed: CharacterSet = {
        var set = CharacterSet.urlQueryAllowed
        set.remove(charactersIn: "&=?+")
        return set
    }()
}
