package com.example.application.chat

import com.example.application.chat.event.ChatEvent
import com.example.application.chat.event.ChatEventPublisher
import com.example.application.chat.event.ChatEventType
import com.example.application.chat.read.ChatMessageReadRepository
import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.board.Board
import com.example.domain.chat.ChatMessage
import com.example.domain.chat.ChatMessageRepository
import com.example.domain.chat.MessageRoom
import com.example.domain.chat.MessageRoomRepository
import com.example.domain.major.Major
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import com.example.domain.post.PostRepository
import com.example.domain.university.University
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import java.util.Optional

class ChatServiceTest {
    private lateinit var postRepository: PostRepository
    private lateinit var memberRepository: MemberRepository
    private lateinit var messageRoomRepository: MessageRoomRepository
    private lateinit var chatMessageRepository: ChatMessageRepository
    private lateinit var chatMessageReadRepository: ChatMessageReadRepository
    private lateinit var chatEventPublisher: RecordingChatEventPublisher
    private lateinit var chatService: ChatService

    @BeforeEach
    fun setUp() {
        postRepository = mock(PostRepository::class.java)
        memberRepository = mock(MemberRepository::class.java)
        messageRoomRepository = mock(MessageRoomRepository::class.java)
        chatMessageRepository = mock(ChatMessageRepository::class.java)
        chatMessageReadRepository = mock(ChatMessageReadRepository::class.java)
        chatEventPublisher = RecordingChatEventPublisher()
        chatService = ChatService(
            postRepository = postRepository,
            memberRepository = memberRepository,
            messageRoomRepository = messageRoomRepository,
            chatMessageRepository = chatMessageRepository,
            chatMessageReadRepository = chatMessageReadRepository,
            chatEventPublisher = chatEventPublisher,
        )
    }

    @Test
    fun `create room allows post owner to choose partner`() {
        val university = university()
        val major = major(university)
        val owner = member(id = 1L, university = university, major = major, email = "owner@tokyo.ac.jp")
        val partner = member(id = 2L, university = university, major = major, email = "partner@tokyo.ac.jp")
        val board = board(id = 10L, university = university)
        val post = post(id = 20L, board = board, member = owner, isAnonymous = true)
        val savedRoom = MessageRoom(
            id = 30L,
            post = post,
            member1 = owner,
            member2 = partner,
            isAnon1 = false,
            isAnon2 = true,
        )

        `when`(memberRepository.findById(owner.id)).thenReturn(Optional.of(owner))
        `when`(postRepository.findByIdAndBoardUniversityIdAndIsDeletedFalse(post.id, university.id)).thenReturn(post)
        `when`(memberRepository.findByIdAndUniversityId(partner.id, university.id)).thenReturn(partner)
        `when`(messageRoomRepository.save(any(MessageRoom::class.java))).thenReturn(savedRoom)

        val result = chatService.createRoom(
            ChatCommand.CreateRoom(
                requesterId = owner.id,
                postId = post.id,
                requesterIsAnonymous = false,
                partnerMemberId = partner.id,
            )
        )

        assertEquals(30L, result.roomId)
        assertEquals(owner.id, result.member1Id)
        assertEquals(partner.id, result.member2Id)
        assertEquals(true, result.isAnon2)
        assertEquals(true, result.created)
    }

