import XCTest
@testable import MiruMiru

final class HomeAPIClientTests: XCTestCase {
    override func tearDown() {
        MockURLProtocol.requestHandler = nil
        super.tearDown()
    }

    func testFetchProfileDecodesMemberProfile() async throws {
        let client = makeClient(
            tokenStore: tokenStore(),
            expectedPath: "/api/v1/members/me",
            responseBody: """
            {
              "success": true,
              "data": {
                "memberId": 1,
                "email": "test@tokyo.ac.jp",
                "nickname": "test-user",
                "university": {
                  "universityId": 10,
                  "name": "The University of Tokyo",
                  "emailDomain": "tokyo.ac.jp"
                },
                "major": {
                  "majorId": 20,
                  "code": "CS",
                  "name": "Computer Science"
                }
              },
              "error": null
            }
            """
        )

        let profile = try await client.fetchProfile()
        XCTAssertEqual(profile.memberId, 1)
        XCTAssertEqual(profile.email, "test@tokyo.ac.jp")
        XCTAssertEqual(profile.nickname, "test-user")
        XCTAssertEqual(profile.universityName, "The University of Tokyo")
        XCTAssertEqual(profile.majorCode, "CS")
    }

    func testFetchSemestersDecodesSemesterList() async throws {
        let client = makeClient(
            tokenStore: tokenStore(),
            expectedPath: "/api/v1/semesters",
            responseBody: """
            {
              "success": true,
              "data": [
                { "id": 20261, "academicYear": 2026, "term": "SPRING" }
              ],
              "error": null
            }
            """
        )

        let semesters = try await client.fetchSemesters()
        XCTAssertEqual(semesters, [HomeSemester(id: 20261, academicYear: 2026, term: "SPRING")])
    }

    func testFetchTimetableDecodesEmptyTimetable() async throws {
        let client = makeClient(
            tokenStore: tokenStore(),
            expectedPath: "/api/v1/timetables/me?semesterId=20261",
            responseBody: """
            {
              "success": true,
              "data": {
                "timetableId": null,
                "semester": { "id": 20261, "academicYear": 2026, "term": "SPRING" },
                "lectures": []
              },
              "error": null
            }
            """
        )

        let timetable = try await client.fetchTimetable(semesterId: 20261)
        XCTAssertNil(timetable.timetableId)
        XCTAssertEqual(timetable.semester, HomeSemester(id: 20261, academicYear: 2026, term: "SPRING"))
        XCTAssertTrue(timetable.lectures.isEmpty)
    }

