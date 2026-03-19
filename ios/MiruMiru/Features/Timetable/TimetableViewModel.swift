import Foundation

@MainActor
final class TimetableViewModel: ObservableObject {
    @Published private(set) var screenState: TimetableScreenState = .loading
    @Published private(set) var semesters: [TimetableSemester] = []
    @Published private(set) var selectedSemesterId: Int64?
    @Published private(set) var catalogState: TimetableCatalogState = .idle
    @Published private(set) var actionMessage: String?
    @Published private(set) var catalogActionMessage: String?

    private let client: TimetableClientProtocol
    private var hasLoaded = false
    private var memberContext: TimetableMemberContext?
    private var currentTimetable: TimetableDetail?
    private var currentHourRange = TimetableHourRange(startHour: 9, endHour: 18)
    private var lectureCatalogCache: [Int64: [TimetableLectureItem]] = [:]

    enum TimetableActionOrigin {
        case screen
        case catalog
    }

    init(client: TimetableClientProtocol) {
        self.client = client
    }

    func loadIfNeeded() async {
        guard hasLoaded == false else { return }
        hasLoaded = true
        await loadInitialData()
    }

    func reload() async {
        await loadCurrentSemester(forceReloadCatalog: false)
    }

    func selectSemester(_ semesterId: Int64) async {
        guard selectedSemesterId != semesterId else { return }
        selectedSemesterId = semesterId
        actionMessage = nil
        clearCatalogActionMessage()
        catalogState = .idle
        await loadCurrentSemester(forceReloadCatalog: false)
    }

    func loadCatalogIfNeeded(forceRefresh: Bool = false) async {
        guard let semesterId = selectedSemesterId else { return }

        if forceRefresh == false, let cached = lectureCatalogCache[semesterId] {
            catalogState = .loaded(cached)
            return
        }

        catalogState = .loading

        do {
            let catalog = try await client.fetchLectureCatalog(semesterId: semesterId)
            lectureCatalogCache[semesterId] = catalog
            catalogState = .loaded(catalog)
        } catch let error as TimetableClientError {
            let failure = Self.map(error)
            if failure == .invalidSession {
                screenState = .failed(.invalidSession)
            } else {
                catalogState = .failed(failure)
            }
        } catch {
            catalogState = .failed(.unexpected)
        }
    }

    func filteredCatalog(
        query: String,
        filter: TimetableCatalogFilter
    ) -> [TimetableLectureItem] {
        guard case let .loaded(items) = catalogState else { return [] }

        let filteredByCategory = items.filter { item in
            switch filter {
            case .all:
                return true
            case .major:
                return item.major?.code == memberContext?.majorCode
            case .general:
                return item.major == nil
            }
        }

        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.isEmpty == false else {
            return filteredByCategory
        }

        return filteredByCategory.filter { item in
            item.name.localizedCaseInsensitiveContains(trimmed)
                || item.professor.localizedCaseInsensitiveContains(trimmed)
                || item.code.localizedCaseInsensitiveContains(trimmed)
        }
    }

    func isLectureAdded(_ lectureId: Int64) -> Bool {
        currentTimetable?.lectures.contains(where: { $0.id == lectureId }) == true
    }

    func addLecture(_ lectureId: Int64, origin: TimetableActionOrigin = .screen) async {
        guard let semesterId = selectedSemesterId else { return }
        actionMessage = nil
        clearCatalogActionMessage()

        do {
            try await client.addLecture(semesterId: semesterId, lectureId: lectureId)
            await loadCurrentSemester(forceReloadCatalog: false)
        } catch let error as TimetableClientError {
            handleActionError(error, origin: origin)
        } catch {
            let failure = TimetableFailure.unexpected
            setActionMessage(failure.message, origin: origin)
        }
    }

    func removeLecture(_ lectureId: Int64, origin: TimetableActionOrigin = .screen) async {
        guard let semesterId = selectedSemesterId else { return }
        actionMessage = nil
        clearCatalogActionMessage()

        do {
            try await client.removeLecture(semesterId: semesterId, lectureId: lectureId)
            await loadCurrentSemester(forceReloadCatalog: false)
        } catch let error as TimetableClientError {
            handleActionError(error, origin: origin)
        } catch {
            setActionMessage(TimetableFailure.unexpected.message, origin: origin)
        }
    }

    func invalidateStateIfNeeded() -> Bool {
        if case .failed(.invalidSession) = screenState {
            return true
        }
        return false
    }

    func clearActionMessage() {
        actionMessage = nil
    }

    func clearCatalogActionMessage() {
        catalogActionMessage = nil
    }

    func clearCatalogFailure() {
        if case .failed = catalogState {
            catalogState = .idle
        }
    }

    private func handleActionError(_ error: TimetableClientError, origin: TimetableActionOrigin = .screen) {
        let failure = Self.map(error)
        if failure == .invalidSession {
            screenState = .failed(.invalidSession)
            return
        }
        setActionMessage(failure.message, origin: origin)
    }

    private func setActionMessage(_ message: String, origin: TimetableActionOrigin) {
        switch origin {
        case .screen:
            actionMessage = message
        case .catalog:
            catalogActionMessage = message
        }
    }

