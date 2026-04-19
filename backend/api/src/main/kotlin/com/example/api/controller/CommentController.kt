package com.example.api.controller

import com.example.api.common.ApiResponse
import com.example.application.comment.CommentCommand
import com.example.application.comment.CommentWriteService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/comments")
class CommentController(
    private val commentWriteService: CommentWriteService
) {
    @DeleteMapping("/{commentId}")
    fun deleteComment(
        @AuthenticationPrincipal userId: String,
        @PathVariable commentId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        commentWriteService.deleteComment(
            CommentCommand.DeleteComment(
                userId = userId,
                commentId = commentId
            )
        )
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }
}
