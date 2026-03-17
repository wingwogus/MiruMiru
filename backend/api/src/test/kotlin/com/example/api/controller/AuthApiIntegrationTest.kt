package com.example.api.controller

import com.example.ApiApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
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
class AuthApiIntegrationTest(
    @Autowired private val mockMvc: MockMvc
) {

    @Test
    fun `get majors returns seeded university majors without requiring authentication`() {
        val response = mockMvc.get("/api/v1/auth/majors") {
            param("email", "user@tokyo.ac.jp")
        }.andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"majorId\":"))
        assertTrue(response.contentAsString.contains("\"code\":\"CS\""))
        assertTrue(response.contentAsString.contains("\"name\":\"Computer Science\""))
    }

    @Test
    fun `get majors returns business error for unregistered university email`() {
        val response = mockMvc.get("/api/v1/auth/majors") {
            param("email", "user@kyoto.ac.jp")
        }.andReturn().response

        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("AUTH_010"))
    }
}
