package com.example.api.post

import com.example.ApiApplication
import com.example.application.security.TokenProvider
import com.example.domain.board.BoardRepository
import com.example.domain.comment.CommentRepository
import com.example.domain.member.MemberRepository
import com.example.domain.post.PostAnonymousMappingRepository
import com.example.domain.post.PostImageRepository
import com.example.domain.post.PostLikeRepository
import com.example.domain.post.PostRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
@ActiveProfiles("local")
class PostApiIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val memberRepository: MemberRepository,
    @Autowired private val boardRepository: BoardRepository,
    @Autowired private val postRepository: PostRepository,
    @Autowired private val postImageRepository: PostImageRepository,
    @Autowired private val postLikeRepository: PostLikeRepository,
    @Autowired private val postAnonymousMappingRepository: PostAnonymousMappingRepository,
    @Autowired private val commentRepository: CommentRepository,
    @Autowired private val tokenProvider: TokenProvider
) {
    private lateinit var accessToken: String
    private lateinit var emptyAccessToken: String
    private var generalBoardId: Long = 0L
    private var freeBoardId: Long = 0L
    private var seededGeneralPostId: Long = 0L
    private var seededFreePostId: Long = 0L
    private var emptyMemberId: Long = 0L

    @BeforeEach
    fun setUp() {
        val member = memberRepository.findByEmail("test@tokyo.ac.jp")!!
        val emptyMember = memberRepository.findByEmail("empty@tokyo.ac.jp")!!
        accessToken = tokenProvider.createAccessToken(member.id, member.role)
        emptyAccessToken = tokenProvider.createAccessToken(emptyMember.id, emptyMember.role)
        emptyMemberId = emptyMember.id
        generalBoardId = boardRepository.findByUniversityIdAndCode(member.university.id, "general")!!.id
        freeBoardId = boardRepository.findByUniversityIdAndCode(member.university.id, "free")!!.id
        seededGeneralPostId = postRepository.findByBoardIdAndTitle(generalBoardId, "Welcome to MiruMiru")!!.id
        seededFreePostId = postRepository.findByBoardIdAndTitle(freeBoardId, "Best lunch near campus?")!!.id
    }

    @AfterEach
    fun tearDown() {
        val createdPosts = postRepository.findAllByMemberId(emptyMemberId)
            .filter { it.title.startsWith("[test]") }
        if (createdPosts.isEmpty()) {
            return
        }

        val postIds = createdPosts.map { it.id }
        commentRepository.deleteAll(commentRepository.findAllByPostIdIn(postIds))
        postLikeRepository.deleteAll(postLikeRepository.findAllByPostIdIn(postIds))
        postAnonymousMappingRepository.deleteAll(postAnonymousMappingRepository.findAllByPostIdIn(postIds))
        postImageRepository.deleteAll(postImageRepository.findAllByPostIdIn(postIds))
        postRepository.deleteAll(createdPosts)
    }

    @Test
    fun `get my boards returns seeded boards`() {
        val response = mockMvc.get("/api/v1/boards/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"code\":\"general\""))
        assertTrue(response.contentAsString.contains("\"code\":\"free\""))
        assertTrue(response.contentAsString.contains("\"isAnonymousAllowed\":true"))
    }

    @Test
    fun `get board posts returns seeded posts with anonymous numbering`() {
        val response = mockMvc.get("/api/v1/boards/$freeBoardId/posts") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"title\":\"Best lunch near campus?\""))
        assertTrue(response.contentAsString.contains("\"authorDisplayName\":\"익명 1\""))
        assertTrue(response.contentAsString.contains("\"commentCount\":2"))
    }

    @Test
    fun `get hot posts returns seeded hot posts across boards`() {
        val response = mockMvc.get("/api/v1/posts/hot") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"postId\":$seededFreePostId"))
        assertTrue(response.contentAsString.contains("\"boardId\":$freeBoardId"))
        assertTrue(response.contentAsString.contains("\"boardCode\":\"free\""))
        assertTrue(response.contentAsString.contains("\"boardName\":\"Free Talk\""))
        assertTrue(response.contentAsString.contains("\"authorDisplayName\":\"익명 1\""))
        assertTrue(response.contentAsString.contains("\"likeCount\":1"))
    }

    @Test
    fun `get hot posts requires authentication`() {
        val response = mockMvc.get("/api/v1/posts/hot")
            .andReturn().response

        assertEquals(401, response.status)
    }

    @Test
    fun `get post detail returns seeded post detail images likes and comments`() {
        val response = mockMvc.get("/api/v1/posts/$seededFreePostId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
        }.andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"title\":\"Best lunch near campus?\""))
        assertTrue(response.contentAsString.contains("\"authorDisplayName\":\"익명 1\""))
        assertTrue(response.contentAsString.contains("\"isLikedByMe\":true"))
        assertTrue(response.contentAsString.contains("\"authorDisplayName\":\"익명 2\""))
        assertTrue(response.contentAsString.contains("\"children\":["))
    }

    @Test
    fun `post create post returns created response and persists detail`() {
        val title = "[test] Need textbook tips"

        val createResponse = mockMvc.post("/api/v1/boards/$freeBoardId/posts") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "title": "$title",
                  "content": "Where can I buy used textbooks?",
                  "isAnonymous": true,
                  "images": [
                    {"imageUrl":"https://example.com/textbooks.png","displayOrder":0}
                  ]
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(201, createResponse.status)
        assertTrue(createResponse.contentAsString.contains("\"postId\":"))

        val createdPost = postRepository.findByBoardIdAndTitle(freeBoardId, title)
        assertNotNull(createdPost)

        val detailResponse = mockMvc.get("/api/v1/posts/${createdPost!!.id}") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
        }.andReturn().response

        assertEquals(200, detailResponse.status)
        assertTrue(detailResponse.contentAsString.contains("\"authorDisplayName\":\"익명 1\""))
        assertTrue(detailResponse.contentAsString.contains("\"isMine\":true"))
        assertTrue(detailResponse.contentAsString.contains("\"imageUrl\":\"https://example.com/textbooks.png\""))
    }

    @Test
    fun `post create post rejects anonymous on anonymous disabled board`() {
        val response = mockMvc.post("/api/v1/boards/$generalBoardId/posts") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "title": "[test] Anonymous attempt",
                  "content": "Should fail",
                  "isAnonymous": true,
                  "images": []
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("POST_002"))
    }

    @Test
    fun `post like and unlike update created post detail`() {
        val title = "[test] Like me"
        val createdPostId = createPost(title = title, isAnonymous = false)

        val likeResponse = mockMvc.post("/api/v1/posts/$createdPostId/likes") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, likeResponse.status)

        val likedDetailResponse = mockMvc.get("/api/v1/posts/$createdPostId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, likedDetailResponse.status)
        assertTrue(likedDetailResponse.contentAsString.contains("\"isLikedByMe\":true"))
        assertTrue(likedDetailResponse.contentAsString.contains("\"likeCount\":1"))

        val unlikeResponse = mockMvc.delete("/api/v1/posts/$createdPostId/likes") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, unlikeResponse.status)

        val unlikedDetailResponse = mockMvc.get("/api/v1/posts/$createdPostId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, unlikedDetailResponse.status)
        assertTrue(unlikedDetailResponse.contentAsString.contains("\"isLikedByMe\":false"))
        assertTrue(unlikedDetailResponse.contentAsString.contains("\"likeCount\":0"))
    }

    @Test
    fun `post create and delete comment update detail tree`() {
        val title = "[test] Comment me"
        val createdPostId = createPost(title = title, isAnonymous = true)

        val createRootResponse = mockMvc.post("/api/v1/posts/$createdPostId/comments") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "content": "root comment",
                  "isAnonymous": true
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(201, createRootResponse.status)
        val rootCommentId = extractId(createRootResponse.contentAsString, "commentId")

        val createChildResponse = mockMvc.post("/api/v1/posts/$createdPostId/comments") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "content": "child comment",
                  "parentId": $rootCommentId,
                  "isAnonymous": true
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(201, createChildResponse.status)
        val childCommentId = extractId(createChildResponse.contentAsString, "commentId")

        val detailResponse = mockMvc.get("/api/v1/posts/$createdPostId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, detailResponse.status)
        assertTrue(detailResponse.contentAsString.contains("\"commentCount\":2"))
        assertTrue(detailResponse.contentAsString.contains("\"authorDisplayName\":\"익명 2\""))
        assertTrue(detailResponse.contentAsString.contains("\"authorDisplayName\":\"익명 1\""))

        val deleteResponse = mockMvc.delete("/api/v1/comments/$childCommentId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
        }.andReturn().response

        assertEquals(200, deleteResponse.status)

        val deletedDetailResponse = mockMvc.get("/api/v1/posts/$createdPostId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, deletedDetailResponse.status)
        assertTrue(deletedDetailResponse.contentAsString.contains("\"commentCount\":1"))
        assertTrue(deletedDetailResponse.contentAsString.contains("\"content\":\"삭제된 댓글입니다.\""))
    }

    @Test
    fun `delete post hides post detail afterward`() {
        val createdPostId = createPost(title = "[test] Delete me", isAnonymous = false)

        val deleteResponse = mockMvc.delete("/api/v1/posts/$createdPostId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
        }.andReturn().response

        assertEquals(200, deleteResponse.status)

        val detailResponse = mockMvc.get("/api/v1/posts/$createdPostId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
        }.andReturn().response

        assertEquals(404, detailResponse.status)
        assertTrue(detailResponse.contentAsString.contains("POST_001"))
    }

    private fun createPost(title: String, isAnonymous: Boolean): Long {
        val response = mockMvc.post("/api/v1/boards/$freeBoardId/posts") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "title": "$title",
                  "content": "integration test body",
                  "isAnonymous": $isAnonymous,
                  "images": []
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(201, response.status)
        return extractId(response.contentAsString, "postId")
    }

    private fun extractId(content: String, field: String): Long {
        val regex = """"$field":(\d+)""".toRegex()
        return regex.find(content)?.groupValues?.get(1)?.toLong()
            ?: error("Unable to extract $field from response: $content")
    }
}
