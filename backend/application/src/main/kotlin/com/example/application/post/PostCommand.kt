package com.example.application.post

object PostCommand {
    data class CreatePost(
        val userId: String,
        val boardId: Long,
        val title: String,
        val content: String,
        val isAnonymous: Boolean,
        val images: List<ImageInput>
    )

    data class ImageInput(
        val imageUrl: String,
        val displayOrder: Int
    )

    data class DeletePost(
        val userId: String,
        val postId: Long
    )

    data class LikePost(
        val userId: String,
        val postId: Long
    )

    data class UnlikePost(
        val userId: String,
        val postId: Long
    )
}
