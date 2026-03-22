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

    func testFetchProfileMapsUnauthorizedToInvalidSession() async {
        let client = makeClient(
            tokenStore: tokenStore(),
            expectedPath: "/api/v1/members/me",
            responseBody: """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "AUTH_001",
                "message": "Unauthorized",
                "detail": null
              }
            }
            """,
            statusCode: 401
        )

        do {
            _ = try await client.fetchProfile()
            XCTFail("Expected invalid session")
        } catch let error as HomeClientError {
            XCTAssertEqual(error, .invalidSession)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
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
