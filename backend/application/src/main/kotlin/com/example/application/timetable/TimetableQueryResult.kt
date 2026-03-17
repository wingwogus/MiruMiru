package com.example.application.timetable

object TimetableQueryResult {
    data class MajorItem(
        val majorId: Long,
        val code: String,
        val name: String
    )

    data class SemesterSummary(
        val id: Long,
        val academicYear: Int,
        val term: String
    )

    data class LectureScheduleItem(
        val dayOfWeek: String,
        val startTime: String,
        val endTime: String,
        val location: String
    )

    data class LectureItem(
        val id: Long,
        val code: String,
        val name: String,
        val professor: String,
        val credit: Int,
        val major: MajorItem?,
        val schedules: List<LectureScheduleItem>
    )

    data class TimetableDetail(
        val timetableId: Long,
        val semester: SemesterSummary,
        val lectures: List<LectureItem>
    )
}
