package com.example.application.comment

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.application.post.PostAnonymousService
import com.example.domain.board.Board
import com.example.domain.comment.Comment
import com.example.domain.comment.CommentRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import com.example.domain.post.PostRepository
import com.example.domain.university.University
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import java.util.Optional

class CommentWriteServiceTest {
    private lateinit var memberRepository: MemberRepository
    private lateinit var postRepository: PostRepository
    private lateinit var commentRepository: CommentRepository
    private lateinit var postAnonymousService: PostAnonymousService
    private lateinit var commentWriteService: CommentWriteService

    @BeforeEach
    fun setUp() {
        memberRepository = mock(MemberRepository::class.java)
        postRepository = mock(PostRepository::class.java)
        commentRepository = mock(CommentRepository::class.java)
        postAnonymousService = mock(PostAnonymousService::class.java)
        commentWriteService = CommentWriteService(
            memberRepository = memberRepository,
            postRepository = postRepository,
            commentRepository = commentRepository,
            postAnonymousService = postAnonymousService
        )
    }

    @Test
    fun `create comment increments count and allocates anon number`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val board = board(university, isAnonymousAllowed = true)
        val post = post(id = 40L, board = board, member = member).apply { commentCount = 0 }
        val savedComment = Comment(
            id = 50L,
            post = post,
            member = member,
            parent = null,
            content = "hello",
            isAnonymous = true,
            isDeleted = false
        )

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(postRepository.findByIdAndBoardUniversityId(post.id, university.id)).thenReturn(post)
        `when`(postAnonymousService.getOrCreateAnonNumber(post, member)).thenReturn(1)
        `when`(commentRepository.save(any(Comment::class.java))).thenReturn(savedComment)

        val commentId = commentWriteService.createComment(
            CommentCommand.CreateComment(
                userId = member.id.toString(),
                postId = post.id,
                parentId = null,
                content = "hello",
                isAnonymous = true
            )
        )

        assertEquals(savedComment.id, commentId)
        assertEquals(1, post.commentCount)
        verify(postAnonymousService).getOrCreateAnonNumber(post, member)
    }

    @Test
    fun `create comment fails for depth over one`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val board = board(university, isAnonymousAllowed = true)
        val post = post(id = 40L, board = board, member = member)
        val root = Comment(id = 50L, post = post, member = member, parent = null, content = "root", isAnonymous = false, isDeleted = false)
        val child = Comment(id = 51L, post = post, member = member, parent = root, content = "child", isAnonymous = false, isDeleted = false)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(postRepository.findByIdAndBoardUniversityId(post.id, university.id)).thenReturn(post)
        `when`(commentRepository.findByIdAndPostBoardUniversityId(child.id, university.id)).thenReturn(child)

        val exception = assertThrows(BusinessException::class.java) {
            commentWriteService.createComment(
                CommentCommand.CreateComment(
                    userId = member.id.toString(),
                    postId = post.id,
                    parentId = child.id,
                    content = "too deep",
                    isAnonymous = false
                )
            )
        }

        assertEquals(ErrorCode.COMMENT_DEPTH_NOT_ALLOWED, exception.errorCode)
        verify(commentRepository).findByIdAndPostBoardUniversityId(child.id, university.id)
        verifyNoMoreInteractions(commentRepository)
    }

    @Test
    fun `create comment fails when parent comment is deleted`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val board = board(university, isAnonymousAllowed = true)
        val post = post(id = 40L, board = board, member = member)
        val deletedParent = Comment(
            id = 52L,
            post = post,
            member = member,
            parent = null,
            content = "deleted",
            isAnonymous = false,
            isDeleted = true
        )

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(postRepository.findByIdAndBoardUniversityId(post.id, university.id)).thenReturn(post)
        `when`(commentRepository.findByIdAndPostBoardUniversityId(deletedParent.id, university.id)).thenReturn(deletedParent)

        val exception = assertThrows(BusinessException::class.java) {
            commentWriteService.createComment(
                CommentCommand.CreateComment(
                    userId = member.id.toString(),
                    postId = post.id,
                    parentId = deletedParent.id,
                    content = "should fail",
                    isAnonymous = false
                )
            )
        }

        assertEquals(ErrorCode.COMMENT_NOT_FOUND, exception.errorCode)
        verify(commentRepository).findByIdAndPostBoardUniversityId(deletedParent.id, university.id)
        verifyNoMoreInteractions(commentRepository)
    }

    @Test
    fun `delete comment soft deletes and decrements count`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val board = board(university, isAnonymousAllowed = true)
        val post = post(id = 40L, board = board, member = member).apply { commentCount = 1 }
        val comment = Comment(id = 50L, post = post, member = member, parent = null, content = "hello", isAnonymous = false, isDeleted = false)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(commentRepository.findByIdAndPostBoardUniversityId(comment.id, university.id)).thenReturn(comment)

        commentWriteService.deleteComment(
            CommentCommand.DeleteComment(
                userId = member.id.toString(),
                commentId = comment.id
            )
        )

        assertEquals(true, comment.isDeleted)
        assertEquals(0, post.commentCount)
    }

    private fun university(): University {
        return University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp")
    }

    private fun member(id: Long, university: University): Member {
        return Member(
            id = id,
            university = university,
            major = com.example.domain.major.Major(id = 10L, university = university, code = "CS", name = "Computer Science"),
            email = "user$id@tokyo.ac.jp",
            password = "encoded",
            nickname = "user-$id"
        )
    }

    private fun board(university: University, isAnonymousAllowed: Boolean): Board {
        return Board(
            id = 3L,
            university = university,
            code = "free",
            name = "Free Talk",
            isAnonymousAllowed = isAnonymousAllowed
        )
    }

    private fun post(id: Long, board: Board, member: Member): Post {
        return Post(
            id = id,
            board = board,
            member = member,
            title = "hello",
            content = "content",
            isAnonymous = false,
            likeCount = 0,
            commentCount = 0,
            isDeleted = false
        )
    }
}
