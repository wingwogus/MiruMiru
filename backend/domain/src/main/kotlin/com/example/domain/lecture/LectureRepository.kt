package com.example.domain.lecture

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface LectureRepository : JpaRepository<Lecture, Long> {
    @EntityGraph(attributePaths = ["major", "course"])
    fun findAllBySemesterIdOrderByCodeAsc(semesterId: Long): List<Lecture>

    @EntityGraph(attributePaths = ["major", "course"])
    fun findBySemesterIdAndCode(semesterId: Long, code: String): Lecture?

    fun findByIdAndCourseIdAndSemesterUniversityId(id: Long, courseId: Long, universityId: Long): Lecture?
}
