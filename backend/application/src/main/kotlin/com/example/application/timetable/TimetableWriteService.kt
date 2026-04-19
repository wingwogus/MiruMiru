package com.example.application.timetable

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.lecture.Lecture
import com.example.domain.lecture.LectureRepository
import com.example.domain.lecture.LectureSchedule
import com.example.domain.lecture.LectureScheduleRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.semester.Semester
import com.example.domain.semester.SemesterRepository
import com.example.domain.timetable.Timetable
import com.example.domain.timetable.TimetableLecture
import com.example.domain.timetable.TimetableLectureRepository
import com.example.domain.timetable.TimetableRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TimetableWriteService(
    private val memberRepository: MemberRepository,
    private val semesterRepository: SemesterRepository,
    private val lectureRepository: LectureRepository,
    private val lectureScheduleRepository: LectureScheduleRepository,
    private val timetableRepository: TimetableRepository,
    private val timetableLectureRepository: TimetableLectureRepository
) {

    fun addLecture(command: TimetableCommand.AddLectureToMyTimetable) {
        val member = findMember(command.userId)
        val semester = findSemester(member, command.semesterId)
        val lecture = findLecture(command.lectureId)

        validateLectureScope(member, semester, lecture)

        val timetable = findOrCreateTimetable(member, semester)

        if (timetableLectureRepository.existsByTimetableIdAndLectureId(timetable.id, lecture.id)) {
            throw BusinessException(ErrorCode.TIMETABLE_LECTURE_DUPLICATE)
        }

        validateNoScheduleConflict(timetable, lecture)

        timetableLectureRepository.save(
            TimetableLecture(
                timetable = timetable,
                lecture = lecture
            )
        )
    }

    fun removeLecture(command: TimetableCommand.RemoveLectureFromMyTimetable) {
        val member = findMember(command.userId)
        val semester = findSemester(member, command.semesterId)
        val timetable = timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)
            ?: throw BusinessException(ErrorCode.TIMETABLE_NOT_FOUND)
        val timetableLecture = timetableLectureRepository.findByTimetableIdAndLectureId(timetable.id, command.lectureId)
            ?: throw BusinessException(ErrorCode.TIMETABLE_LECTURE_NOT_FOUND)

        timetableLectureRepository.delete(timetableLecture)
    }

    private fun findMember(userId: String): Member {
        val parsedUserId = userId.toLongOrNull()
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        return memberRepository.findById(parsedUserId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }
    }

    private fun findSemester(member: Member, semesterId: Long): Semester {
        return semesterRepository.findByIdAndUniversityId(semesterId, member.university.id)
            ?: throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
    }

    private fun findLecture(lectureId: Long): Lecture {
        return lectureRepository.findById(lectureId).orElseThrow {
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
    }

    private fun validateLectureScope(member: Member, semester: Semester, lecture: Lecture) {
        if (lecture.semester.id != semester.id) {
            throw BusinessException(ErrorCode.LECTURE_NOT_IN_SEMESTER)
        }

        if (lecture.semester.university.id != member.university.id) {
            throw BusinessException(ErrorCode.LECTURE_NOT_IN_MEMBER_UNIVERSITY)
        }
    }

    private fun findOrCreateTimetable(member: Member, semester: Semester): Timetable {
        timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)?.let { return it }

        return try {
            timetableRepository.saveAndFlush(
                Timetable(
                    member = member,
                    semester = semester
                )
            )
        } catch (_: DataIntegrityViolationException) {
            timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)
                ?: throw BusinessException(ErrorCode.INTERNAL_ERROR)
        }
    }

    private fun validateNoScheduleConflict(timetable: Timetable, lecture: Lecture) {
        val existingLectureIds = timetableLectureRepository.findAllByTimetableId(timetable.id)
            .map { it.lecture.id }
        if (existingLectureIds.isEmpty()) {
            return
        }

        val schedulesByLectureId = lectureScheduleRepository.findAllByLectureIdIn(existingLectureIds + lecture.id)
            .groupBy { it.lecture.id }
        val candidateSchedules = schedulesByLectureId[lecture.id].orEmpty()
        if (candidateSchedules.isEmpty()) {
            return
        }

        val hasConflict = existingLectureIds.any { existingLectureId ->
            schedulesByLectureId[existingLectureId].orEmpty().any { existingSchedule ->
                candidateSchedules.any { candidateSchedule ->
                    isOverlapping(existingSchedule, candidateSchedule)
                }
            }
        }

        if (hasConflict) {
            throw BusinessException(ErrorCode.TIMETABLE_LECTURE_CONFLICT)
        }
    }

    private fun isOverlapping(first: LectureSchedule, second: LectureSchedule): Boolean {
        if (first.dayOfWeek != second.dayOfWeek) {
            return false
        }

        return first.startTime < second.endTime && second.startTime < first.endTime
    }
}
