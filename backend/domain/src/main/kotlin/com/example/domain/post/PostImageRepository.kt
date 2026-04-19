package com.example.domain.post

import org.springframework.data.jpa.repository.JpaRepository

interface PostImageRepository : JpaRepository<PostImage, Long> {
    fun findAllByPostIdOrderByDisplayOrderAsc(postId: Long): List<PostImage>
    fun findByPostIdAndDisplayOrder(postId: Long, displayOrder: Int): PostImage?
    fun findAllByPostIdIn(postIds: List<Long>): List<PostImage>
}
