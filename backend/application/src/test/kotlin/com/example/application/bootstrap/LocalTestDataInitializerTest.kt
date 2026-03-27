package com.example.application.bootstrap

import com.example.domain.board.Board
import com.example.domain.board.BoardRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import com.example.domain.post.PostRepository
import com.example.domain.university.University
import com.example.domain.university.UniversityRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.security.crypto.password.PasswordEncoder

class LocalTestDataInitializerTest {
    private val universityRepository = mock(UniversityRepository::class.java)
    private val memberRepository = mock(MemberRepository::class.java)
    private val boardRepository = mock(BoardRepository::class.java)
    private val postRepository = mock(PostRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val initializer = LocalTestDataInitializer(
        universityRepository = universityRepository,
        memberRepository = memberRepository,
        boardRepository = boardRepository,
        postRepository = postRepository,
        passwordEncoder = passwordEncoder
    )

    @Test
    fun `creates local seed resources when missing`() {
        val university = University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp")

        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp")).thenReturn(null)
        `when`(universityRepository.save(any(University::class.java)))
            .thenReturn(university)
        `when`(memberRepository.findByEmail("test@tokyo.ac.jp")).thenReturn(null)
        `when`(memberRepository.findByEmail("test2@tokyo.ac.jp")).thenReturn(null)
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.getArgument(0) }
        `when`(passwordEncoder.encode("password123!")).thenReturn("encoded-password")
        `when`(boardRepository.findByUniversityAndName(university, "Local Free Board")).thenReturn(null)
        `when`(boardRepository.save(any(Board::class.java))).thenAnswer { it.getArgument(0) }
        `when`(postRepository.findByTitle("[LOCAL] Chat Seed Post")).thenReturn(null)
        `when`(postRepository.save(any(Post::class.java))).thenAnswer { it.getArgument(0) }

        initializer.run(DefaultApplicationArguments(*emptyArray<String>()))

        verify(universityRepository, times(1)).save(any(University::class.java))
        val memberCaptor = ArgumentCaptor.forClass(Member::class.java)
        verify(memberRepository, times(2)).save(memberCaptor.capture())
        assertEquals("test@tokyo.ac.jp", memberCaptor.allValues.first().email)
        assertEquals("test-user", memberCaptor.allValues.first().nickname)
        assertEquals("tokyo.ac.jp", memberCaptor.allValues.first().university.emailDomain)
        assertEquals("encoded-password", memberCaptor.allValues.first().password)
        verify(boardRepository, times(1)).save(any(Board::class.java))
        verify(postRepository, times(1)).save(any(Post::class.java))
    }

    @Test
    fun `reuses existing resources and skips creation when seed exists`() {
        val university = University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp")
        val member1 = Member(
            id = 1L,
            university = university,
            email = "test@tokyo.ac.jp",
            password = "encoded-password",
            nickname = "test-user",
            role = "ROLE_USER"
        )
        val member2 = Member(
            id = 2L,
            university = university,
            email = "test2@tokyo.ac.jp",
            password = "encoded-password",
            nickname = "test-user-2",
            role = "ROLE_USER"
        )
        val board = Board(
            id = 1L,
            university = university,
            category = "COMMUNITY",
            name = "Local Free Board",
            isAnonymousAllowed = true,
        )
        val post = Post(
            id = 1L,
            board = board,
            member = member2,
            title = "[LOCAL] Chat Seed Post",
            content = "This is a local seed post for creating 1:1 chat rooms.",
            isAnonymous = true,
        )

        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp")).thenReturn(university)
        `when`(memberRepository.findByEmail("test@tokyo.ac.jp")).thenReturn(member1)
        `when`(memberRepository.findByEmail("test2@tokyo.ac.jp")).thenReturn(member2)
        `when`(boardRepository.findByUniversityAndName(university, "Local Free Board")).thenReturn(board)
        `when`(postRepository.findByTitle("[LOCAL] Chat Seed Post")).thenReturn(post)

        initializer.run(DefaultApplicationArguments(*emptyArray<String>()))

        verify(universityRepository, never()).save(any(University::class.java))
        verify(memberRepository, never()).save(any(Member::class.java))
        verify(boardRepository, never()).save(any(Board::class.java))
        verify(postRepository, never()).save(any(Post::class.java))
    }
}
