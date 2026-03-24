import Foundation

final class CourseReviewsAPIClient: CourseReviewsClientProtocol, @unchecked Sendable {
    private let apiClient: APIClient
    private let tokenStore: TokenStore
    private let encoder = JSONEncoder()

    init(apiClient: APIClient, tokenStore: TokenStore) {
        self.apiClient = apiClient
        self.tokenStore = tokenStore
    }

    func fetchReviewFeed(page: Int, size: Int) async throws -> CourseReviewFeedPage {
        let payload: FeedPageResponse = try await requestPayload(path: "/api/v1/course-reviews?page=\(page)&size=\(size)")
        return payload.toDomain()
    }

    func fetchTargets(query: String, page: Int, size: Int) async throws -> CourseReviewTargetPage {
        let encodedQuery = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let payload: TargetPageResponse = try await requestPayload(
            path: "/api/v1/course-review-targets?query=\(encodedQuery)&page=\(page)&size=\(size)"
        )
        return payload.toDomain()
    }

    func fetchTargetReviews(targetId: Int64, page: Int, size: Int) async throws -> CourseReviewPage {
        let payload: ReviewPageResponse = try await requestPayload(
            path: "/api/v1/course-review-targets/\(targetId)/reviews?page=\(page)&size=\(size)"
        )
        return payload.toDomain()
    }

    func fetchMyReview(targetId: Int64) async throws -> CourseReviewItem {
        let payload: ReviewItemResponse = try await requestPayload(
            path: "/api/v1/course-review-targets/\(targetId)/reviews/me"
        )
        return payload.toDomain
    }

    func createReview(targetId: Int64, payload: CourseReviewWritePayload) async throws -> Int64 {
        let body = try encoder.encode(payload)
        let response: ReviewIdResponse = try await sendPayload(
            path: "/api/v1/course-review-targets/\(targetId)/reviews",
            method: .post,
            body: body
        )
        return response.reviewId
    }

    func updateMyReview(targetId: Int64, payload: CourseReviewWritePayload) async throws -> Int64 {
        let body = try encoder.encode(payload)
        let response: ReviewIdResponse = try await sendPayload(
            path: "/api/v1/course-review-targets/\(targetId)/reviews/me",
            method: .put,
            body: body
        )
        return response.reviewId
    }

    func deleteMyReview(targetId: Int64) async throws {
        try await sendEmpty(path: "/api/v1/course-review-targets/\(targetId)/reviews/me", method: .delete)
    }

    private func requestPayload<Response: Decodable>(path: String) async throws -> Response {
        let accessToken = try readAccessToken()

        do {
            let (data, _) = try await apiClient.send(path: path, method: .get, accessToken: accessToken)
            let envelope = try apiClient.decode(APIResponseEnvelope<Response>.self, from: data)
            guard envelope.success, let payload = envelope.data else {
                throw CourseReviewsClientError.unexpected
            }
            return payload
        } catch let error as APIClientError {
            throw map(apiError: error)
        } catch let error as CourseReviewsClientError {
            throw error
        } catch {
            throw CourseReviewsClientError.unexpected
        }
    }

    private func sendPayload<Response: Decodable>(path: String, method: HTTPMethod, body: Data) async throws -> Response {
        let accessToken = try readAccessToken()

        do {
            let (data, _) = try await apiClient.send(path: path, method: method, body: body, accessToken: accessToken)
            let envelope = try apiClient.decode(APIResponseEnvelope<Response>.self, from: data)
            guard envelope.success, let payload = envelope.data else {
                throw CourseReviewsClientError.unexpected
            }
            return payload
        } catch let error as APIClientError {
            throw map(apiError: error)
        } catch let error as CourseReviewsClientError {
            throw error
        } catch {
            throw CourseReviewsClientError.unexpected
        }
    }

    private func sendEmpty(path: String, method: HTTPMethod) async throws {
        let accessToken = try readAccessToken()

        do {
            let (data, _) = try await apiClient.send(path: path, method: method, accessToken: accessToken)
            let envelope = try apiClient.decode(APIResponseEnvelope<EmptyPayload>.self, from: data)
            guard envelope.success else {
                throw CourseReviewsClientError.unexpected
            }
        } catch let error as APIClientError {
            throw map(apiError: error)
        } catch let error as CourseReviewsClientError {
            throw error
        } catch {
            throw CourseReviewsClientError.unexpected
        }
    }

    private func readAccessToken() throws -> String {
        do {
            guard let session = try tokenStore.readSession(),
                  session.accessToken.isEmpty == false else {
                throw CourseReviewsClientError.invalidSession
            }
            return session.accessToken
        } catch let error as CourseReviewsClientError {
            throw error
        } catch {
            throw CourseReviewsClientError.invalidSession
        }
    }

