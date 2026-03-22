package com.example.domain.chat

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class ChatMessageQueryRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : ChatMessageQueryRepository {

    override fun findLatest(roomId: Long, limit: Int): List<ChatMessageSummary> {
        val message = QChatMessage.chatMessage

        return queryFactory
            .select(Projections.constructor(
                ChatMessageSummary::class.java,
                message.id,
                message.room.id,
                message.sender.id,
                message.content,
                message.createdAt,
            ))
            .from(message)
            .where(message.room.id.eq(roomId))
            .orderBy(message.id.desc())
            .limit(limit.toLong())
            .fetch()
    }

    override fun findBefore(roomId: Long, beforeMessageId: Long, limit: Int): List<ChatMessageSummary> {
        val message = QChatMessage.chatMessage

        return queryFactory
            .select(Projections.constructor(
                ChatMessageSummary::class.java,
                message.id,
                message.room.id,
                message.sender.id,
                message.content,
                message.createdAt,
            ))
            .from(message)
            .where(
                message.room.id.eq(roomId),
                message.id.lt(beforeMessageId),
            )
            .orderBy(message.id.desc())
            .limit(limit.toLong())
            .fetch()
    }

    override fun countUnread(roomId: Long, memberId: Long, lastReadMessageId: Long?): Long {
        val message = QChatMessage.chatMessage
        val effectiveLastRead = lastReadMessageId ?: 0L

        return queryFactory
            .select(message.id.count())
            .from(message)
            .where(
                message.room.id.eq(roomId),
                message.id.gt(effectiveLastRead),
                message.sender.id.ne(memberId),
            )
            .fetchOne()
            ?: 0L
    }
}
