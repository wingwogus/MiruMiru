package com.example.application.bootstrap

import com.example.domain.lecture.Lecture
import com.example.domain.lecture.LectureRepository
import com.example.domain.lecture.LectureSchedule
import com.example.domain.lecture.LectureScheduleRepository
import com.example.domain.major.Major
import com.example.domain.major.MajorRepository
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
import com.example.domain.university.UniversityRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.DayOfWeek
import java.time.LocalTime

class LocalTestDataInitializerTest {
    private val universityRepository = mock(UniversityRepository::class.java)
    private val majorRepository = mock(MajorRepository::class.java)
    private val memberRepository = mock(MemberRepository::class.java)
    private val semesterRepository = mock(SemesterRepository::class.java)
    private val lectureRepository = mock(LectureRepository::class.java)
    private val lectureScheduleRepository = mock(LectureScheduleRepository::class.java)
    private val timetableRepository = mock(TimetableRepository::class.java)
    private val timetableLectureRepository = mock(TimetableLectureRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val initializer = LocalTestDataInitializer(
        universityRepository = universityRepository,
        majorRepository = majorRepository,
        memberRepository = memberRepository,
        semesterRepository = semesterRepository,
        lectureRepository = lectureRepository,
        lectureScheduleRepository = lectureScheduleRepository,
        timetableRepository = timetableRepository,
        timetableLectureRepository = timetableLectureRepository,
        passwordEncoder = passwordEncoder
    )

    @Test
    fun `creates timetable graph when seed data is missing`() {
        val university = University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp")
        val computerScience = Major(id = 10L, university = university, code = "CS", name = "Computer Science")
        val mathematics = Major(id = 11L, university = university, code = "MATH", name = "Mathematics")
        val member = Member(
            id = 2L,
            university = university,
            major = computerScience,
            email = "test@tokyo.ac.jp",
            password = "encoded-password",
            nickname = "test-user"
        )
        val semester = Semester(
            id = 3L,
            university = university,
            academicYear = 2026,
            term = SemesterTerm.SPRING
        )
        val cs101 = Lecture(
            id = 4L,
            semester = semester,
            major = computerScience,
            code = "CS101",
            name = "Introduction to Computer Science",
            professor = "Prof. Akiyama",
            credit = 3
        )
        val math201 = Lecture(
            id = 5L,
            semester = semester,
            major = null,
            code = "MATH201",
            name = "Linear Algebra",
            professor = "Prof. Sato",
            credit = 2
        )
        val timetable = Timetable(
            id = 6L,
            member = member,
            semester = semester
        )

        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp")).thenReturn(null)
        `when`(universityRepository.save(any(University::class.java))).thenReturn(university)
        `when`(majorRepository.findByUniversityIdAndCode(1L, "CS")).thenReturn(null)
        `when`(majorRepository.findByUniversityIdAndCode(1L, "MATH")).thenReturn(null)
        `when`(majorRepository.save(any(Major::class.java)))
            .thenReturn(computerScience)
            .thenReturn(mathematics)
        `when`(memberRepository.findByEmail("test@tokyo.ac.jp")).thenReturn(null)
        `when`(passwordEncoder.encode("password123!")).thenReturn("encoded-password")
        `when`(memberRepository.save(any(Member::class.java))).thenReturn(member)
        `when`(semesterRepository.findByUniversityIdAndAcademicYearAndTerm(1L, 2026, SemesterTerm.SPRING))
            .thenReturn(null)
        `when`(semesterRepository.save(any(Semester::class.java))).thenReturn(semester)
        `when`(lectureRepository.findBySemesterIdAndCode(3L, "CS101")).thenReturn(null)
        `when`(lectureRepository.findBySemesterIdAndCode(3L, "MATH201")).thenReturn(null)
        `when`(lectureRepository.save(any(Lecture::class.java)))
            .thenReturn(cs101)
            .thenReturn(math201)
        stubMissingScheduleLookups()
        `when`(lectureScheduleRepository.save(any(LectureSchedule::class.java)))
            .thenAnswer { it.arguments.first() }
        `when`(timetableRepository.findByMemberIdAndSemesterId(2L, 3L)).thenReturn(null)
        `when`(timetableRepository.save(any(Timetable::class.java))).thenReturn(timetable)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(6L, 4L)).thenReturn(false)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(6L, 5L)).thenReturn(false)

        initializer.run(DefaultApplicationArguments(*emptyArray<String>()))

        verify(universityRepository).save(any(University::class.java))
        verify(majorRepository, times(2)).save(any(Major::class.java))
        verify(memberRepository).save(any(Member::class.java))
        verify(semesterRepository).save(any(Semester::class.java))
        verify(lectureRepository, times(2)).save(any(Lecture::class.java))
        verify(lectureScheduleRepository, times(3)).save(any(LectureSchedule::class.java))
        verify(timetableRepository).save(any(Timetable::class.java))
        verify(timetableLectureRepository, times(2)).save(any(TimetableLecture::class.java))

