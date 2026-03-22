package com.example.domain.post

import com.example.domain.board.Board
import com.example.domain.common.AuditableEntity
import com.example.domain.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "post")
class Post(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    val board: Board,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Column(nullable = false)
    val title: String,

    @Lob
    @Column(nullable = false)
    val content: String,

    @Column(name = "is_anonymous", nullable = false)
    val isAnonymous: Boolean = false,

    @Column(name = "like_count", nullable = false)
    val likeCount: Int = 0,

    @Column(name = "comment_count", nullable = false)
    val commentCount: Int = 0,

    @Column(name = "is_deleted", nullable = false)
    val isDeleted: Boolean = false,
) : AuditableEntity()

