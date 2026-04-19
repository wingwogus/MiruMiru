package com.example.api.controller

import com.example.api.common.ApiResponse
import com.example.api.dto.timetable.TimetableRequests
import com.example.api.dto.timetable.TimetableResponses
import com.example.application.timetable.TimetableCommand
import com.example.application.timetable.TimetableQueryService
import com.example.application.timetable.TimetableWriteService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/timetables")
class TimetableController(
    private val timetableQueryService: TimetableQueryService,
    private val timetableWriteService: TimetableWriteService
) {

    @GetMapping("/me")
    fun getMyTimetable(
        @AuthenticationPrincipal userId: String,
        @RequestParam semesterId: Long
    ): ResponseEntity<ApiResponse<TimetableResponses.TimetableDetail>> {
        val response = timetableQueryService.getMyTimetable(userId, semesterId)
        return ResponseEntity.ok(ApiResponse.ok(TimetableResponses.TimetableDetail.from(response)))
    }

    @PostMapping("/me/lectures")
    fun addLectureToMyTimetable(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: TimetableRequests.AddLectureRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        timetableWriteService.addLecture(
            TimetableCommand.AddLectureToMyTimetable(
                userId = userId,
                semesterId = request.semesterId!!,
                lectureId = request.lectureId!!
            )
        )
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }

    @DeleteMapping("/me/lectures/{lectureId}")
    fun removeLectureFromMyTimetable(
        @AuthenticationPrincipal userId: String,
        @PathVariable lectureId: Long,
        @RequestParam semesterId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        timetableWriteService.removeLecture(
            TimetableCommand.RemoveLectureFromMyTimetable(
                userId = userId,
                semesterId = semesterId,
                lectureId = lectureId
            )
        )
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }
}
