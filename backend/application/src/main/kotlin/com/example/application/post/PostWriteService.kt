package com.example.application.post

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.board.Board
import com.example.domain.board.BoardRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import com.example.domain.post.PostImage
import com.example.domain.post.PostLike
import com.example.domain.post.PostImageRepository
import com.example.domain.post.PostLikeRepository
import com.example.domain.post.PostRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PostWriteService(
    private val memberRepository: MemberRepository,
    private val boardRepository: BoardRepository,
    private val postRepository: PostRepository,
    private val postImageRepository: PostImageRepository,
    private val postLikeRepository: PostLikeRepository,
    private val postAnonymousService: PostAnonymousService
) {
    fun createPost(command: PostCommand.CreatePost): Long {
        val member = findMember(command.userId)
        val board = findBoard(member, command.boardId)

        if (command.isAnonymous && !board.isAnonymousAllowed) {
            throw BusinessException(ErrorCode.ANONYMOUS_NOT_ALLOWED)
        }

        validateDisplayOrders(command.images)

        val post = postRepository.save(
            Post(
                board = board,
                member = member,
                title = command.title.trim(),
                content = command.content.trim(),
                isAnonymous = command.isAnonymous,
                likeCount = 0,
                commentCount = 0,
                isDeleted = false
            )
        )

        if (command.images.isNotEmpty()) {
            postImageRepository.saveAll(
                command.images.map { image ->
                    PostImage(
                        post = post,
                        imageUrl = image.imageUrl.trim(),
                        displayOrder = image.displayOrder
                    )
                }
            )
        }

        if (command.isAnonymous) {
            postAnonymousService.getOrCreateAnonNumber(post, member)
        }

        return post.id
    }

    fun deletePost(command: PostCommand.DeletePost) {
        val member = findMember(command.userId)
        val post = findPost(member, command.postId)

        if (post.member.id != member.id) {
            throw BusinessException(ErrorCode.FORBIDDEN)
        }

        post.delete()
    }

    fun likePost(command: PostCommand.LikePost) {
        val member = findMember(command.userId)
        val post = findPost(member, command.postId)

        if (post.isDeleted) {
            throw BusinessException(ErrorCode.POST_ALREADY_DELETED)
        }

        if (postLikeRepository.existsByPostIdAndMemberId(post.id, member.id)) {
            throw BusinessException(ErrorCode.POST_LIKE_DUPLICATE)
        }

        try {
            postLikeRepository.save(
                PostLike(
                    post = post,
                    member = member
                )
            )
        } catch (_: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.POST_LIKE_DUPLICATE)
        }
        post.increaseLikeCount()
    }

    fun unlikePost(command: PostCommand.UnlikePost) {
        val member = findMember(command.userId)
        val post = findPost(member, command.postId)

        if (post.isDeleted) {
            throw BusinessException(ErrorCode.POST_ALREADY_DELETED)
        }

        val postLike = postLikeRepository.findByPostIdAndMemberId(post.id, member.id)
            ?: throw BusinessException(ErrorCode.POST_LIKE_NOT_FOUND)
        postLikeRepository.delete(postLike)
        post.decreaseLikeCount()
    }

    private fun validateDisplayOrders(images: List<PostCommand.ImageInput>) {
        val orders = images.map { it.displayOrder }
        if (orders.distinct().size != orders.size) {
            throw BusinessException(
                errorCode = ErrorCode.INVALID_INPUT,
                detail = mapOf("field" to "images.displayOrder", "reason" to "displayOrder values must be unique")
            )
        }
    }

    private fun findBoard(member: Member, boardId: Long): Board {
        return boardRepository.findByIdAndUniversityId(boardId, member.university.id)
            ?: throw BusinessException(ErrorCode.BOARD_NOT_FOUND)
    }

    private fun findPost(member: Member, postId: Long): Post {
        return postRepository.findByIdAndBoardUniversityId(postId, member.university.id)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)
    }

    private fun findMember(userId: String): Member {
        val parsedUserId = userId.toLongOrNull()
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        return memberRepository.findById(parsedUserId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }
    }
}
