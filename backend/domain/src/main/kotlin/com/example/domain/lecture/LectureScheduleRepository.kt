package com.example.domain.lecture

import org.springframework.data.jpa.repository.JpaRepository
import java.time.DayOfWeek
import java.time.LocalTime

interface LectureScheduleRepository : JpaRepository<LectureSchedule, Long> {
    fun findAllByLectureIdIn(lectureIds: Collection<Long>): List<LectureSchedule>
    fun findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(
        lectureId: Long,
        dayOfWeek: DayOfWeek,
        startTime: LocalTime,
        endTime: LocalTime
    ): LectureSchedule?
}
