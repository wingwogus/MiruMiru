package com.example.application.timetable

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.lecture.Lecture
import com.example.domain.lecture.LectureRepository
import com.example.domain.lecture.LectureSchedule
import com.example.domain.lecture.LectureScheduleRepository
import com.example.domain.major.Major
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.semester.Semester
import com.example.domain.semester.SemesterRepository
import com.example.domain.semester.SemesterTerm
import com.example.domain.timetable.Timetable
import com.example.domain.timetable.TimetableLecture
import com.example.domain.timetable.TimetableLectureRepository
import com.example.domain.timetable.TimetableRepository
import com.example.domain.university.University
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Optional

class TimetableWriteServiceTest {
    private lateinit var memberRepository: MemberRepository
    private lateinit var semesterRepository: SemesterRepository
    private lateinit var lectureRepository: LectureRepository
    private lateinit var lectureScheduleRepository: LectureScheduleRepository
    private lateinit var timetableRepository: TimetableRepository
    private lateinit var timetableLectureRepository: TimetableLectureRepository
    private lateinit var timetableWriteService: TimetableWriteService

    @BeforeEach
    fun setUp() {
        memberRepository = mock(MemberRepository::class.java)
        semesterRepository = mock(SemesterRepository::class.java)
        lectureRepository = mock(LectureRepository::class.java)
        lectureScheduleRepository = mock(LectureScheduleRepository::class.java)
        timetableRepository = mock(TimetableRepository::class.java)
        timetableLectureRepository = mock(TimetableLectureRepository::class.java)
        timetableWriteService = TimetableWriteService(
            memberRepository = memberRepository,
            semesterRepository = semesterRepository,
            lectureRepository = lectureRepository,
            lectureScheduleRepository = lectureScheduleRepository,
            timetableRepository = timetableRepository,
            timetableLectureRepository = timetableLectureRepository
        )
    }

