import Foundation
@testable import MiruMiru

final class MockAuthClient: AuthClientProtocol, @unchecked Sendable {
    var loginResult: Result<TokenPair, Error> = .failure(AuthError.unexpected)
    var reissueResult: Result<TokenPair, Error> = .failure(AuthError.invalidSession)
    var sendEmailCodeResult: Result<Void, Error> = .success(())
    var verifyEmailCodeResult: Result<Void, Error> = .success(())
    var fetchMajorsResult: Result<[MajorOption], Error> = .success([])
    var verifyNicknameResult: Result<Void, Error> = .success(())
    var signUpResult: Result<Void, Error> = .success(())
    var restoreResult: Result<Void, Error> = .success(())
    private(set) var lastLoginEmail: String?
    private(set) var lastReissueAccessToken: String?
    private(set) var lastReissueRefreshToken: String?
    private(set) var lastSendEmailCodeEmail: String?
    private(set) var lastVerifyEmailCodeEmail: String?
    private(set) var lastVerifyEmailCodeValue: String?
    private(set) var lastFetchMajorsEmail: String?
    private(set) var lastVerifyNickname: String?
    private(set) var lastSignUpEmail: String?
    private(set) var lastSignUpNickname: String?
    private(set) var lastSignUpMajorId: Int64?
    private(set) var lastRestoreToken: String?

    func login(email: String, password: String) async throws -> TokenPair {
        lastLoginEmail = email
        switch loginResult {
        case let .success(tokens):
            return tokens
        case let .failure(error):
            throw error
        }
    }

    func reissue(accessToken: String, refreshToken: String) async throws -> TokenPair {
        lastReissueAccessToken = accessToken
        lastReissueRefreshToken = refreshToken
        switch reissueResult {
        case let .success(tokens):
            return tokens
        case let .failure(error):
            throw error
        }
    }

    func sendEmailCode(email: String) async throws {
        lastSendEmailCodeEmail = email
        try sendEmailCodeResult.get()
    }

    func verifyEmailCode(email: String, code: String) async throws {
        lastVerifyEmailCodeEmail = email
        lastVerifyEmailCodeValue = code
        try verifyEmailCodeResult.get()
    }

    func fetchMajors(email: String) async throws -> [MajorOption] {
        lastFetchMajorsEmail = email
        return try fetchMajorsResult.get()
    }

    func verifyNickname(nickname: String) async throws {
        lastVerifyNickname = nickname
        try verifyNicknameResult.get()
    }

    func signUp(email: String, password: String, nickname: String, majorId: Int64) async throws {
        lastSignUpEmail = email
        lastSignUpNickname = nickname
        lastSignUpMajorId = majorId
        try signUpResult.get()
    }

    func validateRestoredSession(accessToken: String) async throws {
        lastRestoreToken = accessToken
        switch restoreResult {
        case .success:
            return
        case let .failure(error):
            throw error
        }
    }
}

final class InMemoryTokenStore: TokenStore, @unchecked Sendable {
    var storedSession: TokenPair?
    var shouldFailOnSave = false
    var shouldFailOnRead = false

    func readSession() throws -> TokenPair? {
        if shouldFailOnRead {
            throw TokenStoreError.invalidPayload
        }
        return storedSession
    }

    func saveSession(_ session: TokenPair) throws {
        if shouldFailOnSave {
            storedSession = nil
            throw TokenStoreError.invalidPayload
        }
        storedSession = session
    }

    func clearSession() throws {
        storedSession = nil
    }
}

final class MockHomeClient: HomeClientProtocol, @unchecked Sendable {
    var profileResult: Result<HomeMemberProfile, Error> = .failure(HomeClientError.unexpected)
    var semestersResult: Result<[HomeSemester], Error> = .failure(HomeClientError.unexpected)
    var timetableResult: Result<HomeTimetable, Error> = .failure(HomeClientError.unexpected)
    var hotPostsResult: Result<[HotPostSummary], Error> = .success([])
    private(set) var requestedSemesterId: Int64?

    func fetchProfile() async throws -> HomeMemberProfile {
        try profileResult.get()
    }

    func fetchSemesters() async throws -> [HomeSemester] {
        try semestersResult.get()
    }

