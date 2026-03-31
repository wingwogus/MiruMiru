package com.example.domain.chat

import com.example.domain.common.BaseTimeEntity
import com.example.domain.member.Member
import com.example.domain.post.Post
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "message_room")
class MessageRoom(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: Post,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id_1", nullable = false)
    val member1: Member,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id_2", nullable = false)
    val member2: Member,

    @Column(name = "is_anon_1", nullable = false)
    val isAnon1: Boolean = false,

    @Column(name = "is_anon_2", nullable = false)
    val isAnon2: Boolean = false,

    @Column(name = "member_1_last_read_id")
    var member1LastReadMessageId: Long? = null,

    @Column(name = "member_2_last_read_id")
    var member2LastReadMessageId: Long? = null,
) : BaseTimeEntity() {

    fun isParticipant(memberId: Long): Boolean = member1.id == memberId || member2.id == memberId

    fun otherMemberId(memberId: Long): Long {
        if (member1.id == memberId) return member2.id
        if (member2.id == memberId) return member1.id
        throw IllegalArgumentException("Not a room participant")
    }

    fun getLastReadMessageId(memberId: Long): Long? {
        if (member1.id == memberId) return member1LastReadMessageId
        if (member2.id == memberId) return member2LastReadMessageId
        throw IllegalArgumentException("Not a room participant")
    }

    fun updateLastReadMessageId(memberId: Long, lastReadMessageId: Long?) {
        if (lastReadMessageId == null) return

        if (member1.id == memberId) {
            member1LastReadMessageId = maxOf(member1LastReadMessageId ?: 0L, lastReadMessageId)
            return
        }

        if (member2.id == memberId) {
            member2LastReadMessageId = maxOf(member2LastReadMessageId ?: 0L, lastReadMessageId)
            return
        }

        throw IllegalArgumentException("Not a room participant")
    }
}
