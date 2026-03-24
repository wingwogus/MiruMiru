package com.example.api.dto.post

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero

object PostRequests {
    data class CreatePostImageRequest(
        @field:NotBlank(message = "imageUrl은 비어 있을 수 없습니다")
        val imageUrl: String,

        @field:NotNull(message = "displayOrder는 필수입니다")
        @field:PositiveOrZero(message = "displayOrder는 0 이상이어야 합니다")
        val displayOrder: Int?
    )

    data class CreatePostRequest(
        @field:NotBlank(message = "제목을 입력해주세요")
        val title: String,

        @field:NotBlank(message = "내용을 입력해주세요")
        val content: String,

        val isAnonymous: Boolean = false,

        @field:Valid
        val images: List<CreatePostImageRequest> = emptyList()
    )

    data class CreateCommentRequest(
        @field:NotBlank(message = "댓글 내용을 입력해주세요")
        val content: String,

        val parentId: Long? = null,

        val isAnonymous: Boolean = false
    )
}
