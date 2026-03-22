import Foundation
@testable import MiruMiru

final class MockAuthClient: AuthClientProtocol, @unchecked Sendable {
    var loginResult: Result<TokenPair, Error> = .failure(AuthError.unexpected)
    var sendEmailCodeResult: Result<Void, Error> = .success(())
    var verifyEmailCodeResult: Result<Void, Error> = .success(())
    var fetchMajorsResult: Result<[MajorOption], Error> = .success([])
    var verifyNicknameResult: Result<Void, Error> = .success(())
    var signUpResult: Result<Void, Error> = .success(())
    var restoreResult: Result<Void, Error> = .success(())
    private(set) var lastLoginEmail: String?
    private(set) var lastSendEmailCodeEmail: String?
    private(set) var lastVerifyEmailCodeEmail: String?
    private(set) var lastVerifyEmailCodeValue: String?
    private(set) var lastFetchMajorsEmail: String?
    private(set) var lastVerifyNickname: String?
    private(set) var lastSignUpEmail: String?
    private(set) var lastSignUpNickname: String?
    private(set) var lastSignUpMajorId: Int64?
    private(set) var lastRestoreToken: String?

    func login(email: String, password: String) async throws -> TokenPair {
        lastLoginEmail = email
        switch loginResult {
        case let .success(tokens):
            return tokens
        case let .failure(error):
            throw error
        }
    }

    func sendEmailCode(email: String) async throws {
        lastSendEmailCodeEmail = email
        try sendEmailCodeResult.get()
    }

    func verifyEmailCode(email: String, code: String) async throws {
        lastVerifyEmailCodeEmail = email
        lastVerifyEmailCodeValue = code
        try verifyEmailCodeResult.get()
    }

    func fetchMajors(email: String) async throws -> [MajorOption] {
        lastFetchMajorsEmail = email
        return try fetchMajorsResult.get()
    }

    func verifyNickname(nickname: String) async throws {
        lastVerifyNickname = nickname
        try verifyNicknameResult.get()
    }

    func signUp(email: String, password: String, nickname: String, majorId: Int64) async throws {
        lastSignUpEmail = email
        lastSignUpNickname = nickname
        lastSignUpMajorId = majorId
        try signUpResult.get()
    }

    func validateRestoredSession(accessToken: String) async throws {
        lastRestoreToken = accessToken
        switch restoreResult {
        case .success:
            return
        case let .failure(error):
            throw error
        }
    }
}

final class InMemoryTokenStore: TokenStore, @unchecked Sendable {
    var storedSession: TokenPair?
    var shouldFailOnSave = false
    var shouldFailOnRead = false

    func readSession() throws -> TokenPair? {
        if shouldFailOnRead {
            throw TokenStoreError.invalidPayload
        }
        return storedSession
    }

    func saveSession(_ session: TokenPair) throws {
        if shouldFailOnSave {
            storedSession = nil
            throw TokenStoreError.invalidPayload
        }
        storedSession = session
    }

    func clearSession() throws {
        storedSession = nil
    }
}

final class MockHomeClient: HomeClientProtocol, @unchecked Sendable {
    var profileResult: Result<HomeMemberProfile, Error> = .failure(HomeClientError.unexpected)
    var semestersResult: Result<[HomeSemester], Error> = .failure(HomeClientError.unexpected)
    var timetableResult: Result<HomeTimetable, Error> = .failure(HomeClientError.unexpected)
    var hotPostsResult: Result<[HotPostSummary], Error> = .success([])
    private(set) var requestedSemesterId: Int64?

    func fetchProfile() async throws -> HomeMemberProfile {
        try profileResult.get()
    }

    func fetchSemesters() async throws -> [HomeSemester] {
        try semestersResult.get()
    }

    func fetchTimetable(semesterId: Int64) async throws -> HomeTimetable {
        requestedSemesterId = semesterId
        return try timetableResult.get()
    }

    func fetchHotPosts() async throws -> [HotPostSummary] {
        try hotPostsResult.get()
    }
}

