import Foundation

enum PreviewFactory {
    @MainActor
    static func makeSession(
        state: AuthSessionState,
        bannerMessage: String? = nil
    ) -> AppSession {
        AppSession(
            authClient: PreviewAuthClient(),
            tokenStore: PreviewTokenStore(session: state == .authenticated ? TokenPair(accessToken: "preview-access", refreshToken: "preview-refresh") : nil),
            initialState: state,
            initialBannerMessage: bannerMessage,
            hasBootstrapped: true
        )
    }
}

final class PreviewAuthClient: AuthClientProtocol, @unchecked Sendable {
    var loginTokens = TokenPair(accessToken: "preview-access", refreshToken: "preview-refresh")

    func login(email: String, password: String) async throws -> TokenPair {
        loginTokens
    }

    func reissue(accessToken: String, refreshToken: String) async throws -> TokenPair {
        loginTokens
    }

    func sendEmailCode(email: String) async throws {}
    func verifyEmailCode(email: String, code: String) async throws {}

    func fetchMajors(email: String) async throws -> [MajorOption] {
        [
            MajorOption(majorId: 1, code: "CS", name: "Computer Science")
        ]
    }

    func verifyNickname(nickname: String) async throws {}
    func signUp(email: String, password: String, nickname: String, majorId: Int64) async throws {}
    func validateRestoredSession(accessToken: String) async throws {}
}

final class PreviewTokenStore: TokenStore, @unchecked Sendable {
    var session: TokenPair?

    init(session: TokenPair? = nil) {
        self.session = session
    }

    func readSession() throws -> TokenPair? {
        session
    }

    func saveSession(_ session: TokenPair) throws {
        self.session = session
    }

    func clearSession() throws {
        session = nil
    }
}

@MainActor
final class PreviewSignupClient: SignupClientProtocol {
    func sendEmailCode(email: String) async throws {}
    func verifyEmailCode(email: String, code: String) async throws {}

    func fetchMajors(email: String) async throws -> [SignupMajorOption] {
        [
            SignupMajorOption(id: 1, code: "CS", name: "Computer Science"),
            SignupMajorOption(id: 2, code: "EE", name: "Electrical Engineering")
        ]
    }

    func verifyNickname(_ nickname: String) async throws {}
    func signUp(email: String, password: String, nickname: String, majorId: Int64) async throws {}
}

struct PreviewHomeClient: HomeClientProtocol {
    enum Scenario {
        case loaded
        case loadedLongTitle
        case noSemester
        case noTimetable
        case noClassesToday
        case networkFailure
    }

    let scenario: Scenario

    static func loaded() -> PreviewHomeClient {
        PreviewHomeClient(scenario: .loaded)
    }

    func fetchProfile() async throws -> HomeMemberProfile {
        PreviewHomeData.profile
    }

    func fetchSemesters() async throws -> [HomeSemester] {
        switch scenario {
        case .noSemester:
            return []
        default:
            return [PreviewHomeData.semester]
        }
    }

    func fetchTimetable(semesterId: Int64) async throws -> HomeTimetable {
        switch scenario {
        case .noTimetable:
            return HomeTimetable(
                timetableId: nil,
                semester: PreviewHomeData.semester,
                lectures: []
            )
        case .noClassesToday:
            return HomeTimetable(
                timetableId: 44,
                semester: PreviewHomeData.semester,
                lectures: PreviewHomeData.nonMatchingLectures
            )
        case .networkFailure:
            throw HomeClientError.network
        case .loadedLongTitle:
            return HomeTimetable(
                timetableId: 77,
                semester: PreviewHomeData.semester,
                lectures: PreviewHomeData.longTitleLectures
            )
        default:
            return HomeTimetable(
                timetableId: 55,
                semester: PreviewHomeData.semester,
                lectures: PreviewHomeData.lectures
            )
        }
    }

    func fetchHotPosts() async throws -> [HotPostSummary] {
        switch scenario {
        case .networkFailure:
            throw HomeClientError.network
        default:
            return PreviewBoardsData.hotPosts
        }
    }
}

