package com.example.application.chat

import com.example.domain.board.Board
import com.example.domain.chat.ChatMessageRepository
import com.example.domain.chat.MessageRoom
import com.example.domain.chat.MessageRoomRepository
import com.example.domain.major.Major
import com.example.domain.member.Member
import com.example.domain.post.Post
import com.example.domain.university.University
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.LocalDateTime

class ChatRoomCleanupServiceTest {
    private lateinit var messageRoomRepository: MessageRoomRepository
    private lateinit var chatMessageRepository: ChatMessageRepository
    private lateinit var chatRoomCleanupService: ChatRoomCleanupService
    private lateinit var fixedNow: LocalDateTime

    @BeforeEach
    fun setUp() {
        messageRoomRepository = mock(MessageRoomRepository::class.java)
        chatMessageRepository = mock(ChatMessageRepository::class.java)
        fixedNow = LocalDateTime.of(2026, 3, 29, 0, 0, 0)
        chatRoomCleanupService = ChatRoomCleanupService(
            messageRoomRepository = messageRoomRepository,
            chatMessageRepository = chatMessageRepository,
            nowProvider = { fixedNow },
        )
    }

    @Test
    fun `delete rooms older than seven days removes messages first`() {
        val rooms = listOf(room(1L), room(2L))
        val cutoff = fixedNow.minusDays(7)
        `when`(messageRoomRepository.findAllByCreatedAtBefore(cutoff)).thenReturn(rooms)

        val deleted = chatRoomCleanupService.deleteRoomsOlderThan(7)

        assertEquals(2L, deleted)
        verify(chatMessageRepository).deleteByRoomIdIn(listOf(1L, 2L))
        verify(messageRoomRepository).deleteAllByIdInBatch(listOf(1L, 2L))
    }

    @Test
    fun `delete rooms older than seven days skips when nothing expired`() {
        val cutoff = fixedNow.minusDays(7)
        `when`(messageRoomRepository.findAllByCreatedAtBefore(cutoff)).thenReturn(emptyList())

        val deleted = chatRoomCleanupService.deleteRoomsOlderThan(7)

        assertEquals(0L, deleted)
        verifyNoInteractions(chatMessageRepository)
        verify(messageRoomRepository, never()).deleteAllByIdInBatch(listOf(1L))
    }

    private fun room(id: Long): MessageRoom {
        val university = University(
            id = 10L,
            name = "Tokyo",
            emailDomain = "tokyo.ac.jp",
        )
        val major = Major(
            id = 20L,
            university = university,
            code = "CS",
            name = "Computer Science",
        )
        val member1 = Member(
            id = 30L + id,
            university = university,
            major = major,
            email = "member1_$id@tokyo.ac.jp",
            password = "pw",
            nickname = "m1_$id",
            role = "ROLE_USER",
        )
        val member2 = Member(
            id = 40L + id,
            university = university,
            major = major,
            email = "member2_$id@tokyo.ac.jp",
            password = "pw",
            nickname = "m2_$id",
            role = "ROLE_USER",
        )
        val board = Board(
            id = 50L + id,
            university = university,
            code = "free",
            name = "Free",
            isAnonymousAllowed = true,
        )
        val post = Post(
            id = 60L + id,
            board = board,
            member = member1,
            title = "title-$id",
            content = "content-$id",
            isAnonymous = true,
            likeCount = 0,
            commentCount = 0,
            isDeleted = false,
        )
        return MessageRoom(
            id = id,
            post = post,
            member1 = member1,
            member2 = member2,
            isAnon1 = true,
            isAnon2 = true,
        )
    }
}
