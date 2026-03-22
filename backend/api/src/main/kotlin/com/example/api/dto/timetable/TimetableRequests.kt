package com.example.api.dto.timetable

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

object TimetableRequests {
    data class AddLectureRequest(
        @field:NotNull(message = "학기를 선택해주세요")
        @field:Positive(message = "semesterId는 양수여야 합니다")
        val semesterId: Long?,
        @field:NotNull(message = "강의를 선택해주세요")
        @field:Positive(message = "lectureId는 양수여야 합니다")
        val lectureId: Long?
    )
}