struct PreviewTimetableClient: TimetableClientProtocol {
    enum Scenario {
        case loaded
        case loadedLongTitle
        case empty
        case conflict
    }

    let scenario: Scenario

    static func loaded() -> PreviewTimetableClient {
        PreviewTimetableClient(scenario: .loaded)
    }

    func fetchMemberContext() async throws -> TimetableMemberContext {
        PreviewTimetableData.memberContext
    }

    func fetchSemesters() async throws -> [TimetableSemester] {
        PreviewTimetableData.semesters
    }

    func fetchLectureCatalog(semesterId: Int64) async throws -> [TimetableLectureItem] {
        PreviewTimetableData.catalog
    }

    func fetchMyTimetable(semesterId: Int64) async throws -> TimetableDetail {
        switch scenario {
        case .empty:
            return TimetableDetail(
                timetableId: nil,
                semester: PreviewTimetableData.semesters[0],
                lectures: []
            )
        case .loadedLongTitle:
            return TimetableDetail(
                timetableId: 78,
                semester: PreviewTimetableData.semesters[0],
                lectures: PreviewTimetableData.longTitleLectures
            )
        default:
            return TimetableDetail(
                timetableId: 77,
                semester: PreviewTimetableData.semesters[0],
                lectures: PreviewTimetableData.currentLectures
            )
        }
    }

    func addLecture(semesterId: Int64, lectureId: Int64) async throws {
        if scenario == .conflict {
            throw TimetableClientError.timeConflict
        }
    }

    func removeLecture(semesterId: Int64, lectureId: Int64) async throws {}
}

struct PreviewBoardsClient: BoardsClientProtocol {
    enum Scenario {
        case loaded
        case empty
        case networkFailure
    }

    let scenario: Scenario

    func fetchBoards() async throws -> [BoardSummary] {
        switch scenario {
        case .empty:
            return []
        case .networkFailure:
            throw BoardsClientError.network
        case .loaded:
            return PreviewBoardsData.boards
        }
    }

    func fetchHotPosts() async throws -> [HotPostSummary] {
        switch scenario {
        case .empty:
            return []
        case .networkFailure:
            throw BoardsClientError.network
        case .loaded:
            return PreviewBoardsData.hotPosts
        }
    }

    func fetchBoardPosts(boardId: Int64) async throws -> [BoardPostSummary] {
        if scenario == .networkFailure {
            throw BoardsClientError.network
        }
        return PreviewBoardsData.boardPosts[boardId] ?? []
    }

    func fetchPostDetail(postId: Int64) async throws -> PostDetailContent {
        if scenario == .networkFailure {
            throw BoardsClientError.network
        }
        return PreviewBoardsData.postDetails[postId] ?? PreviewBoardsData.defaultPostDetail
    }

    func createPost(boardId: Int64, input: CreatePostInput) async throws -> Int64 {
        if scenario == .networkFailure {
            throw BoardsClientError.network
        }
        return 99001
    }

    func likePost(postId: Int64) async throws {
        if scenario == .networkFailure {
            throw BoardsClientError.network
        }
    }

    func unlikePost(postId: Int64) async throws {
        if scenario == .networkFailure {
            throw BoardsClientError.network
        }
    }

    func createComment(postId: Int64, input: CreateCommentInput) async throws -> Int64 {
        if scenario == .networkFailure {
            throw BoardsClientError.network
        }
        return 88001
    }

    func deleteComment(commentId: Int64) async throws {
        if scenario == .networkFailure {
            throw BoardsClientError.network
        }
    }

    func deletePost(postId: Int64) async throws {
        if scenario == .networkFailure {
            throw BoardsClientError.network
        }
    }
}

enum PreviewBoardsData {
    static let generalBoard = BoardSummary(
        id: 101,
        code: "general",
        name: "University Notices",
        isAnonymousAllowed: false
    )

    static let freeBoard = BoardSummary(
        id: 102,
        code: "free",
        name: "Free Board",
        isAnonymousAllowed: true
    )

