package com.example.api.chat

import com.example.ApiApplication
import com.example.application.security.TokenProvider
import com.example.domain.board.BoardRepository
import com.example.domain.chat.ChatBlockRepository
import com.example.domain.chat.ChatReportRepository
import com.example.domain.member.MemberRepository
import com.example.domain.post.PostRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(
    classes = [ApiApplication::class],
    properties = [
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password"
    ]
)
@AutoConfigureMockMvc
@ActiveProfiles("local", "test")
class ChatModerationApiIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val boardRepository: BoardRepository,
    @Autowired private val memberRepository: MemberRepository,
    @Autowired private val chatBlockRepository: ChatBlockRepository,
    @Autowired private val chatReportRepository: ChatReportRepository,
    @Autowired private val postRepository: PostRepository,
    @Autowired private val tokenProvider: TokenProvider,
) {
    private lateinit var ownerAccessToken: String
    private lateinit var requesterAccessToken: String
    private var ownerId: Long = 0L
    private var requesterId: Long = 0L
    private var seededPostId: Long = 0L

    @BeforeEach
    fun setUp() {
        val owner = memberRepository.findByEmail("test@tokyo.ac.jp")!!
        val requester = memberRepository.findByEmail("empty@tokyo.ac.jp")!!
        ownerId = owner.id
        requesterId = requester.id
        ownerAccessToken = tokenProvider.createAccessToken(owner.id, owner.role)
        requesterAccessToken = tokenProvider.createAccessToken(requester.id, requester.role)
        val freeBoardId = boardRepository.findByUniversityIdAndCode(owner.university.id, "free")!!.id
        seededPostId = postRepository.findByBoardIdAndTitle(freeBoardId, "Best lunch near campus?")!!.id
    }

    @AfterEach
    fun tearDown() {
        chatReportRepository.deleteAll()
        chatBlockRepository.deleteAll()
    }

    @Test
    fun `block list and unblock endpoints work`() {
        val blockResponse = mockMvc.post("/api/v1/chat/blocks") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $requesterAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"targetMemberId":$ownerId}"""
        }.andReturn().response

        assertEquals(200, blockResponse.status)
        assertTrue(blockResponse.contentAsString.contains("\"targetMemberId\":$ownerId"))
        assertTrue(blockResponse.contentAsString.contains("\"created\":true"))

        val listResponse = mockMvc.get("/api/v1/chat/blocks") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $requesterAccessToken")
        }.andReturn().response

        assertEquals(200, listResponse.status)
        assertTrue(listResponse.contentAsString.contains("\"targetMemberId\":$ownerId"))

        val unblockResponse = mockMvc.delete("/api/v1/chat/blocks/$ownerId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $requesterAccessToken")
        }.andReturn().response

        assertEquals(200, unblockResponse.status)
        assertTrue(unblockResponse.contentAsString.contains("\"unblocked\":true"))
    }

    @Test
    fun `report endpoint auto blocks target`() {
        val reportResponse = mockMvc.post("/api/v1/chat/reports") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $requesterAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "targetMemberId": $ownerId,
                  "reason": "spam",
                  "detail": "unwanted messages"
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(200, reportResponse.status)
        assertTrue(reportResponse.contentAsString.contains("\"targetMemberId\":$ownerId"))
        assertTrue(reportResponse.contentAsString.contains("\"blocked\":true"))
    }

    @Test
    fun `unblocking my own row does not remove the other member block`() {
        mockMvc.post("/api/v1/chat/blocks") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $requesterAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"targetMemberId":$ownerId}"""
        }.andExpect {
            status { isOk() }
        }

        mockMvc.post("/api/v1/chat/blocks") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"targetMemberId":$requesterId}"""
        }.andExpect {
            status { isOk() }
        }

        mockMvc.delete("/api/v1/chat/blocks/$ownerId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $requesterAccessToken")
        }.andExpect {
            status { isOk() }
        }

        val createResponse = mockMvc.post("/api/v1/message-rooms") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $requesterAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "postId": $seededPostId,
                  "requesterIsAnonymous": true
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(403, createResponse.status)
        assertTrue(createResponse.contentAsString.contains("chat_blocked_between_members"))
    }
}
