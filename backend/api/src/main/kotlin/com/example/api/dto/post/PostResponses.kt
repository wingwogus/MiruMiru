package com.example.api.dto.post

import com.example.application.post.PostQueryResult

object PostResponses {
    data class CommentItem(
        val commentId: Long,
        val parentId: Long?,
        val content: String,
        val authorMemberId: Long?,
        val authorDisplayName: String,
        val isAnonymous: Boolean,
        val isMine: Boolean,
        val isDeleted: Boolean,
        val createdAt: String,
        val children: List<CommentItem>
    ) {
        companion object {
            fun from(result: PostQueryResult.CommentItem): CommentItem {
                return CommentItem(
                    commentId = result.commentId,
                    parentId = result.parentId,
                    content = result.content,
                    authorMemberId = result.authorMemberId,
                    authorDisplayName = result.authorDisplayName,
                    isAnonymous = result.isAnonymous,
                    isMine = result.isMine,
                    isDeleted = result.isDeleted,
                    createdAt = result.createdAt,
                    children = result.children.map(CommentItem::from)
                )
            }
        }
    }

    data class PostImageItem(
        val imageUrl: String,
        val displayOrder: Int
    ) {
        companion object {
            fun from(result: PostQueryResult.PostImageItem): PostImageItem {
                return PostImageItem(
                    imageUrl = result.imageUrl,
                    displayOrder = result.displayOrder
                )
            }
        }
    }

    data class PostListItem(
        val postId: Long,
        val title: String,
        val authorDisplayName: String,
        val isAnonymous: Boolean,
        val likeCount: Int,
        val commentCount: Int,
        val createdAt: String
    ) {
        companion object {
            fun from(result: PostQueryResult.PostListItem): PostListItem {
                return PostListItem(
                    postId = result.postId,
                    title = result.title,
                    authorDisplayName = result.authorDisplayName,
                    isAnonymous = result.isAnonymous,
                    likeCount = result.likeCount,
                    commentCount = result.commentCount,
                    createdAt = result.createdAt
                )
            }
        }
    }

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
    ) {
        companion object {
            fun from(result: PostQueryResult.HotPostItem): HotPostItem {
                return HotPostItem(
                    postId = result.postId,
                    boardId = result.boardId,
                    boardCode = result.boardCode,
                    boardName = result.boardName,
                    title = result.title,
                    authorDisplayName = result.authorDisplayName,
                    isAnonymous = result.isAnonymous,
                    likeCount = result.likeCount,
                    commentCount = result.commentCount,
                    createdAt = result.createdAt
                )
            }
        }
    }

    data class PostDetail(
        val postId: Long,
        val boardId: Long,
        val boardCode: String,
        val boardName: String,
        val title: String,
        val content: String,
        val authorMemberId: Long,
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
    ) {
        companion object {
            fun from(result: PostQueryResult.PostDetail): PostDetail {
                return PostDetail(
                    postId = result.postId,
                    boardId = result.boardId,
                    boardCode = result.boardCode,
                    boardName = result.boardName,
                    title = result.title,
                    content = result.content,
                    authorMemberId = result.authorMemberId,
                    authorDisplayName = result.authorDisplayName,
                    isAnonymous = result.isAnonymous,
                    isMine = result.isMine,
                    isLikedByMe = result.isLikedByMe,
                    likeCount = result.likeCount,
                    commentCount = result.commentCount,
                    comments = result.comments.map(CommentItem::from),
                    images = result.images.map(PostImageItem::from),
                    createdAt = result.createdAt,
                    updatedAt = result.updatedAt
                )
            }
        }
    }

    data class CreatePostResponse(
        val postId: Long
    )

    data class CreateCommentResponse(
        val commentId: Long
    )
}
