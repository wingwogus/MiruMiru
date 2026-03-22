package com.example.domain.post

import org.springframework.data.jpa.repository.JpaRepository

interface PostLikeRepository : JpaRepository<PostLike, Long> {
    fun existsByPostIdAndMemberId(postId: Long, memberId: Long): Boolean
    fun findByPostIdAndMemberId(postId: Long, memberId: Long): PostLike?
    fun findAllByPostIdIn(postIds: List<Long>): List<PostLike>
}
