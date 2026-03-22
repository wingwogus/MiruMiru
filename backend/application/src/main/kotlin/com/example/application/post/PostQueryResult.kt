package com.example.application.post

object PostQueryResult {
    data class CommentItem(
        val commentId: Long,
        val parentId: Long?,
        val content: String,
        val authorDisplayName: String,
        val isAnonymous: Boolean,
        val isMine: Boolean,
        val isDeleted: Boolean,
        val createdAt: String,
        val children: List<CommentItem>
    )

    data class PostImageItem(
        val imageUrl: String,
        val displayOrder: Int
    )

    data class PostListItem(
        val postId: Long,
        val title: String,
        val authorDisplayName: String,
        val isAnonymous: Boolean,
        val likeCount: Int,
        val commentCount: Int,
        val createdAt: String
    )

    data class HotPostItem(
        val postId: Long,
        val boardId: Long,
        val boardCode: String,
        val boardName: String,
        val title: String,
        val authorDisplayName: String,
        val isAnonymous: Boolean,
        val likeCount: Int,
        val commentCount: Int,
        val createdAt: String
    )

    data class PostDetail(
        val postId: Long,
        val boardId: Long,
        val boardCode: String,
        val boardName: String,
        val title: String,
        val content: String,
        val authorDisplayName: String,
        val isAnonymous: Boolean,
        val isMine: Boolean,
        val isLikedByMe: Boolean,
        val likeCount: Int,
        val commentCount: Int,
        val comments: List<CommentItem>,
        val images: List<PostImageItem>,
        val createdAt: String,
        val updatedAt: String
    )
}