    static let clubBoard = BoardSummary(
        id: 103,
        code: "club",
        name: "Club Recruitment",
        isAnonymousAllowed: false
    )

    static let boards = [
        generalBoard,
        freeBoard,
        clubBoard
    ]

    static let hotPosts = [
        HotPostSummary(
            id: 2001,
            boardId: freeBoard.id,
            boardCode: freeBoard.code,
            boardName: freeBoard.name,
            title: "Does anyone know the scope for next week's Econ exam?",
            authorDisplayName: "Anonymous 1",
            isAnonymous: true,
            likeCount: 42,
            commentCount: 15,
            createdAt: "2026-03-22T20:50:00"
        ),
        HotPostSummary(
            id: 2002,
            boardId: generalBoard.id,
            boardCode: generalBoard.code,
            boardName: generalBoard.name,
            title: "Has anyone tried the new ramen at the cafeteria?",
            authorDisplayName: "Kenta Tanaka",
            isAnonymous: false,
            likeCount: 18,
            commentCount: 6,
            createdAt: "2026-03-22T20:28:00"
        )
    ]

    static let boardPosts: [Int64: [BoardPostSummary]] = [
        freeBoard.id: [
            BoardPostSummary(
                id: 2001,
                title: "Anyone want to study together for the midterms?",
                authorDisplayName: "Anonymous",
                isAnonymous: true,
                likeCount: 12,
                commentCount: 4,
                createdAt: "2026-03-22T20:58:00"
            ),
            BoardPostSummary(
                id: 2003,
                title: "Looking for lunch buddies around Engineering Hall",
                authorDisplayName: "Anonymous 2",
                isAnonymous: true,
                likeCount: 7,
                commentCount: 3,
                createdAt: "2026-03-22T18:25:00"
            )
        ],
        generalBoard.id: [
            BoardPostSummary(
                id: 2002,
                title: "Library opening hours changed for exam week",
                authorDisplayName: "Student Affairs",
                isAnonymous: false,
                likeCount: 9,
                commentCount: 1,
                createdAt: "2026-03-22T19:40:00"
            )
        ]
    ]

    static let defaultPostDetail = PostDetailContent(
        postId: 2001,
        boardId: freeBoard.id,
        boardCode: freeBoard.code,
        boardName: freeBoard.name,
        title: "Anyone want to study together for the midterms?",
        content: "I'm looking for a group to study advanced calculus. We can meet at the central library or a cafe near campus.",
        authorMemberId: 11,
        authorDisplayName: "Anonymous",
        isAnonymous: true,
        isMine: false,
        isLikedByMe: false,
        likeCount: 12,
        commentCount: 4,
        comments: [],
        images: [],
        createdAt: "2026-03-22T20:58:00",
        updatedAt: "2026-03-22T20:58:00"
    )

    static let postDetails: [Int64: PostDetailContent] = [
        2001: PostDetailContent(
            postId: 2001,
            boardId: freeBoard.id,
            boardCode: freeBoard.code,
            boardName: freeBoard.name,
            title: "Anyone want to study together for the midterms?",
            content: "I'm looking for a group to study advanced calculus. We can meet at the central library or a cafe near campus. I'm struggling a bit with the vector fields section and would love to collaborate.",
            authorMemberId: 11,
            authorDisplayName: "Anonymous",
            isAnonymous: true,
            isMine: false,
            isLikedByMe: false,
            likeCount: 12,
            commentCount: 4,
            comments: [
                PostCommentItem(
                    commentId: 1,
                    parentId: nil,
                    content: "I'm interested! Are you thinking of meeting this Friday?",
                    authorMemberId: 12,
                    authorDisplayName: "Anonymous 1",
                    isAnonymous: true,
                    isMine: false,
                    isDeleted: false,
                    createdAt: "2026-03-22T21:01:00",
                    children: [
                        PostCommentItem(
                            commentId: 2,
                            parentId: 1,
                            content: "Friday afternoon works for me! Around 3 PM?",
                            authorMemberId: 11,
                            authorDisplayName: "Author",
                            isAnonymous: true,
                            isMine: false,
                            isDeleted: false,
                            createdAt: "2026-03-22T21:02:00",
                            children: []
                        )
                    ]
                ),
                PostCommentItem(
                    commentId: 3,
                    parentId: nil,
                    content: "Calculus is killing me too... count me in please!",
                    authorMemberId: 13,
                    authorDisplayName: "Anonymous 2",
                    isAnonymous: true,
                    isMine: false,
                    isDeleted: false,
                    createdAt: "2026-03-22T21:04:00",
                    children: []
                )
            ],
            images: [
                PostImageSummary(
                    imageUrl: "https://example.com/images/study-group.png",
                    displayOrder: 0
                )
            ],
            createdAt: "2026-03-22T20:58:00",
            updatedAt: "2026-03-22T20:58:00"
        )
    ]
}

