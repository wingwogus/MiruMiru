package com.example.application.chat.read

import com.example.domain.chat.QMessageRoom
import com.example.domain.chat.QMessageRoomSummary
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class ChatRoomReadRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : ChatRoomReadRepository {

    override fun findMyRooms(memberId: Long, limit: Int): List<ChatQueryResult.RoomSummaryRow> {
        val room = QMessageRoom.messageRoom
        val summary = QMessageRoomSummary.messageRoomSummary

        val otherMemberId = CaseBuilder()
            .`when`(room.member1.id.eq(memberId)).then(room.member2.id)
            .otherwise(room.member1.id)

        val otherMemberNickname = CaseBuilder()
            .`when`(room.member1.id.eq(memberId)).then(room.member2.nickname)
            .otherwise(room.member1.nickname)

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

        val unreadCount = CaseBuilder()
            .`when`(room.member1.id.eq(memberId)).then(summary.member1UnreadCount.coalesce(0L))
            .otherwise(summary.member2UnreadCount.coalesce(0L))

        return queryFactory
            .select(
                Projections.constructor(
                    ChatQueryResult.RoomSummaryRow::class.java,
                    room.id,
                    room.post.id,
                    room.post.title,
                    otherMemberId,
                    otherMemberNickname,
                    summary.lastMessageId,
                    summary.lastMessageContent,
                    summary.lastMessageCreatedAt,
                    unreadCount,
                    myLastReadId,
                    otherLastReadId,
                    isAnonMe,
                    isAnonOther,
                    summary.roomId.isNotNull,
                )
            )
            .from(room)
            .leftJoin(summary).on(summary.roomId.eq(room.id))
            .where(
                room.member1.id.eq(memberId)
                    .or(room.member2.id.eq(memberId))
            )
            .orderBy(summary.lastMessageCreatedAt.desc().nullsLast(), room.id.desc())
            .limit(limit.toLong())
            .fetch()
    }
}