    @Test
    fun `create room fails when post owner omits partner`() {
        val university = university()
        val major = major(university)
        val owner = member(id = 1L, university = university, major = major, email = "owner@tokyo.ac.jp")
        val board = board(id = 10L, university = university)
        val post = post(id = 20L, board = board, member = owner, isAnonymous = true)

        `when`(memberRepository.findById(owner.id)).thenReturn(Optional.of(owner))
        `when`(postRepository.findByIdAndBoardUniversityIdAndIsDeletedFalse(post.id, university.id)).thenReturn(post)

        val exception = assertThrows(BusinessException::class.java) {
            chatService.createRoom(
                ChatCommand.CreateRoom(
                    requesterId = owner.id,
                    postId = post.id,
                    requesterIsAnonymous = true,
                    partnerMemberId = null,
                )
            )
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        verify(messageRoomRepository, never()).save(any(MessageRoom::class.java))
    }

    @Test
    fun `create room returns existing room when duplicate room already exists`() {
        val university = university()
        val major = major(university)
        val owner = member(id = 1L, university = university, major = major, email = "owner@tokyo.ac.jp")
        val partner = member(id = 2L, university = university, major = major, email = "partner@tokyo.ac.jp")
        val board = board(id = 10L, university = university)
        val post = post(id = 20L, board = board, member = owner, isAnonymous = true)
        val existing = MessageRoom(
            id = 77L,
            post = post,
            member1 = owner,
            member2 = partner,
            isAnon1 = false,
            isAnon2 = true,
        )

        `when`(memberRepository.findById(owner.id)).thenReturn(Optional.of(owner))
        `when`(postRepository.findByIdAndBoardUniversityIdAndIsDeletedFalse(post.id, university.id)).thenReturn(post)
        `when`(memberRepository.findByIdAndUniversityId(partner.id, university.id)).thenReturn(partner)
        `when`(
            messageRoomRepository.findByPostIdAndMember1IdAndMember2Id(
                post.id,
                owner.id,
                partner.id
            )
        ).thenReturn(existing)

        val result = chatService.createRoom(
            ChatCommand.CreateRoom(
                requesterId = owner.id,
                postId = post.id,
                requesterIsAnonymous = false,
                partnerMemberId = partner.id,
            )
        )

        assertEquals(existing.id, result.roomId)
        assertEquals(owner.id, result.member1Id)
        assertEquals(partner.id, result.member2Id)
        assertEquals(existing.isAnon1, result.isAnon1)
        assertEquals(existing.isAnon2, result.isAnon2)
        assertEquals(false, result.created)
        verify(messageRoomRepository, never()).save(any(MessageRoom::class.java))
    }

    @Test
    fun `create room by non owner always targets post author`() {
        val university = university()
        val major = major(university)
        val owner = member(id = 1L, university = university, major = major, email = "owner@tokyo.ac.jp")
        val requester = member(id = 3L, university = university, major = major, email = "requester@tokyo.ac.jp")
        val arbitraryPartner = member(id = 2L, university = university, major = major, email = "other@tokyo.ac.jp")
        val board = board(id = 10L, university = university)
        val post = post(id = 20L, board = board, member = owner, isAnonymous = true)
        val savedRoom = MessageRoom(
            id = 31L,
            post = post,
            member1 = requester,
            member2 = owner,
            isAnon1 = true,
            isAnon2 = true,
        )

        `when`(memberRepository.findById(requester.id)).thenReturn(Optional.of(requester))
        `when`(postRepository.findByIdAndBoardUniversityIdAndIsDeletedFalse(post.id, university.id)).thenReturn(post)
        `when`(messageRoomRepository.save(any(MessageRoom::class.java))).thenReturn(savedRoom)

        val result = chatService.createRoom(
            ChatCommand.CreateRoom(
                requesterId = requester.id,
                postId = post.id,
                requesterIsAnonymous = true,
                partnerMemberId = arbitraryPartner.id,
            )
        )

        assertEquals(owner.id, result.member2Id)
        assertEquals(true, result.created)
        verify(memberRepository, never()).findByIdAndUniversityId(arbitraryPartner.id, university.id)
        verify(messageRoomRepository, times(1))
            .findByPostIdAndMember1IdAndMember2Id(eq(post.id), eq(owner.id), eq(requester.id))
    }

    @Test
    fun `create room blocks posts outside requesters university or deleted posts`() {
        val requesterUniversity = university(id = 100L, name = "The University of Tokyo")
        val major = major(requesterUniversity)
        val requester = member(id = 1L, university = requesterUniversity, major = major, email = "requester@tokyo.ac.jp")

        `when`(memberRepository.findById(requester.id)).thenReturn(Optional.of(requester))
        `when`(postRepository.findByIdAndBoardUniversityIdAndIsDeletedFalse(999L, requesterUniversity.id)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            chatService.createRoom(
                ChatCommand.CreateRoom(
                    requesterId = requester.id,
                    postId = 999L,
                    requesterIsAnonymous = true,
                )
            )
        }

        assertEquals(ErrorCode.POST_NOT_FOUND, exception.errorCode)
        verify(messageRoomRepository, never()).save(any(MessageRoom::class.java))
    }

    @Test
    fun `create room rejects partner outside requesters university`() {
        val university = university()
        val foreignUniversity = university(id = 101L, name = "Kyoto University", emailDomain = "kyoto.ac.jp")
        val major = major(university)
        val foreignMajor = major(foreignUniversity, id = 201L, code = "EE")
        val owner = member(id = 1L, university = university, major = major, email = "owner@tokyo.ac.jp")
        val partner = member(id = 2L, university = foreignUniversity, major = foreignMajor, email = "partner@kyoto.ac.jp")
        val board = board(id = 10L, university = university)
        val post = post(id = 20L, board = board, member = owner, isAnonymous = true)

        `when`(memberRepository.findById(owner.id)).thenReturn(Optional.of(owner))
        `when`(postRepository.findByIdAndBoardUniversityIdAndIsDeletedFalse(post.id, university.id)).thenReturn(post)
        `when`(memberRepository.findByIdAndUniversityId(partner.id, university.id)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            chatService.createRoom(
                ChatCommand.CreateRoom(
                    requesterId = owner.id,
                    postId = post.id,
                    requesterIsAnonymous = false,
                    partnerMemberId = partner.id,
                )
            )
        }

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.errorCode)
        verify(messageRoomRepository, never()).save(any(MessageRoom::class.java))
    }

