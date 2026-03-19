import XCTest
@testable import MiruMiru

@MainActor
final class HomeViewModelTests: XCTestCase {
    func testLoadSuccessUsesFirstSemesterAndBuildsRows() async {
        let client = MockHomeClient()
        client.profileResult = .success(sampleProfile)
        client.semestersResult = .success([
            HomeSemester(id: 20261, academicYear: 2026, term: "SPRING"),
            HomeSemester(id: 20252, academicYear: 2025, term: "FALL")
        ])
        client.timetableResult = .success(
            HomeTimetable(
                timetableId: 88,
                semester: HomeSemester(id: 20261, academicYear: 2026, term: "SPRING"),
                lectures: [
                    HomeLecture(
                        id: 1,
                        code: "CS101",
                        name: "Computer Science",
                        professor: "Prof. Ito",
                        schedules: [
                            HomeLectureSchedule(dayOfWeek: "WEDNESDAY", startTime: "09:00", endTime: "10:30", location: "Engineering Hall 101")
                        ]
                    )
                ]
            )
        )

        let viewModel = HomeViewModel(
            client: client,
            nowProvider: fixedDate("2026-03-18T09:15:00+09:00"),
            calendar: seoulCalendar
        )

        await viewModel.loadIfNeeded()

        guard case let .loaded(content) = viewModel.state else {
            return XCTFail("Expected loaded state")
        }

        XCTAssertEqual(client.requestedSemesterId, 20261)
        XCTAssertEqual(content.profile.nickname, "test-user")
        XCTAssertEqual(content.semesterTitle, "2026 Spring")
        XCTAssertEqual(content.todayClasses.count, 1)
        XCTAssertEqual(content.todayClasses.first?.badge, .now)
    }

    func testLoadWithoutSemestersShowsNoSemesterEmptyState() async {
        let client = MockHomeClient()
        client.profileResult = .success(sampleProfile)
        client.semestersResult = .success([])

        let viewModel = HomeViewModel(client: client)
        await viewModel.loadIfNeeded()

        guard case let .empty(content) = viewModel.state else {
            return XCTFail("Expected empty state")
        }
        XCTAssertEqual(content.state, .noSemester)
    }

    func testLoadWithoutTimetableShowsNoTimetableEmptyState() async {
        let client = MockHomeClient()
        client.profileResult = .success(sampleProfile)
        client.semestersResult = .success([HomeSemester(id: 20261, academicYear: 2026, term: "SPRING")])
        client.timetableResult = .success(
            HomeTimetable(
                timetableId: nil,
                semester: HomeSemester(id: 20261, academicYear: 2026, term: "SPRING"),
                lectures: []
            )
        )

        let viewModel = HomeViewModel(client: client)
        await viewModel.loadIfNeeded()

        guard case let .empty(content) = viewModel.state else {
            return XCTFail("Expected empty state")
        }
        XCTAssertEqual(content.state, .noTimetable)
    }

    func testLoadWithoutTodayClassesShowsNoClassesEmptyState() async {
        let client = MockHomeClient()
        client.profileResult = .success(sampleProfile)
        client.semestersResult = .success([HomeSemester(id: 20261, academicYear: 2026, term: "SPRING")])
        client.timetableResult = .success(
            HomeTimetable(
                timetableId: 7,
                semester: HomeSemester(id: 20261, academicYear: 2026, term: "SPRING"),
                lectures: [
                    HomeLecture(
                        id: 1,
                        code: "CS101",
                        name: "Computer Science",
                        professor: "Prof. Ito",
                        schedules: [
                            HomeLectureSchedule(dayOfWeek: "MONDAY", startTime: "09:00", endTime: "10:30", location: "Engineering Hall 101")
                        ]
                    )
                ]
            )
        )

        let viewModel = HomeViewModel(
            client: client,
            nowProvider: fixedDate("2026-03-18T09:15:00+09:00"),
            calendar: seoulCalendar
        )
        await viewModel.loadIfNeeded()

        guard case let .empty(content) = viewModel.state else {
            return XCTFail("Expected empty state")
        }
        XCTAssertEqual(content.state, .noClassesToday)
    }

    func testUnauthorizedMapsToInvalidSessionFailure() async {
        let client = MockHomeClient()
        client.profileResult = .failure(HomeClientError.invalidSession)

        let viewModel = HomeViewModel(client: client)
        await viewModel.loadIfNeeded()

        XCTAssertEqual(viewModel.state, .failed(.invalidSession))
        XCTAssertTrue(viewModel.invalidateStateIfNeeded())
    }

    func testTodayClassRowsAssignNowAndNextBadges() {
        let timetable = HomeTimetable(
            timetableId: 1,
            semester: HomeSemester(id: 20261, academicYear: 2026, term: "SPRING"),
            lectures: [
                HomeLecture(
                    id: 1,
                    code: "CS101",
                    name: "Computer Science",
                    professor: "Prof. Ito",
                    schedules: [
                        HomeLectureSchedule(dayOfWeek: "WEDNESDAY", startTime: "09:00", endTime: "10:30", location: "Engineering Hall 101")
                    ]
                ),
                HomeLecture(
                    id: 2,
                    code: "MATH201",
                    name: "Applied Mathematics",
                    professor: "Prof. Sakamoto",
                    schedules: [
                        HomeLectureSchedule(dayOfWeek: "WEDNESDAY", startTime: "13:00", endTime: "14:30", location: "Science 202")
                    ]
                )
            ]
        )

        let rows = HomeViewModel.makeTodayClassRows(
            from: timetable,
            now: fixedDate("2026-03-18T09:15:00+09:00")(),
            calendar: seoulCalendar
        )

        XCTAssertEqual(rows.count, 2)
        XCTAssertEqual(rows[0].badge, .now)
        XCTAssertEqual(rows[1].badge, .next)
    }

    private var sampleProfile: HomeMemberProfile {
        HomeMemberProfile(
            memberId: 1,
            email: "test@tokyo.ac.jp",
            nickname: "test-user",
            universityName: "The University of Tokyo",
            universityEmailDomain: "tokyo.ac.jp",
            majorCode: "CS",
            majorName: "Computer Science"
        )
    }

    private var seoulCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return calendar
    }

    private func fixedDate(_ value: String) -> @Sendable () -> Date {
        {
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime]
            return formatter.date(from: value)!
        }
    }
}
