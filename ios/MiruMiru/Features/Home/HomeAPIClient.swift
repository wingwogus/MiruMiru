import Foundation

final class HomeAPIClient: HomeClientProtocol, @unchecked Sendable {
    private let apiClient: APIClient
    private let tokenStore: TokenStore

    init(apiClient: APIClient, tokenStore: TokenStore) {
        self.apiClient = apiClient
        self.tokenStore = tokenStore
    }

    func fetchProfile() async throws -> HomeMemberProfile {
        let payload: ProfileResponse = try await requestPayload(path: "/api/v1/members/me")
        return payload.toDomain()
    }

    func fetchSemesters() async throws -> [HomeSemester] {
        let payload: [SemesterResponse] = try await requestPayload(path: "/api/v1/semesters")
        return payload.map(\.toDomain)
    }

    func fetchTimetable(semesterId: Int64) async throws -> HomeTimetable {
        let payload: TimetableResponse = try await requestPayload(
            path: "/api/v1/timetables/me?semesterId=\(semesterId)"
        )
        return payload.toDomain()
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
                throw HomeClientError.unexpected
            }
            return payload
        } catch let error as APIClientError {
            throw map(apiError: error)
        } catch let error as HomeClientError {
            throw error
        } catch {
            throw HomeClientError.unexpected
        }
    }

    private func readAccessToken() throws -> String {
        do {
            guard let session = try tokenStore.readSession(),
                  session.accessToken.isEmpty == false else {
                throw HomeClientError.invalidSession
            }
            return session.accessToken
        } catch let error as HomeClientError {
            throw error
        } catch {
            throw HomeClientError.invalidSession
        }
    }

    private func map(apiError: APIClientError) -> HomeClientError {
        switch apiError {
        case .transport:
            return .network
        case let .server(statusCode, _):
            if statusCode == 401 {
                return .invalidSession
            }
            return .unexpected
        default:
            return .unexpected
        }
    }
}

private extension HomeAPIClient {
    struct ProfileResponse: Decodable {
        let memberId: Int64
        let email: String
        let nickname: String
        let university: UniversityResponse
        let major: MajorResponse

        func toDomain() -> HomeMemberProfile {
            HomeMemberProfile(
                memberId: memberId,
                email: email,
                nickname: nickname,
                universityName: university.name,
                universityEmailDomain: university.emailDomain,
                majorCode: major.code,
                majorName: major.name
            )
        }
    }

    struct UniversityResponse: Decodable {
        let universityId: Int64
        let name: String
        let emailDomain: String
    }

    struct MajorResponse: Decodable {
        let majorId: Int64
        let code: String
        let name: String
    }

    struct SemesterResponse: Decodable {
        let id: Int64
        let academicYear: Int
        let term: String

        var toDomain: HomeSemester {
            HomeSemester(
                id: id,
                academicYear: academicYear,
                term: term
            )
        }
    }

    struct TimetableResponse: Decodable {
        let timetableId: Int64?
        let semester: SemesterResponse
        let lectures: [LectureResponse]

        func toDomain() -> HomeTimetable {
            HomeTimetable(
                timetableId: timetableId,
                semester: semester.toDomain,
                lectures: lectures.map(\.toDomain)
            )
        }
    }

    struct LectureResponse: Decodable {
        let id: Int64
        let code: String
        let name: String
        let professor: String
        let schedules: [ScheduleResponse]

        var toDomain: HomeLecture {
            HomeLecture(
                id: id,
                code: code,
                name: name,
                professor: professor,
                schedules: schedules.map(\.toDomain)
            )
        }
    }

    struct ScheduleResponse: Decodable {
        let dayOfWeek: String
        let startTime: String
        let endTime: String
        let location: String

        var toDomain: HomeLectureSchedule {
            HomeLectureSchedule(
                dayOfWeek: dayOfWeek,
                startTime: startTime,
                endTime: endTime,
                location: location
            )
        }
    }
}
