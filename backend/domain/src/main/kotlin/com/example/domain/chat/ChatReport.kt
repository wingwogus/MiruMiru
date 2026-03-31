package com.example.domain.chat

import com.example.domain.common.CreatedTimeEntity
import com.example.domain.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "chat_report",
    indexes = [
        Index(name = "idx_chat_report_reporter_created", columnList = "reporter_id, created_at"),
        Index(name = "idx_chat_report_target_created", columnList = "target_id, created_at"),
    ]
)
class ChatReport(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    val reporter: Member,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    val target: Member,

    @Column(name = "room_id")
    val roomId: Long? = null,

    @Column(name = "message_id")
    val messageId: Long? = null,

    @Column(nullable = false, length = 100)
    val reason: String,

    @Column(columnDefinition = "TEXT")
    val detail: String? = null,
) : CreatedTimeEntity()

