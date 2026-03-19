import XCTest
@testable import MiruMiru

@MainActor
final class TimetableViewModelTests: XCTestCase {
    func testLoadUsesFirstSemesterAndBuildsBlocks() async {
        let client = MockTimetableClient()
        client.memberContextResult = .success(sampleMemberContext)
        client.semestersResult = .success(sampleSemesters)
        client.timetableResult = .success(sampleTimetable)

        let viewModel = TimetableViewModel(client: client)
        await viewModel.loadIfNeeded()

        guard case let .loaded(content) = viewModel.screenState else {
            return XCTFail("Expected loaded state")
        }

        XCTAssertEqual(client.requestedSemesterId, 20261)
        XCTAssertEqual(content.selectedSemester.id, 20261)
        XCTAssertEqual(content.blocks.count, 2)
        XCTAssertEqual(content.hourRange, TimetableHourRange(startHour: 9, endHour: 18))
        XCTAssertTrue(content.addedLectureIds.contains(101))
    }

    func testLoadWithoutSemestersShowsEmptyState() async {
        let client = MockTimetableClient()
        client.memberContextResult = .success(sampleMemberContext)
        client.semestersResult = .success([])

        let viewModel = TimetableViewModel(client: client)
        await viewModel.loadIfNeeded()

        guard case let .empty(content) = viewModel.screenState else {
            return XCTFail("Expected empty state")
        }

        XCTAssertEqual(content.state, .noSemesters)
    }

    func testEmptyTimetableShowsNoLecturesState() async {
        let client = MockTimetableClient()
        client.memberContextResult = .success(sampleMemberContext)
        client.semestersResult = .success(sampleSemesters)
        client.timetableResult = .success(
            TimetableDetail(
                timetableId: nil,
                semester: sampleSemesters[0],
                lectures: []
            )
        )

        let viewModel = TimetableViewModel(client: client)
        await viewModel.loadIfNeeded()

        guard case let .empty(content) = viewModel.screenState else {
            return XCTFail("Expected empty state")
        }

        XCTAssertEqual(content.state, .noLectures)
        XCTAssertEqual(content.selectedSemester?.id, 20261)
    }

    func testSelectSemesterReloadsTimetable() async {
        let client = MockTimetableClient()
        client.memberContextResult = .success(sampleMemberContext)
        client.semestersResult = .success(sampleSemesters)
        client.timetableResult = .success(sampleTimetable)

        let viewModel = TimetableViewModel(client: client)
        await viewModel.loadIfNeeded()
        client.timetableResult = .success(
            TimetableDetail(
                timetableId: 78,
                semester: sampleSemesters[1],
                lectures: []
            )
        )

        await viewModel.selectSemester(20252)

        XCTAssertEqual(client.requestedSemesterId, 20252)
    }

    func testCatalogLoadsLazilyAndCachesPerSemester() async {
        let client = MockTimetableClient()
        client.memberContextResult = .success(sampleMemberContext)
        client.semestersResult = .success(sampleSemesters)
        client.timetableResult = .success(sampleTimetable)
        client.catalogResult = .success(sampleCatalog)

        let viewModel = TimetableViewModel(client: client)
        await viewModel.loadIfNeeded()
        await viewModel.loadCatalogIfNeeded()
        await viewModel.loadCatalogIfNeeded()

        XCTAssertEqual(client.catalogRequestedSemesterIds, [20261])
    }

    func testAddLectureRefreshesTimetableAndCatalog() async {
        let client = MockTimetableClient()
        client.memberContextResult = .success(sampleMemberContext)
        client.semestersResult = .success(sampleSemesters)
        client.timetableResult = .success(sampleTimetable)
        client.catalogResult = .success(sampleCatalog)

        let viewModel = TimetableViewModel(client: client)
        await viewModel.loadIfNeeded()
        await viewModel.loadCatalogIfNeeded()
        await viewModel.addLecture(202)

        XCTAssertEqual(client.addedPayloads.first?.semesterId, 20261)
        XCTAssertEqual(client.addedPayloads.first?.lectureId, 202)
        XCTAssertEqual(client.catalogRequestedSemesterIds, [20261, 20261])
        XCTAssertEqual(client.requestedSemesterId, 20261)
    }

    func testMakeBlocksUsesMinuteAccurateOffsets() {
        let blocks = TimetableViewModel.makeBlocks(from: sampleTimetable.lectures)
        XCTAssertEqual(blocks.count, 2)
        XCTAssertEqual(blocks[0].dayIndex, 0)
        XCTAssertEqual(blocks[1].startMinutes, 640)
        XCTAssertEqual(blocks[1].endMinutes, 720)
    }

    private var sampleMemberContext: TimetableMemberContext {
        TimetableMemberContext(
            memberId: 1,
            nickname: "test-user",
            email: "test@tokyo.ac.jp",
            majorCode: "CS",
            majorName: "Computer Science"
        )
    }

    private var sampleSemesters: [TimetableSemester] {
        [
            TimetableSemester(id: 20261, academicYear: 2026, term: "SPRING"),
            TimetableSemester(id: 20252, academicYear: 2025, term: "FALL")
        ]
    }

    private var sampleTimetable: TimetableDetail {
        TimetableDetail(
            timetableId: 77,
            semester: sampleSemesters[0],
            lectures: [
                TimetableLectureItem(
                    id: 101,
                    code: "CS101",
                    name: "Introduction to Computer Science",
                    professor: "Prof. Ito",
                    credit: 3,
                    major: TimetableMajorSummary(majorId: 1, code: "CS", name: "Computer Science"),
                    schedules: [
                        TimetableLectureSchedule(dayOfWeek: "MONDAY", startTime: "09:00", endTime: "10:30", location: "Room 301")
                    ]
                ),
                TimetableLectureItem(
                    id: 202,
                    code: "STAT230",
                    name: "Statistics I",
                    professor: "Prof. Ahn",
                    credit: 2,
                    major: nil,
                    schedules: [
                        TimetableLectureSchedule(dayOfWeek: "THURSDAY", startTime: "10:40", endTime: "12:00", location: "PC-2")
                    ]
                )
            ]
        )
    }

    private var sampleCatalog: [TimetableLectureItem] {
        sampleTimetable.lectures
    }
}