    private func loadInitialData() async {
        screenState = .loading

        do {
            async let memberTask = client.fetchMemberContext()
            async let semestersTask = client.fetchSemesters()

            let (memberContext, semesters) = try await (memberTask, semestersTask)
            self.memberContext = memberContext
            self.semesters = semesters

            guard let firstSemester = semesters.first else {
                currentTimetable = nil
                currentHourRange = TimetableHourRange(startHour: 9, endHour: 18)
                screenState = .empty(
                    TimetableEmptyContent(
                        memberContext: memberContext,
                        selectedSemester: nil,
                        hourRange: currentHourRange,
                        state: .noSemesters
                    )
                )
                return
            }

            if selectedSemesterId == nil {
                selectedSemesterId = firstSemester.id
            }

            await loadCurrentSemester(forceReloadCatalog: false)
        } catch let error as TimetableClientError {
            screenState = .failed(Self.map(error))
        } catch {
            screenState = .failed(.unexpected)
        }
    }

    private func loadCurrentSemester(forceReloadCatalog: Bool) async {
        guard let semesterId = selectedSemesterId else {
            screenState = .empty(
                TimetableEmptyContent(
                    memberContext: memberContext,
                    selectedSemester: nil,
                    hourRange: TimetableHourRange(startHour: 9, endHour: 18),
                    state: .noSemesters
                )
            )
            return
        }

        if memberContext == nil || semesters.isEmpty {
            await loadInitialData()
            return
        }

        screenState = .loading

        do {
            let timetable = try await client.fetchMyTimetable(semesterId: semesterId)
            currentTimetable = timetable
            currentHourRange = Self.hourRange(from: timetable.lectures)

            let selectedSemester = semesters.first(where: { $0.id == semesterId }) ?? timetable.semester
            let addedLectureIds = Set(timetable.lectures.map(\.id))
            let blocks = Self.makeBlocks(from: timetable.lectures)

            if blocks.isEmpty {
                screenState = .empty(
                    TimetableEmptyContent(
                        memberContext: memberContext,
                        selectedSemester: selectedSemester,
                        hourRange: currentHourRange,
                        state: .noLectures
                    )
                )
            } else {
                screenState = .loaded(
                    TimetableLoadedContent(
                        memberContext: memberContext!,
                        selectedSemester: selectedSemester,
                        timetableId: timetable.timetableId,
                        lectures: timetable.lectures,
                        blocks: blocks,
                        addedLectureIds: addedLectureIds,
                        hourRange: currentHourRange
                    )
                )
            }

            if forceReloadCatalog {
                await loadCatalogIfNeeded(forceRefresh: true)
            }
        } catch let error as TimetableClientError {
            screenState = .failed(Self.map(error))
        } catch {
            screenState = .failed(.unexpected)
        }
    }

    static func makeBlocks(from lectures: [TimetableLectureItem]) -> [TimetableGridBlock] {
        lectures.flatMap { lecture in
            lecture.schedules.compactMap { schedule in
                guard let dayIndex = dayIndex(for: schedule.dayOfWeek),
                      let startMinutes = minutes(from: schedule.startTime),
                      let endMinutes = minutes(from: schedule.endTime),
                      endMinutes > startMinutes else {
                    return nil
                }

                return TimetableGridBlock(
                    id: "\(lecture.id)-\(schedule.dayOfWeek)-\(schedule.startTime)",
                    lectureId: lecture.id,
                    title: lecture.name,
                    professor: lecture.professor,
                    location: schedule.location,
                    dayIndex: dayIndex,
                    startMinutes: startMinutes,
                    endMinutes: endMinutes,
                    accentIndex: accentIndex(for: lecture.id)
                )
            }
        }
        .sorted {
            if $0.dayIndex == $1.dayIndex {
                return $0.startMinutes < $1.startMinutes
            }
            return $0.dayIndex < $1.dayIndex
        }
    }

    static func hourRange(from lectures: [TimetableLectureItem]) -> TimetableHourRange {
        let scheduleBounds: [(Int, Int)] = lectures
            .flatMap(\.schedules)
            .compactMap { schedule in
                guard let start = minutes(from: schedule.startTime),
                      let end = minutes(from: schedule.endTime) else {
                    return nil
                }
                return (start, end)
            }

        guard let earliest = scheduleBounds.map({ $0.0 }).min(),
              let latest = scheduleBounds.map({ $0.1 }).max() else {
            return TimetableHourRange(startHour: 9, endHour: 18)
        }

        let startHour = min(9, earliest / 60)
        let endHour = max(18, Int(ceil(Double(latest) / 60.0)))
        return TimetableHourRange(startHour: startHour, endHour: endHour)
    }

    static func accentIndex(for lectureId: Int64) -> Int {
        let paletteCount = 6
        return Int(abs(lectureId) % Int64(paletteCount))
    }

    static func minutes(from time: String) -> Int? {
        let parts = time.split(separator: ":")
        guard parts.count == 2,
              let hour = Int(parts[0]),
              let minute = Int(parts[1]) else {
            return nil
        }
        return (hour * 60) + minute
    }

    static func dayIndex(for dayOfWeek: String) -> Int? {
        switch dayOfWeek.uppercased() {
        case "MONDAY":
            return 0
        case "TUESDAY":
            return 1
        case "WEDNESDAY":
            return 2
        case "THURSDAY":
            return 3
        case "FRIDAY":
            return 4
        default:
            return nil
        }
    }

    private static func map(_ error: TimetableClientError) -> TimetableFailure {
        switch error {
        case .invalidSession:
            return .invalidSession
        case .duplicateLecture:
            return .duplicateLecture
        case .timeConflict:
            return .timeConflict
        case .lectureNotFound:
            return .lectureNotFound
        case .network:
            return .network
        case .unexpected:
            return .unexpected
        }
    }
}
