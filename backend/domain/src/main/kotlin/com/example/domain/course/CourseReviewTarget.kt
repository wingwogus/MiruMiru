package com.example.domain.course

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
    name = "course_review_target",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_course_review_target_course_professor_key",
            columnNames = ["course_id", "professor_key"]
        )
    ],
    indexes = [
        Index(name = "idx_course_review_target_course_professor", columnList = "course_id, professor_key")
    ]
)
class CourseReviewTarget(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    val course: Course,

    @Column(name = "professor_display_name", nullable = false)
    val professorDisplayName: String,

    @Column(name = "professor_key", nullable = false, length = 255)
    val professorKey: String
)
