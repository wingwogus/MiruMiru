package com.example.domain.timetable

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface TimetableLectureRepository : JpaRepository<TimetableLecture, Long> {
    fun existsByTimetableIdAndLectureId(timetableId: Long, lectureId: Long): Boolean

    @EntityGraph(attributePaths = ["lecture", "lecture.major", "lecture.course"])
    fun findAllByTimetableId(timetableId: Long): List<TimetableLecture>

    fun findByTimetableIdAndLectureId(timetableId: Long, lectureId: Long): TimetableLecture?
}
