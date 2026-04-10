import Foundation

struct BoardSummary: Identifiable, Hashable, Equatable, Sendable {
    let id: Int64
    let code: String
    let name: String
    let isAnonymousAllowed: Bool
}

struct HotPostSummary: Identifiable, Hashable, Equatable, Sendable {
    let id: Int64
    let boardId: Int64
    let boardCode: String
    let boardName: String
    let title: String
    let authorDisplayName: String
    let isAnonymous: Bool
    let likeCount: Int
    let commentCount: Int
    let createdAt: String

    var relativeCreatedAt: String {
        RelativeTimestampFormatter.string(from: createdAt)
    }
}

struct BoardPostSummary: Identifiable, Hashable, Equatable, Sendable {
    let id: Int64
    let title: String
    let authorDisplayName: String
    let isAnonymous: Bool
    let likeCount: Int
    let commentCount: Int
    let createdAt: String

    var relativeCreatedAt: String {
        RelativeTimestampFormatter.string(from: createdAt)
    }
}

struct PostImageSummary: Identifiable, Hashable, Equatable, Sendable {
    let imageUrl: String
    let displayOrder: Int

    var id: String { "\(displayOrder)-\(imageUrl)" }
}

struct PostCommentItem: Identifiable, Hashable, Equatable, Sendable {
    let commentId: Int64
    let parentId: Int64?
    let content: String
    let authorMemberId: Int64?
    let authorDisplayName: String
    let isAnonymous: Bool
    let isMine: Bool
    let isDeleted: Bool
    let createdAt: String
    let children: [PostCommentItem]

    var id: Int64 { commentId }

    var relativeCreatedAt: String {
        RelativeTimestampFormatter.string(from: createdAt)
    }
}

struct PostDetailContent: Equatable, Sendable {
    let postId: Int64
    let boardId: Int64
    let boardCode: String
    let boardName: String
    let title: String
    let content: String
    let authorMemberId: Int64
    let authorDisplayName: String
    let isAnonymous: Bool
    let isMine: Bool
    let isLikedByMe: Bool
    let likeCount: Int
    let commentCount: Int
    let comments: [PostCommentItem]
    let images: [PostImageSummary]
    let createdAt: String
    let updatedAt: String

    var relativeCreatedAt: String {
        RelativeTimestampFormatter.string(from: createdAt)
    }
}

struct CreatePostInput: Equatable, Sendable {
    let title: String
    let content: String
    let isAnonymous: Bool
}

struct CreateCommentInput: Equatable, Sendable {
    let content: String
    let parentId: Int64?
    let isAnonymous: Bool
}

struct BoardsHomeContent: Equatable {
    let sections: [BoardSection]
    let hotPosts: [HotPostSummary]
}

enum BoardsHomeState: Equatable {
    case loading
    case loaded(BoardsHomeContent)
    case empty
    case failed(BoardsFailure)
}

enum BoardFeedState: Equatable {
    case loading
    case loaded([BoardPostSummary])
    case empty
    case failed(BoardsFailure)
}

enum PostDetailState: Equatable {
    case loading
    case loaded(PostDetailContent)
    case failed(BoardsFailure)
}

enum BoardsFailure: Error, Equatable {
    case invalidSession
    case forbidden
    case boardNotFound
    case postNotFound
    case anonymousNotAllowed
    case deletedPost
    case commentNotFound
    case replyDepthNotAllowed
    case invalidCommentParent
    case network
    case unexpected

    var message: String {
        switch self {
        case .invalidSession:
            return "Your session expired. Please log in again."
        case .forbidden:
            return "You don't have permission to perform that action."
        case .boardNotFound:
            return "We couldn't find that board."
        case .postNotFound:
            return "We couldn't find that post anymore."
        case .anonymousNotAllowed:
            return "Anonymous posting isn't allowed in this board."
        case .deletedPost:
            return "This post was already deleted."
        case .commentNotFound:
            return "That comment is no longer available."
        case .replyDepthNotAllowed:
            return "Replies can only go one level deep."
        case .invalidCommentParent:
            return "That reply target is no longer valid."
        case .network:
            return "Unable to connect to Boards right now."
        case .unexpected:
            return "Something went wrong while loading Boards."
        }
    }
}

enum BoardsClientError: Error, Equatable {
    case invalidSession
    case forbidden
    case boardNotFound
    case postNotFound
    case anonymousNotAllowed
    case deletedPost
    case commentNotFound
    case replyDepthNotAllowed
    case invalidCommentParent
    case network
    case unexpected
}

protocol BoardsClientProtocol: Sendable {
    func fetchBoards() async throws -> [BoardSummary]
    func fetchHotPosts() async throws -> [HotPostSummary]
    func fetchBoardPosts(boardId: Int64) async throws -> [BoardPostSummary]
    func fetchPostDetail(postId: Int64) async throws -> PostDetailContent
    func createPost(boardId: Int64, input: CreatePostInput) async throws -> Int64
    func likePost(postId: Int64) async throws
    func unlikePost(postId: Int64) async throws
    func createComment(postId: Int64, input: CreateCommentInput) async throws -> Int64
    func deleteComment(commentId: Int64) async throws
    func deletePost(postId: Int64) async throws
    func invalidateCache() async
}

