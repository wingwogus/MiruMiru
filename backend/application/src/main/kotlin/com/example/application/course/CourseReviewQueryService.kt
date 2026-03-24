package com.example.application.course

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.course.Course
import com.example.domain.course.CourseRepository
import com.example.domain.course.CourseReview
import com.example.domain.course.CourseReviewRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.round

@Service
@Transactional(readOnly = true)
class CourseReviewQueryService(
    private val memberRepository: MemberRepository,
    private val courseRepository: CourseRepository,
    private val courseReviewRepository: CourseReviewRepository
) {
    fun getCourseReviews(userId: String, courseId: Long, page: Int, size: Int): CourseReviewQueryResult.CourseReviewPage {
        validatePage(page)
        validateSize(size)

        val member = findMember(userId)
        val course = findCourse(member, courseId)
        val pageable = PageRequest.of(
            page,
            size.coerceAtMost(MAX_PAGE_SIZE),
            Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
            )
        )
        val reviewPage = courseReviewRepository.findAllByCourseId(course.id, pageable)
        val summary = courseReviewRepository.summarizeByCourseId(course.id)
        val totalElements = reviewPage.totalElements
        val totalPages = if (totalElements == 0L) 0 else ((totalElements + pageable.pageSize - 1) / pageable.pageSize).toInt()

        return CourseReviewQueryResult.CourseReviewPage(
            summary = CourseReviewQueryResult.CourseReviewSummary(
                courseId = course.id,
                code = course.code,
                name = course.name,
                reviewCount = summary.reviewCount,
                averageOverall = roundNullable(summary.averageOverall),
                averageDifficulty = roundNullable(summary.averageDifficulty),
                averageWorkload = roundNullable(summary.averageWorkload),
                wouldTakeAgainRate = roundNullable(summary.wouldTakeAgainRate)
            ),
            reviews = reviewPage.content.map { review -> review.toItem(member.id) },
            page = reviewPage.number,
            size = reviewPage.size,
            totalElements = totalElements,
            totalPages = totalPages,
            hasNext = reviewPage.hasNext()
        )
    }

    fun getMyCourseReview(userId: String, courseId: Long): CourseReviewQueryResult.CourseReviewItem {
        val member = findMember(userId)
        val course = findCourse(member, courseId)
        val review = courseReviewRepository.findByCourseIdAndMemberId(course.id, member.id)
            ?: throw BusinessException(ErrorCode.COURSE_REVIEW_NOT_FOUND)

        return review.toItem(member.id)
    }

    private fun CourseReview.toItem(memberId: Long): CourseReviewQueryResult.CourseReviewItem {
        return CourseReviewQueryResult.CourseReviewItem(
            reviewId = id,
            overallRating = overallRating,
            difficulty = difficulty,
            workload = workload,
            wouldTakeAgain = wouldTakeAgain,
            content = content,
            academicYear = academicYear,
            term = term.name,
            professor = professor,
            isMine = member.id == memberId,
            createdAt = createdAt?.toString().orEmpty(),
            updatedAt = updatedAt?.toString().orEmpty()
        )
    }

    private fun roundNullable(value: Double?): Double? {
        return value?.let { round(it * 10.0) / 10.0 }
    }

    private fun validatePage(page: Int) {
        if (page < 0) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun validateSize(size: Int) {
        if (size <= 0) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
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
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50
    }
}
