package com.example.application.chat

import com.example.application.chat.read.ChatMessageReadRepository
import com.example.application.chat.read.ChatQueryResult
import com.example.domain.board.Board
import com.example.domain.chat.ChatMessage
import com.example.domain.chat.MessageRoom
import com.example.domain.chat.MessageRoomSummary
import com.example.domain.chat.MessageRoomSummaryRepository
import com.example.domain.major.Major
import com.example.domain.member.Member
import com.example.domain.post.Post
import com.example.domain.university.University
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDateTime

class ChatRoomSummaryServiceTest {
    private lateinit var summaryRepository: MessageRoomSummaryRepository
    private lateinit var messageReadRepository: ChatMessageReadRepository
    private lateinit var service: ChatRoomSummaryService

    @BeforeEach
    fun setUp() {
        summaryRepository = mock(MessageRoomSummaryRepository::class.java)
        messageReadRepository = mock(ChatMessageReadRepository::class.java)
        service = ChatRoomSummaryService(
            messageRoomSummaryRepository = summaryRepository,
            chatMessageReadRepository = messageReadRepository,
        )
    }

    @Test
    fun `on message sent updates last message and receiver unread count`() {
        val room = room(member1Id = 1L, member2Id = 2L)
        val summary = MessageRoomSummary(roomId = room.id)
        val message = ChatMessage(
            id = 99L,
            room = room,
            sender = room.member1,
            content = "hello",
        ).also {
            it.createdAt = LocalDateTime.of(2026, 4, 9, 0, 0)
        }

        `when`(summaryRepository.findByRoomIdForUpdate(room.id)).thenReturn(summary)
        `when`(summaryRepository.save(summary)).thenReturn(summary)

        service.onMessageSent(
            room = room,
            message = message,
            receiverId = room.member2.id,
            receiverUnreadCount = 7L,
        )

        assertEquals(99L, summary.lastMessageId)
        assertEquals("hello", summary.lastMessageContent)
        assertEquals(7L, summary.member2UnreadCount)
    }

    @Test
    fun `on read marked overwrites reader unread count`() {
        val room = room(member1Id = 1L, member2Id = 2L)
        val summary = MessageRoomSummary(
            roomId = room.id,
            member1UnreadCount = 4L,
            member2UnreadCount = 9L,
        )

        `when`(summaryRepository.findByRoomIdForUpdate(room.id)).thenReturn(summary)
        `when`(summaryRepository.save(summary)).thenReturn(summary)

        service.onReadMarked(
            room = room,
            readerId = room.member2.id,
            readerUnreadCount = 0L,
        )

        assertEquals(0L, summary.member2UnreadCount)
        assertEquals(4L, summary.member1UnreadCount)
    }

    @Test
    fun `reconcile from read pointers recalculates unread for both members`() {
        val room = room(member1Id = 1L, member2Id = 2L).apply {
            member1LastReadMessageId = 10L
            member2LastReadMessageId = 20L
        }
        val summary = MessageRoomSummary(roomId = room.id)
        val latest = ChatQueryResult.MessageSummary(
            id = 21L,
            roomId = room.id,
            senderId = room.member1.id,
            content = "latest",
            createdAt = LocalDateTime.of(2026, 4, 9, 0, 1),
        )

        `when`(summaryRepository.findByRoomIdForUpdate(room.id)).thenReturn(summary)
        `when`(messageReadRepository.findLatest(room.id, 1)).thenReturn(listOf(latest))
        `when`(messageReadRepository.countUnread(room.id, room.member1.id, room.member1LastReadMessageId)).thenReturn(3L)
        `when`(messageReadRepository.countUnread(room.id, room.member2.id, room.member2LastReadMessageId)).thenReturn(1L)
        `when`(summaryRepository.save(summary)).thenReturn(summary)

        service.reconcileFromReadPointers(room)

        assertEquals(21L, summary.lastMessageId)
        assertEquals("latest", summary.lastMessageContent)
        assertEquals(3L, summary.member1UnreadCount)
        assertEquals(1L, summary.member2UnreadCount)
    }

    private fun room(member1Id: Long, member2Id: Long): MessageRoom {
        val university = University(
            id = 100L,
            name = "The University of Tokyo",
            emailDomain = "tokyo.ac.jp",
        )
        val major = Major(
            id = 200L,
            university = university,
            code = "CS",
            name = "Computer Science",
        )
        val member1 = Member(
            id = member1Id,
            university = university,
            major = major,
            email = "user-$member1Id@tokyo.ac.jp",
            password = "encoded-password",
            nickname = "user-$member1Id",
            role = "ROLE_USER",
        )
        val member2 = Member(
            id = member2Id,
            university = university,
            major = major,
            email = "user-$member2Id@tokyo.ac.jp",
            password = "encoded-password",
            nickname = "user-$member2Id",
            role = "ROLE_USER",
        )
        val board = Board(
            id = 10L,
            university = university,
            code = "free",
            name = "Free Talk",
            isAnonymousAllowed = true,
        )
        val post = Post(
            id = 20L,
            board = board,
            member = member1,
            title = "seed post",
            content = "seed content",
            isAnonymous = false,
            likeCount = 0,
            commentCount = 0,
            isDeleted = false,
        )
        return MessageRoom(
            id = 30L,
            post = post,
            member1 = member1,
            member2 = member2,
        )
    }
}
