package com.example.domain.semester

import org.springframework.data.jpa.repository.JpaRepository

interface SemesterRepository : JpaRepository<Semester, Long> {
    fun findAllByUniversityId(universityId: Long): List<Semester>
    fun findByIdAndUniversityId(id: Long, universityId: Long): Semester?
    fun findByUniversityIdAndAcademicYearAndTerm(
        universityId: Long,
        academicYear: Int,
        term: SemesterTerm
    ): Semester?
}
