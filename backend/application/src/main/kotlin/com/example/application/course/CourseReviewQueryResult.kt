package com.example.application.course

object CourseReviewQueryResult {
    data class CourseReviewSummary(
        val courseId: Long,
        val code: String,
        val name: String,
        val reviewCount: Long,
        val averageOverall: Double?,
        val averageDifficulty: Double?,
        val averageWorkload: Double?,
        val wouldTakeAgainRate: Double?
    )

    data class CourseReviewItem(
        val reviewId: Long,
        val overallRating: Int,
        val difficulty: Int,
        val workload: Int,
        val wouldTakeAgain: Boolean,
        val content: String,
        val academicYear: Int,
        val term: String,
        val professor: String,
        val isMine: Boolean,
        val createdAt: String,
        val updatedAt: String
    )

    data class CourseReviewPage(
        val summary: CourseReviewSummary,
        val reviews: List<CourseReviewItem>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
        val hasNext: Boolean
    )
}