    private func map(apiError: APIClientError) -> CourseReviewsClientError {
        switch apiError {
        case .transport:
            return .network
        case let .server(statusCode, payload):
            if statusCode == 401 {
                return .invalidSession
            }
            switch payload?.code {
            case "COURSE_002":
                return .reviewNotFound
            case "COURSE_003":
                return .reviewAlreadyExists
            case "COURSE_004":
                return .targetNotFound
            default:
                return .unexpected
            }
        default:
            return .unexpected
        }
    }
}

private extension CourseReviewsAPIClient {
    struct ReviewIdResponse: Decodable {
        let reviewId: Int64
    }

    struct FeedPageResponse: Decodable {
        let items: [FeedItemResponse]
        let page: Int
        let size: Int
        let totalElements: Int
        let totalPages: Int
        let hasNext: Bool

        func toDomain() -> CourseReviewFeedPage {
            CourseReviewFeedPage(items: items.map(\.toDomain), page: page, size: size, totalElements: totalElements, totalPages: totalPages, hasNext: hasNext)
        }
    }

    struct FeedItemResponse: Decodable {
        let reviewId: Int64
        let targetId: Int64
        let courseId: Int64
        let courseCode: String
        let courseName: String
        let professorDisplayName: String
        let displayName: String
        let overallRating: Int
        let difficulty: Int
        let workload: Int
        let wouldTakeAgain: Bool
        let content: String
        let academicYear: Int
        let term: String
        let isMine: Bool
        let createdAt: String
        let updatedAt: String

        var toDomain: CourseReviewFeedItem {
            CourseReviewFeedItem(
                reviewId: reviewId,
                targetId: targetId,
                courseId: courseId,
                courseCode: courseCode,
                courseName: courseName,
                professorDisplayName: professorDisplayName,
                displayName: displayName,
                overallRating: overallRating,
                difficulty: difficulty,
                workload: workload,
                wouldTakeAgain: wouldTakeAgain,
                content: content,
                academicYear: academicYear,
                term: term,
                isMine: isMine,
                createdAt: createdAt,
                updatedAt: updatedAt
            )
        }
    }

    struct TargetPageResponse: Decodable {
        let items: [TargetItemResponse]
        let page: Int
        let size: Int
        let totalElements: Int
        let totalPages: Int
        let hasNext: Bool

        func toDomain() -> CourseReviewTargetPage {
            CourseReviewTargetPage(items: items.map(\.toDomain), page: page, size: size, totalElements: totalElements, totalPages: totalPages, hasNext: hasNext)
        }
    }

    struct TargetItemResponse: Decodable {
        let targetId: Int64
        let courseId: Int64
        let courseCode: String
        let courseName: String
        let professorDisplayName: String
        let displayName: String

        var toDomain: CourseReviewTargetItem {
            CourseReviewTargetItem(
                targetId: targetId,
                courseId: courseId,
                courseCode: courseCode,
                courseName: courseName,
                professorDisplayName: professorDisplayName,
                displayName: displayName
            )
        }
    }

    struct ReviewPageResponse: Decodable {
        let summary: SummaryResponse
        let reviews: [ReviewItemResponse]
        let page: Int
        let size: Int
        let totalElements: Int
        let totalPages: Int
        let hasNext: Bool

        func toDomain() -> CourseReviewPage {
            CourseReviewPage(summary: summary.toDomain, reviews: reviews.map(\.toDomain), page: page, size: size, totalElements: totalElements, totalPages: totalPages, hasNext: hasNext)
        }
    }

    struct SummaryResponse: Decodable {
        let targetId: Int64
        let courseId: Int64
        let courseCode: String
        let courseName: String
        let professorDisplayName: String
        let displayName: String
        let reviewCount: Int
        let averageOverall: Double?
        let averageDifficulty: Double?
        let averageWorkload: Double?
        let wouldTakeAgainRate: Double?

        var toDomain: CourseReviewSummary {
            CourseReviewSummary(
                targetId: targetId,
                courseId: courseId,
                courseCode: courseCode,
                courseName: courseName,
                professorDisplayName: professorDisplayName,
                displayName: displayName,
                reviewCount: reviewCount,
                averageOverall: averageOverall,
                averageDifficulty: averageDifficulty,
                averageWorkload: averageWorkload,
                wouldTakeAgainRate: wouldTakeAgainRate
            )
        }
    }

    struct ReviewItemResponse: Decodable {
        let reviewId: Int64
        let overallRating: Int
        let difficulty: Int
        let workload: Int
        let wouldTakeAgain: Bool
        let content: String
        let academicYear: Int
        let term: String
        let professorDisplayName: String
        let isMine: Bool
        let createdAt: String
        let updatedAt: String

        var toDomain: CourseReviewItem {
            CourseReviewItem(
                reviewId: reviewId,
                overallRating: overallRating,
                difficulty: difficulty,
                workload: workload,
                wouldTakeAgain: wouldTakeAgain,
                content: content,
                academicYear: academicYear,
                term: term,
                professorDisplayName: professorDisplayName,
                isMine: isMine,
                createdAt: createdAt,
                updatedAt: updatedAt
            )
        }
    }
}
