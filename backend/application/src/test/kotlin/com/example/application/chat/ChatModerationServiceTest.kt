package com.example.application.chat

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.chat.ChatBlock
import com.example.domain.chat.ChatBlockRepository
import com.example.domain.chat.ChatMessage
import com.example.domain.chat.ChatMessageRepository
import com.example.domain.chat.ChatReport
import com.example.domain.chat.ChatReportRepository
import com.example.domain.chat.MessageRoom
import com.example.domain.chat.MessageRoomRepository
import com.example.domain.major.Major
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import com.example.domain.board.Board
import com.example.domain.university.University
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

class ChatModerationServiceTest {
    private lateinit var memberRepository: MemberRepository
    private lateinit var chatBlockRepository: ChatBlockRepository
    private lateinit var chatReportRepository: ChatReportRepository
    private lateinit var messageRoomRepository: MessageRoomRepository
    private lateinit var chatMessageRepository: ChatMessageRepository
    private lateinit var chatModerationService: ChatModerationService

    @BeforeEach
    fun setUp() {
        memberRepository = mock(MemberRepository::class.java)
        chatBlockRepository = mock(ChatBlockRepository::class.java)
        chatReportRepository = mock(ChatReportRepository::class.java)
        messageRoomRepository = mock(MessageRoomRepository::class.java)
        chatMessageRepository = mock(ChatMessageRepository::class.java)
        chatModerationService = ChatModerationService(
            memberRepository = memberRepository,
            chatBlockRepository = chatBlockRepository,
            chatReportRepository = chatReportRepository,
            messageRoomRepository = messageRoomRepository,
            chatMessageRepository = chatMessageRepository,
        )
    }

    @Test
    fun `block creates new relation`() {
        val university = university(1L)
        val major = major(university)
        val requester = member(1L, university, major)
        val target = member(2L, university, major)

        `when`(memberRepository.findById(requester.id)).thenReturn(Optional.of(requester))
        `when`(memberRepository.findById(target.id)).thenReturn(Optional.of(target))
        `when`(chatBlockRepository.findByMember1IdAndMember2Id(1L, 2L)).thenReturn(null)
        `when`(chatBlockRepository.save(any(ChatBlock::class.java))).thenAnswer { it.getArgument(0) }

        val result = chatModerationService.block(
            ChatModerationCommand.Block(requester.id, target.id)
        )

        assertEquals(true, result.blocked)
        assertEquals(true, result.created)
        assertEquals(target.id, result.targetMemberId)
    }

    @Test
    fun `report auto blocks target`() {
        val university = university(1L)
        val major = major(university)
        val requester = member(1L, university, major)
        val target = member(2L, university, major)
        val report = ChatReport(
            id = 10L,
            reporter = requester,
            target = target,
            roomId = null,
            messageId = null,
            reason = "spam",
            detail = "too many unwanted messages",
        )

        `when`(memberRepository.findById(requester.id)).thenReturn(Optional.of(requester))
        `when`(memberRepository.findById(target.id)).thenReturn(Optional.of(target))
        `when`(chatReportRepository.save(any(ChatReport::class.java))).thenReturn(report)
        `when`(chatBlockRepository.findByMember1IdAndMember2Id(1L, 2L)).thenReturn(null)
        `when`(chatBlockRepository.save(any(ChatBlock::class.java))).thenAnswer { it.getArgument(0) }

        val result = chatModerationService.report(
            ChatModerationCommand.Report(
                requesterId = requester.id,
                targetMemberId = target.id,
                reason = "spam",
                detail = "too many unwanted messages",
            )
        )

        assertEquals(report.id, result.reportId)
        assertEquals(true, result.blocked)
        assertEquals(true, result.blockCreated)
    }

    @Test
    fun `block fails across universities`() {
        val universityA = university(1L)
        val universityB = university(2L)
        val majorA = major(universityA)
        val majorB = major(universityB)
        val requester = member(1L, universityA, majorA)
        val target = member(2L, universityB, majorB)

        `when`(memberRepository.findById(requester.id)).thenReturn(Optional.of(requester))
        `when`(memberRepository.findById(target.id)).thenReturn(Optional.of(target))

        val exception = assertThrows(BusinessException::class.java) {
            chatModerationService.block(ChatModerationCommand.Block(requester.id, target.id))
        }

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode)
        assertEquals("cross_university_chat_not_allowed", exception.customMessage)
    }

    @Test
    fun `report validates message target`() {
        val university = university(1L)
        val major = major(university)
        val requester = member(1L, university, major)
        val target = member(2L, university, major)
        val board = Board(
            id = 20L,
            university = university,
            code = "free",
            name = "Free",
            isAnonymousAllowed = true,
        )
        val post = Post(
            id = 30L,
            board = board,
            member = requester,
            title = "t",
            content = "c",
            isAnonymous = true,
            likeCount = 0,
            commentCount = 0,
            isDeleted = false,
        )
        val room = MessageRoom(
            id = 40L,
            post = post,
            member1 = requester,
            member2 = target,
            isAnon1 = true,
            isAnon2 = true,
        )
        val message = ChatMessage(
            id = 50L,
            room = room,
            sender = requester,
            content = "hi",
        )

        `when`(memberRepository.findById(requester.id)).thenReturn(Optional.of(requester))
        `when`(memberRepository.findById(target.id)).thenReturn(Optional.of(target))
        `when`(chatMessageRepository.findById(message.id)).thenReturn(Optional.of(message))

        val exception = assertThrows(BusinessException::class.java) {
            chatModerationService.report(
                ChatModerationCommand.Report(
                    requesterId = requester.id,
                    targetMemberId = target.id,
                    messageId = message.id,
                    reason = "abuse",
                )
            )
        }

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode)
        assertEquals("invalid_report_target_message", exception.customMessage)
    }

    private fun university(id: Long): University =
        University(
            id = id,
            name = "University-$id",
            emailDomain = "uni$id.ac.jp",
        )

    private fun major(university: University): Major =
        Major(
            id = university.id * 10,
            university = university,
            code = "CS",
            name = "Computer Science",
        )

    private fun member(id: Long, university: University, major: Major): Member =
        Member(
            id = id,
            university = university,
            major = major,
            email = "user$id@${university.emailDomain}",
            password = "pw",
            nickname = "user$id",
            role = "ROLE_USER",
        )
}

