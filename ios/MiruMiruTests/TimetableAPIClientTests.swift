import XCTest
@testable import MiruMiru

final class TimetableAPIClientTests: XCTestCase {
    override func tearDown() {
        MockURLProtocol.requestHandler = nil
        super.tearDown()
    }

    func testFetchLectureCatalogDecodesLectures() async throws {
        let client = makeClient(
            expectedPath: "/api/v1/semesters/20261/lectures",
            expectedMethod: "GET",
            responseBody: """
            {
              "success": true,
              "data": [
                {
                  "id": 101,
                  "code": "CS101",
                  "name": "Computer Science",
                  "professor": "Prof. Ito",
                  "credit": 3,
                  "major": { "majorId": 1, "code": "CS", "name": "Computer Science" },
                  "schedules": [
                    { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "10:30", "location": "Room 301" }
                  ]
                }
              ],
              "error": null
            }
            """
        )

        let lectures = try await client.fetchLectureCatalog(semesterId: 20261)
        XCTAssertEqual(lectures.count, 1)
        XCTAssertEqual(lectures[0].id, 101)
        XCTAssertEqual(lectures[0].major?.code, "CS")
        XCTAssertEqual(lectures[0].primaryLocation, "Room 301")
    }

    func testFetchMyTimetableDecodesEmptyPayload() async throws {
        let client = makeClient(
            expectedPath: "/api/v1/timetables/me?semesterId=20261",
            expectedMethod: "GET",
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

        let timetable = try await client.fetchMyTimetable(semesterId: 20261)
        XCTAssertNil(timetable.timetableId)
        XCTAssertEqual(timetable.semester, TimetableSemester(id: 20261, academicYear: 2026, term: "SPRING"))
        XCTAssertTrue(timetable.lectures.isEmpty)
    }

    func testAddLecturePostsExpectedBody() async throws {
        let client = makeClient(
            expectedPath: "/api/v1/timetables/me/lectures",
            expectedMethod: "POST",
            responseBody: """
            { "success": true, "data": null, "error": null }
            """
        ) { request in
            let body = try XCTUnwrap(request.httpBody)
            let decoded = try JSONDecoder().decode(AddLectureRequest.self, from: body)
            XCTAssertEqual(decoded, AddLectureRequest(semesterId: 20261, lectureId: 101))
        }

        try await client.addLecture(semesterId: 20261, lectureId: 101)
    }

    func testRemoveLectureUsesDeleteEndpoint() async throws {
        let client = makeClient(
            expectedPath: "/api/v1/timetables/me/lectures/101?semesterId=20261",
            expectedMethod: "DELETE",
            responseBody: """
            { "success": true, "data": null, "error": null }
            """
        )

        try await client.removeLecture(semesterId: 20261, lectureId: 101)
    }

    func testAddLectureMapsConflictPayloadToTimeConflict() async {
        let client = makeClient(
            expectedPath: "/api/v1/timetables/me/lectures",
            expectedMethod: "POST",
            responseBody: """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "TIMETABLE_006",
                "message": "error.timetable_conflict",
                "detail": null
              }
            }
            """,
            statusCode: 409
        )

        do {
            try await client.addLecture(semesterId: 20261, lectureId: 101)
            XCTFail("Expected conflict")
        } catch let error as TimetableClientError {
            XCTAssertEqual(error, .timeConflict)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    private func makeClient(
        expectedPath: String,
        expectedMethod: String,
        responseBody: String,
        statusCode: Int = 200,
        requestValidator: ((URLRequest) throws -> Void)? = nil
    ) -> TimetableAPIClient {
        MockURLProtocol.requestHandler = { request in
            XCTAssertEqual(request.httpMethod, expectedMethod)
            XCTAssertTrue(request.url?.absoluteString.contains(expectedPath) == true)
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer preview-access")
            try requestValidator?(request)

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

        let tokenStore = InMemoryTokenStore()
        tokenStore.storedSession = TokenPair(accessToken: "preview-access", refreshToken: "preview-refresh")
        return TimetableAPIClient(apiClient: apiClient, tokenStore: tokenStore)
    }
}

private struct AddLectureRequest: Decodable, Equatable {
    let semesterId: Int64
    let lectureId: Int64
}