    @Test
    fun `create room normalizes participant order when requester id is larger`() {
        val university = university()
        val major = major(university)
        val owner = member(id = 1L, university = university, major = major, email = "owner@tokyo.ac.jp")
        val requester = member(id = 3L, university = university, major = major, email = "requester@tokyo.ac.jp")
        val board = board(id = 10L, university = university)
        val post = post(id = 20L, board = board, member = owner, isAnonymous = true)
        val existing = MessageRoom(
            id = 77L,
            post = post,
            member1 = owner,
            member2 = requester,
            isAnon1 = true,
            isAnon2 = false,
        )

        `when`(memberRepository.findById(requester.id)).thenReturn(Optional.of(requester))
        `when`(postRepository.findByIdAndBoardUniversityIdAndIsDeletedFalse(post.id, university.id)).thenReturn(post)
        `when`(
            messageRoomRepository.findByPostIdAndMember1IdAndMember2Id(
                post.id,
                owner.id,
                requester.id,
            )
        ).thenReturn(existing)

        val result = chatService.createRoom(
            ChatCommand.CreateRoom(
                requesterId = requester.id,
                postId = post.id,
                requesterIsAnonymous = false,
            )
        )

        assertEquals(existing.id, result.roomId)
        assertEquals(requester.id, result.member1Id)
        assertEquals(owner.id, result.member2Id)
        assertEquals(false, result.isAnon1)
        assertEquals(true, result.isAnon2)
        verify(messageRoomRepository, never()).save(any(MessageRoom::class.java))
    }