    func fetchTimetable(semesterId: Int64) async throws -> HomeTimetable {
        requestedSemesterId = semesterId
        return try timetableResult.get()
    }

    func fetchHotPosts() async throws -> [HotPostSummary] {
        try hotPostsResult.get()
    }
}

final class MockTimetableClient: TimetableClientProtocol, @unchecked Sendable {
    var memberContextResult: Result<TimetableMemberContext, Error> = .failure(TimetableClientError.unexpected)
    var semestersResult: Result<[TimetableSemester], Error> = .failure(TimetableClientError.unexpected)
    var catalogResult: Result<[TimetableLectureItem], Error> = .failure(TimetableClientError.unexpected)
    var timetableResult: Result<TimetableDetail, Error> = .failure(TimetableClientError.unexpected)
    var addLectureResult: Result<Void, Error> = .success(())
    var removeLectureResult: Result<Void, Error> = .success(())

    private(set) var requestedSemesterId: Int64?
    private(set) var catalogRequestedSemesterIds: [Int64] = []
    private(set) var addedPayloads: [(semesterId: Int64, lectureId: Int64)] = []
    private(set) var removedPayloads: [(semesterId: Int64, lectureId: Int64)] = []

    func fetchMemberContext() async throws -> TimetableMemberContext {
        try memberContextResult.get()
    }

    func fetchSemesters() async throws -> [TimetableSemester] {
        try semestersResult.get()
    }

    func fetchLectureCatalog(semesterId: Int64) async throws -> [TimetableLectureItem] {
        catalogRequestedSemesterIds.append(semesterId)
        return try catalogResult.get()
    }

    func fetchMyTimetable(semesterId: Int64) async throws -> TimetableDetail {
        requestedSemesterId = semesterId
        return try timetableResult.get()
    }

    func addLecture(semesterId: Int64, lectureId: Int64) async throws {
        addedPayloads.append((semesterId, lectureId))
        try addLectureResult.get()
    }

    func removeLecture(semesterId: Int64, lectureId: Int64) async throws {
        removedPayloads.append((semesterId, lectureId))
        try removeLectureResult.get()
    }
}

final class MockBoardsClient: BoardsClientProtocol, @unchecked Sendable {
    var boardsResult: Result<[BoardSummary], Error> = .success([])
    var hotPostsResult: Result<[HotPostSummary], Error> = .success([])
    var boardPostsResult: Result<[BoardPostSummary], Error> = .success([])
    var postDetailResult: Result<PostDetailContent, Error> = .failure(BoardsClientError.unexpected)
    var createPostResult: Result<Int64, Error> = .success(1)
    var likePostResult: Result<Void, Error> = .success(())
    var unlikePostResult: Result<Void, Error> = .success(())
    var createCommentResult: Result<Int64, Error> = .success(1)
    var deleteCommentResult: Result<Void, Error> = .success(())
    var deletePostResult: Result<Void, Error> = .success(())

    private(set) var fetchedBoardPostsIds: [Int64] = []
    private(set) var fetchedPostDetailIds: [Int64] = []
    private(set) var createdPostBoardIds: [Int64] = []
    private(set) var likedPostIds: [Int64] = []
    private(set) var unlikedPostIds: [Int64] = []
    private(set) var deletedCommentIds: [Int64] = []
    private(set) var deletedPostIds: [Int64] = []
    private(set) var fetchHotPostsCallCount = 0

    func fetchBoards() async throws -> [BoardSummary] {
        try boardsResult.get()
    }

    func fetchHotPosts() async throws -> [HotPostSummary] {
        fetchHotPostsCallCount += 1
        return try hotPostsResult.get()
    }

    func fetchBoardPosts(boardId: Int64) async throws -> [BoardPostSummary] {
        fetchedBoardPostsIds.append(boardId)
        return try boardPostsResult.get()
    }

    func fetchPostDetail(postId: Int64) async throws -> PostDetailContent {
        fetchedPostDetailIds.append(postId)
        return try postDetailResult.get()
    }

    func createPost(boardId: Int64, input: CreatePostInput) async throws -> Int64 {
        createdPostBoardIds.append(boardId)
        return try createPostResult.get()
    }

    func likePost(postId: Int64) async throws {
        likedPostIds.append(postId)
        try likePostResult.get()
    }

