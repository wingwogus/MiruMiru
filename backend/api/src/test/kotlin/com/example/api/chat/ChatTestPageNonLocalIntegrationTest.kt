package com.example.api.chat

import com.example.ApiApplication
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
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.properties.mail.smtp.starttls.required=false",
        "spring.mail.properties.mail.smtp.connectiontimeout=1000",
        "spring.mail.properties.mail.smtp.timeout=1000",
        "spring.mail.properties.mail.smtp.writetimeout=1000",
        "spring.mail.auth-code-expiration-millis=1800000",
        "spring.mail.verified-state-expiration-millis=1800000",
        "redis.host=localhost",
        "redis.port=6379",
        "jwt.secret=t2oRk29vBQZWS8GEt4xr8AJznlPK0ipBKUwdyqe10SOGZB26vVBMjzqualdJsjcOY1wX9DOqJC9V1DFl58F0tQ==",
        "spring.datasource.url=jdbc:h2:mem:chat-page-non-local;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.open-in-view=false"
    ]
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatTestPageNonLocalIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `chat test page is not exposed outside local profile`() {
        mockMvc.get("/chat-test.html")
            .andExpect {
                status { isNotFound() }
            }
    }
}