    @Test
    fun `create room recovers when unique constraint races with another request`() {
        val university = university()
        val major = major(university)
        val owner = member(id = 1L, university = university, major = major, email = "owner@tokyo.ac.jp")
        val partner = member(id = 2L, university = university, major = major, email = "partner@tokyo.ac.jp")
        val board = board(id = 10L, university = university)
        val post = post(id = 20L, board = board, member = owner, isAnonymous = true)
        val existing = MessageRoom(
            id = 88L,
            post = post,
            member1 = owner,
            member2 = partner,
            isAnon1 = false,
            isAnon2 = true,
        )

        `when`(memberRepository.findById(owner.id)).thenReturn(Optional.of(owner))
        `when`(postRepository.findByIdAndBoardUniversityIdAndIsDeletedFalse(post.id, university.id)).thenReturn(post)
        `when`(memberRepository.findByIdAndUniversityId(partner.id, university.id)).thenReturn(partner)
        `when`(messageRoomRepository.findByPostIdAndMember1IdAndMember2Id(post.id, owner.id, partner.id))
            .thenReturn(null)
            .thenReturn(existing)
        `when`(messageRoomRepository.save(any(MessageRoom::class.java)))
            .thenThrow(DataIntegrityViolationException("duplicate"))

        val result = chatService.createRoom(
            ChatCommand.CreateRoom(
                requesterId = owner.id,
                postId = post.id,
                requesterIsAnonymous = false,
                partnerMemberId = partner.id,
            )
        )

        assertEquals(existing.id, result.roomId)
        assertEquals(false, result.created)
    }

