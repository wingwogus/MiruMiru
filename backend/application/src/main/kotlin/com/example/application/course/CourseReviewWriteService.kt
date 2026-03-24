package com.example.application.course

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.course.Course
import com.example.domain.course.CourseRepository
import com.example.domain.course.CourseReview
import com.example.domain.course.CourseReviewRepository
import com.example.domain.lecture.Lecture
import com.example.domain.lecture.LectureRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CourseReviewWriteService(
    private val memberRepository: MemberRepository,
    private val courseRepository: CourseRepository,
    private val courseReviewRepository: CourseReviewRepository,
    private val lectureRepository: LectureRepository
) {
    fun createCourseReview(command: CourseReviewCommand.CreateCourseReview): Long {
        val member = findMember(command.userId)
        val course = findCourse(member, command.courseId)
        validateRatings(command.overallRating, command.difficulty, command.workload)
        val content = command.content.trim()
        validateContent(content)

        if (courseReviewRepository.findByCourseIdAndMemberId(course.id, member.id) != null) {
            throw BusinessException(ErrorCode.COURSE_REVIEW_ALREADY_EXISTS)
        }

        val lecture = findLecture(course, member, command.lectureId)

        return try {
            courseReviewRepository.save(
                CourseReview(
                    course = course,
                    member = member,
                    lecture = lecture,
                    academicYear = lecture.semester.academicYear,
                    term = lecture.semester.term,
                    professor = lecture.professor,
                    overallRating = command.overallRating,
                    difficulty = command.difficulty,
                    workload = command.workload,
                    wouldTakeAgain = command.wouldTakeAgain,
                    content = content
                )
            ).id
        } catch (_: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.COURSE_REVIEW_ALREADY_EXISTS)
        }
    }

    fun updateCourseReview(command: CourseReviewCommand.UpdateCourseReview): Long {
        val member = findMember(command.userId)
        val course = findCourse(member, command.courseId)
        validateRatings(command.overallRating, command.difficulty, command.workload)
        val content = command.content.trim()
        validateContent(content)

        val review = courseReviewRepository.findByCourseIdAndMemberId(course.id, member.id)
            ?: throw BusinessException(ErrorCode.COURSE_REVIEW_NOT_FOUND)
        val lecture = findLecture(course, member, command.lectureId)

        review.update(
            lecture = lecture,
            academicYear = lecture.semester.academicYear,
            term = lecture.semester.term,
            professor = lecture.professor,
            overallRating = command.overallRating,
            difficulty = command.difficulty,
            workload = command.workload,
            wouldTakeAgain = command.wouldTakeAgain,
            content = content
        )

        return review.id
    }

    fun deleteCourseReview(command: CourseReviewCommand.DeleteCourseReview) {
        val member = findMember(command.userId)
        val course = findCourse(member, command.courseId)
        val review = courseReviewRepository.findByCourseIdAndMemberId(course.id, member.id)
            ?: throw BusinessException(ErrorCode.COURSE_REVIEW_NOT_FOUND)

        courseReviewRepository.delete(review)
    }

    private fun validateRatings(overallRating: Int, difficulty: Int, workload: Int) {
        if (overallRating !in MIN_RATING..MAX_RATING || difficulty !in MIN_RATING..MAX_RATING || workload !in MIN_RATING..MAX_RATING) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun validateContent(content: String) {
        if (content.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun findLecture(course: Course, member: Member, lectureId: Long): Lecture {
        return lectureRepository.findByIdAndCourseIdAndSemesterUniversityId(lectureId, course.id, member.university.id)
            ?: throw BusinessException(ErrorCode.LECTURE_NOT_IN_COURSE)
    }

    private fun findMember(userId: String): Member {
        val parsedUserId = userId.toLongOrNull()
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        return memberRepository.findById(parsedUserId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }
    }

    private fun findCourse(member: Member, courseId: Long): Course {
        return courseRepository.findByIdAndUniversityId(courseId, member.university.id)
            ?: throw BusinessException(ErrorCode.COURSE_NOT_FOUND)
    }

    companion object {
        private const val MIN_RATING = 1
        private const val MAX_RATING = 5
    }
}