        val timetableCaptor = ArgumentCaptor.forClass(Timetable::class.java)
        verify(timetableRepository).save(timetableCaptor.capture())
        assertEquals(semester.id, timetableCaptor.value.semester.id)
        assertEquals(member.id, timetableCaptor.value.member.id)
        assertEquals(computerScience.id, member.major.id)
    }

    @Test
    fun `reuses existing timetable graph without creating duplicates`() {
        val university = University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp")
        val computerScience = Major(id = 10L, university = university, code = "CS", name = "Computer Science")
        val mathematics = Major(id = 11L, university = university, code = "MATH", name = "Mathematics")
        val member = Member(
            id = 2L,
            university = university,
            major = computerScience,
            email = "test@tokyo.ac.jp",
            password = "encoded-password",
            nickname = "test-user"
        )
        val semester = Semester(
            id = 3L,
            university = university,
            academicYear = 2026,
            term = SemesterTerm.SPRING
        )
        val cs101 = Lecture(
            id = 4L,
            semester = semester,
            major = computerScience,
            code = "CS101",
            name = "Introduction to Computer Science",
            professor = "Prof. Akiyama",
            credit = 3
        )
        val math201 = Lecture(
            id = 5L,
            semester = semester,
            major = null,
            code = "MATH201",
            name = "Linear Algebra",
            professor = "Prof. Sato",
            credit = 2
        )
        val timetable = Timetable(
            id = 6L,
            member = member,
            semester = semester
        )

        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp")).thenReturn(university)
        `when`(majorRepository.findByUniversityIdAndCode(1L, "CS")).thenReturn(computerScience)
        `when`(majorRepository.findByUniversityIdAndCode(1L, "MATH")).thenReturn(mathematics)
        `when`(memberRepository.findByEmail("test@tokyo.ac.jp")).thenReturn(member)
        `when`(semesterRepository.findByUniversityIdAndAcademicYearAndTerm(1L, 2026, SemesterTerm.SPRING))
            .thenReturn(semester)
        `when`(lectureRepository.findBySemesterIdAndCode(3L, "CS101")).thenReturn(cs101)
        `when`(lectureRepository.findBySemesterIdAndCode(3L, "MATH201")).thenReturn(math201)
        stubExistingScheduleLookups(cs101, math201)
        `when`(timetableRepository.findByMemberIdAndSemesterId(2L, 3L)).thenReturn(timetable)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(6L, 4L)).thenReturn(true)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(6L, 5L)).thenReturn(true)

        initializer.run(DefaultApplicationArguments(*emptyArray<String>()))

        verify(universityRepository, never()).save(any(University::class.java))
        verify(majorRepository, never()).save(any(Major::class.java))
        verify(memberRepository, never()).save(any(Member::class.java))
        verify(semesterRepository, never()).save(any(Semester::class.java))
        verify(lectureRepository, never()).save(any(Lecture::class.java))
        verify(lectureScheduleRepository, never()).save(any(LectureSchedule::class.java))
        verify(timetableRepository, never()).save(any(Timetable::class.java))
        verify(timetableLectureRepository, never()).save(any(TimetableLecture::class.java))
    }

    private fun stubMissingScheduleLookups() {
        `when`(
            lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(
                4L,
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(10, 30)
            )
        ).thenReturn(null)
        `when`(
            lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(
                4L,
                DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0),
                LocalTime.of(10, 30)
            )
        ).thenReturn(null)
        `when`(
            lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(
                5L,
                DayOfWeek.TUESDAY,
                LocalTime.of(13, 0),
                LocalTime.of(14, 30)
            )
        ).thenReturn(null)
    }

    private fun stubExistingScheduleLookups(cs101: Lecture, math201: Lecture) {
        `when`(
            lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(
                4L,
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(10, 30)
            )
        ).thenReturn(
            LectureSchedule(
                id = 7L,
                lecture = cs101,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 30),
                location = "Engineering Hall 101"
            )
        )
        `when`(
            lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(
                4L,
                DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0),
                LocalTime.of(10, 30)
            )
        ).thenReturn(
            LectureSchedule(
                id = 8L,
                lecture = cs101,
                dayOfWeek = DayOfWeek.WEDNESDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 30),
                location = "Engineering Hall 101"
            )
        )
        `when`(
            lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(
                5L,
                DayOfWeek.TUESDAY,
                LocalTime.of(13, 0),
                LocalTime.of(14, 30)
            )
        ).thenReturn(
            LectureSchedule(
                id = 9L,
                lecture = math201,
                dayOfWeek = DayOfWeek.TUESDAY,
                startTime = LocalTime.of(13, 0),
                endTime = LocalTime.of(14, 30),
                location = "Science Building 202"
            )
        )
    }
}
