package com.example.api.controller

import com.example.api.common.ApiResponse
import com.example.api.dto.course.CourseReviewRequests
import com.example.api.dto.course.CourseReviewResponses
import com.example.application.course.CourseReviewCommand
import com.example.application.course.CourseReviewQueryService
import com.example.application.course.CourseReviewWriteService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/courses")
class CourseReviewController(
    private val courseReviewQueryService: CourseReviewQueryService,
    private val courseReviewWriteService: CourseReviewWriteService
) {
    @GetMapping("/{courseId}/reviews")
    fun getCourseReviews(
        @AuthenticationPrincipal userId: String,
        @PathVariable courseId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<CourseReviewResponses.CourseReviewPageResponse>> {
        val response = courseReviewQueryService.getCourseReviews(userId, courseId, page, size)
        return ResponseEntity.ok(ApiResponse.ok(CourseReviewResponses.CourseReviewPageResponse.from(response)))
    }

    @GetMapping("/{courseId}/reviews/me")
    fun getMyCourseReview(
        @AuthenticationPrincipal userId: String,
        @PathVariable courseId: Long
    ): ResponseEntity<ApiResponse<CourseReviewResponses.CourseReviewItem>> {
        val response = courseReviewQueryService.getMyCourseReview(userId, courseId)
        return ResponseEntity.ok(ApiResponse.ok(CourseReviewResponses.CourseReviewItem.from(response)))
    }

    @PostMapping("/{courseId}/reviews")
    fun createCourseReview(
        @AuthenticationPrincipal userId: String,
        @PathVariable courseId: Long,
        @Valid @RequestBody request: CourseReviewRequests.UpsertCourseReviewRequest
    ): ResponseEntity<ApiResponse<CourseReviewResponses.CourseReviewIdResponse>> {
        val reviewId = courseReviewWriteService.createCourseReview(
            CourseReviewCommand.CreateCourseReview(
                userId = userId,
                courseId = courseId,
                lectureId = request.lectureId!!,
                overallRating = request.overallRating!!,
                difficulty = request.difficulty!!,
                workload = request.workload!!,
                wouldTakeAgain = request.wouldTakeAgain!!,
                content = request.content
            )
        )
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.ok(CourseReviewResponses.CourseReviewIdResponse(reviewId)))
    }

    @PutMapping("/{courseId}/reviews/me")
    fun updateCourseReview(
        @AuthenticationPrincipal userId: String,
        @PathVariable courseId: Long,
        @Valid @RequestBody request: CourseReviewRequests.UpsertCourseReviewRequest
    ): ResponseEntity<ApiResponse<CourseReviewResponses.CourseReviewIdResponse>> {
        val reviewId = courseReviewWriteService.updateCourseReview(
            CourseReviewCommand.UpdateCourseReview(
                userId = userId,
                courseId = courseId,
                lectureId = request.lectureId!!,
                overallRating = request.overallRating!!,
                difficulty = request.difficulty!!,
                workload = request.workload!!,
                wouldTakeAgain = request.wouldTakeAgain!!,
                content = request.content
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(CourseReviewResponses.CourseReviewIdResponse(reviewId)))
    }

    @DeleteMapping("/{courseId}/reviews/me")
    fun deleteCourseReview(
        @AuthenticationPrincipal userId: String,
        @PathVariable courseId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        courseReviewWriteService.deleteCourseReview(
            CourseReviewCommand.DeleteCourseReview(
                userId = userId,
                courseId = courseId
            )
        )
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }
}
