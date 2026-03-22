package com.example.domain.comment

import com.example.domain.common.BaseTimeEntity
import com.example.domain.member.Member
import com.example.domain.post.Post
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
    name = "comment",
    indexes = [
        Index(name = "idx_comment_post_created", columnList = "post_id, created_at"),
        Index(name = "idx_comment_post_parent_created", columnList = "post_id, parent_id, created_at")
    ]
)
class Comment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: Post,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    val parent: Comment? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(name = "is_anonymous", nullable = false)
    val isAnonymous: Boolean,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false
) : BaseTimeEntity() {
    fun delete() {
        isDeleted = true
    }
}
