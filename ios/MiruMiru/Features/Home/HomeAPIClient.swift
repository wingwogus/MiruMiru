import Foundation

final class HomeAPIClient: HomeClientProtocol, @unchecked Sendable {
    private let apiClient: APIClient
    private let authorizedExecutor: AuthorizedRequestExecutor

    init(
        apiClient: APIClient,
        tokenStore: TokenStore,
        authorizedExecutor: AuthorizedRequestExecutor? = nil
    ) {
        self.apiClient = apiClient
        self.authorizedExecutor = authorizedExecutor ?? AuthorizedRequestExecutor(apiClient: apiClient, tokenStore: tokenStore)
    }

    func fetchProfile() async throws -> HomeMemberProfile {
        let payload: ProfileResponse = try await requestPayload(
            path: "/api/v1/members/me",
            cachePolicy: RequestCachePolicy(
                key: APICacheKey.sharedMemberMe,
                maxAge: APICacheTTL.memberProfile
            )
        )
        return payload.toDomain()
    }

    func fetchSemesters() async throws -> [HomeSemester] {
        let payload: [SemesterResponse] = try await requestPayload(
            path: "/api/v1/semesters",
            cachePolicy: RequestCachePolicy(
                key: APICacheKey.sharedSemesters,
                maxAge: APICacheTTL.semesters
            )
        )
        return payload.map(\.toDomain)
    }

    func fetchTimetable(semesterId: Int64) async throws -> HomeTimetable {
        let payload: TimetableResponse = try await requestPayload(
            path: "/api/v1/timetables/me?semesterId=\(semesterId)",
            cachePolicy: RequestCachePolicy(
                key: APICacheKey.sharedTimetable(semesterId: semesterId),
                maxAge: APICacheTTL.timetable
            )
        )
        return payload.toDomain()
    }

    func fetchHotPosts() async throws -> [HotPostSummary] {
        let payload: [HotPostResponse] = try await requestPayload(
            path: "/api/v1/posts/hot",
            cachePolicy: RequestCachePolicy(
                key: APICacheKey.sharedHotPosts,
                maxAge: APICacheTTL.hotPosts
            )
        )
        return payload.map(\.toDomain)
    }

    func invalidateCache() async {
        await authorizedExecutor.invalidateCache(key: APICacheKey.sharedMemberMe)
        await authorizedExecutor.invalidateCache(key: APICacheKey.sharedSemesters)
        await authorizedExecutor.invalidateCache(key: APICacheKey.sharedHotPosts)
        await authorizedExecutor.invalidateCache(prefix: APICacheKey.sharedTimetablePrefix)
    }

    private func requestPayload<Response: Decodable>(
        path: String,
        cachePolicy: RequestCachePolicy? = nil
    ) async throws -> Response {
        do {
            let data = try await authorizedExecutor.get(
                path: path,
                cachePolicy: cachePolicy
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

    struct HotPostResponse: Decodable {
        let postId: Int64
        let boardId: Int64
        let boardCode: String
        let boardName: String
        let title: String
        let authorDisplayName: String
        let isAnonymous: Bool
        let likeCount: Int
        let commentCount: Int
        let createdAt: String

        var toDomain: HotPostSummary {
            HotPostSummary(
                id: postId,
                boardId: boardId,
                boardCode: boardCode,
                boardName: boardName,
                title: title,
                authorDisplayName: authorDisplayName,
                isAnonymous: isAnonymous,
                likeCount: likeCount,
                commentCount: commentCount,
                createdAt: createdAt
            )
        }
    }
}