final class MockTimetableClient: TimetableClientProtocol, @unchecked Sendable {
    var memberContextResult: Result<TimetableMemberContext, Error> = .failure(TimetableClientError.unexpected)
    var semestersResult: Result<[TimetableSemester], Error> = .failure(TimetableClientError.unexpected)
    var catalogResult: Result<[TimetableLectureItem], Error> = .failure(TimetableClientError.unexpected)
    var timetableResult: Result<TimetableDetail, Error> = .failure(TimetableClientError.unexpected)
    var addLectureResult: Result<Void, Error> = .success(())
    var removeLectureResult: Result<Void, Error> = .success(())

    private(set) var requestedSemesterId: Int64?
    private(set) var catalogRequestedSemesterIds: [Int64] = []
    private(set) var addedPayloads: [(semesterId: Int64, lectureId: Int64)] = []
    private(set) var removedPayloads: [(semesterId: Int64, lectureId: Int64)] = []

    func fetchMemberContext() async throws -> TimetableMemberContext {
        try memberContextResult.get()
    }

    func fetchSemesters() async throws -> [TimetableSemester] {
        try semestersResult.get()
    }

    func fetchLectureCatalog(semesterId: Int64) async throws -> [TimetableLectureItem] {
        catalogRequestedSemesterIds.append(semesterId)
        return try catalogResult.get()
    }

    func fetchMyTimetable(semesterId: Int64) async throws -> TimetableDetail {
        requestedSemesterId = semesterId
        return try timetableResult.get()
    }

    func addLecture(semesterId: Int64, lectureId: Int64) async throws {
        addedPayloads.append((semesterId, lectureId))
        try addLectureResult.get()
    }

    func removeLecture(semesterId: Int64, lectureId: Int64) async throws {
        removedPayloads.append((semesterId, lectureId))
        try removeLectureResult.get()
    }
}

@MainActor
final class MockSignupClient: SignupClientProtocol {
    var sendEmailCodeResult: Result<Void, Error> = .success(())
    var verifyEmailCodeResult: Result<Void, Error> = .success(())
    var fetchMajorsResult: Result<[SignupMajorOption], Error> = .success([])
    var verifyNicknameResult: Result<Void, Error> = .success(())
    var signUpResult: Result<Void, Error> = .success(())

    private(set) var eventLog: [String] = []
    private(set) var sentEmail: String?
    private(set) var verifiedEmail: String?
    private(set) var verifiedCode: String?
    private(set) var fetchedMajorsEmail: String?
    private(set) var verifiedNickname: String?
    private(set) var signUpPayload: (email: String, password: String, nickname: String, majorId: Int64)?

    func sendEmailCode(email: String) async throws {
        sentEmail = email
        eventLog.append("sendEmailCode")
        switch sendEmailCodeResult {
        case .success:
            return
        case let .failure(error):
            throw error
        }
    }

    func verifyEmailCode(email: String, code: String) async throws {
        verifiedEmail = email
        verifiedCode = code
        eventLog.append("verifyEmailCode")
        switch verifyEmailCodeResult {
        case .success:
            return
        case let .failure(error):
            throw error
        }
    }

    func fetchMajors(email: String) async throws -> [SignupMajorOption] {
        fetchedMajorsEmail = email
        eventLog.append("fetchMajors")
        switch fetchMajorsResult {
        case let .success(options):
            return options
        case let .failure(error):
            throw error
        }
    }

    func verifyNickname(_ nickname: String) async throws {
        verifiedNickname = nickname
        eventLog.append("verifyNickname")
        switch verifyNicknameResult {
        case .success:
            return
        case let .failure(error):
            throw error
        }
    }

    func signUp(email: String, password: String, nickname: String, majorId: Int64) async throws {
        signUpPayload = (email, password, nickname, majorId)
        eventLog.append("signUp")
        switch signUpResult {
        case .success:
            return
        case let .failure(error):
            throw error
        }
    }
}
