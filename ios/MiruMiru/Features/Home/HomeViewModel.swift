import Foundation

@MainActor
final class HomeViewModel: ObservableObject {
    @Published private(set) var state: HomeViewState = .loading

    private let client: HomeClientProtocol
    private let nowProvider: @Sendable () -> Date
    private let calendar: Calendar
    private var hasLoaded = false

    init(
        client: HomeClientProtocol,
        nowProvider: @escaping @Sendable () -> Date = Date.init,
        calendar: Calendar = .current
    ) {
        self.client = client
        self.nowProvider = nowProvider
        self.calendar = calendar
    }

    func loadIfNeeded() async {
        guard hasLoaded == false else { return }
        hasLoaded = true
        await load()
    }

    func reload() async {
        await load()
    }

    func invalidateStateIfNeeded() -> Bool {
        if case .failed(.invalidSession) = state {
            return true
        }
        return false
    }

    func hotPostsSnapshotForSync() -> [HotPostSummary]? {
        switch state {
        case let .loaded(content):
            return content.trendingPosts
        case let .empty(content):
            return content.trendingPosts
        default:
            return nil
        }
    }

    private func load() async {
        state = .loading

        do {
            let profileTask = Task { try await client.fetchProfile() }
            let semestersTask = Task { try await client.fetchSemesters() }
            let hotPostsTask = Task { try await client.fetchHotPosts() }

            let profile = try await profileTask.value
            let semesters = try await semestersTask.value
            let trendingPosts = try await resolveTrendingPosts(from: hotPostsTask)

            guard let semester = semesters.first else {
                state = .empty(
                    HomeEmptyContent(
                        profile: profile,
                        semesterTitle: nil,
                        state: .noSemester,
                        trendingPosts: trendingPosts
                    )
                )
                return
            }

            let timetable = try await client.fetchTimetable(semesterId: semester.id)

            guard timetable.timetableId != nil else {
                state = .empty(
                    HomeEmptyContent(
                        profile: profile,
                        semesterTitle: semester.titleText,
                        state: .noTimetable,
                        trendingPosts: trendingPosts
                    )
                )
                return
            }

            let todayClasses = Self.makeTodayClassRows(
                from: timetable,
                now: nowProvider(),
                calendar: calendar
            )

            guard todayClasses.isEmpty == false else {
                state = .empty(
                    HomeEmptyContent(
                        profile: profile,
                        semesterTitle: semester.titleText,
                        state: .noClassesToday,
                        trendingPosts: trendingPosts
                    )
                )
                return
            }

            state = .loaded(
                HomeLoadedContent(
                    profile: profile,
                    semesterTitle: semester.titleText,
                    todayClasses: todayClasses,
                    trendingPosts: trendingPosts
                )
            )
        } catch let error as HomeClientError {
            state = .failed(Self.map(error))
        } catch {
            state = .failed(.unexpected)
        }
    }

    private func resolveTrendingPosts(
        from task: Task<[HotPostSummary], Error>
    ) async throws -> [HotPostSummary] {
        do {
            return try await task.value
        } catch let error as HomeClientError {
            if error == .invalidSession {
                throw error
            }
            return []
        } catch {
            return []
        }
    }

    static func weekdayKey(for date: Date, calendar: Calendar) -> String {
        switch calendar.component(.weekday, from: date) {
        case 1: "SUNDAY"
        case 2: "MONDAY"
        case 3: "TUESDAY"
        case 4: "WEDNESDAY"
        case 5: "THURSDAY"
        case 6: "FRIDAY"
        default: "SATURDAY"
        }
    }

    static func makeTodayClassRows(
        from timetable: HomeTimetable,
        now: Date,
        calendar: Calendar
    ) -> [TodayClassRow] {
        let weekday = weekdayKey(for: now, calendar: calendar)

        let rows = timetable.lectures.flatMap { lecture in
            lecture.schedules.compactMap { schedule -> TodayClassRow? in
                guard schedule.dayOfWeek == weekday else { return nil }
                return TodayClassRow(
                    id: "\(lecture.id)-\(schedule.dayOfWeek)-\(schedule.startTime)",
                    lectureCode: lecture.code,
                    title: lecture.name,
                    professor: lecture.professor,
                    location: schedule.location,
                    startTime: schedule.startTime,
                    endTime: schedule.endTime,
                    badge: nil
                )
            }
        }
        .sorted { lhs, rhs in
            lhs.startTime < rhs.startTime
        }

        guard rows.isEmpty == false else { return [] }

        let currentMinutes = minutes(from: now, calendar: calendar)
        let nowIndex = rows.firstIndex { row in
            guard let start = minutes(from: row.startTime),
                  let end = minutes(from: row.endTime) else {
                return false
            }
            return start <= currentMinutes && currentMinutes < end
        }

        let nextIndex: Int? = {
            if let nowIndex {
                return rows.indices.dropFirst(nowIndex + 1).first
            }
            return rows.firstIndex { row in
                guard let start = minutes(from: row.startTime) else {
                    return false
                }
                return start > currentMinutes
            }
        }()

        return rows.enumerated().map { index, row in
            let badge: TodayClassBadge?
            if nowIndex == index {
                badge = .now
            } else if nextIndex == index {
                badge = .next
            } else {
                badge = nil
            }

            return TodayClassRow(
                id: row.id,
                lectureCode: row.lectureCode,
                title: row.title,
                professor: row.professor,
                location: row.location,
                startTime: row.startTime,
                endTime: row.endTime,
                badge: badge
            )
        }
    }

    static func minutes(from time: String) -> Int? {
        let parts = time.split(separator: ":")
        guard parts.count == 2,
              let hour = Int(parts[0]),
              let minute = Int(parts[1]) else {
            return nil
        }
        return hour * 60 + minute
    }

    private static func minutes(from date: Date, calendar: Calendar) -> Int {
        let components = calendar.dateComponents([.hour, .minute], from: date)
        return (components.hour ?? 0) * 60 + (components.minute ?? 0)
    }

    private static func map(_ error: HomeClientError) -> HomeFailure {
        switch error {
        case .invalidSession:
            return .invalidSession
        case .network:
            return .network
        case .unexpected:
            return .unexpected
        }
    }
}
