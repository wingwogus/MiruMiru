package com.example.domain.post

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface PostRepository : JpaRepository<Post, Long> {
    @EntityGraph(attributePaths = ["board", "member"])
    fun findAllByBoardIdAndBoardUniversityIdAndIsDeletedFalseOrderByCreatedAtDesc(boardId: Long, universityId: Long): List<Post>

    @EntityGraph(attributePaths = ["board", "member"])
    fun findAllByBoardUniversityIdAndIsDeletedFalseAndLikeCountGreaterThanEqualAndCreatedAtGreaterThanEqual(
        universityId: Long,
        likeCount: Int,
        createdAt: LocalDateTime,
        pageable: Pageable
    ): List<Post>

    @EntityGraph(attributePaths = ["board", "member"])
    fun findByIdAndBoardUniversityIdAndIsDeletedFalse(id: Long, universityId: Long): Post?

    @EntityGraph(attributePaths = ["board", "member"])
    fun findByIdAndBoardUniversityId(id: Long, universityId: Long): Post?

    fun findByBoardIdAndTitle(boardId: Long, title: String): Post?
    fun findAllByMemberId(memberId: Long): List<Post>
}
