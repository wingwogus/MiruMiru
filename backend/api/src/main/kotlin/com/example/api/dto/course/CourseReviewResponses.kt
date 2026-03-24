package com.example.api.dto.course

import com.example.application.course.CourseReviewQueryResult

object CourseReviewResponses {
    data class CourseReviewSummary(
        val courseId: Long,
        val code: String,
        val name: String,
        val reviewCount: Long,
        val averageOverall: Double?,
        val averageDifficulty: Double?,
        val averageWorkload: Double?,
        val wouldTakeAgainRate: Double?
    ) {
        companion object {
            fun from(result: CourseReviewQueryResult.CourseReviewSummary): CourseReviewSummary {
                return CourseReviewSummary(
                    courseId = result.courseId,
                    code = result.code,
                    name = result.name,
                    reviewCount = result.reviewCount,
                    averageOverall = result.averageOverall,
                    averageDifficulty = result.averageDifficulty,
                    averageWorkload = result.averageWorkload,
                    wouldTakeAgainRate = result.wouldTakeAgainRate
                )
            }
        }
    }

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
    ) {
        companion object {
            fun from(result: CourseReviewQueryResult.CourseReviewItem): CourseReviewItem {
                return CourseReviewItem(
                    reviewId = result.reviewId,
                    overallRating = result.overallRating,
                    difficulty = result.difficulty,
                    workload = result.workload,
                    wouldTakeAgain = result.wouldTakeAgain,
                    content = result.content,
                    academicYear = result.academicYear,
                    term = result.term,
                    professor = result.professor,
                    isMine = result.isMine,
                    createdAt = result.createdAt,
                    updatedAt = result.updatedAt
                )
            }
        }
    }

    data class CourseReviewPageResponse(
        val summary: CourseReviewSummary,
        val reviews: List<CourseReviewItem>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
        val hasNext: Boolean
    ) {
        companion object {
            fun from(result: CourseReviewQueryResult.CourseReviewPage): CourseReviewPageResponse {
                return CourseReviewPageResponse(
                    summary = CourseReviewSummary.from(result.summary),
                    reviews = result.reviews.map(CourseReviewItem::from),
                    page = result.page,
                    size = result.size,
                    totalElements = result.totalElements,
                    totalPages = result.totalPages,
                    hasNext = result.hasNext
                )
            }
        }
    }

    data class CourseReviewIdResponse(
        val reviewId: Long
    )
}
