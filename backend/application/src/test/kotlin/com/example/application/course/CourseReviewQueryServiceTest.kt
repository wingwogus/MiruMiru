package com.example.application.course

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.course.Course
import com.example.domain.course.CourseRepository
import com.example.domain.course.CourseReview
import com.example.domain.course.CourseReviewRepository
import com.example.domain.course.CourseReviewSummaryProjection
import com.example.domain.major.Major
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.semester.Semester
import com.example.domain.semester.SemesterTerm
import com.example.domain.university.University
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import java.util.Optional

class CourseReviewQueryServiceTest {
    private lateinit var memberRepository: MemberRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var courseReviewRepository: CourseReviewRepository
    private lateinit var courseReviewQueryService: CourseReviewQueryService

    @BeforeEach
    fun setUp() {
        memberRepository = mock(MemberRepository::class.java)
        courseRepository = mock(CourseRepository::class.java)
        courseReviewRepository = mock(CourseReviewRepository::class.java)
        courseReviewQueryService = CourseReviewQueryService(
            memberRepository = memberRepository,
            courseRepository = courseRepository,
            courseReviewRepository = courseReviewRepository
        )
    }

    @Test
    fun `get course reviews returns paged reviews and rounded summary`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val course = course(id = 10L, university = university)
        val pageRequest = PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Order.desc("createdAt"),
            org.springframework.data.domain.Sort.Order.desc("id")
        ))
        val firstReview = review(
            id = 20L,
            course = course,
            member = member,
            academicYear = 2026,
            term = SemesterTerm.SPRING,
            professor = "Prof. Akiyama",
            content = "Great class",
            createdAt = LocalDateTime.of(2026, 3, 1, 9, 0),
            updatedAt = LocalDateTime.of(2026, 3, 2, 9, 0)
        )
        val secondReview = review(
            id = 21L,
            course = course,
            member = member(id = 3L, university = university),
            academicYear = 2025,
            term = SemesterTerm.FALL,
            professor = "Prof. Ito",
            content = "Old but helpful review",
            createdAt = LocalDateTime.of(2026, 2, 1, 9, 0),
            updatedAt = LocalDateTime.of(2026, 2, 1, 10, 0)
        )

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseRepository.findByIdAndUniversityId(course.id, university.id)).thenReturn(course)
        `when`(courseReviewRepository.findAllByCourseId(course.id, pageRequest))
            .thenReturn(PageImpl(listOf(firstReview, secondReview), pageRequest, 2))
        `when`(courseReviewRepository.summarizeByCourseId(course.id))
            .thenReturn(
                CourseReviewSummaryProjection(
                    reviewCount = 2,
                    averageOverall = 4.44,
                    averageDifficulty = 3.55,
                    averageWorkload = 2.49,
                    wouldTakeAgainRate = 87.66
                )
            )

        val result = courseReviewQueryService.getCourseReviews(member.id.toString(), course.id, 0, 20)

        assertEquals(course.id, result.summary.courseId)
        assertEquals(2, result.summary.reviewCount)
        assertEquals(4.4, result.summary.averageOverall)
        assertEquals(3.6, result.summary.averageDifficulty)
        assertEquals(2.5, result.summary.averageWorkload)
        assertEquals(87.7, result.summary.wouldTakeAgainRate)
        assertEquals(listOf(20L, 21L), result.reviews.map { it.reviewId })
        assertEquals(true, result.reviews.first().isMine)
        assertEquals(false, result.hasNext)
    }

    @Test
    fun `get course reviews returns null summary values when no reviews exist`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val course = course(id = 10L, university = university)
        val pageRequest = PageRequest.of(0, 1, org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Order.desc("createdAt"),
            org.springframework.data.domain.Sort.Order.desc("id")
        ))

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseRepository.findByIdAndUniversityId(course.id, university.id)).thenReturn(course)
        `when`(courseReviewRepository.findAllByCourseId(course.id, pageRequest))
            .thenReturn(PageImpl(emptyList(), pageRequest, 0))
        `when`(courseReviewRepository.summarizeByCourseId(course.id))
            .thenReturn(CourseReviewSummaryProjection(0, null, null, null, null))

        val result = courseReviewQueryService.getCourseReviews(member.id.toString(), course.id, 0, 1)

        assertEquals(0, result.summary.reviewCount)
        assertNull(result.summary.averageOverall)
        assertNull(result.summary.averageDifficulty)
        assertNull(result.summary.averageWorkload)
        assertNull(result.summary.wouldTakeAgainRate)
        assertEquals(0, result.totalPages)
        assertEquals(emptyList<Long>(), result.reviews.map { it.reviewId })
    }

    @Test
    fun `get course reviews fails for negative page`() {
        val exception = assertThrows(BusinessException::class.java) {
            courseReviewQueryService.getCourseReviews("1", 1L, -1, 20)
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    private fun university(): University {
        return University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp")
    }

    private fun course(id: Long, university: University): Course {
        return Course(
            id = id,
            university = university,
            code = "CS101",
            name = "Introduction to Computer Science"
        )
    }

    private fun member(id: Long, university: University): Member {
        return Member(
            id = id,
            university = university,
            major = Major(id = 11L, university = university, code = "CS", name = "Computer Science"),
            email = "user$id@tokyo.ac.jp",
            password = "encoded",
            nickname = "user-$id"
        )
    }

    private fun review(
        id: Long,
        course: Course,
        member: Member,
        academicYear: Int,
        term: SemesterTerm,
        professor: String,
        content: String,
        createdAt: LocalDateTime,
        updatedAt: LocalDateTime
    ): CourseReview {
        val lecture = com.example.domain.lecture.Lecture(
            id = 50L + id,
            semester = Semester(id = 70L + id, university = member.university, academicYear = academicYear, term = term),
            major = member.major,
            course = course,
            code = course.code,
            name = course.name,
            professor = professor,
            credit = 3
        )
        return CourseReview(
            id = id,
            course = course,
            member = member,
            lecture = lecture,
            academicYear = academicYear,
            term = term,
            professor = professor,
            overallRating = 4,
            difficulty = 3,
            workload = 2,
            wouldTakeAgain = true,
            content = content
        ).apply {
            this.createdAt = createdAt
            this.updatedAt = updatedAt
        }
    }
}
