import Foundation

struct CourseReviewTargetItem: Hashable, Identifiable, Sendable {
    let targetId: Int64
    let courseId: Int64
    let courseCode: String
    let courseName: String
    let professorDisplayName: String
    let displayName: String

    var id: Int64 { targetId }
}

struct CourseReviewFeedItem: Identifiable, Equatable, Sendable {
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

    var id: Int64 { reviewId }
}

struct CourseReviewFeedPage: Equatable, Sendable {
    let items: [CourseReviewFeedItem]
    let page: Int
    let size: Int
    let totalElements: Int
    let totalPages: Int
    let hasNext: Bool
}

struct CourseReviewSummary: Equatable, Sendable {
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
}

struct CourseReviewItem: Identifiable, Equatable, Sendable {
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

    var id: Int64 { reviewId }
}

struct CourseReviewPage: Equatable, Sendable {
    let summary: CourseReviewSummary
    let reviews: [CourseReviewItem]
    let page: Int
    let size: Int
    let totalElements: Int
    let totalPages: Int
    let hasNext: Bool
}

struct CourseReviewTargetPage: Equatable, Sendable {
    let items: [CourseReviewTargetItem]
    let page: Int
    let size: Int
    let totalElements: Int
    let totalPages: Int
    let hasNext: Bool
}

struct CourseReviewWritePayload: Codable, Equatable, Sendable {
    let academicYear: Int
    let term: String
    let overallRating: Int
    let difficulty: Int
    let workload: Int
    let wouldTakeAgain: Bool
    let content: String
}

enum CourseReviewsFeedFilter: String, CaseIterable, Identifiable, Sendable {
    case all = "All"
    case liberalArts = "Liberal Arts"
    case major = "Major"
    case highRating = "High Rating"

    var id: String { rawValue }
}

enum CourseReviewWriteChip: String, CaseIterable, Hashable, Sendable {
    case easy = "Easy A"
    case challenging = "Challenging"
    case lightLoad = "Light Load"
    case heavyLoad = "Heavy Load"
    case wouldTakeAgain = "Would Take Again"
}

enum CourseReviewTagTone: Sendable {
    case green
    case blue
    case purple
    case orange
    case red
}

struct CourseReviewPresentationTag: Identifiable, Equatable, Sendable {
    let title: String
    let tone: CourseReviewTagTone

    var id: String { "\(title)-\(String(describing: tone))" }
}

enum CourseReviewsClientError: Error, Equatable {
    case invalidSession
    case reviewAlreadyExists
    case reviewNotFound
    case targetNotFound
    case network
    case unexpected
}

enum CourseReviewsFeedState: Equatable {
    case loading
    case empty
    case loaded(CourseReviewFeedPage)
    case failed(CourseReviewsClientError)
}

enum CourseReviewTargetSearchState: Equatable {
    case idle
    case loading
    case empty
    case loaded(CourseReviewTargetPage)
    case failed(CourseReviewsClientError)
}

enum CourseReviewDetailState: Equatable {
    case loading
    case empty(CourseReviewSummary, myReview: CourseReviewItem?)
    case loaded(CourseReviewPage, myReview: CourseReviewItem?)
    case failed(CourseReviewsClientError)
}

protocol CourseReviewsClientProtocol: Sendable {
    func fetchReviewFeed(page: Int, size: Int) async throws -> CourseReviewFeedPage
    func fetchTargets(query: String, page: Int, size: Int) async throws -> CourseReviewTargetPage
    func fetchTargetReviews(targetId: Int64, page: Int, size: Int) async throws -> CourseReviewPage
    func fetchMyReview(targetId: Int64) async throws -> CourseReviewItem
    func createReview(targetId: Int64, payload: CourseReviewWritePayload) async throws -> Int64
    func updateMyReview(targetId: Int64, payload: CourseReviewWritePayload) async throws -> Int64
    func deleteMyReview(targetId: Int64) async throws
}

enum CourseReviewFormatters {
    private static let iso8601WithFractionalSeconds: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let iso8601 = ISO8601DateFormatter()

    static func relativeTimeText(from source: String, now: Date = Date()) -> String {
        guard let date = iso8601WithFractionalSeconds.date(from: source) ?? iso8601.date(from: source) else {
            return "Recently"
        }

        let delta = max(0, Int(now.timeIntervalSince(date)))
        if delta < 60 { return "Just now" }
        if delta < 3600 { return "\(delta / 60)m ago" }
        if delta < 86_400 { return "\(delta / 3600)h ago" }
        if delta < 172_800 { return "Yesterday" }
        return "\(delta / 86_400)d ago"
    }
}

extension CourseReviewFeedItem {
    var presentationTags: [CourseReviewPresentationTag] {
        var tags: [CourseReviewPresentationTag] = []
        if difficulty <= 2 { tags.append(.init(title: "Easy A", tone: .green)) }
        if difficulty >= 4 { tags.append(.init(title: "Challenging", tone: .orange)) }
        if workload <= 2 { tags.append(.init(title: "Light Load", tone: .blue)) }
        if workload >= 4 { tags.append(.init(title: "Heavy Load", tone: .red)) }
        if wouldTakeAgain { tags.append(.init(title: "Would Take Again", tone: .purple)) }
        return tags
    }

    var termLabel: String {
        "Semester \(term == "SPRING" ? "1" : "2"), \(academicYear)"
    }

    var relativeTimeText: String {
        CourseReviewFormatters.relativeTimeText(from: createdAt)
    }
}

extension CourseReviewItem {
    var presentationTags: [CourseReviewPresentationTag] {
        var tags: [CourseReviewPresentationTag] = []
        if difficulty <= 2 { tags.append(.init(title: "Easy A", tone: .green)) }
        if difficulty >= 4 { tags.append(.init(title: "Challenging", tone: .orange)) }
        if workload <= 2 { tags.append(.init(title: "Light Load", tone: .blue)) }
        if workload >= 4 { tags.append(.init(title: "Heavy Load", tone: .red)) }
        if wouldTakeAgain { tags.append(.init(title: "Would Take Again", tone: .purple)) }
        return tags
    }

    var termLabel: String {
        "Semester \(term == "SPRING" ? "1" : "2"), \(academicYear)"
    }

    var relativeTimeText: String {
        CourseReviewFormatters.relativeTimeText(from: createdAt)
    }
}
