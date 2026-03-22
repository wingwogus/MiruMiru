package com.example.domain.post

import org.springframework.data.jpa.repository.JpaRepository

interface PostAnonymousMappingRepository : JpaRepository<PostAnonymousMapping, Long> {
    fun findByPostIdAndMemberId(postId: Long, memberId: Long): PostAnonymousMapping?
    fun findAllByPostId(postId: Long): List<PostAnonymousMapping>
    fun findTopByPostIdOrderByAnonNumberDesc(postId: Long): PostAnonymousMapping?
    fun findAllByPostIdIn(postIds: List<Long>): List<PostAnonymousMapping>
}
