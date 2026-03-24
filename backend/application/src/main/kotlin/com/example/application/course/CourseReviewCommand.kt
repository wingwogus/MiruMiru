package com.example.application.course

object CourseReviewCommand {
    data class CreateCourseReview(
        val userId: String,
        val courseId: Long,
        val lectureId: Long,
        val overallRating: Int,
        val difficulty: Int,
        val workload: Int,
        val wouldTakeAgain: Boolean,
        val content: String
    )

    data class UpdateCourseReview(
        val userId: String,
        val courseId: Long,
        val lectureId: Long,
        val overallRating: Int,
        val difficulty: Int,
        val workload: Int,
        val wouldTakeAgain: Boolean,
        val content: String
    )

    data class DeleteCourseReview(
        val userId: String,
        val courseId: Long
    )
}
