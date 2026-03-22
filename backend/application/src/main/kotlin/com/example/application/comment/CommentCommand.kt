package com.example.application.comment

object CommentCommand {
    data class CreateComment(
        val userId: String,
        val postId: Long,
        val parentId: Long?,
        val content: String,
        val isAnonymous: Boolean
    )

    data class DeleteComment(
        val userId: String,
        val commentId: Long
    )
}
