import SwiftUI

@MainActor
final class BoardsSyncStore: ObservableObject {
    @Published private var hotPostsSnapshot: [HotPostSummary]?
    @Published private var likeCountOverrides: [Int64: Int] = [:]
    @Published private var deletedPostIds: Set<Int64> = []

    func reset() {
        hotPostsSnapshot = nil
        likeCountOverrides = [:]
        deletedPostIds = []
    }

    func ingestHotPosts(_ posts: [HotPostSummary]) {
        let snapshotIds = Set(posts.map(\.id))
        likeCountOverrides = likeCountOverrides.filter { key, _ in
            snapshotIds.contains(key) == false
        }
        hotPostsSnapshot = posts
    }

    func refreshHotPosts(using client: BoardsClientProtocol) async {
        do {
            let posts = try await client.fetchHotPosts()
            ingestHotPosts(posts)
        } catch {
            // Keep the current UI snapshot if the background refresh fails.
        }
    }

    func setLikeCount(postId: Int64, likeCount: Int) {
        likeCountOverrides[postId] = max(0, likeCount)
    }

    func clearLikeOverride(postId: Int64) {
        likeCountOverrides.removeValue(forKey: postId)
    }

    func markDeleted(postId: Int64) {
        deletedPostIds.insert(postId)
        likeCountOverrides.removeValue(forKey: postId)
        if let hotPostsSnapshot {
            self.hotPostsSnapshot = hotPostsSnapshot.filter { $0.id != postId }
        }
    }

    func projectHotPosts(_ fallback: [HotPostSummary]) -> [HotPostSummary] {
        let source = hotPostsSnapshot ?? fallback
        return source.compactMap(project)
    }

    func projectBoardPosts(_ posts: [BoardPostSummary]) -> [BoardPostSummary] {
        posts.compactMap(project)
    }

    private func project(_ post: HotPostSummary) -> HotPostSummary? {
        guard deletedPostIds.contains(post.id) == false else {
            return nil
        }

        let patchedLikeCount = likeCountOverrides[post.id] ?? post.likeCount
        guard patchedLikeCount != post.likeCount else {
            return post
        }

        return HotPostSummary(
            id: post.id,
            boardId: post.boardId,
            boardCode: post.boardCode,
            boardName: post.boardName,
            title: post.title,
            authorDisplayName: post.authorDisplayName,
            isAnonymous: post.isAnonymous,
            likeCount: patchedLikeCount,
            commentCount: post.commentCount,
            createdAt: post.createdAt
        )
    }

    private func project(_ post: BoardPostSummary) -> BoardPostSummary? {
        guard deletedPostIds.contains(post.id) == false else {
            return nil
        }

        let patchedLikeCount = likeCountOverrides[post.id] ?? post.likeCount
        guard patchedLikeCount != post.likeCount else {
            return post
        }

        return BoardPostSummary(
            id: post.id,
            title: post.title,
            authorDisplayName: post.authorDisplayName,
            isAnonymous: post.isAnonymous,
            likeCount: patchedLikeCount,
            commentCount: post.commentCount,
            createdAt: post.createdAt
        )
    }
}