    @Test
    fun `send message fails when sender is not room participant`() {
        val university = university()
        val major = major(university)
        val sender = member(id = 9L, university = university, major = major, email = "sender@tokyo.ac.jp")
        val member1 = member(id = 1L, university = university, major = major, email = "member1@tokyo.ac.jp")
        val member2 = member(id = 2L, university = university, major = major, email = "member2@tokyo.ac.jp")
        val board = board(id = 10L, university = university)
        val post = post(id = 20L, board = board, member = member1, isAnonymous = false)
        val room = MessageRoom(id = 30L, post = post, member1 = member1, member2 = member2)

        `when`(memberRepository.findById(sender.id)).thenReturn(Optional.of(sender))
        `when`(messageRoomRepository.findById(room.id)).thenReturn(Optional.of(room))

        val exception = assertThrows(BusinessException::class.java) {
            chatService.sendMessage(
                ChatCommand.SendMessage(
                    senderId = sender.id,
                    roomId = room.id,
                    content = "hello",
                )
            )
        }

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode)
        verify(chatMessageRepository, never()).save(any(ChatMessage::class.java))
        assertEquals(0, chatEventPublisher.events.size)
    }

    @Test
    fun `send message publishes MESSAGE and UNREAD_COUNT events`() {
        val university = university()
        val major = major(university)
        val sender = member(id = 1L, university = university, major = major, email = "sender@tokyo.ac.jp")
        val receiver = member(id = 2L, university = university, major = major, email = "receiver@tokyo.ac.jp")
        val board = board(id = 10L, university = university)
        val post = post(id = 20L, board = board, member = receiver, isAnonymous = false)
        val room = MessageRoom(id = 30L, post = post, member1 = sender, member2 = receiver)
        val savedMessage = ChatMessage(id = 40L, room = room, sender = sender, content = "hi")

        `when`(memberRepository.findById(sender.id)).thenReturn(Optional.of(sender))
        `when`(messageRoomRepository.findById(room.id)).thenReturn(Optional.of(room))
        `when`(chatMessageRepository.save(any(ChatMessage::class.java))).thenReturn(savedMessage)
        `when`(chatMessageReadRepository.countUnread(room.id, receiver.id, null)).thenReturn(1L)

        val result = chatService.sendMessage(
            ChatCommand.SendMessage(
                senderId = sender.id,
                roomId = room.id,
                content = "hi",
            )
        )

        assertEquals(savedMessage.id, result.id)
        assertEquals(room.id, result.roomId)
        assertEquals(sender.id, result.senderId)
        assertEquals(savedMessage.content, result.content)

        val eventTypes = chatEventPublisher.events.map { it.type }
        assertEquals(listOf(ChatEventType.MESSAGE, ChatEventType.UNREAD_COUNT), eventTypes)
    }

    @Test
    fun `mark read updates last read and publishes READ and UNREAD_COUNT events`() {
        val university = university()
        val major = major(university)
        val reader = member(id = 1L, university = university, major = major, email = "reader@tokyo.ac.jp")
        val other = member(id = 2L, university = university, major = major, email = "other@tokyo.ac.jp")
        val board = board(id = 10L, university = university)
        val post = post(id = 20L, board = board, member = other, isAnonymous = false)
        val room = MessageRoom(id = 30L, post = post, member1 = reader, member2 = other)

        `when`(messageRoomRepository.findById(room.id)).thenReturn(Optional.of(room))
        `when`(chatMessageRepository.existsByIdAndRoomId(55L, room.id)).thenReturn(true)
        `when`(messageRoomRepository.save(any(MessageRoom::class.java))).thenAnswer { it.getArgument(0) }
        `when`(chatMessageReadRepository.countUnread(room.id, reader.id, 55L)).thenReturn(0L)

        val result = chatService.markRead(
            ChatCommand.MarkRead(
                readerId = reader.id,
                roomId = room.id,
                lastReadMessageId = 55L,
            )
        )

        assertEquals(55L, result.lastReadMessageId)
        assertEquals(0L, result.unreadCount)

        val eventTypes = chatEventPublisher.events.map { it.type }
        assertEquals(listOf(ChatEventType.READ, ChatEventType.UNREAD_COUNT), eventTypes)
    }

    @Test
    fun `mark read rejects message outside room`() {
        val university = university()
        val major = major(university)
        val reader = member(id = 1L, university = university, major = major, email = "reader@tokyo.ac.jp")
        val other = member(id = 2L, university = university, major = major, email = "other@tokyo.ac.jp")
        val board = board(id = 10L, university = university)
        val post = post(id = 20L, board = board, member = other, isAnonymous = false)
        val room = MessageRoom(id = 30L, post = post, member1 = reader, member2 = other)

        `when`(messageRoomRepository.findById(room.id)).thenReturn(Optional.of(room))
        `when`(chatMessageRepository.existsByIdAndRoomId(999L, room.id)).thenReturn(false)

        val exception = assertThrows(BusinessException::class.java) {
            chatService.markRead(
                ChatCommand.MarkRead(
                    readerId = reader.id,
                    roomId = room.id,
                    lastReadMessageId = 999L,
                )
            )
        }

        assertEquals(ErrorCode.CHAT_MESSAGE_NOT_FOUND, exception.errorCode)
        verify(messageRoomRepository, never()).save(any(MessageRoom::class.java))
    }

    private fun university(
        id: Long = 100L,
        name: String = "The University of Tokyo",
        emailDomain: String = "tokyo.ac.jp",
    ): University =
        University(
            id = id,
            name = name,
            emailDomain = emailDomain,
        )

    private fun major(
        university: University,
        id: Long = 200L,
        code: String = "CS",
        name: String = "Computer Science",
    ): Major =
        Major(
            id = id,
            university = university,
            code = code,
            name = name,
        )

    private fun member(id: Long, university: University, major: Major, email: String): Member =
        Member(
            id = id,
            university = university,
            major = major,
            email = email,
            password = "encoded-password",
            nickname = "user-$id",
            role = "ROLE_USER",
        )

    private fun board(id: Long, university: University): Board =
        Board(
            id = id,
            university = university,
            code = "free",
            name = "Free Talk",
            isAnonymousAllowed = true,
        )

    private fun post(id: Long, board: Board, member: Member, isAnonymous: Boolean): Post =
        Post(
            id = id,
            board = board,
            member = member,
            title = "seed post",
            content = "seed content",
            isAnonymous = isAnonymous,
            likeCount = 0,
            commentCount = 0,
            isDeleted = false,
        )

    private class RecordingChatEventPublisher : ChatEventPublisher {
        val events = mutableListOf<ChatEvent>()

        override fun publish(event: ChatEvent) {
            events += event
        }
    }
}