enum PreviewHomeData {
    static let previewNow: Date = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 9 * 60 * 60) ?? .current
        return calendar.date(
            from: DateComponents(
                year: 2026,
                month: 3,
                day: 20,
                hour: 10,
                minute: 45
            )
        ) ?? Date()
    }()

    static let profile = HomeMemberProfile(
        memberId: 1,
        email: "kenta@tokyo.ac.jp",
        nickname: "Kenta Tanaka",
        universityName: "The University of Tokyo",
        universityEmailDomain: "tokyo.ac.jp",
        majorCode: "ECON",
        majorName: "Economics"
    )

    static let semester = HomeSemester(
        id: 20261,
        academicYear: 2026,
        term: "SPRING"
    )

    static let lectures = [
        HomeLecture(
            id: 101,
            code: "ECON101",
            name: "Intro to Economics",
            professor: "Prof. Sato",
            schedules: [
                HomeLectureSchedule(
                    dayOfWeek: "THURSDAY",
                    startTime: "10:30",
                    endTime: "12:00",
                    location: "Bldg 3, 201"
                )
            ]
        ),
        HomeLecture(
            id: 202,
            code: "ENG201",
            name: "English Communication",
            professor: "Lecturer Smith",
            schedules: [
                HomeLectureSchedule(
                    dayOfWeek: "THURSDAY",
                    startTime: "13:00",
                    endTime: "14:30",
                    location: "Bldg 5, 10B"
                )
            ]
        )
    ]

    static let longTitleLectures = [
        HomeLecture(
            id: 401,
            code: "ECON410",
            name: "Applied Macroeconomic Policy and International Trade Strategy",
            professor: "Prof. Nakamura",
            schedules: [
                HomeLectureSchedule(
                    dayOfWeek: "FRIDAY",
                    startTime: "10:30",
                    endTime: "12:00",
                    location: "Bldg 3, 201"
                )
            ]
        ),
        HomeLecture(
            id: 402,
            code: "LANG305",
            name: "Advanced Academic English Presentation",
            professor: "Lecturer Smith",
            schedules: [
                HomeLectureSchedule(
                    dayOfWeek: "FRIDAY",
                    startTime: "13:00",
                    endTime: "14:30",
                    location: "Bldg 5, 10B"
                )
            ]
        )
    ]

    static let nonMatchingLectures = [
        HomeLecture(
            id: 303,
            code: "HIST120",
            name: "Modern History",
            professor: "Prof. Kim",
            schedules: [
                HomeLectureSchedule(
                    dayOfWeek: "MONDAY",
                    startTime: "09:00",
                    endTime: "10:30",
                    location: "Humanities 110"
                )
            ]
        )
    ]
}

enum PreviewTimetableData {
    static let memberContext = TimetableMemberContext(
        memberId: 1,
        nickname: "Kenta Tanaka",
        email: "kenta@tokyo.ac.jp",
        majorCode: "CS",
        majorName: "Computer Science"
    )

    static let semesters = [
        TimetableSemester(id: 20261, academicYear: 2026, term: "SPRING"),
        TimetableSemester(id: 20252, academicYear: 2025, term: "FALL")
    ]

