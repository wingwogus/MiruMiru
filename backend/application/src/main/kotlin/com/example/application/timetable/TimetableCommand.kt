package com.example.application.timetable

object TimetableCommand {
    data class AddLectureToMyTimetable(
        val userId: String,
        val semesterId: Long,
        val lectureId: Long
    )

    data class RemoveLectureFromMyTimetable(
        val userId: String,
        val semesterId: Long,
        val lectureId: Long
    )
}
