package com.example.api.timetable

import com.example.ApiApplication
import com.example.application.security.TokenProvider
import com.example.domain.member.MemberRepository
import com.example.domain.semester.SemesterRepository
import com.example.domain.semester.SemesterTerm
import com.example.domain.timetable.TimetableRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

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
    @Autowired private val memberRepository: MemberRepository,
    @Autowired private val semesterRepository: SemesterRepository,
    @Autowired private val timetableRepository: TimetableRepository,
    @Autowired private val tokenProvider: TokenProvider
) {
    private lateinit var accessToken: String
    private var semesterId: Long = 0L
    private var timetableId: Long = 0L

    @BeforeEach
    fun setUp() {
        val member = memberRepository.findByEmail("test@tokyo.ac.jp")!!
        accessToken = tokenProvider.createAccessToken(member.id, member.role)
        val semester = semesterRepository.findByUniversityIdAndAcademicYearAndTerm(
            universityId = member.university.id,
            academicYear = 2026,
            term = SemesterTerm.SPRING
        )!!
        semesterId = semester.id
        timetableId = timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)!!.id
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
    fun `get lectures returns not found for inaccessible semester`() {
        val response = mockMvc.get("/api/v1/semesters/999/lectures") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(404, response.status)
    }
}
