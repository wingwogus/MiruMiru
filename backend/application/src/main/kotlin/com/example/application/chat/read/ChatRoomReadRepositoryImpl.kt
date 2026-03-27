package com.example.application.chat.read

import com.example.domain.chat.QChatMessage
import com.example.domain.chat.QMessageRoom
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class ChatRoomReadRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : ChatRoomReadRepository {

    override fun findMyRooms(memberId: Long, limit: Int): List<ChatQueryResult.RoomSummary> {
        val room = QMessageRoom.messageRoom
        val msg = QChatMessage.chatMessage
        val lastMsg = QChatMessage("lastMsg")

        val lastMessageIdSubquery = JPAExpressions
            .select(msg.id.max())
            .from(msg)
            .where(msg.room.id.eq(room.id))

        val otherMemberId = CaseBuilder()
            .`when`(room.member1.id.eq(memberId)).then(room.member2.id)
            .otherwise(room.member1.id)

        val myLastReadId = CaseBuilder()
            .`when`(room.member1.id.eq(memberId)).then(room.member1LastReadMessageId.coalesce(0L))
            .otherwise(room.member2LastReadMessageId.coalesce(0L))

        val otherLastReadId = CaseBuilder()
            .`when`(room.member1.id.eq(memberId)).then(room.member2LastReadMessageId.coalesce(0L))
            .otherwise(room.member1LastReadMessageId.coalesce(0L))

        val isAnonMe = CaseBuilder()
            .`when`(room.member1.id.eq(memberId)).then(room.isAnon1)
            .otherwise(room.isAnon2)

        val isAnonOther = CaseBuilder()
            .`when`(room.member1.id.eq(memberId)).then(room.isAnon2)
            .otherwise(room.isAnon1)

        val unreadCase = CaseBuilder()
            .`when`(
                msg.id.gt(myLastReadId)
                    .and(msg.sender.id.ne(memberId))
            )
            .then(1L)
            .otherwise(0L)

        return queryFactory
            .select(
                Projections.constructor(
                    ChatQueryResult.RoomSummary::class.java,
                    room.id,
                    room.post.id,
                    room.post.title,
                    otherMemberId,
                    lastMsg.id,
                    lastMsg.content,
                    lastMsg.createdAt,
                    unreadCase.sum().coalesce(0L),
                    myLastReadId,
                    otherLastReadId,
                    isAnonMe,
                    isAnonOther,
                )
            )
            .from(room)
            .leftJoin(lastMsg).on(lastMsg.id.eq(lastMessageIdSubquery))
            .leftJoin(msg).on(msg.room.id.eq(room.id))
            .where(
                room.member1.id.eq(memberId)
                    .or(room.member2.id.eq(memberId))
            )
            .groupBy(
                room.id,
                room.post.id,
                room.post.title,
                room.member1.id,
                room.member2.id,
                room.member1LastReadMessageId,
                room.member2LastReadMessageId,
                room.isAnon1,
                room.isAnon2,
                lastMsg.id,
                lastMsg.content,
                lastMsg.createdAt,
            )
            .orderBy(lastMsg.id.desc(), room.id.desc())
            .limit(limit.toLong())
            .fetch()
    }
}
