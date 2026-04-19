package com.example.application.post

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.board.Board
import com.example.domain.board.BoardRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import com.example.domain.post.PostImage
import com.example.domain.post.PostImageRepository
import com.example.domain.post.PostLike
import com.example.domain.post.PostLikeRepository
import com.example.domain.post.PostRepository
import com.example.domain.university.University
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import java.util.Optional

class PostWriteServiceTest {
    private lateinit var memberRepository: MemberRepository
    private lateinit var boardRepository: BoardRepository
    private lateinit var postRepository: PostRepository
    private lateinit var postImageRepository: PostImageRepository
    private lateinit var postLikeRepository: PostLikeRepository
    private lateinit var postAnonymousService: PostAnonymousService
    private lateinit var postWriteService: PostWriteService

    @BeforeEach
    fun setUp() {
        memberRepository = mock(MemberRepository::class.java)
        boardRepository = mock(BoardRepository::class.java)
        postRepository = mock(PostRepository::class.java)
        postImageRepository = mock(PostImageRepository::class.java)
        postLikeRepository = mock(PostLikeRepository::class.java)
        postAnonymousService = mock(PostAnonymousService::class.java)
        postWriteService = PostWriteService(
            memberRepository = memberRepository,
            boardRepository = boardRepository,
            postRepository = postRepository,
            postImageRepository = postImageRepository,
            postLikeRepository = postLikeRepository,
            postAnonymousService = postAnonymousService
        )
    }

    @Test
    fun `create post saves post images and anonymous mapping`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val board = board(id = 3L, university = university, isAnonymousAllowed = true)
        val savedPost = post(id = 30L, board = board, member = member, isAnonymous = true)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(boardRepository.findByIdAndUniversityId(board.id, university.id)).thenReturn(board)
        `when`(postRepository.save(any(Post::class.java))).thenReturn(savedPost)
        `when`(postAnonymousService.getOrCreateAnonNumber(savedPost, member)).thenReturn(1)

        val postId = postWriteService.createPost(
            PostCommand.CreatePost(
                userId = member.id.toString(),
                boardId = board.id,
                title = "hello",
                content = "content",
                isAnonymous = true,
                images = listOf(PostCommand.ImageInput("https://example.com/1.png", 0))
            )
        )

        assertEquals(savedPost.id, postId)
        verify(postRepository).save(any(Post::class.java))
        verify(postImageRepository).saveAll(anyList())
        verify(postAnonymousService).getOrCreateAnonNumber(savedPost, member)
    }

    @Test
    fun `like post increments count and saves like`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val board = board(id = 3L, university = university, isAnonymousAllowed = true)
        val post = post(id = 40L, board = board, member = member, isAnonymous = false).apply { likeCount = 0 }

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(postRepository.findByIdAndBoardUniversityId(post.id, university.id)).thenReturn(post)
        `when`(postLikeRepository.existsByPostIdAndMemberId(post.id, member.id)).thenReturn(false)

        postWriteService.likePost(PostCommand.LikePost(member.id.toString(), post.id))

        assertEquals(1, post.likeCount)
        verify(postLikeRepository).save(any(PostLike::class.java))
    }

    @Test
    fun `unlike post decrements count`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val board = board(id = 3L, university = university, isAnonymousAllowed = true)
        val post = post(id = 40L, board = board, member = member, isAnonymous = false).apply { likeCount = 1 }
        val postLike = PostLike(id = 50L, post = post, member = member)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(postRepository.findByIdAndBoardUniversityId(post.id, university.id)).thenReturn(post)
        `when`(postLikeRepository.findByPostIdAndMemberId(post.id, member.id)).thenReturn(postLike)

        postWriteService.unlikePost(PostCommand.UnlikePost(member.id.toString(), post.id))

        assertEquals(0, post.likeCount)
        verify(postLikeRepository).delete(postLike)
    }

    @Test
    fun `like post maps unique constraint race to duplicate error`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val board = board(id = 3L, university = university, isAnonymousAllowed = true)
        val post = post(id = 40L, board = board, member = member, isAnonymous = false).apply { likeCount = 0 }

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(postRepository.findByIdAndBoardUniversityId(post.id, university.id)).thenReturn(post)
        `when`(postLikeRepository.existsByPostIdAndMemberId(post.id, member.id)).thenReturn(false)
        `when`(postLikeRepository.save(any(PostLike::class.java))).thenThrow(DataIntegrityViolationException("duplicate"))

        val exception = assertThrows(BusinessException::class.java) {
            postWriteService.likePost(PostCommand.LikePost(member.id.toString(), post.id))
        }

        assertEquals(ErrorCode.POST_LIKE_DUPLICATE, exception.errorCode)
        assertEquals(0, post.likeCount)
    }

    @Test
    fun `delete post marks post deleted for owner`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val board = board(id = 3L, university = university, isAnonymousAllowed = true)
        val post = post(id = 40L, board = board, member = member, isAnonymous = false)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(postRepository.findByIdAndBoardUniversityId(post.id, university.id)).thenReturn(post)

        postWriteService.deletePost(PostCommand.DeletePost(member.id.toString(), post.id))

        assertTrue(post.isDeleted)
    }

    @Test
    fun `delete post fails for non owner`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val otherMember = member(id = 5L, university = university)
        val board = board(id = 3L, university = university, isAnonymousAllowed = true)
        val post = post(id = 40L, board = board, member = otherMember, isAnonymous = false)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(postRepository.findByIdAndBoardUniversityId(post.id, university.id)).thenReturn(post)

        val exception = assertThrows(BusinessException::class.java) {
            postWriteService.deletePost(PostCommand.DeletePost(member.id.toString(), post.id))
        }

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode)
        assertFalse(post.isDeleted)
    }

    @Test
    fun `create post fails when image display orders are duplicated`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val board = board(id = 3L, university = university, isAnonymousAllowed = true)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(boardRepository.findByIdAndUniversityId(board.id, university.id)).thenReturn(board)

        val exception = assertThrows(BusinessException::class.java) {
            postWriteService.createPost(
                PostCommand.CreatePost(
                    userId = member.id.toString(),
                    boardId = board.id,
                    title = "hello",
                    content = "content",
                    isAnonymous = false,
                    images = listOf(
                        PostCommand.ImageInput("https://example.com/1.png", 0),
                        PostCommand.ImageInput("https://example.com/2.png", 0)
                    )
                )
            )
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        verify(postRepository, never()).save(any(Post::class.java))
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

    private fun board(id: Long, university: University, isAnonymousAllowed: Boolean): Board {
        return Board(
            id = id,
            university = university,
            code = "free",
            name = "Free Talk",
            isAnonymousAllowed = isAnonymousAllowed
        )
    }

    private fun post(id: Long, board: Board, member: Member, isAnonymous: Boolean): Post {
        return Post(
            id = id,
            board = board,
            member = member,
            title = "hello",
            content = "content",
            isAnonymous = isAnonymous,
            likeCount = 0,
            commentCount = 0,
            isDeleted = false
        )
    }
}
