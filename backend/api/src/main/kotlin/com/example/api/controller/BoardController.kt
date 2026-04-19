package com.example.api.controller

import com.example.api.common.ApiResponse
import com.example.api.dto.board.BoardResponses
import com.example.api.dto.post.PostRequests
import com.example.api.dto.post.PostResponses
import com.example.application.board.BoardQueryService
import com.example.application.post.PostCommand
import com.example.application.post.PostQueryService
import com.example.application.post.PostWriteService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/boards")
class BoardController(
    private val boardQueryService: BoardQueryService,
    private val postQueryService: PostQueryService,
    private val postWriteService: PostWriteService
) {
    @GetMapping("/me")
    fun getMyBoards(@AuthenticationPrincipal userId: String): ResponseEntity<ApiResponse<List<BoardResponses.BoardItem>>> {
        val response = boardQueryService.getMyBoards(userId)
            .map(BoardResponses.BoardItem::from)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @GetMapping("/{boardId}/posts")
    fun getBoardPosts(
        @AuthenticationPrincipal userId: String,
        @PathVariable boardId: Long
    ): ResponseEntity<ApiResponse<List<PostResponses.PostListItem>>> {
        val response = postQueryService.getBoardPosts(userId, boardId)
            .map(PostResponses.PostListItem::from)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @PostMapping("/{boardId}/posts")
    fun createPost(
        @AuthenticationPrincipal userId: String,
        @PathVariable boardId: Long,
        @Valid @RequestBody request: PostRequests.CreatePostRequest
    ): ResponseEntity<ApiResponse<PostResponses.CreatePostResponse>> {
        val postId = postWriteService.createPost(
            PostCommand.CreatePost(
                userId = userId,
                boardId = boardId,
                title = request.title,
                content = request.content,
                isAnonymous = request.isAnonymous,
                images = request.images.map { image ->
                    PostCommand.ImageInput(
                        imageUrl = image.imageUrl,
                        displayOrder = image.displayOrder!!
                    )
                }
            )
        )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.ok(PostResponses.CreatePostResponse(postId)))
    }
}
