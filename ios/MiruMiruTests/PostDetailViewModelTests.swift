import XCTest
@testable import MiruMiru

@MainActor
final class PostDetailViewModelTests: XCTestCase {
    func testToggleLikeUpdatesDetailAndSyncStoreImmediately() async {
        let client = MockBoardsClient()
        let store = BoardsSyncStore()
        let detail = PostDetailContent(
            postId: 100,
            boardId: 101,
            boardCode: "free",
            boardName: "Free Board",
            title: "Anyone free to study?",
            content: "Let's meet at the library.",
            authorDisplayName: "Anonymous",
            isAnonymous: true,
            isMine: false,
            isLikedByMe: false,
            likeCount: 4,
            commentCount: 1,
            comments: [],
            images: [],
            createdAt: "2026-03-24T00:00:00.000Z",
            updatedAt: "2026-03-24T00:00:00.000Z"
        )
        let hotPost = HotPostSummary(
            id: 100,
            boardId: 101,
            boardCode: "free",
            boardName: "Free Board",
            title: detail.title,
            authorDisplayName: detail.authorDisplayName,
            isAnonymous: detail.isAnonymous,
            likeCount: 4,
            commentCount: 1,
            createdAt: detail.createdAt
        )

        client.postDetailResult = .success(detail)
        client.hotPostsResult = .success([hotPost])
        store.ingestHotPosts([hotPost])

        let viewModel = PostDetailViewModel(client: client, syncStore: store, postId: detail.postId)

        await viewModel.loadIfNeeded()
        await viewModel.toggleLike()
        await Task.yield()

        guard case let .loaded(updatedDetail) = viewModel.state else {
            return XCTFail("Expected loaded detail state")
        }

        XCTAssertTrue(updatedDetail.isLikedByMe)
        XCTAssertEqual(updatedDetail.likeCount, 5)
        XCTAssertEqual(store.projectHotPosts([hotPost]).first?.likeCount, 5)
        XCTAssertEqual(client.likedPostIds, [detail.postId])
    }

    func testDeletePostMarksDismissAndRemovesPostFromProjectedLists() async {
        let client = MockBoardsClient()
        let store = BoardsSyncStore()
        let detail = PostDetailContent(
            postId: 200,
            boardId: 101,
            boardCode: "free",
            boardName: "Free Board",
            title: "Delete me",
            content: "Temporary post",
            authorDisplayName: "Anonymous",
            isAnonymous: true,
            isMine: true,
            isLikedByMe: false,
            likeCount: 2,
            commentCount: 0,
            comments: [],
            images: [],
            createdAt: "2026-03-24T00:00:00.000Z",
            updatedAt: "2026-03-24T00:00:00.000Z"
        )
        let hotPost = HotPostSummary(
            id: 200,
            boardId: 101,
            boardCode: "free",
            boardName: "Free Board",
            title: detail.title,
            authorDisplayName: detail.authorDisplayName,
            isAnonymous: detail.isAnonymous,
            likeCount: 2,
            commentCount: 0,
            createdAt: detail.createdAt
        )
        let boardPost = BoardPostSummary(
            id: 200,
            title: detail.title,
            authorDisplayName: detail.authorDisplayName,
            isAnonymous: detail.isAnonymous,
            likeCount: 2,
            commentCount: 0,
            createdAt: detail.createdAt
        )

        client.postDetailResult = .success(detail)
        client.hotPostsResult = .success([])
        store.ingestHotPosts([hotPost])

        let viewModel = PostDetailViewModel(client: client, syncStore: store, postId: detail.postId)

        await viewModel.loadIfNeeded()
        await viewModel.deletePost()
        await Task.yield()

        XCTAssertTrue(viewModel.didDeletePost)
        XCTAssertTrue(store.projectHotPosts([hotPost]).isEmpty)
        XCTAssertTrue(store.projectBoardPosts([boardPost]).isEmpty)
        XCTAssertEqual(client.deletedPostIds, [detail.postId])
    }
}
