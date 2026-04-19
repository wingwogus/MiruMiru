package com.example.application.post

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.board.Board
import com.example.domain.board.BoardRepository
import com.example.domain.comment.Comment
import com.example.domain.comment.CommentRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import com.example.domain.post.PostAnonymousMapping
import com.example.domain.post.PostAnonymousMappingRepository
import com.example.domain.post.PostImage
import com.example.domain.post.PostImageRepository
import com.example.domain.post.PostLikeRepository
import com.example.domain.post.PostRepository
import com.example.domain.university.University
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.util.Optional

class PostQueryServiceTest {
    private lateinit var memberRepository: MemberRepository
    private lateinit var boardRepository: BoardRepository
    private lateinit var postRepository: PostRepository
    private lateinit var postImageRepository: PostImageRepository
    private lateinit var postLikeRepository: PostLikeRepository
    private lateinit var commentRepository: CommentRepository
    private lateinit var postAnonymousMappingRepository: PostAnonymousMappingRepository
    private lateinit var postQueryService: PostQueryService

    @BeforeEach
    fun setUp() {
        memberRepository = mock(MemberRepository::class.java)
        boardRepository = mock(BoardRepository::class.java)
        postRepository = mock(PostRepository::class.java)
        postImageRepository = mock(PostImageRepository::class.java)
        postLikeRepository = mock(PostLikeRepository::class.java)
        commentRepository = mock(CommentRepository::class.java)
        postAnonymousMappingRepository = mock(PostAnonymousMappingRepository::class.java)
        postQueryService = PostQueryService(
            memberRepository = memberRepository,
            boardRepository = boardRepository,
            postRepository = postRepository,
            postImageRepository = postImageRepository,
            postLikeRepository = postLikeRepository,
            commentRepository = commentRepository,
            postAnonymousMappingRepository = postAnonymousMappingRepository
        )
    }

    @Test
    fun `get post detail maps anonymous numbers likes and comment tree`() {
        val university = university()
        val author = member(id = 2L, university = university)
        val commenter = member(id = 3L, university = university)
        val board = board(university)
        val post = post(id = 40L, board = board, member = author, isAnonymous = true).apply {
            likeCount = 1
            commentCount = 2
            createdAt = LocalDateTime.of(2026, 3, 22, 12, 0)
            updatedAt = LocalDateTime.of(2026, 3, 22, 12, 30)
        }
        val image = PostImage(id = 41L, post = post, imageUrl = "https://example.com/lunch.png", displayOrder = 0)
        val rootComment = Comment(
            id = 50L,
            post = post,
            member = commenter,
            parent = null,
            content = "root",
            isAnonymous = true,
            isDeleted = false
        ).apply { createdAt = LocalDateTime.of(2026, 3, 22, 12, 31) }
        val childComment = Comment(
            id = 51L,
            post = post,
            member = author,
            parent = rootComment,
            content = "child",
            isAnonymous = true,
            isDeleted = false
        ).apply { createdAt = LocalDateTime.of(2026, 3, 22, 12, 32) }

        `when`(memberRepository.findById(author.id)).thenReturn(Optional.of(author))
        `when`(postRepository.findByIdAndBoardUniversityIdAndIsDeletedFalse(post.id, university.id)).thenReturn(post)
        `when`(postImageRepository.findAllByPostIdOrderByDisplayOrderAsc(post.id)).thenReturn(listOf(image))
        `when`(postLikeRepository.existsByPostIdAndMemberId(post.id, author.id)).thenReturn(true)
        `when`(commentRepository.findAllByPostIdOrderByCreatedAtAsc(post.id)).thenReturn(listOf(rootComment, childComment))
        `when`(postAnonymousMappingRepository.findAllByPostId(post.id)).thenReturn(
            listOf(
                PostAnonymousMapping(id = 60L, post = post, member = author, anonNumber = 1),
                PostAnonymousMapping(id = 61L, post = post, member = commenter, anonNumber = 2)
            )
        )

        val result = postQueryService.getPostDetail(author.id.toString(), post.id)

        assertEquals("익명 1", result.authorDisplayName)
        assertEquals(true, result.isLikedByMe)
        assertEquals(1, result.comments.size)
        assertEquals("익명 2", result.comments.single().authorDisplayName)
        assertEquals(1, result.comments.single().children.size)
        assertEquals("익명 1", result.comments.single().children.single().authorDisplayName)
    }

    @Test
    fun `get board posts fails when board is inaccessible`() {
        val university = university()
        val member = member(id = 2L, university = university)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(boardRepository.findByIdAndUniversityId(999L, university.id)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            postQueryService.getBoardPosts(member.id.toString(), 999L)
        }

        assertEquals(ErrorCode.BOARD_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `get hot posts returns anonymous board agnostic items with expected ordering request`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val freeBoard = board(university)
        val generalBoard = Board(
            id = 4L,
            university = university,
            code = "general",
            name = "General",
            isAnonymousAllowed = false
        )
        val anonymousHotPost = post(id = 40L, board = freeBoard, member = member(id = 3L, university = university), isAnonymous = true).apply {
            likeCount = 5
            commentCount = 2
            createdAt = LocalDateTime.of(2026, 3, 22, 12, 0)
        }
        val normalHotPost = post(id = 41L, board = generalBoard, member = member, isAnonymous = false).apply {
            likeCount = 3
            commentCount = 1
            createdAt = LocalDateTime.of(2026, 3, 22, 11, 0)
        }

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        doReturn(listOf(anonymousHotPost, normalHotPost)).`when`(
            postRepository
        ).findAllByBoardUniversityIdAndIsDeletedFalseAndLikeCountGreaterThanEqualAndCreatedAtGreaterThanEqual(
            eqValue(university.id),
            eqValue(1),
            anyObject(LocalDateTime::class.java),
            anyObject(Pageable::class.java)
        )
        `when`(postAnonymousMappingRepository.findAllByPostIdIn(listOf(anonymousHotPost.id, normalHotPost.id))).thenReturn(
            listOf(
                PostAnonymousMapping(id = 70L, post = anonymousHotPost, member = anonymousHotPost.member, anonNumber = 2)
            )
        )

        val result = postQueryService.getHotPosts(member.id.toString())

        assertEquals(2, result.size)
        assertEquals(freeBoard.id, result.first().boardId)
        assertEquals("free", result.first().boardCode)
        assertEquals("익명 2", result.first().authorDisplayName)
        assertEquals("user-2", result.last().authorDisplayName)
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

    private fun board(university: University): Board {
        return Board(
            id = 3L,
            university = university,
            code = "free",
            name = "Free Talk",
            isAnonymousAllowed = true
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyObject(type: Class<T>): T {
        ArgumentMatchers.any(type)
        return null as T
    }

    private fun <T> eqValue(value: T): T {
        ArgumentMatchers.eq(value)
        return value
    }
}
