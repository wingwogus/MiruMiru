package com.example.domain.post

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
    name = "post_image",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_post_image_post_display_order",
            columnNames = ["post_id", "display_order"]
        )
    ],
    indexes = [
        Index(name = "idx_post_image_post_display_order", columnList = "post_id, display_order")
    ]
)
class PostImage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: Post,

    @Column(name = "image_url", nullable = false, length = 1000)
    val imageUrl: String,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int
)
