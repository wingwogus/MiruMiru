package com.example.api.dto.timetable

import com.example.application.timetable.TimetableQueryResult

object TimetableResponses {
    data class MajorItem(
        val majorId: Long,
        val code: String,
        val name: String
    ) {
        companion object {
            fun from(result: TimetableQueryResult.MajorItem): MajorItem {
                return MajorItem(
                    majorId = result.majorId,
                    code = result.code,
                    name = result.name
                )
            }
        }
    }

    data class SemesterItem(
        val id: Long,
        val academicYear: Int,
        val term: String
    ) {
        companion object {
            fun from(result: TimetableQueryResult.SemesterSummary): SemesterItem {
                return SemesterItem(
                    id = result.id,
                    academicYear = result.academicYear,
                    term = result.term
                )
            }
        }
    }

    data class LectureScheduleItem(
        val dayOfWeek: String,
        val startTime: String,
        val endTime: String,
        val location: String
    ) {
        companion object {
            fun from(result: TimetableQueryResult.LectureScheduleItem): LectureScheduleItem {
                return LectureScheduleItem(
                    dayOfWeek = result.dayOfWeek,
                    startTime = result.startTime,
                    endTime = result.endTime,
                    location = result.location
                )
            }
        }
    }

    data class LectureItem(
        val id: Long,
        val courseId: Long,
        val code: String,
        val name: String,
        val professor: String,
        val credit: Int,
        val major: MajorItem?,
        val schedules: List<LectureScheduleItem>
    ) {
        companion object {
            fun from(result: TimetableQueryResult.LectureItem): LectureItem {
                return LectureItem(
                    id = result.id,
                    courseId = result.courseId,
                    code = result.code,
                    name = result.name,
                    professor = result.professor,
                    credit = result.credit,
                    major = result.major?.let(MajorItem::from),
                    schedules = result.schedules.map(LectureScheduleItem::from)
                )
            }
        }
    }

    data class TimetableDetail(
        val timetableId: Long?,
        val semester: SemesterItem,
        val lectures: List<LectureItem>
    ) {
        companion object {
            fun from(result: TimetableQueryResult.TimetableDetail): TimetableDetail {
                return TimetableDetail(
                    timetableId = result.timetableId,
                    semester = SemesterItem.from(result.semester),
                    lectures = result.lectures.map(LectureItem::from)
                )
            }
        }
    }
}