    func unlikePost(postId: Int64) async throws {
        unlikedPostIds.append(postId)
        try unlikePostResult.get()
    }

    func createComment(postId: Int64, input: CreateCommentInput) async throws -> Int64 {
        try createCommentResult.get()
    }

    func deleteComment(commentId: Int64) async throws {
        deletedCommentIds.append(commentId)
        try deleteCommentResult.get()
    }

    func deletePost(postId: Int64) async throws {
        deletedPostIds.append(postId)
        try deletePostResult.get()
    }
}

final class MockMessagesClient: MessagesClientProtocol, @unchecked Sendable {
    var viewerResult: Result<MessagesViewer, Error> = .success(MessagesViewer(memberId: 1, displayName: "Tester"))
    var roomsResult: Result<[MessageRoomSummary], Error> = .success([])
    var blockedMemberIdsResult: Result<Set<Int64>, Error> = .success([])
    var createRoomResult: Result<MessageRoomCreated, Error> = .failure(MessagesClientError.unexpected)
    var messagesResult: Result<MessagesPage, Error> = .success(MessagesPage(roomId: 1, messages: [], myLastReadMessageId: nil, otherLastReadMessageId: nil, nextBeforeMessageId: nil))
    var sendMessageResult: Result<MessageItem, Error> = .failure(MessagesClientError.unexpected)
    var markReadResult: Result<Int, Error> = .success(0)
    var blockResult: Result<Void, Error> = .success(())
    var unblockResult: Result<Void, Error> = .success(())
    var reportResult: Result<Void, Error> = .success(())

    private(set) var lastCreateRoomRequest: (postId: Int64, requesterIsAnonymous: Bool, partnerMemberId: Int64?)?
    private(set) var blockedMemberIds: [Int64] = []
    private(set) var unblockedMemberIds: [Int64] = []
    private(set) var reportPayloads: [(targetMemberId: Int64, roomId: Int64, reason: String, detail: String?)] = []
    private(set) var sentPayloads: [(roomId: Int64, content: String)] = []

    func fetchViewer() async throws -> MessagesViewer {
        try viewerResult.get()
    }

    func fetchRooms(limit: Int) async throws -> [MessageRoomSummary] {
        try roomsResult.get()
    }

    func fetchBlockedMemberIds() async throws -> Set<Int64> {
        try blockedMemberIdsResult.get()
    }

    func createRoom(postId: Int64, requesterIsAnonymous: Bool, partnerMemberId: Int64?) async throws -> MessageRoomCreated {
        lastCreateRoomRequest = (postId, requesterIsAnonymous, partnerMemberId)
        return try createRoomResult.get()
    }

    func fetchMessages(roomId: Int64, beforeMessageId: Int64?, limit: Int) async throws -> MessagesPage {
        try messagesResult.get()
    }

    func sendMessage(roomId: Int64, content: String) async throws -> MessageItem {
        sentPayloads.append((roomId, content))
        return try sendMessageResult.get()
    }

    func markRead(roomId: Int64, lastReadMessageId: Int64) async throws -> Int {
        try markReadResult.get()
    }

    func blockMember(targetMemberId: Int64) async throws {
        blockedMemberIds.append(targetMemberId)
        try blockResult.get()
    }

    func unblockMember(targetMemberId: Int64) async throws {
        unblockedMemberIds.append(targetMemberId)
        try unblockResult.get()
    }

    func reportMember(targetMemberId: Int64, roomId: Int64, reason: String, detail: String?) async throws {
        reportPayloads.append((targetMemberId, roomId, reason, detail))
        try reportResult.get()
    }
}

actor MockMessagesRealtimeClient: MessagesRealtimeClientProtocol {
    func activate() async {}
    func deactivate() async {}
    func ensureUnreadSubscription() async {}
    func subscribeToRoom(_ roomId: Int64) async {}
    func unsubscribeFromRoom(_ roomId: Int64) async {}
}