    func testFetchProfileMapsFailedReissueToInvalidSession() async {
        let store = tokenStore()
        MockURLProtocol.requestHandler = { request in
            let path = try XCTUnwrap(request.url?.path)

            if path == "/api/v1/members/me" {
                let response = HTTPURLResponse(
                    url: request.url!,
                    statusCode: 401,
                    httpVersion: nil,
                    headerFields: ["Content-Type": "application/json"]
                )!
                return (response, Data("""
                {
                  "success": false,
                  "data": null,
                  "error": {
                    "code": "AUTH_001",
                    "message": "Unauthorized",
                    "detail": null
                  }
                }
                """.utf8))
            }

            if path == "/api/v1/auth/reissue" {
                let response = HTTPURLResponse(
                    url: request.url!,
                    statusCode: 401,
                    httpVersion: nil,
                    headerFields: ["Content-Type": "application/json"]
                )!
                return (response, Data("""
                {
                  "success": false,
                  "data": null,
                  "error": {
                    "code": "AUTH_001",
                    "message": "Unauthorized",
                    "detail": null
                  }
                }
                """.utf8))
            }

            XCTFail("Unexpected path: \(path)")
            throw URLError(.badURL)
        }

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let apiClient = APIClient(
            environment: AppEnvironment(
                apiBaseURL: URL(string: "http://localhost")!,
                enforcesAcademicSuffixValidation: true
            ),
            session: session
        )
        let client = HomeAPIClient(apiClient: apiClient, tokenStore: store)

        do {
            _ = try await client.fetchProfile()
            XCTFail("Expected invalid session")
        } catch let error as HomeClientError {
            XCTAssertEqual(error, .invalidSession)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testFetchProfileReissuesExpiredAccessTokenAndRetriesRequest() async throws {
        let store = tokenStore()
        var membersMeCalls = 0

        MockURLProtocol.requestHandler = { request in
            let path = try XCTUnwrap(request.url?.path)
            let response: HTTPURLResponse
            let body: Data

            switch path {
            case "/api/v1/members/me":
                membersMeCalls += 1
                if membersMeCalls == 1 {
                    XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer preview-access")
                    response = HTTPURLResponse(
                        url: request.url!,
                        statusCode: 401,
                        httpVersion: nil,
                        headerFields: ["Content-Type": "application/json"]
                    )!
                    body = Data("""
                    {
                      "success": false,
                      "data": null,
                      "error": {
                        "code": "AUTH_001",
                        "message": "Unauthorized",
                        "detail": null
                      }
                    }
                    """.utf8)
                } else {
                    XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer refreshed-access")
                    response = HTTPURLResponse(
                        url: request.url!,
                        statusCode: 200,
                        httpVersion: nil,
                        headerFields: ["Content-Type": "application/json"]
                    )!
                    body = Data("""
                    {
                      "success": true,
                      "data": {
                        "memberId": 1,
                        "email": "test@tokyo.ac.jp",
                        "nickname": "test-user",
                        "university": {
                          "universityId": 10,
                          "name": "The University of Tokyo",
                          "emailDomain": "tokyo.ac.jp"
                        },
                        "major": {
                          "majorId": 20,
                          "code": "CS",
                          "name": "Computer Science"
                        }
                      },
                      "error": null
                    }
                    """.utf8)
                }
            case "/api/v1/auth/reissue":
                let requestBody = try XCTUnwrap(request.httpBody)
                let decoded = try JSONDecoder().decode(ReissueRequest.self, from: requestBody)
                XCTAssertEqual(decoded, ReissueRequest(accessToken: "preview-access", refreshToken: "preview-refresh"))
                response = HTTPURLResponse(
                    url: request.url!,
                    statusCode: 200,
                    httpVersion: nil,
                    headerFields: ["Content-Type": "application/json"]
                )!
                body = Data("""
                {
                  "success": true,
                  "data": {
                    "accessToken": "refreshed-access",
                    "refreshToken": "refreshed-refresh"
                  },
                  "error": null
                }
                """.utf8)
            default:
                XCTFail("Unexpected path: \(path)")
                throw URLError(.badURL)
            }

            return (response, body)
        }

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let apiClient = APIClient(
            environment: AppEnvironment(
                apiBaseURL: URL(string: "http://localhost")!,
                enforcesAcademicSuffixValidation: true
            ),
            session: session
        )
        let client = HomeAPIClient(apiClient: apiClient, tokenStore: store)

        let profile = try await client.fetchProfile()

        XCTAssertEqual(membersMeCalls, 2)
        XCTAssertEqual(profile.nickname, "test-user")
        XCTAssertEqual(store.storedSession, TokenPair(accessToken: "refreshed-access", refreshToken: "refreshed-refresh"))
    }

    func testConcurrentClientsShareSingleReissueFlow() async throws {
        let store = tokenStore()
        let state = ConcurrentRefreshState()

        MockURLProtocol.requestHandler = { request in
            let path = try XCTUnwrap(request.url?.path)
            let response: HTTPURLResponse
            let body: Data

            switch path {
            case "/api/v1/members/me":
                let authorization = request.value(forHTTPHeaderField: "Authorization")
                if authorization == "Bearer preview-access" {
                    state.recordInitialMemberRequest()
                    response = HTTPURLResponse(
                        url: request.url!,
                        statusCode: 401,
                        httpVersion: nil,
                        headerFields: ["Content-Type": "application/json"]
                    )!
                    body = Data("""
                    {
                      "success": false,
                      "data": null,
                      "error": {
                        "code": "AUTH_001",
                        "message": "Unauthorized",
                        "detail": null
                      }
                    }
                    """.utf8)
                } else {
                    XCTAssertEqual(authorization, "Bearer refreshed-access")
                    state.recordRetriedMemberRequest()
                    response = HTTPURLResponse(
                        url: request.url!,
                        statusCode: 200,
                        httpVersion: nil,
                        headerFields: ["Content-Type": "application/json"]
                    )!
                    body = Data("""
                    {
                      "success": true,
                      "data": {
                        "memberId": 1,
                        "email": "test@tokyo.ac.jp",
                        "nickname": "test-user",
                        "university": {
                          "universityId": 10,
                          "name": "The University of Tokyo",
                          "emailDomain": "tokyo.ac.jp"
                        },
                        "major": {
                          "majorId": 20,
                          "code": "CS",
                          "name": "Computer Science"
                        }
                      },
                      "error": null
                    }
                    """.utf8)
                }
            case "/api/v1/auth/reissue":
                state.recordReissueRequest()
                let requestBody = try XCTUnwrap(request.httpBody)
                let decoded = try JSONDecoder().decode(ReissueRequest.self, from: requestBody)
                XCTAssertEqual(decoded, ReissueRequest(accessToken: "preview-access", refreshToken: "preview-refresh"))
                response = HTTPURLResponse(
                    url: request.url!,
                    statusCode: 200,
                    httpVersion: nil,
                    headerFields: ["Content-Type": "application/json"]
                )!
                body = Data("""
                {
                  "success": true,
                  "data": {
                    "accessToken": "refreshed-access",
                    "refreshToken": "refreshed-refresh"
                  },
                  "error": null
                }
                """.utf8)
            default:
                XCTFail("Unexpected path: \(path)")
                throw URLError(.badURL)
            }

            return (response, body)
        }

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let apiClient = APIClient(
            environment: AppEnvironment(
                apiBaseURL: URL(string: "http://localhost")!,
                enforcesAcademicSuffixValidation: true
            ),
            session: session
        )
        let sharedExecutor = AuthorizedRequestExecutor(apiClient: apiClient, tokenStore: store)
        let firstClient = HomeAPIClient(
            apiClient: apiClient,
            tokenStore: store,
            authorizedExecutor: sharedExecutor
        )
        let secondClient = HomeAPIClient(
            apiClient: apiClient,
            tokenStore: store,
            authorizedExecutor: sharedExecutor
        )

        async let firstProfile = firstClient.fetchProfile()
        async let secondProfile = secondClient.fetchProfile()
        let profiles = try await [firstProfile, secondProfile]

        XCTAssertEqual(profiles.map(\.nickname), ["test-user", "test-user"])
        XCTAssertEqual(state.initialMemberRequestCount, 2)
        XCTAssertEqual(state.reissuedRequestCount, 1)
        XCTAssertEqual(state.retriedMemberRequestCount, 2)
        XCTAssertEqual(store.storedSession, TokenPair(accessToken: "refreshed-access", refreshToken: "refreshed-refresh"))
    }

    func testFetchProfileMapsReissuePersistenceFailureToInvalidSession() async {
        let store = tokenStore()
        store.shouldFailOnSave = true
        var membersMeCalls = 0

        MockURLProtocol.requestHandler = { request in
            let path = try XCTUnwrap(request.url?.path)
            let response: HTTPURLResponse
            let body: Data

            switch path {
            case "/api/v1/members/me":
                membersMeCalls += 1
                XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer preview-access")
                response = HTTPURLResponse(
                    url: request.url!,
                    statusCode: 401,
                    httpVersion: nil,
                    headerFields: ["Content-Type": "application/json"]
                )!
                body = Data("""
                {
                  "success": false,
                  "data": null,
                  "error": {
                    "code": "AUTH_001",
                    "message": "Unauthorized",
                    "detail": null
                  }
                }
                """.utf8)
            case "/api/v1/auth/reissue":
                response = HTTPURLResponse(
                    url: request.url!,
                    statusCode: 200,
                    httpVersion: nil,
                    headerFields: ["Content-Type": "application/json"]
                )!
                body = Data("""
                {
                  "success": true,
                  "data": {
                    "accessToken": "refreshed-access",
                    "refreshToken": "refreshed-refresh"
                  },
                  "error": null
                }
                """.utf8)
            default:
                XCTFail("Unexpected path: \(path)")
                throw URLError(.badURL)
            }

            return (response, body)
        }

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let apiClient = APIClient(
            environment: AppEnvironment(
                apiBaseURL: URL(string: "http://localhost")!,
                enforcesAcademicSuffixValidation: true
            ),
            session: session
        )
        let client = HomeAPIClient(apiClient: apiClient, tokenStore: store)

        do {
            _ = try await client.fetchProfile()
            XCTFail("Expected invalid session")
        } catch let error as HomeClientError {
            XCTAssertEqual(error, .invalidSession)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }

        XCTAssertEqual(membersMeCalls, 1)
        XCTAssertNil(store.storedSession)
    }

    private func makeClient(
        tokenStore: TokenStore,
        expectedPath: String,
        responseBody: String,
        statusCode: Int = 200
    ) -> HomeAPIClient {
        MockURLProtocol.requestHandler = { request in
            XCTAssertTrue(request.url?.absoluteString.contains(expectedPath) == true)
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer preview-access")

            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: statusCode,
                httpVersion: nil,
                headerFields: ["Content-Type": "application/json"]
            )!
            return (response, Data(responseBody.utf8))
        }

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let apiClient = APIClient(
            environment: AppEnvironment(
                apiBaseURL: URL(string: "http://localhost")!,
                enforcesAcademicSuffixValidation: true
            ),
            session: session
        )
        return HomeAPIClient(apiClient: apiClient, tokenStore: tokenStore)
    }

    private func tokenStore() -> InMemoryTokenStore {
        let store = InMemoryTokenStore()
        store.storedSession = TokenPair(accessToken: "preview-access", refreshToken: "preview-refresh")
        return store
    }
}

private final class ConcurrentRefreshState: @unchecked Sendable {
    private let lock = NSLock()
    private var _initialMemberRequestCount = 0
    private var _retriedMemberRequestCount = 0
    private var _reissuedRequestCount = 0

    var initialMemberRequestCount: Int {
        lock.withLock { _initialMemberRequestCount }
    }

    var retriedMemberRequestCount: Int {
        lock.withLock { _retriedMemberRequestCount }
    }

    var reissuedRequestCount: Int {
        lock.withLock { _reissuedRequestCount }
    }

    func recordInitialMemberRequest() {
        lock.withLock { _initialMemberRequestCount += 1 }
    }

    func recordRetriedMemberRequest() {
        lock.withLock { _retriedMemberRequestCount += 1 }
    }

    func recordReissueRequest() {
        lock.withLock { _reissuedRequestCount += 1 }
    }
}
