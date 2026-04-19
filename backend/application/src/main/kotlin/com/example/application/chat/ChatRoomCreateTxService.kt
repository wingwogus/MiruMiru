package com.example.application.chat

import com.example.domain.chat.MessageRoom
import com.example.domain.chat.MessageRoomRepository
import com.example.domain.member.MemberRepository
import com.example.domain.post.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class ChatRoomCreateTxService(
    private val messageRoomRepository: MessageRoomRepository,
    private val postRepository: PostRepository,
    private val memberRepository: MemberRepository,
) {

    data class CreateRequest(
        val postId: Long,
        val member1Id: Long,
        val member2Id: Long,
        val isAnon1: Boolean,
        val isAnon2: Boolean,
    )

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun create(request: CreateRequest): MessageRoom {
        val room = MessageRoom(
            post = postRepository.getReferenceById(request.postId),
            member1 = memberRepository.getReferenceById(request.member1Id),
            member2 = memberRepository.getReferenceById(request.member2Id),
            isAnon1 = request.isAnon1,
            isAnon2 = request.isAnon2,
        )

        return messageRoomRepository.saveAndFlush(room)
    }
}
