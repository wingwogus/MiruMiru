package com.example.application.bootstrap

import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.university.University
import com.example.domain.university.UniversityRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
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
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val initializer = LocalTestDataInitializer(
        universityRepository = universityRepository,
        memberRepository = memberRepository,
        passwordEncoder = passwordEncoder
    )

    @Test
    fun `creates university and test member when both are missing`() {
        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp")).thenReturn(null)
        `when`(universityRepository.save(org.mockito.ArgumentMatchers.any(University::class.java)))
            .thenReturn(University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp"))
        `when`(memberRepository.existsByEmail("test@tokyo.ac.jp")).thenReturn(false)
        `when`(passwordEncoder.encode("password123!")).thenReturn("encoded-password")

        initializer.run(DefaultApplicationArguments(*emptyArray<String>()))

        verify(universityRepository, times(1)).save(org.mockito.ArgumentMatchers.any(University::class.java))
        val memberCaptor = ArgumentCaptor.forClass(Member::class.java)
        verify(memberRepository).save(memberCaptor.capture())
        assertEquals("test@tokyo.ac.jp", memberCaptor.value.email)
        assertEquals("test-user", memberCaptor.value.nickname)
        assertEquals("tokyo.ac.jp", memberCaptor.value.university.emailDomain)
        assertEquals("encoded-password", memberCaptor.value.password)
    }

    @Test
    fun `reuses existing university and skips member creation when test member exists`() {
        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp"))
            .thenReturn(University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp"))
        `when`(memberRepository.existsByEmail("test@tokyo.ac.jp")).thenReturn(true)

        initializer.run(DefaultApplicationArguments(*emptyArray<String>()))

        verify(universityRepository, never()).save(org.mockito.ArgumentMatchers.any(University::class.java))
        verify(memberRepository, never()).save(org.mockito.ArgumentMatchers.any(Member::class.java))
    }
}