final class MockCourseReviewsClient: CourseReviewsClientProtocol, @unchecked Sendable {
    var feedResult: Result<CourseReviewFeedPage, Error> = .failure(CourseReviewsClientError.unexpected)
    var targetSearchResult: Result<CourseReviewTargetPage, Error> = .failure(CourseReviewsClientError.unexpected)
    var detailResult: Result<CourseReviewPage, Error> = .failure(CourseReviewsClientError.unexpected)
    var myReviewResult: Result<CourseReviewEntry, Error> = .failure(CourseReviewsClientError.reviewNotFound)
    var createResult: Result<Int64, Error> = .success(1)
    var updateResult: Result<Int64, Error> = .success(1)
    var deleteResult: Result<Void, Error> = .success(())

    private(set) var requestedFeedPage: Int?
    private(set) var requestedQuery: String?
    private(set) var requestedDetailTargetId: Int64?
    private(set) var requestedMyReviewTargetId: Int64?
    private(set) var createdTargetId: Int64?
    private(set) var createdPayload: CourseReviewUpsertRequest?
    private(set) var updatedTargetId: Int64?
    private(set) var updatedPayload: CourseReviewUpsertRequest?
    private(set) var deletedTargetId: Int64?

    func fetchReviewFeed(page: Int, size: Int) async throws -> CourseReviewFeedPage {
        requestedFeedPage = page
        return try feedResult.get()
    }

    func fetchReviewTargets(query: String, page: Int, size: Int) async throws -> CourseReviewTargetPage {
        requestedQuery = query
        return try targetSearchResult.get()
    }

    func fetchTargetReviews(targetId: Int64, page: Int, size: Int) async throws -> CourseReviewPage {
        requestedDetailTargetId = targetId
        return try detailResult.get()
    }

    func fetchMyReview(targetId: Int64) async throws -> CourseReviewEntry {
        requestedMyReviewTargetId = targetId
        return try myReviewResult.get()
    }

    func createReview(targetId: Int64, request: CourseReviewUpsertRequest) async throws -> Int64 {
        createdTargetId = targetId
        createdPayload = request
        return try createResult.get()
    }

    func updateMyReview(targetId: Int64, request: CourseReviewUpsertRequest) async throws -> Int64 {
        updatedTargetId = targetId
        updatedPayload = request
        return try updateResult.get()
    }

    func deleteMyReview(targetId: Int64) async throws {
        deletedTargetId = targetId
        try deleteResult.get()
    }
}

@MainActor
final class MockSignupClient: SignupClientProtocol {
    var sendEmailCodeResult: Result<Void, Error> = .success(())
    var verifyEmailCodeResult: Result<Void, Error> = .success(())
    var fetchMajorsResult: Result<[SignupMajorOption], Error> = .success([])
    var verifyNicknameResult: Result<Void, Error> = .success(())
    var signUpResult: Result<Void, Error> = .success(())

    private(set) var eventLog: [String] = []
    private(set) var sentEmail: String?
    private(set) var verifiedEmail: String?
    private(set) var verifiedCode: String?
    private(set) var fetchedMajorsEmail: String?
    private(set) var verifiedNickname: String?
    private(set) var signUpPayload: (email: String, password: String, nickname: String, majorId: Int64)?

    func sendEmailCode(email: String) async throws {
        sentEmail = email
        eventLog.append("sendEmailCode")
        switch sendEmailCodeResult {
        case .success:
            return
        case let .failure(error):
            throw error
        }
    }

    func verifyEmailCode(email: String, code: String) async throws {
        verifiedEmail = email
        verifiedCode = code
        eventLog.append("verifyEmailCode")
        switch verifyEmailCodeResult {
        case .success:
            return
        case let .failure(error):
            throw error
        }
    }

    func fetchMajors(email: String) async throws -> [SignupMajorOption] {
        fetchedMajorsEmail = email
        eventLog.append("fetchMajors")
        switch fetchMajorsResult {
        case let .success(options):
            return options
        case let .failure(error):
            throw error
        }
    }

    func verifyNickname(_ nickname: String) async throws {
        verifiedNickname = nickname
        eventLog.append("verifyNickname")
        switch verifyNicknameResult {
        case .success:
            return
        case let .failure(error):
            throw error
        }
    }

    func signUp(email: String, password: String, nickname: String, majorId: Int64) async throws {
        signUpPayload = (email, password, nickname, majorId)
        eventLog.append("signUp")
        switch signUpResult {
        case .success:
            return
        case let .failure(error):
            throw error
        }
    }
}
