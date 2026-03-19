import Foundation

final class TimetableAPIClient: TimetableClientProtocol, @unchecked Sendable {
    private let apiClient: APIClient
    private let tokenStore: TokenStore
    private let encoder = JSONEncoder()

    init(apiClient: APIClient, tokenStore: TokenStore) {
        self.apiClient = apiClient
        self.tokenStore = tokenStore
    }

    func fetchMemberContext() async throws -> TimetableMemberContext {
        let payload: MemberContextResponse = try await requestPayload(path: "/api/v1/members/me")
        return payload.toDomain()
    }

    func fetchSemesters() async throws -> [TimetableSemester] {
        let payload: [SemesterResponse] = try await requestPayload(path: "/api/v1/semesters")
        return payload.map(\.toDomain)
    }

    func fetchLectureCatalog(semesterId: Int64) async throws -> [TimetableLectureItem] {
        let payload: [LectureResponse] = try await requestPayload(path: "/api/v1/semesters/\(semesterId)/lectures")
        return payload.map(\.toDomain)
    }

    func fetchMyTimetable(semesterId: Int64) async throws -> TimetableDetail {
        let payload: TimetableResponse = try await requestPayload(path: "/api/v1/timetables/me?semesterId=\(semesterId)")
        return payload.toDomain()
    }

    func addLecture(semesterId: Int64, lectureId: Int64) async throws {
        let request = AddLectureRequest(semesterId: semesterId, lectureId: lectureId)
        let body = try encoder.encode(request)
        try await sendEmpty(path: "/api/v1/timetables/me/lectures", method: .post, body: body)
    }

    func removeLecture(semesterId: Int64, lectureId: Int64) async throws {
        try await sendEmpty(
            path: "/api/v1/timetables/me/lectures/\(lectureId)?semesterId=\(semesterId)",
            method: .delete
        )
    }

    private func requestPayload<Response: Decodable>(path: String) async throws -> Response {
        let accessToken = try readAccessToken()

        do {
            let (data, _) = try await apiClient.send(
                path: path,
                method: .get,
                accessToken: accessToken
            )
            let envelope = try apiClient.decode(APIResponseEnvelope<Response>.self, from: data)
            guard envelope.success, let payload = envelope.data else {
                throw TimetableClientError.unexpected
            }
            return payload
        } catch let error as APIClientError {
            throw map(apiError: error)
        } catch let error as TimetableClientError {
            throw error
        } catch {
            throw TimetableClientError.unexpected
        }
    }

    private func sendEmpty(
        path: String,
        method: HTTPMethod,
        body: Data? = nil
    ) async throws {
        let accessToken = try readAccessToken()

        do {
            let (data, _) = try await apiClient.send(
                path: path,
                method: method,
                body: body,
                accessToken: accessToken
            )
            let envelope = try apiClient.decode(APIResponseEnvelope<EmptyPayload>.self, from: data)
            guard envelope.success else {
                throw TimetableClientError.unexpected
            }
        } catch let error as APIClientError {
            throw map(apiError: error)
        } catch let error as TimetableClientError {
            throw error
        } catch {
            throw TimetableClientError.unexpected
        }
    }

    private func readAccessToken() throws -> String {
        do {
            guard let session = try tokenStore.readSession(),
                  session.accessToken.isEmpty == false else {
                throw TimetableClientError.invalidSession
            }
            return session.accessToken
        } catch let error as TimetableClientError {
            throw error
        } catch {
            throw TimetableClientError.invalidSession
        }
    }

    private func map(apiError: APIClientError) -> TimetableClientError {
        switch apiError {
        case .transport:
            return .network
        case let .server(statusCode, payload):
            if statusCode == 401 {
                return .invalidSession
            }

            switch payload?.code {
            case "TIMETABLE_002":
                return .duplicateLecture
            case "TIMETABLE_006":
                return .timeConflict
            case "TIMETABLE_001", "TIMETABLE_005":
                return .lectureNotFound
            default:
                return .unexpected
            }
        default:
            return .unexpected
        }
    }
}

private extension TimetableAPIClient {
    struct AddLectureRequest: Codable, Equatable {
        let semesterId: Int64
        let lectureId: Int64
    }

    struct MemberContextResponse: Decodable {
        let memberId: Int64
        let email: String
        let nickname: String
        let major: MajorResponse

        func toDomain() -> TimetableMemberContext {
            TimetableMemberContext(
                memberId: memberId,
                nickname: nickname,
                email: email,
                majorCode: major.code,
                majorName: major.name
            )
        }
    }

    struct MajorResponse: Decodable {
        let majorId: Int64
        let code: String
        let name: String

        var toDomain: TimetableMajorSummary {
            TimetableMajorSummary(majorId: majorId, code: code, name: name)
        }
    }

    struct SemesterResponse: Decodable {
        let id: Int64
        let academicYear: Int
        let term: String

        var toDomain: TimetableSemester {
            TimetableSemester(id: id, academicYear: academicYear, term: term)
        }
    }

    struct ScheduleResponse: Decodable {
        let dayOfWeek: String
        let startTime: String
        let endTime: String
        let location: String

        var toDomain: TimetableLectureSchedule {
            TimetableLectureSchedule(
                dayOfWeek: dayOfWeek,
                startTime: startTime,
                endTime: endTime,
                location: location
            )
        }
    }

    struct LectureResponse: Decodable {
        let id: Int64
        let code: String
        let name: String
        let professor: String
        let credit: Int
        let major: MajorResponse?
        let schedules: [ScheduleResponse]

        var toDomain: TimetableLectureItem {
            TimetableLectureItem(
                id: id,
                code: code,
                name: name,
                professor: professor,
                credit: credit,
                major: major?.toDomain,
                schedules: schedules.map(\.toDomain)
            )
        }
    }

    struct TimetableResponse: Decodable {
        let timetableId: Int64?
        let semester: SemesterResponse
        let lectures: [LectureResponse]

        func toDomain() -> TimetableDetail {
            TimetableDetail(
                timetableId: timetableId,
                semester: semester.toDomain,
                lectures: lectures.map(\.toDomain)
            )
        }
    }
}
