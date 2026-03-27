package com.example.application.timetable

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.course.Course
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Optional

class TimetableQueryServiceTest {
    private lateinit var memberRepository: MemberRepository
    private lateinit var semesterRepository: SemesterRepository
    private lateinit var lectureRepository: LectureRepository
    private lateinit var lectureScheduleRepository: LectureScheduleRepository
    private lateinit var timetableRepository: TimetableRepository
    private lateinit var timetableLectureRepository: TimetableLectureRepository
    private lateinit var timetableQueryService: TimetableQueryService

    @BeforeEach
    fun setUp() {
        memberRepository = mock(MemberRepository::class.java)
        semesterRepository = mock(SemesterRepository::class.java)
        lectureRepository = mock(LectureRepository::class.java)
        lectureScheduleRepository = mock(LectureScheduleRepository::class.java)
        timetableRepository = mock(TimetableRepository::class.java)
        timetableLectureRepository = mock(TimetableLectureRepository::class.java)
        timetableQueryService = TimetableQueryService(
            memberRepository = memberRepository,
            semesterRepository = semesterRepository,
            lectureRepository = lectureRepository,
            lectureScheduleRepository = lectureScheduleRepository,
            timetableRepository = timetableRepository,
            timetableLectureRepository = timetableLectureRepository
        )
    }

    @Test
    fun `get semesters returns current university semesters ordered newest first`() {
        val university = university()
        val member = member(university)
        val older = semester(id = 10L, university = university, year = 2025, term = SemesterTerm.FALL)
        val newerSpring = semester(id = 11L, university = university, year = 2026, term = SemesterTerm.SPRING)
        val newerFall = semester(id = 12L, university = university, year = 2026, term = SemesterTerm.FALL)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(semesterRepository.findAllByUniversityId(university.id)).thenReturn(listOf(older, newerSpring, newerFall))

        val result = timetableQueryService.getSemesters(member.id.toString())

        assertEquals(listOf(12L, 11L, 10L), result.map { it.id })
    }

    @Test
    fun `get lectures fails when semester is outside member university`() {
        val university = university()
        val member = member(university)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(semesterRepository.findByIdAndUniversityId(999L, university.id)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            timetableQueryService.getLectures(member.id.toString(), 999L)
        }

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `get my timetable returns lecture schedules`() {
        val university = university()
        val major = major(university = university)
        val member = member(university)
        val semester = semester(university = university)
        val lecture = lecture(semester = semester, major = major)
        val timetable = timetable(member = member, semester = semester)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(semesterRepository.findByIdAndUniversityId(semester.id, university.id)).thenReturn(semester)
        `when`(timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)).thenReturn(timetable)
        `when`(timetableLectureRepository.findAllByTimetableId(timetable.id))
            .thenReturn(listOf(TimetableLecture(id = 40L, timetable = timetable, lecture = lecture)))
        `when`(lectureScheduleRepository.findAllByLectureIdIn(listOf(lecture.id)))
            .thenReturn(
                listOf(
                    LectureSchedule(
                        id = 50L,
                        lecture = lecture,
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(10, 30),
                        location = "Engineering Hall 101"
                    )
                )
            )

        val result = timetableQueryService.getMyTimetable(member.id.toString(), semester.id)

        assertEquals(timetable.id, result.timetableId)
        assertEquals("CS101", result.lectures.single().code)
        assertEquals(25L, result.lectures.single().courseId)
        assertEquals("Computer Science", result.lectures.single().major!!.name)
        assertEquals("MONDAY", result.lectures.single().schedules.single().dayOfWeek)
    }

    @Test
    fun `get my timetable returns empty lectures when timetable does not exist`() {
        val university = university()
        val member = member(university)
        val semester = semester(university = university)

        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))
        `when`(semesterRepository.findByIdAndUniversityId(semester.id, university.id)).thenReturn(semester)
        `when`(timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)).thenReturn(null)

        val result = timetableQueryService.getMyTimetable(member.id.toString(), semester.id)

        assertEquals(null, result.timetableId)
        assertEquals(emptyList<String>(), result.lectures.map { it.code })
    }

    private fun university(): University {
        return University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp")
    }

    private fun member(university: University): Member {
        return Member(
            id = 2L,
            university = university,
            major = major(university = university),
            email = "test@tokyo.ac.jp",
            password = "encoded",
            nickname = "test-user"
        )
    }

    private fun major(university: University): Major {
        return Major(
            id = 15L,
            university = university,
            code = "CS",
            name = "Computer Science"
        )
    }

    private fun semester(
        id: Long = 3L,
        university: University,
        year: Int = 2026,
        term: SemesterTerm = SemesterTerm.SPRING
    ): Semester {
        return Semester(
            id = id,
            university = university,
            academicYear = year,
            term = term
        )
    }

    private fun lecture(semester: Semester, major: Major? = null): Lecture {
        return Lecture(
            id = 20L,
            semester = semester,
            major = major,
            course = course(university = semester.university),
            code = "CS101",
            name = "Introduction to Computer Science",
            professor = "Prof. Akiyama",
            credit = 3
        )
    }

    private fun course(university: University): Course {
        return Course(
            id = 25L,
            university = university,
            code = "CS101",
            name = "Introduction to Computer Science"
        )
    }

    private fun timetable(member: Member, semester: Semester): Timetable {
        return Timetable(
            id = 30L,
            member = member,
            semester = semester
        )
    }
}
