package com.example.domain.post

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
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "post_anon_mapping",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_post_anon_mapping_post_member",
            columnNames = ["post_id", "member_id"]
        ),
        UniqueConstraint(
            name = "uk_post_anon_mapping_post_anon_number",
            columnNames = ["post_id", "anon_number"]
        )
    ],
    indexes = [
        Index(name = "idx_post_anon_mapping_post_member", columnList = "post_id, member_id")
    ]
)
class PostAnonymousMapping(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: Post,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Column(name = "anon_number", nullable = false)
    val anonNumber: Int
)
