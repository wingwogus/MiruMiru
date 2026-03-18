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
import com.example.domain.semester.SemesterTerm
import com.example.domain.timetable.TimetableLectureRepository
import com.example.domain.timetable.TimetableRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TimetableQueryService(
    private val memberRepository: MemberRepository,
    private val semesterRepository: SemesterRepository,
    private val lectureRepository: LectureRepository,
    private val lectureScheduleRepository: LectureScheduleRepository,
    private val timetableRepository: TimetableRepository,
    private val timetableLectureRepository: TimetableLectureRepository
) {

    fun getSemesters(userId: String): List<TimetableQueryResult.SemesterSummary> {
        val member = findMember(userId)

        return semesterRepository.findAllByUniversityId(member.university.id)
            .sortedWith(compareByDescending<Semester> { it.academicYear }.thenByDescending { termPriority(it.term) })
            .map { semester ->
                TimetableQueryResult.SemesterSummary(
                    id = semester.id,
                    academicYear = semester.academicYear,
                    term = semester.term.name
                )
            }
    }

    fun getLectures(userId: String, semesterId: Long): List<TimetableQueryResult.LectureItem> {
        val member = findMember(userId)
        val semester = findSemester(member, semesterId)
        val lectures = lectureRepository.findAllBySemesterIdOrderByCodeAsc(semester.id)

        return mapLectureItems(lectures)
    }

    fun getMyTimetable(userId: String, semesterId: Long): TimetableQueryResult.TimetableDetail {
        val member = findMember(userId)
        val semester = findSemester(member, semesterId)
        val timetable = timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)
        val timetableLectures = timetable?.let {
            timetableLectureRepository.findAllByTimetableId(it.id)
                .map { timetableLecture -> timetableLecture.lecture }
                .sortedBy { lecture -> lecture.code }
        }.orEmpty()

        return TimetableQueryResult.TimetableDetail(
            timetableId = timetable?.id,
            semester = TimetableQueryResult.SemesterSummary(
                id = semester.id,
                academicYear = semester.academicYear,
                term = semester.term.name
            ),
            lectures = mapLectureItems(timetableLectures)
        )
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

    private fun mapLectureItems(lectures: List<Lecture>): List<TimetableQueryResult.LectureItem> {
        if (lectures.isEmpty()) {
            return emptyList()
        }

        val lectureIds = lectures.map { it.id }
        val schedulesByLectureId = lectureScheduleRepository.findAllByLectureIdIn(lectureIds)
            .groupBy { it.lecture.id }

        return lectures.map { lecture ->
            val schedules = schedulesByLectureId[lecture.id].orEmpty()
                .sortedWith(compareBy<LectureSchedule>({ it.dayOfWeek.value }, { it.startTime }))
                .map { schedule ->
                    TimetableQueryResult.LectureScheduleItem(
                        dayOfWeek = schedule.dayOfWeek.name,
                        startTime = schedule.startTime.toString(),
                        endTime = schedule.endTime.toString(),
                        location = schedule.location
                    )
                }

            TimetableQueryResult.LectureItem(
                id = lecture.id,
                code = lecture.code,
                name = lecture.name,
                professor = lecture.professor,
                credit = lecture.credit,
                major = lecture.major?.let { major ->
                    TimetableQueryResult.MajorItem(
                        majorId = major.id,
                        code = major.code,
                        name = major.name
                    )
                },
                schedules = schedules
            )
        }
    }

    private fun termPriority(term: SemesterTerm): Int {
        return when (term) {
            SemesterTerm.FALL -> 2
            SemesterTerm.SPRING -> 1
        }
    }
}
