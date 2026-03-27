import Foundation

enum HTTPMethod: String {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case delete = "DELETE"
}

enum APIClientError: Error {
    case invalidURL
    case invalidResponse
    case transport(URLError)
    case server(statusCode: Int, payload: APIErrorPayload?)
    case decoding(Error)
}

final class APIClient: @unchecked Sendable {
    private let environment: AppEnvironment
    private let session: URLSession
    private let decoder: JSONDecoder

    init(
        environment: AppEnvironment,
        session: URLSession = .shared,
        decoder: JSONDecoder = JSONDecoder()
    ) {
        self.environment = environment
        self.session = session
        self.decoder = decoder
    }

    func send(
        path: String,
        method: HTTPMethod,
        body: Data? = nil,
        accessToken: String? = nil
    ) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: path, relativeTo: environment.apiBaseURL) else {
            throw APIClientError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if let accessToken, accessToken.isEmpty == false {
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw APIClientError.invalidResponse
            }

            guard (200...299).contains(httpResponse.statusCode) else {
                let payload = try? decoder.decode(APIResponseEnvelope<EmptyPayload>.self, from: data).error
                throw APIClientError.server(statusCode: httpResponse.statusCode, payload: payload)
            }

            return (data, httpResponse)
        } catch let error as APIClientError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as URLError {
            throw APIClientError.transport(error)
        } catch {
            throw APIClientError.decoding(error)
        }
    }

    func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        do {
            return try decoder.decode(type, from: data)
        } catch {
            throw APIClientError.decoding(error)
        }
    }
}

actor AuthorizedRequestExecutor {
    private let apiClient: APIClient
    private let tokenStore: TokenStore
    private let encoder: JSONEncoder
    private var inFlightRefresh: Task<TokenPair, Error>?

    init(
        apiClient: APIClient,
        tokenStore: TokenStore,
        encoder: JSONEncoder = JSONEncoder()
    ) {
        self.apiClient = apiClient
        self.tokenStore = tokenStore
        self.encoder = encoder
    }

    func send(
        path: String,
        method: HTTPMethod,
        body: Data? = nil
    ) async throws -> (Data, HTTPURLResponse) {
        let session = try readSession()

        do {
            return try await apiClient.send(
                path: path,
                method: method,
                body: body,
                accessToken: session.accessToken
            )
        } catch let error as APIClientError {
            guard shouldReissue(after: error) else {
                throw error
            }

            let refreshedSession = try await reissueSession(using: session)
            return try await apiClient.send(
                path: path,
                method: method,
                body: body,
                accessToken: refreshedSession.accessToken
            )
        }
    }

    private func readSession() throws -> TokenPair {
        guard let session = try tokenStore.readSession(),
              session.accessToken.isEmpty == false,
              session.refreshToken.isEmpty == false else {
            throw invalidSessionError()
        }
        return session
    }

    private func shouldReissue(after error: APIClientError) -> Bool {
        if case let .server(statusCode, _) = error {
            return statusCode == 401
        }
        return false
    }

    private func reissueSession(using session: TokenPair) async throws -> TokenPair {
        if let inFlightRefresh {
            return try await inFlightRefresh.value
        }

        let refreshTask = Task<TokenPair, Error> {
            let request = ReissueRequest(
                accessToken: session.accessToken,
                refreshToken: session.refreshToken
            )
            let body = try encoder.encode(request)
            let (data, _) = try await apiClient.send(
                path: "/api/v1/auth/reissue",
                method: .post,
                body: body
            )
            let envelope = try apiClient.decode(APIResponseEnvelope<TokenPair>.self, from: data)

            guard envelope.success, let tokenPair = envelope.data else {
                throw APIClientError.invalidResponse
            }

            do {
                try tokenStore.saveSession(tokenPair)
            } catch {
                try? tokenStore.clearSession()
                throw invalidSessionError()
            }
            return tokenPair
        }

        inFlightRefresh = refreshTask
        defer { inFlightRefresh = nil }

        do {
            return try await refreshTask.value
        } catch let error as APIClientError {
            if shouldReissue(after: error) {
                try? tokenStore.clearSession()
            }
            throw error
        } catch {
            try? tokenStore.clearSession()
            throw invalidSessionError()
        }
    }

    private func invalidSessionError() -> APIClientError {
        APIClientError.server(statusCode: 401, payload: nil)
    }
}
