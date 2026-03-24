package com.example.application.course

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.course.Course
import com.example.domain.course.CourseRepository
import com.example.domain.course.CourseReview
import com.example.domain.course.CourseReviewRepository
import com.example.domain.lecture.Lecture
import com.example.domain.lecture.LectureRepository
import com.example.domain.major.Major
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.semester.Semester
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
import java.util.Optional

class CourseReviewWriteServiceTest {
    private lateinit var memberRepository: MemberRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var courseReviewRepository: CourseReviewRepository
    private lateinit var lectureRepository: LectureRepository
    private lateinit var courseReviewWriteService: CourseReviewWriteService

    @BeforeEach
    fun setUp() {
        memberRepository = mock(MemberRepository::class.java)
        courseRepository = mock(CourseRepository::class.java)
        courseReviewRepository = mock(CourseReviewRepository::class.java)
        lectureRepository = mock(LectureRepository::class.java)
        courseReviewWriteService = CourseReviewWriteService(
            memberRepository = memberRepository,
            courseRepository = courseRepository,
            courseReviewRepository = courseReviewRepository,
            lectureRepository = lectureRepository
        )
    }

    @Test
    fun `create review saves lecture snapshot and trimmed content`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val course = course(id = 10L, university = university)
        val lecture = lecture(id = 20L, course = course, university = university, year = 2026, term = SemesterTerm.SPRING, professor = "Prof. Akiyama")

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseRepository.findByIdAndUniversityId(course.id, university.id)).thenReturn(course)
        `when`(courseReviewRepository.findByCourseIdAndMemberId(course.id, member.id)).thenReturn(null)
        `when`(lectureRepository.findByIdAndCourseIdAndSemesterUniversityId(lecture.id, course.id, university.id)).thenReturn(lecture)
        `when`(courseReviewRepository.save(any(CourseReview::class.java))).thenAnswer { invocation -> invocation.arguments.first() }

        val reviewId = courseReviewWriteService.createCourseReview(
            CourseReviewCommand.CreateCourseReview(
                userId = member.id.toString(),
                courseId = course.id,
                lectureId = lecture.id,
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
        val course = course(id = 10L, university = university)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseRepository.findByIdAndUniversityId(course.id, university.id)).thenReturn(course)
        `when`(courseReviewRepository.findByCourseIdAndMemberId(course.id, member.id))
            .thenReturn(
                CourseReview(
                    id = 99L,
                    course = course,
                    member = member,
                    lecture = lecture(id = 21L, course = course, university = university, year = 2026, term = SemesterTerm.SPRING, professor = "Prof. Akiyama"),
                    academicYear = 2026,
                    term = SemesterTerm.SPRING,
                    professor = "Prof. Akiyama",
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
                    courseId = course.id,
                    lectureId = 20L,
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
    fun `update review refreshes lecture snapshot`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val course = course(id = 10L, university = university)
        val oldLecture = lecture(id = 20L, course = course, university = university, year = 2025, term = SemesterTerm.FALL, professor = "Prof. Ito")
        val newLecture = lecture(id = 21L, course = course, university = university, year = 2026, term = SemesterTerm.SPRING, professor = "Prof. Akiyama")
        val review = CourseReview(
            id = 30L,
            course = course,
            member = member,
            lecture = oldLecture,
            academicYear = 2025,
            term = SemesterTerm.FALL,
            professor = "Prof. Ito",
            overallRating = 4,
            difficulty = 3,
            workload = 2,
            wouldTakeAgain = true,
            content = "old"
        )

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseRepository.findByIdAndUniversityId(course.id, university.id)).thenReturn(course)
        `when`(courseReviewRepository.findByCourseIdAndMemberId(course.id, member.id)).thenReturn(review)
        `when`(lectureRepository.findByIdAndCourseIdAndSemesterUniversityId(newLecture.id, course.id, university.id)).thenReturn(newLecture)

        val reviewId = courseReviewWriteService.updateCourseReview(
            CourseReviewCommand.UpdateCourseReview(
                userId = member.id.toString(),
                courseId = course.id,
                lectureId = newLecture.id,
                overallRating = 5,
                difficulty = 4,
                workload = 4,
                wouldTakeAgain = false,
                content = "  updated content  "
            )
        )

        assertEquals(review.id, reviewId)
        assertEquals(2026, review.academicYear)
        assertEquals(SemesterTerm.SPRING, review.term)
        assertEquals("Prof. Akiyama", review.professor)
        assertEquals("updated content", review.content)
        assertEquals(false, review.wouldTakeAgain)
    }

    @Test
    fun `delete review removes existing review`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val course = course(id = 10L, university = university)
        val lecture = lecture(id = 20L, course = course, university = university, year = 2026, term = SemesterTerm.SPRING, professor = "Prof. Akiyama")
        val review = CourseReview(
            id = 30L,
            course = course,
            member = member,
            lecture = lecture,
            academicYear = 2026,
            term = SemesterTerm.SPRING,
            professor = "Prof. Akiyama",
            overallRating = 4,
            difficulty = 3,
            workload = 3,
            wouldTakeAgain = true,
            content = "delete me"
        )

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseRepository.findByIdAndUniversityId(course.id, university.id)).thenReturn(course)
        `when`(courseReviewRepository.findByCourseIdAndMemberId(course.id, member.id)).thenReturn(review)

        courseReviewWriteService.deleteCourseReview(
            CourseReviewCommand.DeleteCourseReview(
                userId = member.id.toString(),
                courseId = course.id
            )
        )

        verify(courseReviewRepository).delete(review)
    }

    @Test
    fun `create review fails when lecture does not belong to course`() {
        val university = university()
        val member = member(id = 2L, university = university)
        val course = course(id = 10L, university = university)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(courseRepository.findByIdAndUniversityId(course.id, university.id)).thenReturn(course)
        `when`(courseReviewRepository.findByCourseIdAndMemberId(course.id, member.id)).thenReturn(null)
        `when`(lectureRepository.findByIdAndCourseIdAndSemesterUniversityId(20L, course.id, university.id)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            courseReviewWriteService.createCourseReview(
                CourseReviewCommand.CreateCourseReview(
                    userId = member.id.toString(),
                    courseId = course.id,
                    lectureId = 20L,
                    overallRating = 4,
                    difficulty = 3,
                    workload = 2,
                    wouldTakeAgain = true,
                    content = "hello"
                )
            )
        }

        assertEquals(ErrorCode.LECTURE_NOT_IN_COURSE, exception.errorCode)
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

    private fun lecture(
        id: Long,
        course: Course,
        university: University,
        year: Int,
        term: SemesterTerm,
        professor: String
    ): Lecture {
        return Lecture(
            id = id,
            semester = Semester(id = 20L + id, university = university, academicYear = year, term = term),
            major = Major(id = 11L, university = university, code = "CS", name = "Computer Science"),
            course = course,
            code = course.code,
            name = course.name,
            professor = professor,
            credit = 3
        )
    }
}
