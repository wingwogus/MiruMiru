package com.example.domain.post

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
    name = "post_like",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_post_like_post_member",
            columnNames = ["post_id", "member_id"]
        )
    ],
    indexes = [
        Index(name = "idx_post_like_post_member", columnList = "post_id, member_id")
    ]
)
class PostLike(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: Post,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member
) : BaseTimeEntity()
