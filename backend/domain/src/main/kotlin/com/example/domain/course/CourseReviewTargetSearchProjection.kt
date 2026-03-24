package com.example.domain.course

data class CourseReviewTargetSearchProjection(
    val targetId: Long,
    val courseId: Long,
    val courseCode: String,
    val courseName: String,
    val professorDisplayName: String
)
