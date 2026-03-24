package com.example.application.course

import com.example.domain.course.Course
import com.example.domain.course.CourseReviewTarget
import com.example.domain.course.CourseReviewTargetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CourseReviewTargetService(
    private val courseReviewTargetRepository: CourseReviewTargetRepository
) {
    fun ensureTarget(course: Course, professorDisplayName: String): CourseReviewTarget {
        val normalizedProfessorName = professorDisplayName.trim()
        val professorKey = normalizeProfessorName(normalizedProfessorName)

        courseReviewTargetRepository.findByCourseIdAndProfessorKey(course.id, professorKey)?.let { return it }

        return courseReviewTargetRepository.save(
            CourseReviewTarget(
                course = course,
                professorDisplayName = normalizedProfessorName,
                professorKey = professorKey
            )
        )
    }

    fun normalizeProfessorName(professorDisplayName: String): String {
        return professorDisplayName.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }

    fun buildDisplayName(courseName: String, professorDisplayName: String): String {
        return "$courseName: $professorDisplayName"
    }
}
