package com.example.application.bootstrap

import com.example.domain.board.Board
import com.example.domain.board.BoardRepository
import com.example.domain.comment.Comment
import com.example.domain.comment.CommentRepository
import com.example.domain.lecture.Lecture
import com.example.domain.lecture.LectureRepository
import com.example.domain.lecture.LectureSchedule
import com.example.domain.lecture.LectureScheduleRepository
import com.example.domain.major.Major
import com.example.domain.major.MajorRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import com.example.domain.post.PostAnonymousMapping
import com.example.domain.post.PostAnonymousMappingRepository
import com.example.domain.post.PostImage
import com.example.domain.post.PostImageRepository
import com.example.domain.post.PostLike
import com.example.domain.post.PostLikeRepository
import com.example.domain.post.PostRepository
import com.example.domain.semester.Semester
import com.example.domain.semester.SemesterRepository
import com.example.domain.semester.SemesterTerm
import com.example.domain.timetable.Timetable
import com.example.domain.timetable.TimetableLecture
import com.example.domain.timetable.TimetableLectureRepository
import com.example.domain.timetable.TimetableRepository
import com.example.domain.university.University
import com.example.domain.university.UniversityRepository
import org.junit.jupiter.api.Test
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
    private val boardRepository = mock(BoardRepository::class.java)
    private val postRepository = mock(PostRepository::class.java)
    private val postLikeRepository = mock(PostLikeRepository::class.java)
    private val postAnonymousMappingRepository = mock(PostAnonymousMappingRepository::class.java)
    private val commentRepository = mock(CommentRepository::class.java)
    private val postImageRepository = mock(PostImageRepository::class.java)
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
        boardRepository = boardRepository,
        postRepository = postRepository,
        postLikeRepository = postLikeRepository,
        postAnonymousMappingRepository = postAnonymousMappingRepository,
        commentRepository = commentRepository,
        postImageRepository = postImageRepository,
        semesterRepository = semesterRepository,
        lectureRepository = lectureRepository,
        lectureScheduleRepository = lectureScheduleRepository,
        timetableRepository = timetableRepository,
        timetableLectureRepository = timetableLectureRepository,
        passwordEncoder = passwordEncoder
    )

    @Test
    fun `creates missing seed graph including comments likes and anonymous mappings`() {
        val university = university()
        val computerScience = major(id = 10L, university = university, code = "CS", name = "Computer Science")
        val mathematics = major(id = 11L, university = university, code = "MATH", name = "Mathematics")
        val testMember = member(id = 2L, university = university, major = computerScience, email = "test@tokyo.ac.jp", nickname = "test-user")
        val emptyMember = member(id = 3L, university = university, major = mathematics, email = "empty@tokyo.ac.jp", nickname = "empty-user")
        val generalBoard = board(id = 20L, university = university, code = "general", name = "General", isAnonymousAllowed = false)
        val freeBoard = board(id = 21L, university = university, code = "free", name = "Free Talk", isAnonymousAllowed = true)
        val semester = semester(university)
        val lectures = lectureSeeds(semester, computerScience, mathematics)
        val timetable = Timetable(id = 40L, member = testMember, semester = semester)
        val generalPost = post(id = 50L, board = generalBoard, member = testMember, title = "Welcome to MiruMiru", isAnonymous = false, likeCount = 0, commentCount = 0)
        val freePost = post(id = 51L, board = freeBoard, member = testMember, title = "Best lunch near campus?", isAnonymous = true, likeCount = 1, commentCount = 2)
        val rootComment = Comment(id = 70L, post = freePost, member = emptyMember, parent = null, content = "There is a cheap curry place behind the engineering building.", isAnonymous = true, isDeleted = false)
        val childComment = Comment(id = 71L, post = freePost, member = testMember, parent = rootComment, content = "Thanks, I will try that place tomorrow.", isAnonymous = true, isDeleted = false)

        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp")).thenReturn(null)
        `when`(universityRepository.save(any(University::class.java))).thenReturn(university)

        `when`(majorRepository.findByUniversityIdAndCode(1L, "CS")).thenReturn(null)
        `when`(majorRepository.findByUniversityIdAndCode(1L, "MATH")).thenReturn(null)
        `when`(majorRepository.save(any(Major::class.java))).thenReturn(computerScience).thenReturn(mathematics)

        `when`(memberRepository.findByEmail("test@tokyo.ac.jp")).thenReturn(null)
        `when`(memberRepository.findByEmail("empty@tokyo.ac.jp")).thenReturn(null)
        `when`(passwordEncoder.encode("password123!")).thenReturn("encoded-password")
        `when`(memberRepository.save(any(Member::class.java))).thenReturn(testMember).thenReturn(emptyMember)

        `when`(boardRepository.findByUniversityIdAndCode(1L, "general")).thenReturn(null)
        `when`(boardRepository.findByUniversityIdAndCode(1L, "free")).thenReturn(null)
        `when`(boardRepository.save(any(Board::class.java))).thenReturn(generalBoard).thenReturn(freeBoard)

        `when`(semesterRepository.findByUniversityIdAndAcademicYearAndTerm(1L, 2026, SemesterTerm.SPRING)).thenReturn(null)
        `when`(semesterRepository.save(any(Semester::class.java))).thenReturn(semester)

        stubMissingLectureLookups(semester.id)
        `when`(lectureRepository.save(any(Lecture::class.java)))
            .thenReturn(lectures[0]).thenReturn(lectures[1]).thenReturn(lectures[2]).thenReturn(lectures[3]).thenReturn(lectures[4])
            .thenReturn(lectures[5]).thenReturn(lectures[6]).thenReturn(lectures[7]).thenReturn(lectures[8])
        stubMissingScheduleLookups()
        `when`(lectureScheduleRepository.save(any(LectureSchedule::class.java))).thenAnswer { it.arguments.first() }

        `when`(timetableRepository.findByMemberIdAndSemesterId(2L, 4L)).thenReturn(null)
        `when`(timetableRepository.save(any(Timetable::class.java))).thenReturn(timetable)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(40L, 5L)).thenReturn(false)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(40L, 6L)).thenReturn(false)

        `when`(postRepository.findByBoardIdAndTitle(20L, "Welcome to MiruMiru")).thenReturn(null).thenReturn(generalPost)
        `when`(postRepository.findByBoardIdAndTitle(21L, "Best lunch near campus?")).thenReturn(null).thenReturn(freePost)
        `when`(postRepository.save(any(Post::class.java))).thenReturn(generalPost).thenReturn(freePost)
        `when`(postImageRepository.findByPostIdAndDisplayOrder(50L, 0)).thenReturn(null)
        `when`(postImageRepository.save(any(PostImage::class.java))).thenAnswer { it.arguments.first() }

        `when`(postAnonymousMappingRepository.findByPostIdAndMemberId(51L, 2L)).thenReturn(null)
        `when`(postAnonymousMappingRepository.findByPostIdAndMemberId(51L, 3L)).thenReturn(null)
        `when`(postAnonymousMappingRepository.save(any(PostAnonymousMapping::class.java))).thenAnswer { it.arguments.first() }

        `when`(postLikeRepository.findByPostIdAndMemberId(51L, 3L)).thenReturn(null)
        `when`(postLikeRepository.save(any(PostLike::class.java))).thenAnswer { it.arguments.first() }

        `when`(commentRepository.findAllByPostIdOrderByCreatedAtAsc(51L))
            .thenReturn(emptyList())
            .thenReturn(listOf(rootComment))
            .thenReturn(listOf(rootComment))
            .thenReturn(listOf(rootComment))
        `when`(commentRepository.save(any(Comment::class.java))).thenReturn(rootComment).thenReturn(childComment)

        initializer.run(DefaultApplicationArguments())

        verify(universityRepository).save(any(University::class.java))
        verify(majorRepository, times(2)).save(any(Major::class.java))
        verify(memberRepository, times(2)).save(any(Member::class.java))
        verify(boardRepository, times(2)).save(any(Board::class.java))
        verify(postRepository, times(2)).save(any(Post::class.java))
        verify(postImageRepository).save(any(PostImage::class.java))
        verify(postAnonymousMappingRepository, times(2)).save(any(PostAnonymousMapping::class.java))
        verify(postLikeRepository).save(any(PostLike::class.java))
        verify(commentRepository, times(2)).save(any(Comment::class.java))
        verify(semesterRepository).save(any(Semester::class.java))
        verify(lectureRepository, times(9)).save(any(Lecture::class.java))
        verify(lectureScheduleRepository, times(10)).save(any(LectureSchedule::class.java))
        verify(timetableRepository).save(any(Timetable::class.java))
        verify(timetableLectureRepository, times(2)).save(any(TimetableLecture::class.java))
    }

    @Test
    fun `reuses existing seed graph without creating duplicates`() {
        val university = university()
        val computerScience = major(id = 10L, university = university, code = "CS", name = "Computer Science")
        val mathematics = major(id = 11L, university = university, code = "MATH", name = "Mathematics")
        val testMember = member(id = 2L, university = university, major = computerScience, email = "test@tokyo.ac.jp", nickname = "test-user")
        val emptyMember = member(id = 3L, university = university, major = mathematics, email = "empty@tokyo.ac.jp", nickname = "empty-user")
        val generalBoard = board(id = 20L, university = university, code = "general", name = "General", isAnonymousAllowed = false)
        val freeBoard = board(id = 21L, university = university, code = "free", name = "Free Talk", isAnonymousAllowed = true)
        val semester = semester(university)
        val lectures = lectureSeeds(semester, computerScience, mathematics)
        val timetable = Timetable(id = 40L, member = testMember, semester = semester)
        val generalPost = post(id = 50L, board = generalBoard, member = testMember, title = "Welcome to MiruMiru", isAnonymous = false, likeCount = 0, commentCount = 0)
        val freePost = post(id = 51L, board = freeBoard, member = testMember, title = "Best lunch near campus?", isAnonymous = true, likeCount = 1, commentCount = 2)
        val generalPostImage = PostImage(id = 60L, post = generalPost, imageUrl = "https://example.com/images/mirumiru-welcome.png", displayOrder = 0)
        val freePostAnonForAuthor = PostAnonymousMapping(id = 61L, post = freePost, member = testMember, anonNumber = 1)
        val freePostAnonForEmptyMember = PostAnonymousMapping(id = 62L, post = freePost, member = emptyMember, anonNumber = 2)
        val freePostLike = PostLike(id = 63L, post = freePost, member = emptyMember)
        val rootComment = Comment(id = 70L, post = freePost, member = emptyMember, parent = null, content = "There is a cheap curry place behind the engineering building.", isAnonymous = true, isDeleted = false)
        val childComment = Comment(id = 71L, post = freePost, member = testMember, parent = rootComment, content = "Thanks, I will try that place tomorrow.", isAnonymous = true, isDeleted = false)

        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp")).thenReturn(university)
        `when`(majorRepository.findByUniversityIdAndCode(1L, "CS")).thenReturn(computerScience)
        `when`(majorRepository.findByUniversityIdAndCode(1L, "MATH")).thenReturn(mathematics)
        `when`(memberRepository.findByEmail("test@tokyo.ac.jp")).thenReturn(testMember)
        `when`(memberRepository.findByEmail("empty@tokyo.ac.jp")).thenReturn(emptyMember)
        `when`(boardRepository.findByUniversityIdAndCode(1L, "general")).thenReturn(generalBoard)
        `when`(boardRepository.findByUniversityIdAndCode(1L, "free")).thenReturn(freeBoard)
        `when`(semesterRepository.findByUniversityIdAndAcademicYearAndTerm(1L, 2026, SemesterTerm.SPRING)).thenReturn(semester)

        stubExistingLectureLookups(semester.id, lectures)
        stubExistingScheduleLookups(lectures)
        `when`(timetableRepository.findByMemberIdAndSemesterId(2L, 4L)).thenReturn(timetable)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(40L, 5L)).thenReturn(true)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(40L, 6L)).thenReturn(true)

        `when`(postRepository.findByBoardIdAndTitle(20L, "Welcome to MiruMiru")).thenReturn(generalPost)
        `when`(postRepository.findByBoardIdAndTitle(21L, "Best lunch near campus?")).thenReturn(freePost)
        `when`(postImageRepository.findByPostIdAndDisplayOrder(50L, 0)).thenReturn(generalPostImage)
        `when`(postAnonymousMappingRepository.findByPostIdAndMemberId(51L, 2L)).thenReturn(freePostAnonForAuthor)
        `when`(postAnonymousMappingRepository.findByPostIdAndMemberId(51L, 3L)).thenReturn(freePostAnonForEmptyMember)
        `when`(postLikeRepository.findByPostIdAndMemberId(51L, 3L)).thenReturn(freePostLike)
        `when`(commentRepository.findAllByPostIdOrderByCreatedAtAsc(51L)).thenReturn(listOf(rootComment, childComment))

        initializer.run(DefaultApplicationArguments())

        verify(universityRepository, never()).save(any(University::class.java))
        verify(majorRepository, never()).save(any(Major::class.java))
        verify(memberRepository, never()).save(any(Member::class.java))
        verify(boardRepository, never()).save(any(Board::class.java))
        verify(postRepository, never()).save(any(Post::class.java))
        verify(postImageRepository, never()).save(any(PostImage::class.java))
        verify(postAnonymousMappingRepository, never()).save(any(PostAnonymousMapping::class.java))
        verify(postLikeRepository, never()).save(any(PostLike::class.java))
        verify(commentRepository, never()).save(any(Comment::class.java))
        verify(semesterRepository, never()).save(any(Semester::class.java))
        verify(lectureRepository, never()).save(any(Lecture::class.java))
        verify(lectureScheduleRepository, never()).save(any(LectureSchedule::class.java))
        verify(timetableRepository, never()).save(any(Timetable::class.java))
        verify(timetableLectureRepository, never()).save(any(TimetableLecture::class.java))
    }

    private fun university(): University {
        return University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp")
    }

    private fun major(id: Long, university: University, code: String, name: String): Major {
        return Major(id = id, university = university, code = code, name = name)
    }

    private fun member(id: Long, university: University, major: Major, email: String, nickname: String): Member {
        return Member(
            id = id,
            university = university,
            major = major,
            email = email,
            password = "encoded-password",
            nickname = nickname
        )
    }

    private fun board(id: Long, university: University, code: String, name: String, isAnonymousAllowed: Boolean): Board {
        return Board(
            id = id,
            university = university,
            code = code,
            name = name,
            isAnonymousAllowed = isAnonymousAllowed
        )
    }

    private fun semester(university: University): Semester {
        return Semester(id = 4L, university = university, academicYear = 2026, term = SemesterTerm.SPRING)
    }

    private fun post(
        id: Long,
        board: Board,
        member: Member,
        title: String,
        isAnonymous: Boolean,
        likeCount: Int,
        commentCount: Int
    ): Post {
        return Post(
            id = id,
            board = board,
            member = member,
            title = title,
            content = "seed-content",
            isAnonymous = isAnonymous,
            likeCount = likeCount,
            commentCount = commentCount,
            isDeleted = false
        )
    }

    private fun lectureSeeds(semester: Semester, computerScience: Major, mathematics: Major): List<Lecture> {
        return listOf(
            Lecture(id = 5L, semester = semester, major = computerScience, code = "CS101", name = "Introduction to Computer Science", professor = "Prof. Akiyama", credit = 3),
            Lecture(id = 6L, semester = semester, major = null, code = "MATH201", name = "Linear Algebra", professor = "Prof. Sato", credit = 2),
            Lecture(id = 7L, semester = semester, major = null, code = "PHYS301", name = "Classical Mechanics", professor = "Prof. Tanaka", credit = 3),
            Lecture(id = 8L, semester = semester, major = null, code = "HIST110", name = "World History", professor = "Prof. Nakamura", credit = 2),
            Lecture(id = 9L, semester = semester, major = null, code = "CHEM105", name = "General Chemistry", professor = "Prof. Suzuki", credit = 3),
            Lecture(id = 10L, semester = semester, major = null, code = "ECON210", name = "Microeconomics", professor = "Prof. Kobayashi", credit = 2),
            Lecture(id = 11L, semester = semester, major = null, code = "ENG220", name = "Academic English", professor = "Prof. Wilson", credit = 2),
            Lecture(id = 12L, semester = semester, major = mathematics, code = "STAT230", name = "Statistics Fundamentals", professor = "Prof. Yamamoto", credit = 2),
            Lecture(id = 13L, semester = semester, major = null, code = "ART150", name = "Visual Arts Appreciation", professor = "Prof. Lee", credit = 2)
        )
    }

    private fun stubMissingLectureLookups(semesterId: Long) {
        listOf("CS101", "MATH201", "PHYS301", "HIST110", "CHEM105", "ECON210", "ENG220", "STAT230", "ART150")
            .forEach { code ->
                `when`(lectureRepository.findBySemesterIdAndCode(semesterId, code)).thenReturn(null)
            }
    }

    private fun stubExistingLectureLookups(semesterId: Long, lectures: List<Lecture>) {
        lectures.forEach { lecture ->
            `when`(lectureRepository.findBySemesterIdAndCode(semesterId, lecture.code)).thenReturn(lecture)
        }
    }

    private fun stubMissingScheduleLookups() {
        listOf(
            Triple(5L, DayOfWeek.MONDAY, LocalTime.of(9, 0) to LocalTime.of(10, 30)),
            Triple(5L, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0) to LocalTime.of(10, 30)),
            Triple(6L, DayOfWeek.TUESDAY, LocalTime.of(13, 0) to LocalTime.of(14, 30)),
            Triple(7L, DayOfWeek.MONDAY, LocalTime.of(10, 0) to LocalTime.of(11, 0)),
            Triple(8L, DayOfWeek.TUESDAY, LocalTime.of(9, 0) to LocalTime.of(10, 30)),
            Triple(9L, DayOfWeek.WEDNESDAY, LocalTime.of(14, 0) to LocalTime.of(15, 30)),
            Triple(10L, DayOfWeek.THURSDAY, LocalTime.of(10, 0) to LocalTime.of(11, 30)),
            Triple(11L, DayOfWeek.THURSDAY, LocalTime.of(13, 0) to LocalTime.of(14, 30)),
            Triple(12L, DayOfWeek.FRIDAY, LocalTime.of(9, 0) to LocalTime.of(10, 30)),
            Triple(13L, DayOfWeek.FRIDAY, LocalTime.of(11, 0) to LocalTime.of(12, 30))
        ).forEach { (lectureId, day, timeRange) ->
            `when`(
                lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(
                    lectureId,
                    day,
                    timeRange.first,
                    timeRange.second
                )
            ).thenReturn(null)
        }
    }

    private fun stubExistingScheduleLookups(lectures: List<Lecture>) {
        val schedules = listOf(
            LectureSchedule(id = 100L, lecture = lectures[0], dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), location = "Engineering Hall 101"),
            LectureSchedule(id = 101L, lecture = lectures[0], dayOfWeek = DayOfWeek.WEDNESDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), location = "Engineering Hall 101"),
            LectureSchedule(id = 102L, lecture = lectures[1], dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(14, 30), location = "Science Building 202"),
            LectureSchedule(id = 103L, lecture = lectures[2], dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0), location = "Physics Hall 303"),
            LectureSchedule(id = 104L, lecture = lectures[3], dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), location = "Humanities Hall 105"),
            LectureSchedule(id = 105L, lecture = lectures[4], dayOfWeek = DayOfWeek.WEDNESDAY, startTime = LocalTime.of(14, 0), endTime = LocalTime.of(15, 30), location = "Science Building 110"),
            LectureSchedule(id = 106L, lecture = lectures[5], dayOfWeek = DayOfWeek.THURSDAY, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 30), location = "Social Sciences 204"),
            LectureSchedule(id = 107L, lecture = lectures[6], dayOfWeek = DayOfWeek.THURSDAY, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(14, 30), location = "Language Center 301"),
            LectureSchedule(id = 108L, lecture = lectures[7], dayOfWeek = DayOfWeek.FRIDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), location = "Science Building 305"),
            LectureSchedule(id = 109L, lecture = lectures[8], dayOfWeek = DayOfWeek.FRIDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 30), location = "Arts Hall 201")
        )

        schedules.forEach { schedule ->
            `when`(
                lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(
                    schedule.lecture.id,
                    schedule.dayOfWeek,
                    schedule.startTime,
                    schedule.endTime
                )
            ).thenReturn(schedule)
        }
    }
}
