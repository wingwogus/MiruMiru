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

final class APIClient {
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
