import Foundation

struct RequestCachePolicy: Sendable {
    let key: String
    let maxAge: TimeInterval
}

enum APICacheTTL {
    static let memberProfile: TimeInterval = 300
    static let semesters: TimeInterval = 300
    static let hotPosts: TimeInterval = 120
    static let timetable: TimeInterval = 300
    static let boardsList: TimeInterval = 180
    static let boardsFeed: TimeInterval = 120
    static let boardsPostDetail: TimeInterval = 120
    static let reviewsFeed: TimeInterval = 180
    static let reviewsDetail: TimeInterval = 180
    static let myReview: TimeInterval = 120
}

enum APICacheKey {
    static let sharedMemberMe = "shared.member-me"
    static let sharedSemesters = "shared.semesters"
    static let sharedHotPosts = "shared.hot-posts"
    static let sharedTimetablePrefix = "shared.timetable."
    static let boardsPrefix = "boards."
    static let reviewsPrefix = "reviews."

    static func sharedTimetable(semesterId: Int64) -> String {
        "\(sharedTimetablePrefix)\(semesterId)"
    }

    static var boardsList: String { "\(boardsPrefix)list" }

    static func boardsFeed(_ boardId: Int64) -> String {
        "\(boardsPrefix)feed.\(boardId)"
    }

    static func boardsPostDetail(_ postId: Int64) -> String {
        "\(boardsPrefix)post.\(postId)"
    }

    static func reviewsFeed(page: Int, size: Int) -> String {
        "\(reviewsPrefix)feed.\(page).\(size)"
    }

    static func reviewsTargetReviews(targetId: Int64, page: Int, size: Int) -> String {
        "\(reviewsPrefix)target.\(targetId).\(page).\(size)"
    }

    static func reviewsMyReview(targetId: Int64) -> String {
        "\(reviewsPrefix)mine.\(targetId)"
    }
}

actor RequestCacheStore {
    private struct Entry: Sendable {
        let data: Data
        let storedAt: Date
    }

    private var entries: [String: Entry] = [:]

    func cachedData(for policy: RequestCachePolicy, now: Date = Date()) -> Data? {
        guard let entry = entries[policy.key] else {
            return nil
        }

        guard now.timeIntervalSince(entry.storedAt) <= policy.maxAge else {
            entries.removeValue(forKey: policy.key)
            return nil
        }

        #if DEBUG
        print("[RequestCache] hit \(policy.key)")
        #endif
        return entry.data
    }

    func store(_ data: Data, for key: String, now: Date = Date()) {
        entries[key] = Entry(data: data, storedAt: now)
        #if DEBUG
        print("[RequestCache] store \(key)")
        #endif
    }

    func invalidate(key: String) {
        entries.removeValue(forKey: key)
        #if DEBUG
        print("[RequestCache] invalidate \(key)")
        #endif
    }

    func invalidate(prefix: String) {
        let matchingKeys = entries.keys.filter { $0.hasPrefix(prefix) }
        for key in matchingKeys {
            entries.removeValue(forKey: key)
        }
        #if DEBUG
        if matchingKeys.isEmpty == false {
            print("[RequestCache] invalidate prefix \(prefix) count=\(matchingKeys.count)")
        }
        #endif
    }

    func clear() {
        entries.removeAll()
        #if DEBUG
        print("[RequestCache] clear all")
        #endif
    }
}
