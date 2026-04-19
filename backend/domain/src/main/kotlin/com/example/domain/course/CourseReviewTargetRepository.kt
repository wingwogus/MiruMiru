package com.example.domain.course

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CourseReviewTargetRepository : JpaRepository<CourseReviewTarget, Long> {
    @EntityGraph(attributePaths = ["course"])
    fun findByIdAndCourseUniversityId(id: Long, universityId: Long): CourseReviewTarget?

    @EntityGraph(attributePaths = ["course"])
    fun findByCourseIdAndProfessorKey(courseId: Long, professorKey: String): CourseReviewTarget?

    @Query(
        """
        select new com.example.domain.course.CourseReviewTargetSearchProjection(
            target.id,
            target.course.id,
            target.course.code,
            target.course.name,
            target.professorDisplayName
        )
        from CourseReviewTarget target
        where target.course.university.id = :universityId
          and (
            :query = ''
            or lower(target.course.code) like lower(concat('%', :query, '%'))
            or lower(target.course.name) like lower(concat('%', :query, '%'))
            or lower(target.professorDisplayName) like lower(concat('%', :query, '%'))
          )
        order by target.course.code asc, target.professorDisplayName asc, target.id asc
        """
    )
    fun searchByUniversityId(universityId: Long, query: String, pageable: Pageable): Page<CourseReviewTargetSearchProjection>
}
