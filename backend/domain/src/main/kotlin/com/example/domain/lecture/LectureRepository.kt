package com.example.domain.lecture

import org.springframework.data.jpa.repository.JpaRepository

interface LectureRepository : JpaRepository<Lecture, Long> {
    fun findAllBySemesterIdOrderByCodeAsc(semesterId: Long): List<Lecture>
    fun findBySemesterIdAndCode(semesterId: Long, code: String): Lecture?
}
