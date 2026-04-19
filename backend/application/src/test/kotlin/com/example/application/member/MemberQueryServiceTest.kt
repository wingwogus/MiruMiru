package com.example.application.member

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.major.Major
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.university.University
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MemberQueryServiceTest {
    private lateinit var memberRepository: MemberRepository
    private lateinit var memberQueryService: MemberQueryService

    @BeforeEach
    fun setUp() {
        memberRepository = mock(MemberRepository::class.java)
        memberQueryService = MemberQueryService(memberRepository)
    }

    @Test
    fun `get my profile returns member profile with university and major`() {
        val university = University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp")
        val major = Major(id = 2L, university = university, code = "CS", name = "Computer Science")
        val member = Member(
            id = 3L,
            university = university,
            major = major,
            email = "test@tokyo.ac.jp",
            password = "encoded",
            nickname = "test-user"
        )

        `when`(memberRepository.findProfileById(member.id)).thenReturn(member)

        val result = memberQueryService.getMyProfile(member.id.toString())

        assertEquals(member.id, result.memberId)
        assertEquals(member.email, result.email)
        assertEquals(member.nickname, result.nickname)
        assertEquals(university.name, result.university.name)
        assertEquals(major.code, result.major.code)
    }

    @Test
    fun `get my profile throws user not found when member does not exist`() {
        `when`(memberRepository.findProfileById(99L)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            memberQueryService.getMyProfile("99")
        }

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.errorCode)
    }
}
