package com.example.application.chat

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.chat.MessageRoom
import com.example.domain.chat.MessageRoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class ChatRoomRecoveryService(
    private val messageRoomRepository: MessageRoomRepository,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun findExisting(postId: Long, member1Id: Long, member2Id: Long): MessageRoom {
        return messageRoomRepository.findByPostIdAndMember1IdAndMember2Id(
            postId = postId,
            member1Id = member1Id,
            member2Id = member2Id,
        ) ?: throw BusinessException(ErrorCode.INTERNAL_ERROR)
    }
}
