package com.example.api.controller

import com.example.api.common.ApiResponse
import com.example.api.dto.timetable.TimetableResponses
import com.example.application.timetable.TimetableQueryService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/semesters")
class SemesterController(
    private val timetableQueryService: TimetableQueryService
) {

    @GetMapping
    fun getSemesters(@AuthenticationPrincipal userId: String): ResponseEntity<ApiResponse<List<TimetableResponses.SemesterItem>>> {
        val response = timetableQueryService.getSemesters(userId)
            .map(TimetableResponses.SemesterItem::from)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @GetMapping("/{semesterId}/lectures")
    fun getLectures(
        @AuthenticationPrincipal userId: String,
        @PathVariable semesterId: Long
    ): ResponseEntity<ApiResponse<List<TimetableResponses.LectureItem>>> {
        val response = timetableQueryService.getLectures(userId, semesterId)
            .map(TimetableResponses.LectureItem::from)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }
}
