package com.example.api.controller

import com.example.api.exception.GlobalExceptionHandler
import com.example.application.auth.AuthService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

class AuthControllerValidationTest {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val authService = mock(AuthService::class.java)
        val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }

        mockMvc = MockMvcBuilders
            .standaloneSetup(AuthController(authService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setValidator(validator)
            .build()
    }

    @Test
    fun `login rejects non japanese university email`() {
        val response = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@gmail.com","password":"password123"}"""
        }.andReturn().response

        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("email"))
    }

    @Test
    fun `send code rejects non japanese university email`() {
        val response = mockMvc.post("/api/v1/auth/email/send-code") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@gmail.com"}"""
        }.andReturn().response

        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("email"))
    }

    @Test
    fun `signup rejects non japanese university email`() {
        val response = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@gmail.com","password":"password123","nickname":"maru","majorId":1}"""
        }.andReturn().response

        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("email"))
    }

    @Test
    fun `signup rejects missing major selection`() {
        val response = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@tokyo.ac.jp","password":"password123","nickname":"maru"}"""
        }.andReturn().response

        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("majorId"))
    }

    @Test
    fun `get majors rejects non japanese university email`() {
        val response = mockMvc.get("/api/v1/auth/majors") {
            param("email", "user@gmail.com")
        }.andReturn().response

        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("email"))
    }
}
