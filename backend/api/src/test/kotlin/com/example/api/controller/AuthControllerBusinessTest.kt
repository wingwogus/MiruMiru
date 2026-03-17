package com.example.api.controller

import com.example.api.exception.GlobalExceptionHandler
import com.example.application.auth.AuthCommand
import com.example.application.auth.AuthService
import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

class AuthControllerBusinessTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        authService = mock(AuthService::class.java)
        val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }

        mockMvc = MockMvcBuilders
            .standaloneSetup(AuthController(authService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setValidator(validator)
            .build()
    }

    @Test
    fun `signup returns business error when university is not registered`() {
        doThrow(BusinessException(ErrorCode.UNREGISTERED_UNIVERSITY))
            .`when`(authService)
            .signUp(AuthCommand.SignUp("user@kyoto.ac.jp", "password123", "maru"))

        val response = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@kyoto.ac.jp","password":"password123","nickname":"maru"}"""
        }.andReturn().response

        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains(ErrorCode.UNREGISTERED_UNIVERSITY.code))
    }
}