    static let currentLectures = [
        TimetableLectureItem(
            id: 101,
            code: "CS101",
            name: "Introduction to Computer Science",
            professor: "Prof. Akiyama",
            credit: 3,
            major: TimetableMajorSummary(majorId: 1, code: "CS", name: "Computer Science"),
            schedules: [
                TimetableLectureSchedule(dayOfWeek: "MONDAY", startTime: "09:00", endTime: "10:30", location: "Room 301"),
                TimetableLectureSchedule(dayOfWeek: "WEDNESDAY", startTime: "09:00", endTime: "10:30", location: "Room 301")
            ]
        ),
        TimetableLectureItem(
            id: 102,
            code: "STAT230",
            name: "Statistics I",
            professor: "Prof. Ito",
            credit: 2,
            major: TimetableMajorSummary(majorId: 2, code: "MATH", name: "Mathematics"),
            schedules: [
                TimetableLectureSchedule(dayOfWeek: "THURSDAY", startTime: "10:40", endTime: "12:00", location: "PC-2")
            ]
        ),
        TimetableLectureItem(
            id: 103,
            code: "ENG220",
            name: "English II",
            professor: "Prof. Sarah Jenkins",
            credit: 2,
            major: nil,
            schedules: [
                TimetableLectureSchedule(dayOfWeek: "TUESDAY", startTime: "10:40", endTime: "12:00", location: "AV Hall")
            ]
        )
    ]

    static let longTitleLectures = [
        TimetableLectureItem(
            id: 111,
            code: "ECON410",
            name: "Applied Macroeconomic Policy and International Trade Strategy",
            professor: "Prof. Nakamura",
            credit: 3,
            major: TimetableMajorSummary(majorId: 3, code: "ECON", name: "Economics"),
            schedules: [
                TimetableLectureSchedule(dayOfWeek: "MONDAY", startTime: "09:00", endTime: "10:30", location: "Bldg 3, 201")
            ]
        ),
        TimetableLectureItem(
            id: 112,
            code: "LANG305",
            name: "Advanced Academic English Presentation",
            professor: "Lecturer Smith",
            credit: 2,
            major: nil,
            schedules: [
                TimetableLectureSchedule(dayOfWeek: "THURSDAY", startTime: "10:40", endTime: "12:00", location: "Bldg 5, 10B")
            ]
        )
    ]

    static let catalog = currentLectures + [
        TimetableLectureItem(
            id: 201,
            code: "PSY401",
            name: "Advanced Psychology",
            professor: "Prof. Sarah Jenkins",
            credit: 2,
            major: nil,
            schedules: [
                TimetableLectureSchedule(dayOfWeek: "MONDAY", startTime: "14:00", endTime: "15:30", location: "Room 402")
            ]
        ),
        TimetableLectureItem(
            id: 202,
            code: "DATA210",
            name: "Data Visualization",
            professor: "Prof. Kenji Sato",
            credit: 2,
            major: TimetableMajorSummary(majorId: 1, code: "CS", name: "Computer Science"),
            schedules: [
                TimetableLectureSchedule(dayOfWeek: "WEDNESDAY", startTime: "13:00", endTime: "14:30", location: "PC Lab 3")
            ]
        ),
        TimetableLectureItem(
            id: 203,
            code: "HIST330",
            name: "World History II",
            professor: "Prof. Elena Rossi",
            credit: 2,
            major: nil,
            schedules: [
                TimetableLectureSchedule(dayOfWeek: "THURSDAY", startTime: "13:00", endTime: "14:30", location: "Room 108")
            ]
        ),
        TimetableLectureItem(
            id: 204,
            code: "LING200",
            name: "Linguistics",
            professor: "Prof. Michael Chen",
            credit: 2,
            major: nil,
            schedules: [
                TimetableLectureSchedule(dayOfWeek: "FRIDAY", startTime: "14:40", endTime: "16:00", location: "AV Room")
            ]
        )
    ]
}
