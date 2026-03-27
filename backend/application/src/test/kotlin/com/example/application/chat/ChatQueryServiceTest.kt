package com.example.application.chat

import com.example.application.chat.read.ChatMessageReadRepository
import com.example.application.chat.read.ChatQueryResult
import com.example.application.chat.read.ChatRoomReadRepository
import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.board.Board
import com.example.domain.chat.MessageRoom
import com.example.domain.chat.MessageRoomRepository
import com.example.domain.major.Major
import com.example.domain.member.Member
import com.example.domain.post.Post
import com.example.domain.university.University
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDateTime
import java.util.Optional

class ChatQueryServiceTest {
    private lateinit var messageRoomRepository: MessageRoomRepository
    private lateinit var chatRoomReadRepository: ChatRoomReadRepository
    private lateinit var chatMessageReadRepository: ChatMessageReadRepository
    private lateinit var chatQueryService: ChatQueryService

    @BeforeEach
    fun setUp() {
        messageRoomRepository = mock(MessageRoomRepository::class.java)
        chatRoomReadRepository = mock(ChatRoomReadRepository::class.java)
        chatMessageReadRepository = mock(ChatMessageReadRepository::class.java)
        chatQueryService = ChatQueryService(
            messageRoomRepository = messageRoomRepository,
            chatRoomReadRepository = chatRoomReadRepository,
            chatMessageReadRepository = chatMessageReadRepository,
        )
    }

    @Test
    fun `get my rooms normalizes zero read markers to null`() {
        `when`(chatRoomReadRepository.findMyRooms(1L, 30)).thenReturn(
            listOf(
                ChatQueryResult.RoomSummary(
                    roomId = 10L,
                    postId = 20L,
                    postTitle = "seed post",
                    otherMemberId = 2L,
                    lastMessageId = 99L,
                    lastMessageContent = "hi",
                    lastMessageCreatedAt = LocalDateTime.of(2026, 3, 28, 0, 0),
                    unreadCount = 1L,
                    myLastReadMessageId = 0L,
                    otherLastReadMessageId = 55L,
                    isAnonMe = true,
                    isAnonOther = false,
                )
            )
        )

        val result = chatQueryService.getMyRooms(
            ChatQuery.GetMyRooms(
                requesterId = 1L,
                limit = 30,
            )
        )

        assertEquals(1, result.rooms.size)
        assertEquals(null, result.rooms.first().myLastReadMessageId)
        assertEquals(55L, result.rooms.first().otherLastReadMessageId)
    }

    @Test
    fun `get messages returns paged read model from read repository`() {
        val room = room(member1Id = 1L, member2Id = 2L)
        `when`(messageRoomRepository.findById(room.id)).thenReturn(Optional.of(room))
        `when`(chatMessageReadRepository.findBefore(room.id, 100L, 20)).thenReturn(
            listOf(
                ChatQueryResult.MessageSummary(
                    id = 91L,
                    roomId = room.id,
                    senderId = 1L,
                    content = "newer message",
                    createdAt = LocalDateTime.of(2026, 3, 28, 1, 1),
                ),
                ChatQueryResult.MessageSummary(
                    id = 90L,
                    roomId = room.id,
                    senderId = 2L,
                    content = "older message",
                    createdAt = LocalDateTime.of(2026, 3, 28, 1, 0),
                )
            )
        )

        val result = chatQueryService.getMessages(
            ChatQuery.GetMessages(
                requesterId = 1L,
                roomId = room.id,
                beforeMessageId = 100L,
                limit = 20,
            )
        )

        assertEquals(room.id, result.roomId)
        assertEquals(2, result.messages.size)
        assertEquals(90L, result.messages.first().id)
        assertEquals(91L, result.messages.last().id)
        assertEquals(90L, result.nextBeforeMessageId)
    }

    @Test
    fun `get messages rejects non participant`() {
        val room = room(member1Id = 1L, member2Id = 2L)
        `when`(messageRoomRepository.findById(room.id)).thenReturn(Optional.of(room))

        val exception = assertThrows(BusinessException::class.java) {
            chatQueryService.getMessages(
                ChatQuery.GetMessages(
                    requesterId = 9L,
                    roomId = room.id,
                    beforeMessageId = null,
                    limit = 30,
                )
            )
        }

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode)
    }

    @Test
    fun `get messages returns chat room not found when room is missing`() {
        `when`(messageRoomRepository.findById(999L)).thenReturn(Optional.empty())

        val exception = assertThrows(BusinessException::class.java) {
            chatQueryService.getMessages(
                ChatQuery.GetMessages(
                    requesterId = 1L,
                    roomId = 999L,
                    beforeMessageId = null,
                    limit = 30,
                )
            )
        }

        assertEquals(ErrorCode.CHAT_ROOM_NOT_FOUND, exception.errorCode)
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
