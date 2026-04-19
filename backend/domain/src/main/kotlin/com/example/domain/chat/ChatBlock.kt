package com.example.domain.chat

import com.example.domain.common.BaseTimeEntity
import com.example.domain.member.Member
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "chat_block",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_chat_block_member_pair_by_owner",
            columnNames = ["member_1_id", "member_2_id", "blocked_by_id"]
        )
    ],
    indexes = [
        Index(name = "idx_chat_block_member1", columnList = "member_1_id"),
        Index(name = "idx_chat_block_member2", columnList = "member_2_id"),
    ]
)
class ChatBlock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_1_id", nullable = false)
    val member1: Member,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_2_id", nullable = false)
    val member2: Member,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by_id", nullable = false)
    val blockedBy: Member,
) : BaseTimeEntity()