    @Test
    fun `add lecture creates timetable when missing`() {
        val university = university()
        val member = member(university)
        val semester = semester(university = university)
        val lecture = lecture(id = 20L, semester = semester)
        val timetable = timetable(member = member, semester = semester)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(semesterRepository.findByIdAndUniversityId(semester.id, university.id)).thenReturn(semester)
        `when`(lectureRepository.findById(lecture.id)).thenReturn(Optional.of(lecture))
        `when`(timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id))
            .thenReturn(null)
            .thenReturn(timetable)
        `when`(timetableRepository.saveAndFlush(any(Timetable::class.java))).thenReturn(timetable)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(timetable.id, lecture.id)).thenReturn(false)
        `when`(timetableLectureRepository.findAllByTimetableId(timetable.id)).thenReturn(emptyList())

        timetableWriteService.addLecture(
            TimetableCommand.AddLectureToMyTimetable(
                userId = member.id.toString(),
                semesterId = semester.id,
                lectureId = lecture.id
            )
        )

        verify(timetableRepository).saveAndFlush(any(Timetable::class.java))
        verify(timetableLectureRepository).save(any(TimetableLecture::class.java))
    }

    @Test
    fun `add lecture retries lookup when timetable create races`() {
        val university = university()
        val member = member(university)
        val semester = semester(university = university)
        val lecture = lecture(id = 20L, semester = semester)
        val timetable = timetable(member = member, semester = semester)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(semesterRepository.findByIdAndUniversityId(semester.id, university.id)).thenReturn(semester)
        `when`(lectureRepository.findById(lecture.id)).thenReturn(Optional.of(lecture))
        `when`(timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id))
            .thenReturn(null)
            .thenReturn(timetable)
        `when`(timetableRepository.saveAndFlush(any(Timetable::class.java)))
            .thenThrow(DataIntegrityViolationException("duplicate"))
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(timetable.id, lecture.id)).thenReturn(false)
        `when`(timetableLectureRepository.findAllByTimetableId(timetable.id)).thenReturn(emptyList())

        timetableWriteService.addLecture(
            TimetableCommand.AddLectureToMyTimetable(
                userId = member.id.toString(),
                semesterId = semester.id,
                lectureId = lecture.id
            )
        )

        verify(timetableLectureRepository).save(any(TimetableLecture::class.java))
    }

    @Test
    fun `add lecture fails when lecture already exists in timetable`() {
        val university = university()
        val member = member(university)
        val semester = semester(university = university)
        val lecture = lecture(id = 20L, semester = semester)
        val timetable = timetable(member = member, semester = semester)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(semesterRepository.findByIdAndUniversityId(semester.id, university.id)).thenReturn(semester)
        `when`(lectureRepository.findById(lecture.id)).thenReturn(Optional.of(lecture))
        `when`(timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)).thenReturn(timetable)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(timetable.id, lecture.id)).thenReturn(true)

        val exception = assertThrows(BusinessException::class.java) {
            timetableWriteService.addLecture(
                TimetableCommand.AddLectureToMyTimetable(
                    userId = member.id.toString(),
                    semesterId = semester.id,
                    lectureId = lecture.id
                )
            )
        }

        assertEquals(ErrorCode.TIMETABLE_LECTURE_DUPLICATE, exception.errorCode)
        verify(timetableLectureRepository, never()).save(any(TimetableLecture::class.java))
    }

    @Test
    fun `add lecture fails when schedule conflicts with existing lecture`() {
        val university = university()
        val member = member(university)
        val semester = semester(university = university)
        val existingLecture = lecture(id = 20L, semester = semester, code = "CS101")
        val conflictLecture = lecture(id = 21L, semester = semester, code = "PHYS301")
        val timetable = timetable(member = member, semester = semester)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(semesterRepository.findByIdAndUniversityId(semester.id, university.id)).thenReturn(semester)
        `when`(lectureRepository.findById(conflictLecture.id)).thenReturn(Optional.of(conflictLecture))
        `when`(timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)).thenReturn(timetable)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(timetable.id, conflictLecture.id)).thenReturn(false)
        `when`(timetableLectureRepository.findAllByTimetableId(timetable.id))
            .thenReturn(listOf(TimetableLecture(id = 50L, timetable = timetable, lecture = existingLecture)))
        `when`(lectureScheduleRepository.findAllByLectureIdIn(listOf(existingLecture.id, conflictLecture.id)))
            .thenReturn(
                listOf(
                    schedule(existingLecture, DayOfWeek.MONDAY, 9, 0, 10, 30),
                    schedule(conflictLecture, DayOfWeek.MONDAY, 10, 0, 11, 0)
                )
            )

        val exception = assertThrows(BusinessException::class.java) {
            timetableWriteService.addLecture(
                TimetableCommand.AddLectureToMyTimetable(
                    userId = member.id.toString(),
                    semesterId = semester.id,
                    lectureId = conflictLecture.id
                )
            )
        }

        assertEquals(ErrorCode.TIMETABLE_LECTURE_CONFLICT, exception.errorCode)
    }

    @Test
    fun `remove lecture deletes existing timetable lecture`() {
        val university = university()
        val member = member(university)
        val semester = semester(university = university)
        val lecture = lecture(id = 20L, semester = semester)
        val timetable = timetable(member = member, semester = semester)
        val timetableLecture = TimetableLecture(id = 60L, timetable = timetable, lecture = lecture)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(semesterRepository.findByIdAndUniversityId(semester.id, university.id)).thenReturn(semester)
        `when`(timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)).thenReturn(timetable)
        `when`(timetableLectureRepository.findByTimetableIdAndLectureId(timetable.id, lecture.id)).thenReturn(timetableLecture)

        timetableWriteService.removeLecture(
            TimetableCommand.RemoveLectureFromMyTimetable(
                userId = member.id.toString(),
                semesterId = semester.id,
                lectureId = lecture.id
            )
        )

        verify(timetableLectureRepository).delete(timetableLecture)
    }

    private fun university(): University {
        return University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp")
    }

    private fun major(university: University): Major {
        return Major(id = 10L, university = university, code = "CS", name = "Computer Science")
    }

    private fun member(university: University): Member {
        return Member(
            id = 2L,
            university = university,
            major = major(university),
            email = "test@tokyo.ac.jp",
            password = "encoded",
            nickname = "test-user"
        )
    }

    private fun semester(university: University): Semester {
        return Semester(
            id = 3L,
            university = university,
            academicYear = 2026,
            term = SemesterTerm.SPRING
        )
    }

    private fun lecture(id: Long, semester: Semester, code: String = "CS101"): Lecture {
        return Lecture(
            id = id,
            semester = semester,
            major = major(semester.university),
            code = code,
            name = code,
            professor = "Prof. Akiyama",
            credit = 3
        )
    }

    private fun timetable(member: Member, semester: Semester): Timetable {
        return Timetable(
            id = 30L,
            member = member,
            semester = semester
        )
    }

    private fun schedule(
        lecture: Lecture,
        dayOfWeek: DayOfWeek,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ): LectureSchedule {
        return LectureSchedule(
            id = 100L + lecture.id,
            lecture = lecture,
            dayOfWeek = dayOfWeek,
            startTime = LocalTime.of(startHour, startMinute),
            endTime = LocalTime.of(endHour, endMinute),
            location = "Room"
        )
    }
}
