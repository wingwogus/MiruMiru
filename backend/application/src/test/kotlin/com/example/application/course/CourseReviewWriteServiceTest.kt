package com.example.application.course

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.course.Course
import com.example.domain.course.CourseReview
import com.example.domain.course.CourseReviewRepository
import com.example.domain.course.CourseReviewTarget
import com.example.domain.course.CourseReviewTargetRepository
import com.example.domain.major.Major
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.semester.SemesterTerm
import com.example.domain.university.University
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.Optional

class CourseReviewWriteServiceTest {
    private lateinit var memberRepository: MemberRepository
    private lateinit var courseReviewRepository: CourseReviewRepository
    private lateinit var courseReviewTargetRepository: CourseReviewTargetRepository
    private lateinit var courseReviewWriteService: CourseReviewWriteService

    @BeforeEach
    fun setUp() {
        memberRepository = mock(MemberRepository::class.java)
        courseReviewRepository = mock(CourseReviewRepository::class.java)
        courseReviewTargetRepository = mock(CourseReviewTargetRepository::class.java)
        courseReviewWriteService = CourseReviewWriteService(
            memberRepository = memberRepository,
            courseReviewRepository = courseReviewRepository,
            courseReviewTargetRepository = courseReviewTargetRepository
        )
    }

    @Test
    fun `create review saves target snapshot and trimmed content`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val target = target(id = 10L, university = university, courseCode = "CS101", courseName = "Introduction to Computer Science", professorDisplayName = "Prof. Akiyama")

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseReviewTargetRepository.findByIdAndCourseUniversityId(target.id, university.id)).thenReturn(target)
        `when`(courseReviewRepository.findByTargetIdAndMemberId(target.id, member.id)).thenReturn(null)
        `when`(courseReviewRepository.save(any(CourseReview::class.java))).thenAnswer { invocation -> invocation.arguments.first() }

        val reviewId = courseReviewWriteService.createCourseReview(
            CourseReviewCommand.CreateCourseReview(
                userId = member.id.toString(),
                targetId = target.id,
                academicYear = LocalDate.now().year,
                term = "SPRING",
                overallRating = 5,
                difficulty = 4,
                workload = 3,
                wouldTakeAgain = true,
                content = "  very helpful lecture  "
            )
        )

        assertEquals(0L, reviewId)
        verify(courseReviewRepository).save(any(CourseReview::class.java))
    }

    @Test
    fun `create review fails when duplicate already exists`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val target = target(id = 10L, university = university, courseCode = "CS101", courseName = "Introduction to Computer Science", professorDisplayName = "Prof. Akiyama")

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseReviewTargetRepository.findByIdAndCourseUniversityId(target.id, university.id)).thenReturn(target)
        `when`(courseReviewRepository.findByTargetIdAndMemberId(target.id, member.id))
            .thenReturn(
                CourseReview(
                    id = 99L,
                    target = target,
                    member = member,
                    academicYear = LocalDate.now().year,
                    term = SemesterTerm.SPRING,
                    professorDisplayName = target.professorDisplayName,
                    overallRating = 4,
                    difficulty = 3,
                    workload = 2,
                    wouldTakeAgain = true,
                    content = "existing"
                )
            )

        val exception = assertThrows(BusinessException::class.java) {
            courseReviewWriteService.createCourseReview(
                CourseReviewCommand.CreateCourseReview(
                    userId = member.id.toString(),
                    targetId = target.id,
                    academicYear = LocalDate.now().year,
                    term = "SPRING",
                    overallRating = 5,
                    difficulty = 4,
                    workload = 3,
                    wouldTakeAgain = true,
                    content = "duplicate"
                )
            )
        }

        assertEquals(ErrorCode.COURSE_REVIEW_ALREADY_EXISTS, exception.errorCode)
    }

    @Test
    fun `update review refreshes snapshot fields`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val target = target(id = 10L, university = university, courseCode = "CS101", courseName = "Introduction to Computer Science", professorDisplayName = "Prof. Ito")
        val review = CourseReview(
            id = 30L,
            target = target,
            member = member,
            academicYear = 2024,
            term = SemesterTerm.FALL,
            professorDisplayName = target.professorDisplayName,
            overallRating = 4,
            difficulty = 3,
            workload = 2,
            wouldTakeAgain = true,
            content = "old"
        )

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseReviewTargetRepository.findByIdAndCourseUniversityId(target.id, university.id)).thenReturn(target)
        `when`(courseReviewRepository.findByTargetIdAndMemberId(target.id, member.id)).thenReturn(review)

        val reviewId = courseReviewWriteService.updateCourseReview(
            CourseReviewCommand.UpdateCourseReview(
                userId = member.id.toString(),
                targetId = target.id,
                academicYear = 2025,
                term = "SPRING",
                overallRating = 5,
                difficulty = 4,
                workload = 4,
                wouldTakeAgain = false,
                content = "  updated content  "
            )
        )

        assertEquals(review.id, reviewId)
        assertEquals(2025, review.academicYear)
        assertEquals(SemesterTerm.SPRING, review.term)
        assertEquals("updated content", review.content)
        assertEquals(false, review.wouldTakeAgain)
    }

    @Test
    fun `delete review removes existing review`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val target = target(id = 10L, university = university, courseCode = "CS101", courseName = "Introduction to Computer Science", professorDisplayName = "Prof. Akiyama")
        val review = CourseReview(
            id = 30L,
            target = target,
            member = member,
            academicYear = LocalDate.now().year,
            term = SemesterTerm.SPRING,
            professorDisplayName = target.professorDisplayName,
            overallRating = 4,
            difficulty = 3,
            workload = 3,
            wouldTakeAgain = true,
            content = "delete me"
        )

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseReviewTargetRepository.findByIdAndCourseUniversityId(target.id, university.id)).thenReturn(target)
        `when`(courseReviewRepository.findByTargetIdAndMemberId(target.id, member.id)).thenReturn(review)

        courseReviewWriteService.deleteCourseReview(
            CourseReviewCommand.DeleteCourseReview(
                userId = member.id.toString(),
                targetId = target.id
            )
        )

        verify(courseReviewRepository).delete(review)
    }

    @Test
    fun `create review fails when target is inaccessible`() {
        val university = university()
        val member = member(id = 2L, university = university)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseReviewTargetRepository.findByIdAndCourseUniversityId(20L, university.id)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            courseReviewWriteService.createCourseReview(
                CourseReviewCommand.CreateCourseReview(
                    userId = member.id.toString(),
                    targetId = 20L,
                    academicYear = LocalDate.now().year,
                    term = "SPRING",
                    overallRating = 4,
                    difficulty = 3,
                    workload = 2,
                    wouldTakeAgain = true,
                    content = "hello"
                )
            )
        }

        assertEquals(ErrorCode.COURSE_REVIEW_TARGET_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `create review fails when academic year is unreasonable`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val target = target(id = 10L, university = university, courseCode = "CS101", courseName = "Introduction to Computer Science", professorDisplayName = "Prof. Akiyama")

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseReviewTargetRepository.findByIdAndCourseUniversityId(target.id, university.id)).thenReturn(target)
        `when`(courseReviewRepository.findByTargetIdAndMemberId(target.id, member.id)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            courseReviewWriteService.createCourseReview(
                CourseReviewCommand.CreateCourseReview(
                    userId = member.id.toString(),
                    targetId = target.id,
                    academicYear = LocalDate.now().year - 40,
                    term = "SPRING",
                    overallRating = 4,
                    difficulty = 3,
                    workload = 2,
                    wouldTakeAgain = true,
                    content = "hello"
                )
            )
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    private fun university(): University {
        return University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp")
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

    private fun target(
        id: Long,
        university: University,
        courseCode: String,
        courseName: String,
        professorDisplayName: String
    ): CourseReviewTarget {
        return CourseReviewTarget(
            id = id,
            course = Course(id = 100L + id, university = university, code = courseCode, name = courseName),
            professorDisplayName = professorDisplayName,
            professorKey = professorDisplayName.lowercase()
        )
    }
}
