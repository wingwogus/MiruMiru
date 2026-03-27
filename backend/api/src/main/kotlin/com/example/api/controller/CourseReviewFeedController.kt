package com.example.api.controller

import com.example.api.common.ApiResponse
import com.example.api.dto.course.CourseReviewResponses
import com.example.application.course.CourseReviewQueryService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/course-reviews")
class CourseReviewFeedController(
    private val courseReviewQueryService: CourseReviewQueryService
) {
    @GetMapping
    fun getSchoolCourseReviews(
        @AuthenticationPrincipal userId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<CourseReviewResponses.CourseReviewFeedPageResponse>> {
        val response = courseReviewQueryService.getSchoolCourseReviews(userId, page, size)
        return ResponseEntity.ok(ApiResponse.ok(CourseReviewResponses.CourseReviewFeedPageResponse.from(response)))
    }
}
