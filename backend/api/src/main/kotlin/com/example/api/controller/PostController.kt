package com.example.api.controller

import com.example.api.common.ApiResponse
import com.example.api.dto.post.PostRequests
import com.example.api.dto.post.PostResponses
import com.example.application.comment.CommentCommand
import com.example.application.comment.CommentWriteService
import com.example.application.post.PostCommand
import com.example.application.post.PostQueryService
import com.example.application.post.PostWriteService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/posts")
class PostController(
    private val postQueryService: PostQueryService,
    private val postWriteService: PostWriteService,
    private val commentWriteService: CommentWriteService
) {
    @GetMapping("/hot")
    fun getHotPosts(
        @AuthenticationPrincipal userId: String
    ): ResponseEntity<ApiResponse<List<PostResponses.HotPostItem>>> {
        val response = postQueryService.getHotPosts(userId)
            .map(PostResponses.HotPostItem::from)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @GetMapping("/{postId}")
    fun getPostDetail(
        @AuthenticationPrincipal userId: String,
        @PathVariable postId: Long
    ): ResponseEntity<ApiResponse<PostResponses.PostDetail>> {
        val response = postQueryService.getPostDetail(userId, postId)
        return ResponseEntity.ok(ApiResponse.ok(PostResponses.PostDetail.from(response)))
    }

    @PostMapping("/{postId}/likes")
    fun likePost(
        @AuthenticationPrincipal userId: String,
        @PathVariable postId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        postWriteService.likePost(
            PostCommand.LikePost(
                userId = userId,
                postId = postId
            )
        )
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }

    @DeleteMapping("/{postId}/likes")
    fun unlikePost(
        @AuthenticationPrincipal userId: String,
        @PathVariable postId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        postWriteService.unlikePost(
            PostCommand.UnlikePost(
                userId = userId,
                postId = postId
            )
        )
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }

    @PostMapping("/{postId}/comments")
    fun createComment(
        @AuthenticationPrincipal userId: String,
        @PathVariable postId: Long,
        @Valid @RequestBody request: PostRequests.CreateCommentRequest
    ): ResponseEntity<ApiResponse<PostResponses.CreateCommentResponse>> {
        val commentId = commentWriteService.createComment(
            CommentCommand.CreateComment(
                userId = userId,
                postId = postId,
                parentId = request.parentId,
                content = request.content,
                isAnonymous = request.isAnonymous
            )
        )
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.ok(PostResponses.CreateCommentResponse(commentId)))
    }

    @DeleteMapping("/{postId}")
    fun deletePost(
        @AuthenticationPrincipal userId: String,
        @PathVariable postId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        postWriteService.deletePost(
            PostCommand.DeletePost(
                userId = userId,
                postId = postId
            )
        )
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }
}
