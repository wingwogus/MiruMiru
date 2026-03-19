package com.example.api.controller

import com.example.ApiApplication
import com.example.application.security.TokenProvider
import com.example.domain.member.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(
    classes = [ApiApplication::class],
    properties = [
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password"
    ]
)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MemberApiIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val memberRepository: MemberRepository,
    @Autowired private val tokenProvider: TokenProvider
) {
    private lateinit var accessToken: String

    @BeforeEach
    fun setUp() {
        val member = memberRepository.findByEmail("test@tokyo.ac.jp")!!
        accessToken = tokenProvider.createAccessToken(member.id, member.role)
    }

    @Test
    fun `get my profile returns authenticated members profile`() {
        val response = mockMvc.get("/api/v1/members/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"memberId\":"))
        assertTrue(response.contentAsString.contains("\"email\":\"test@tokyo.ac.jp\""))
        assertTrue(response.contentAsString.contains("\"nickname\":\"test-user\""))
        assertTrue(response.contentAsString.contains("\"name\":\"The University of Tokyo\""))
        assertTrue(response.contentAsString.contains("\"code\":\"CS\""))
    }

    @Test
    fun `get my profile returns unauthorized without token`() {
        val response = mockMvc.get("/api/v1/members/me")
            .andReturn().response

        assertEquals(401, response.status)
    }
}
