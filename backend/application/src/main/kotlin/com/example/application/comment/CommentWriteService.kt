package com.example.application.comment

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.application.post.PostAnonymousService
import com.example.domain.comment.Comment
import com.example.domain.comment.CommentRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import com.example.domain.post.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CommentWriteService(
    private val memberRepository: MemberRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val postAnonymousService: PostAnonymousService
) {
    fun createComment(command: CommentCommand.CreateComment): Long {
        val member = findMember(command.userId)
        val post = findPost(member, command.postId)

        if (post.isDeleted) {
            throw BusinessException(ErrorCode.POST_ALREADY_DELETED)
        }

        if (command.isAnonymous && !post.board.isAnonymousAllowed) {
            throw BusinessException(ErrorCode.ANONYMOUS_NOT_ALLOWED)
        }

        val parent = command.parentId?.let { parentId ->
            val foundParent = commentRepository.findByIdAndPostBoardUniversityId(parentId, member.university.id)
                ?: throw BusinessException(ErrorCode.COMMENT_NOT_FOUND)
            if (foundParent.isDeleted) {
                throw BusinessException(ErrorCode.COMMENT_NOT_FOUND)
            }
            if (foundParent.post.id != post.id) {
                throw BusinessException(ErrorCode.INVALID_COMMENT_PARENT)
            }
            if (foundParent.parent != null) {
                throw BusinessException(ErrorCode.COMMENT_DEPTH_NOT_ALLOWED)
            }
            foundParent
        }

        if (command.isAnonymous) {
            postAnonymousService.getOrCreateAnonNumber(post, member)
        }

        val comment = commentRepository.save(
            Comment(
                post = post,
                member = member,
                parent = parent,
                content = command.content.trim(),
                isAnonymous = command.isAnonymous,
                isDeleted = false
            )
        )
        post.increaseCommentCount()
        return comment.id
    }

    fun deleteComment(command: CommentCommand.DeleteComment) {
        val member = findMember(command.userId)
        val comment = commentRepository.findByIdAndPostBoardUniversityId(command.commentId, member.university.id)
            ?: throw BusinessException(ErrorCode.COMMENT_NOT_FOUND)

        if (comment.isDeleted) {
            throw BusinessException(ErrorCode.COMMENT_NOT_FOUND)
        }
        if (comment.member.id != member.id) {
            throw BusinessException(ErrorCode.FORBIDDEN)
        }

        comment.delete()
        comment.post.decreaseCommentCount()
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
