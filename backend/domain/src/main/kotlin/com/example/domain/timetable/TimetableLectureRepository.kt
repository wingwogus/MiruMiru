package com.example.domain.timetable

import org.springframework.data.jpa.repository.JpaRepository

interface TimetableLectureRepository : JpaRepository<TimetableLecture, Long> {
    fun existsByTimetableIdAndLectureId(timetableId: Long, lectureId: Long): Boolean
    fun findAllByTimetableId(timetableId: Long): List<TimetableLecture>
}
