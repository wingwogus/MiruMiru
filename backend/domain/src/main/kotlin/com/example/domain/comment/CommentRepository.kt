package com.example.domain.comment

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface CommentRepository : JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = ["member", "parent", "post"])
    fun findAllByPostIdOrderByCreatedAtAsc(postId: Long): List<Comment>

    @EntityGraph(attributePaths = ["member", "parent", "post"])
    fun findByIdAndPostBoardUniversityId(commentId: Long, universityId: Long): Comment?

    fun findAllByPostIdIn(postIds: List<Long>): List<Comment>
    fun existsByPostIdAndMemberId(postId: Long, memberId: Long): Boolean
}
