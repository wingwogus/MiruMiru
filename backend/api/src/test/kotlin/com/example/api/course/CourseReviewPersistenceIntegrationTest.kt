package com.example.api.course

import com.example.ApiApplication
import com.example.domain.course.CourseReviewRepository
import com.example.domain.lecture.LectureRepository
import com.example.domain.semester.SemesterRepository
import com.example.domain.semester.SemesterTerm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(
    classes = [ApiApplication::class],
    properties = [
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password"
    ]
)
@ActiveProfiles("local")
@Transactional
class CourseReviewPersistenceIntegrationTest(
    @Autowired private val semesterRepository: SemesterRepository,
    @Autowired private val lectureRepository: LectureRepository,
    @Autowired private val courseReviewRepository: CourseReviewRepository
) {
    @Test
    fun `local bootstrap seeds course scoped reviews across semesters`() {
        val springSemester = semesterRepository.findAll()
            .firstOrNull { it.academicYear == 2026 && it.term == SemesterTerm.SPRING }
        val fallSemester = semesterRepository.findAll()
            .firstOrNull { it.academicYear == 2025 && it.term == SemesterTerm.FALL }

        assertNotNull(springSemester)
        assertNotNull(fallSemester)

        val springLecture = lectureRepository.findBySemesterIdAndCode(springSemester!!.id, "CS101")
        val fallLecture = lectureRepository.findBySemesterIdAndCode(fallSemester!!.id, "CS101")

        assertNotNull(springLecture)
        assertNotNull(fallLecture)
        assertEquals(springLecture!!.course.id, fallLecture!!.course.id)

        val reviews = courseReviewRepository.findAllByCourseId(
            springLecture.course.id,
            PageRequest.of(
                0,
                10,
                Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
                )
            )
        )

        assertEquals(2, reviews.totalElements)
        assertEquals(setOf(2025, 2026), reviews.content.map { it.academicYear }.toSet())
        assertEquals(setOf("Prof. Ito", "Prof. Akiyama"), reviews.content.map { it.professor }.toSet())
    }
}
