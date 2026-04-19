package com.example.api.chat

import com.example.ApiApplication
import com.example.application.chat.ChatCommand
import com.example.application.chat.ChatRoomCreateTxService
import com.example.application.chat.ChatResult
import com.example.application.chat.ChatService
import com.example.domain.board.BoardRepository
import com.example.domain.chat.MessageRoomRepository
import com.example.domain.member.MemberRepository
import com.example.domain.post.PostRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    classes = [ApiApplication::class],
    properties = [
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password"
    ]
)
@ActiveProfiles("local", "test")
class ChatRoomConcurrencyIntegrationTest(
    @Autowired private val chatService: ChatService,
    @Autowired private val memberRepository: MemberRepository,
    @Autowired private val boardRepository: BoardRepository,
    @Autowired private val postRepository: PostRepository,
    @Autowired private val messageRoomRepository: MessageRoomRepository,
) {
    @SpyBean
    private lateinit var chatRoomCreateTxService: ChatRoomCreateTxService

    private var requesterId: Long = 0L
    private var ownerId: Long = 0L
    private var seededPostId: Long = 0L

    @BeforeEach
    fun setUp() {
        messageRoomRepository.deleteAll()

        val requester = memberRepository.findByEmail("empty@tokyo.ac.jp")!!
        val owner = memberRepository.findByEmail("test@tokyo.ac.jp")!!
        val freeBoardId = boardRepository.findByUniversityIdAndCode(requester.university.id, "free")!!.id

        requesterId = requester.id
        ownerId = owner.id
        seededPostId = postRepository.findByBoardIdAndTitle(freeBoardId, "Best lunch near campus?")!!.id
    }

    @AfterEach
    fun tearDown() {
        reset(chatRoomCreateTxService)
        messageRoomRepository.deleteAll()
    }

    @Test
    fun `concurrent create room resolves duplicate without leaving broken session state`() {
        val ready = CountDownLatch(2)
        val release = CountDownLatch(1)
        val createRequest = ChatRoomCreateTxService.CreateRequest(
            postId = seededPostId,
            member1Id = ownerId,
            member2Id = requesterId,
            isAnon1 = true,
            isAnon2 = true,
        )

        doAnswer { invocation ->
            ready.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "Timed out waiting to release concurrent room creation" }
            invocation.callRealMethod()
        }.`when`(chatRoomCreateTxService).create(createRequest)

        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = listOf(1, 2).map {
                executor.submit<ChatResult.RoomCreated> {
                    chatService.createRoom(
                        ChatCommand.CreateRoom(
                            requesterId = requesterId,
                            postId = seededPostId,
                            requesterIsAnonymous = true,
                        )
                    )
                }
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS))
            release.countDown()

            val results = futures.map { it.get(10, TimeUnit.SECONDS) }

            assertEquals(1L, messageRoomRepository.count())
            assertEquals(1, results.map { it.roomId }.toSet().size)
            assertEquals(setOf(true, false), results.map { it.created }.toSet())
        } finally {
            executor.shutdownNow()
        }
    }
}
