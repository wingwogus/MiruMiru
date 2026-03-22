package com.example.api.timetable

import com.example.ApiApplication
import com.example.application.security.TokenProvider
import com.example.domain.lecture.LectureRepository
import com.example.domain.member.MemberRepository
import com.example.domain.semester.SemesterRepository
import com.example.domain.semester.SemesterTerm
import com.example.domain.timetable.TimetableLectureRepository
import com.example.domain.timetable.TimetableRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(
    classes = [ApiApplication::class],
    properties = [
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password"
    ]
)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TimetableApiIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val lectureRepository: LectureRepository,
    @Autowired private val memberRepository: MemberRepository,
    @Autowired private val semesterRepository: SemesterRepository,
    @Autowired private val timetableLectureRepository: TimetableLectureRepository,
    @Autowired private val timetableRepository: TimetableRepository,
    @Autowired private val tokenProvider: TokenProvider
) {
    private lateinit var accessToken: String
    private lateinit var emptyAccessToken: String
    private var semesterId: Long = 0L
    private var timetableId: Long = 0L
    private var cs101Id: Long = 0L
    private var math201Id: Long = 0L
    private var phys301Id: Long = 0L

    @AfterEach
    fun tearDown() {
        val emptyMember = memberRepository.findByEmail("empty@tokyo.ac.jp") ?: return
        val semester = semesterRepository.findByUniversityIdAndAcademicYearAndTerm(
            universityId = emptyMember.university.id,
            academicYear = 2026,
            term = SemesterTerm.SPRING
        ) ?: return
        val timetable = timetableRepository.findByMemberIdAndSemesterId(emptyMember.id, semester.id) ?: return
        timetableLectureRepository.deleteAll(timetableLectureRepository.findAllByTimetableId(timetable.id))
        timetableRepository.delete(timetable)
    }

    @BeforeEach
    fun setUp() {
        val member = memberRepository.findByEmail("test@tokyo.ac.jp")!!
        val emptyMember = memberRepository.findByEmail("empty@tokyo.ac.jp")!!
        accessToken = tokenProvider.createAccessToken(member.id, member.role)
        emptyAccessToken = tokenProvider.createAccessToken(emptyMember.id, emptyMember.role)
        val semester = semesterRepository.findByUniversityIdAndAcademicYearAndTerm(
            universityId = member.university.id,
            academicYear = 2026,
            term = SemesterTerm.SPRING
        )!!
        semesterId = semester.id
        timetableId = timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)!!.id
        cs101Id = lectureRepository.findBySemesterIdAndCode(semester.id, "CS101")!!.id
        math201Id = lectureRepository.findBySemesterIdAndCode(semester.id, "MATH201")!!.id
        phys301Id = lectureRepository.findBySemesterIdAndCode(semester.id, "PHYS301")!!.id
    }

    @Test
    fun `get semesters returns authenticated members semesters`() {
        val response = mockMvc.get("/api/v1/semesters") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"academicYear\":2026"))
        assertTrue(response.contentAsString.contains("\"term\":\"SPRING\""))
    }

    @Test
    fun `get semester lectures returns seeded lecture catalog`() {
        val response = mockMvc.get("/api/v1/semesters/$semesterId/lectures") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"code\":\"CS101\""))
        assertTrue(response.contentAsString.contains("\"code\":\"MATH201\""))
        assertTrue(response.contentAsString.contains("\"name\":\"Computer Science\""))
        assertTrue(response.contentAsString.contains("\"major\":null"))
        assertTrue(response.contentAsString.contains("\"dayOfWeek\":\"MONDAY\""))
    }

    @Test
    fun `get my timetable returns seeded lectures for semester`() {
        val response = mockMvc.get("/api/v1/timetables/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            param("semesterId", semesterId.toString())
        }.andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"timetableId\":$timetableId"))
        assertTrue(response.contentAsString.contains("\"code\":\"CS101\""))
        assertTrue(response.contentAsString.contains("\"majorId\":"))
    }

    @Test
    fun `get my timetable returns empty response when timetable does not exist`() {
        val response = mockMvc.get("/api/v1/timetables/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            param("semesterId", semesterId.toString())
        }.andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"timetableId\":null"))
        assertTrue(response.contentAsString.contains("\"lectures\":[]"))
    }

    @Test
    fun `post add lecture lazily creates timetable for empty user`() {
        val response = mockMvc.post("/api/v1/timetables/me/lectures") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"semesterId":$semesterId,"lectureId":$cs101Id}"""
        }.andReturn().response

        assertEquals(200, response.status)

        val timetableResponse = mockMvc.get("/api/v1/timetables/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            param("semesterId", semesterId.toString())
        }.andReturn().response

        assertEquals(200, timetableResponse.status)
        assertTrue(timetableResponse.contentAsString.contains("\"code\":\"CS101\""))
        assertTrue(timetableResponse.contentAsString.contains("\"timetableId\":"))
        assertTrue(!timetableResponse.contentAsString.contains("\"timetableId\":null"))
    }

    @Test
    fun `post add lecture rejects duplicate lecture`() {
        val response = mockMvc.post("/api/v1/timetables/me/lectures") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"semesterId":$semesterId,"lectureId":$cs101Id}"""
        }.andReturn().response

        assertEquals(409, response.status)
        assertTrue(response.contentAsString.contains("TIMETABLE_002"))
    }

    @Test
    fun `post add lecture rejects time conflict`() {
        val addFirst = mockMvc.post("/api/v1/timetables/me/lectures") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"semesterId":$semesterId,"lectureId":$cs101Id}"""
        }.andReturn().response
        assertEquals(200, addFirst.status)

        val conflictResponse = mockMvc.post("/api/v1/timetables/me/lectures") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"semesterId":$semesterId,"lectureId":$phys301Id}"""
        }.andReturn().response

        assertEquals(409, conflictResponse.status)
        assertTrue(conflictResponse.contentAsString.contains("TIMETABLE_006"))
    }

    @Test
    fun `delete remove lecture removes it from timetable`() {
        val addResponse = mockMvc.post("/api/v1/timetables/me/lectures") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"semesterId":$semesterId,"lectureId":$math201Id}"""
        }.andReturn().response
        assertEquals(200, addResponse.status)

        val deleteResponse = mockMvc.delete("/api/v1/timetables/me/lectures/$math201Id") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            param("semesterId", semesterId.toString())
        }.andReturn().response

        assertEquals(200, deleteResponse.status)

        val timetableResponse = mockMvc.get("/api/v1/timetables/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            param("semesterId", semesterId.toString())
        }.andReturn().response

        assertEquals(200, timetableResponse.status)
        assertTrue(timetableResponse.contentAsString.contains("\"lectures\":[]"))
    }

    @Test
    fun `get lectures returns not found for inaccessible semester`() {
        val response = mockMvc.get("/api/v1/semesters/999/lectures") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(404, response.status)
    }
}
