package com.example.domain.post

import com.example.domain.board.Board
import com.example.domain.common.BaseTimeEntity
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
    name = "post",
    indexes = [
        Index(name = "idx_post_board_deleted_created", columnList = "board_id, is_deleted, created_at"),
        Index(name = "idx_post_member_created", columnList = "member_id, created_at")
    ]
)
class Post(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    val board: Board,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Column(nullable = false, length = 255)
    val title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "is_anonymous", nullable = false)
    val isAnonymous: Boolean,

    @Column(name = "like_count", nullable = false)
    var likeCount: Int = 0,

    @Column(name = "comment_count", nullable = false)
    var commentCount: Int = 0,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false
) : BaseTimeEntity() {
    fun delete() {
        isDeleted = true
    }

    fun increaseLikeCount() {
        likeCount += 1
    }

    fun decreaseLikeCount() {
        if (likeCount > 0) {
            likeCount -= 1
        }
    }

    fun increaseCommentCount() {
        commentCount += 1
    }

    fun decreaseCommentCount() {
        if (commentCount > 0) {
            commentCount -= 1
        }
    }
}
