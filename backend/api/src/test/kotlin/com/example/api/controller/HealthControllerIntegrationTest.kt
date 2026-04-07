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
@ActiveProfiles("local", "test")
class HealthControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc
) {

    @Test
    fun `health endpoint returns up without authentication`() {
        val response = mockMvc.get("/api/v1/health")
            .andReturn()
            .response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"status\":\"UP\""))
    }
}
