package com.example.api.controller

import com.example.api.common.ApiResponse
import com.example.api.dto.timetable.TimetableResponses
import com.example.application.timetable.TimetableQueryService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/timetables")
class TimetableController(
    private val timetableQueryService: TimetableQueryService
) {

    @GetMapping("/me")
    fun getMyTimetable(
        @AuthenticationPrincipal userId: String,
        @RequestParam semesterId: Long
    ): ResponseEntity<ApiResponse<TimetableResponses.TimetableDetail>> {
        val response = timetableQueryService.getMyTimetable(userId, semesterId)
        return ResponseEntity.ok(ApiResponse.ok(TimetableResponses.TimetableDetail.from(response)))
    }
}
