package com.example.domain.timetable

import org.springframework.data.jpa.repository.JpaRepository

interface TimetableRepository : JpaRepository<Timetable, Long> {
    fun findByMemberIdAndSemesterId(memberId: Long, semesterId: Long): Timetable?
}