extension BoardsClientProtocol {
    func invalidateCache() async {}
}

enum BoardSectionKind: String, CaseIterable, Equatable, Hashable, Sendable {
    case official
    case community
    case clubs
    case fallback

    var title: String {
        switch self {
        case .official:
            return "OFFICIAL · NOTICES"
        case .community:
            return "GENERAL COMMUNITY"
        case .clubs:
            return "CLUBS · EVENTS"
        case .fallback:
            return "COMMUNITY"
        }
    }
}

struct BoardSection: Identifiable, Equatable {
    let kind: BoardSectionKind
    let boards: [BoardPresentationItem]

    var id: BoardSectionKind { kind }
}

struct BoardPresentationItem: Identifiable, Hashable, Equatable {
    let board: BoardSummary
    let subtitle: String
    let iconSystemName: String
    let iconBackground: String
    let accentBadge: String?

    var id: Int64 { board.id }
}

enum BoardGroupingRule {
    static func sections(for boards: [BoardSummary]) -> [BoardSection] {
        let grouped = Dictionary(grouping: boards) { board in
            classify(board)
        }

        return BoardSectionKind.allCases.compactMap { kind in
            guard let boards = grouped[kind], boards.isEmpty == false else { return nil }
            return BoardSection(
                kind: kind,
                boards: boards.map(Self.presentationItem(for:))
            )
        }
    }

    private static func classify(_ board: BoardSummary) -> BoardSectionKind {
        let value = "\(board.code) \(board.name)".lowercased()

        if value.contains("notice") || value.contains("official") || value.contains("department") {
            return .official
        }

        if value.contains("club") || value.contains("event") || value.contains("job") || value.contains("intern") {
            return .clubs
        }

        if value.contains("free") || value.contains("secret") || value.contains("freshman") || value.contains("general") {
            return .community
        }

        return .fallback
    }

    private static func presentationItem(for board: BoardSummary) -> BoardPresentationItem {
        let value = "\(board.code) \(board.name)".lowercased()

        if value.contains("notice") || value.contains("official") {
            return BoardPresentationItem(
                board: board,
                subtitle: "Class cancellations, scholarships, academic calendar",
                iconSystemName: "megaphone.fill",
                iconBackground: "official",
                accentBadge: nil
            )
        }

        if value.contains("department") {
            return BoardPresentationItem(
                board: board,
                subtitle: "Major-specific notices and contact info",
                iconSystemName: "graduationcap.fill",
                iconBackground: "department",
                accentBadge: nil
            )
        }

        if value.contains("free") {
            return BoardPresentationItem(
                board: board,
                subtitle: "Daily chat, Q&A, sharing info",
                iconSystemName: "message.fill",
                iconBackground: "free",
                accentBadge: nil
            )
        }

        if value.contains("secret") {
            return BoardPresentationItem(
                board: board,
                subtitle: "Anonymous board for personal concerns",
                iconSystemName: "lock.fill",
                iconBackground: "secret",
                accentBadge: nil
            )
        }

        if value.contains("freshman") {
            return BoardPresentationItem(
                board: board,
                subtitle: "Interaction and Q&A for new students",
                iconSystemName: "person.3.fill",
                iconBackground: "freshman",
                accentBadge: nil
            )
        }

        if value.contains("club") {
            return BoardPresentationItem(
                board: board,
                subtitle: "New member recruiting and search",
                iconSystemName: "person.3.fill",
                iconBackground: "club",
                accentBadge: nil
            )
        }

        if value.contains("event") {
            return BoardPresentationItem(
                board: board,
                subtitle: "Festivals, parties, and lectures",
                iconSystemName: "calendar",
                iconBackground: "event",
                accentBadge: nil
            )
        }

        if value.contains("job") || value.contains("intern") {
            return BoardPresentationItem(
                board: board,
                subtitle: "Custom job info for students",
                iconSystemName: "briefcase.fill",
                iconBackground: "career",
                accentBadge: nil
            )
        }

        return BoardPresentationItem(
            board: board,
            subtitle: board.isAnonymousAllowed ? "Open discussion with optional anonymity" : "Campus updates and student discussions",
            iconSystemName: board.isAnonymousAllowed ? "message.fill" : "text.bubble.fill",
            iconBackground: board.isAnonymousAllowed ? "free" : "fallback",
            accentBadge: nil
        )
    }
}

enum RelativeTimestampFormatter {
    static func string(from raw: String, now: Date = Date()) -> String {
        guard let date = date(from: raw) else {
            return raw
        }

        let relativeFormatter = RelativeDateTimeFormatter()
        relativeFormatter.unitsStyle = .abbreviated
        let value = relativeFormatter.localizedString(for: date, relativeTo: now)
        return value.replacingOccurrences(of: " ago", with: " ago")
    }

    static func date(from raw: String) -> Date? {
        let formatters = makeFormatters()

        for formatter in formatters {
            if let date = formatter.date(from: raw) {
                return date
            }
        }
        return nil
    }

    private static func makeFormatters() -> [DateFormatter] {
        let base = ["yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", "yyyy-MM-dd'T'HH:mm"]

        return base.map { format in
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = TimeZone.current
            formatter.dateFormat = format
            return formatter
        }
    }
}
