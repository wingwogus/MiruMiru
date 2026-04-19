import XCTest
@testable import MiruMiru

@MainActor
final class BoardsSyncStoreTests: XCTestCase {
    func testProjectHotPostsAppliesLikeOverrideAndDeletion() {
        let store = BoardsSyncStore()
        let hotPost = HotPostSummary(
            id: 1,
            boardId: 101,
            boardCode: "free",
            boardName: "Free Board",
            title: "Hot post",
            authorDisplayName: "Anon",
            isAnonymous: true,
            likeCount: 3,
            commentCount: 1,
            createdAt: "2026-03-24T00:00:00.000Z"
        )

        store.ingestHotPosts([hotPost])
        store.setLikeCount(postId: 1, likeCount: 4)

        XCTAssertEqual(store.projectHotPosts([]).first?.likeCount, 4)

        store.markDeleted(postId: 1)

        XCTAssertTrue(store.projectHotPosts([hotPost]).isEmpty)
    }

    func testProjectBoardPostsAppliesLikeOverride() {
        let store = BoardsSyncStore()
        let boardPost = BoardPostSummary(
            id: 2,
            title: "Board post",
            authorDisplayName: "Student",
            isAnonymous: false,
            likeCount: 7,
            commentCount: 2,
            createdAt: "2026-03-24T00:00:00.000Z"
        )

        store.setLikeCount(postId: 2, likeCount: 8)

        XCTAssertEqual(store.projectBoardPosts([boardPost]).first?.likeCount, 8)
    }
}
