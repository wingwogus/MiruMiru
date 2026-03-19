import XCTest
@testable import MiruMiru

@MainActor
final class AppSessionTests: XCTestCase {
    func testBootstrapWithoutStoredSessionShowsUnauthenticated() async {
        let session = AppSession(
            authClient: MockAuthClient(),
            tokenStore: InMemoryTokenStore()
        )

        await session.bootstrap()

        XCTAssertEqual(session.state, .unauthenticated)
    }

    func testBootstrapValidatesStoredSessionWithRestoreProbe() async {
        let authClient = MockAuthClient()
        let tokenStore = InMemoryTokenStore()
        tokenStore.storedSession = TokenPair(accessToken: "saved-access", refreshToken: "saved-refresh")
        let session = AppSession(authClient: authClient, tokenStore: tokenStore)

        await session.bootstrap()

        XCTAssertEqual(authClient.lastRestoreToken, "saved-access")
        XCTAssertEqual(session.state, .authenticated)
    }

    func testBootstrapInvalidSessionClearsTokens() async throws {
        let authClient = MockAuthClient()
        authClient.restoreResult = .failure(AuthError.invalidSession)
        let tokenStore = InMemoryTokenStore()
        tokenStore.storedSession = TokenPair(accessToken: "saved-access", refreshToken: "saved-refresh")
        let session = AppSession(authClient: authClient, tokenStore: tokenStore)

        await session.bootstrap()

        XCTAssertEqual(session.state, .invalidSession)
        XCTAssertNil(try tokenStore.readSession())
        XCTAssertEqual(session.bannerMessage, "Your session expired. Please log in again.")
        session.markInvalidSessionHandled()
        XCTAssertEqual(session.state, .unauthenticated)
    }

    func testLoginSaveFailureRollsBackSession() async {
        let authClient = MockAuthClient()
        authClient.loginResult = .success(TokenPair(accessToken: "a", refreshToken: "r"))
        let tokenStore = InMemoryTokenStore()
        tokenStore.shouldFailOnSave = true
        let session = AppSession(authClient: authClient, tokenStore: tokenStore)

        do {
            try await session.login(email: "test@tokyo.ac.jp", password: "password123!")
            XCTFail("Expected save failure")
        } catch let error as AuthError {
            XCTAssertEqual(error, .sessionPersistenceFailure)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }

        XCTAssertNil(tokenStore.storedSession)
        XCTAssertEqual(session.state, .unauthenticated)
    }

    func testBootstrapReadFailureClearsBrokenSession() async throws {
        let authClient = MockAuthClient()
        let tokenStore = InMemoryTokenStore()
        tokenStore.storedSession = TokenPair(accessToken: "saved-access", refreshToken: "saved-refresh")
        tokenStore.shouldFailOnRead = true
        let session = AppSession(authClient: authClient, tokenStore: tokenStore)

        await session.bootstrap()

        XCTAssertEqual(session.state, .unauthenticated)
        XCTAssertEqual(session.bannerMessage, "We couldn't restore your previous session.")
        tokenStore.shouldFailOnRead = false
        XCTAssertNil(try tokenStore.readSession())
    }

    func testInvalidateSessionReturnsToLoginWithBanner() async {
        let session = AppSession(
            authClient: MockAuthClient(),
            tokenStore: InMemoryTokenStore(),
            initialState: .authenticated
        )

        session.invalidateSession(message: "Session expired.")

        XCTAssertEqual(session.state, .unauthenticated)
        XCTAssertEqual(session.bannerMessage, "Session expired.")
    }
}
