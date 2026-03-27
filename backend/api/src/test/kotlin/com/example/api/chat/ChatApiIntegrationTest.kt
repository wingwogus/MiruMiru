package com.example.api.chat

import com.example.ApiApplication
import com.example.application.security.TokenProvider
import com.example.domain.board.BoardRepository
import com.example.domain.chat.ChatMessageRepository
import com.example.domain.chat.MessageRoomRepository
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
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
class ChatApiIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val memberRepository: MemberRepository,
    @Autowired private val boardRepository: BoardRepository,
    @Autowired private val postRepository: PostRepository,
    @Autowired private val messageRoomRepository: MessageRoomRepository,
    @Autowired private val chatMessageRepository: ChatMessageRepository,
    @Autowired private val tokenProvider: TokenProvider,
) {
    private lateinit var ownerAccessToken: String
    private lateinit var requesterAccessToken: String
    private var seededPostId: Long = 0L

    @BeforeEach
    fun setUp() {
        val owner = memberRepository.findByEmail("test@tokyo.ac.jp")!!
        val requester = memberRepository.findByEmail("empty@tokyo.ac.jp")!!
        ownerAccessToken = tokenProvider.createAccessToken(owner.id, owner.role)
        requesterAccessToken = tokenProvider.createAccessToken(requester.id, requester.role)
        val freeBoardId = boardRepository.findByUniversityIdAndCode(owner.university.id, "free")!!.id
        seededPostId = postRepository.findByBoardIdAndTitle(freeBoardId, "Best lunch near campus?")!!.id
    }

    @AfterEach
    fun tearDown() {
        chatMessageRepository.deleteAll()
        messageRoomRepository.deleteAll()
    }

    @Test
    fun `create room returns existing room on duplicate request`() {
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

        assertEquals(201, createResponse.status)
        assertTrue(createResponse.contentAsString.contains("\"created\":true"))

        val roomId = extractId(createResponse.contentAsString, "roomId")

        val duplicateResponse = mockMvc.post("/api/v1/message-rooms") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $requesterAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "postId": $seededPostId,
                  "requesterIsAnonymous": true
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(200, duplicateResponse.status)
        assertTrue(duplicateResponse.contentAsString.contains("\"created\":false"))
        assertTrue(duplicateResponse.contentAsString.contains("\"roomId\":$roomId"))
    }

    @Test
    fun `room message flow returns unread counts and read markers`() {
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
        val roomId = extractId(createResponse.contentAsString, "roomId")

        val sendResponse = mockMvc.post("/api/v1/message-rooms/$roomId/messages") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $requesterAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"[test] hello chat"}"""
        }.andReturn().response

        assertEquals(200, sendResponse.status)

        val ownerRoomsResponse = mockMvc.get("/api/v1/message-rooms") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerAccessToken")
        }.andReturn().response

        assertEquals(200, ownerRoomsResponse.status)
        assertTrue(ownerRoomsResponse.contentAsString.contains("\"roomId\":$roomId"))
        assertTrue(ownerRoomsResponse.contentAsString.contains("\"unreadCount\":1"))

        val messagesResponse = mockMvc.get("/api/v1/message-rooms/$roomId/messages") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerAccessToken")
        }.andReturn().response

        assertEquals(200, messagesResponse.status)
        assertTrue(messagesResponse.contentAsString.contains("[test] hello chat"))
        val messageId = extractId(messagesResponse.contentAsString, "id")

        val readResponse = mockMvc.patch("/api/v1/message-rooms/$roomId/read") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"lastReadMessageId":$messageId}"""
        }.andReturn().response

        assertEquals(200, readResponse.status)
        assertTrue(readResponse.contentAsString.contains("\"unreadCount\":0"))

        val refreshedRoomsResponse = mockMvc.get("/api/v1/message-rooms") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerAccessToken")
        }.andReturn().response

        assertEquals(200, refreshedRoomsResponse.status)
        assertTrue(refreshedRoomsResponse.contentAsString.contains("\"myLastReadMessageId\":$messageId"))
        assertTrue(refreshedRoomsResponse.contentAsString.contains("\"unreadCount\":0"))
    }

    private fun extractId(content: String, field: String): Long {
        val regex = """"$field":(\d+)""".toRegex()
        return regex.find(content)?.groupValues?.get(1)?.toLong()
            ?: throw IllegalStateException("Failed to extract $field from $content")
    }
}
