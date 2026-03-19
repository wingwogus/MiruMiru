import XCTest
@testable import MiruMiru

@MainActor
final class LoginViewModelTests: XCTestCase {
    func testValidationRejectsNonAcademicSuffix() {
        let viewModel = makeViewModel()
        viewModel.email = "user@gmail.com"
        viewModel.password = "password123!"

        XCTAssertFalse(viewModel.validateInputs())
        XCTAssertEqual(viewModel.emailError, "Use your university email ending in .ac.jp.")
    }

    func testSubmitMapsInvalidCredentials() async {
        let authClient = MockAuthClient()
        authClient.loginResult = .failure(AuthError.invalidCredentials)
        let tokenStore = InMemoryTokenStore()
        let session = AppSession(authClient: authClient, tokenStore: tokenStore)
        let viewModel = LoginViewModel(session: session)

        viewModel.email = "test@tokyo.ac.jp"
        viewModel.password = "wrong-password"

        await viewModel.submit()

        XCTAssertEqual(viewModel.generalError, "Invalid email or password.")
        XCTAssertEqual(session.state, .unauthenticated)
    }

    func testSubmitSuccessTransitionsToAuthenticated() async throws {
        let authClient = MockAuthClient()
        authClient.loginResult = .success(
            TokenPair(accessToken: "access", refreshToken: "refresh")
        )
        let tokenStore = InMemoryTokenStore()
        let session = AppSession(authClient: authClient, tokenStore: tokenStore)
        let viewModel = LoginViewModel(session: session)

        viewModel.email = "test@tokyo.ac.jp"
        viewModel.password = "password123!"

        await viewModel.submit()

        XCTAssertEqual(session.state, .authenticated)
        XCTAssertEqual(tokenStore.storedSession, TokenPair(accessToken: "access", refreshToken: "refresh"))
    }

    func testSubmitShowsSessionPersistenceFailure() async {
        let authClient = MockAuthClient()
        authClient.loginResult = .success(
            TokenPair(accessToken: "access", refreshToken: "refresh")
        )
        let tokenStore = InMemoryTokenStore()
        tokenStore.shouldFailOnSave = true
        let session = AppSession(authClient: authClient, tokenStore: tokenStore)
        let viewModel = LoginViewModel(session: session)

        viewModel.email = "test@tokyo.ac.jp"
        viewModel.password = "password123!"

        await viewModel.submit()

        XCTAssertEqual(
            viewModel.generalError,
            "Login succeeded, but we couldn't save your session on this device. Please try again."
        )
        XCTAssertEqual(session.state, .unauthenticated)
    }

    private func makeViewModel() -> LoginViewModel {
        let authClient = MockAuthClient()
        let tokenStore = InMemoryTokenStore()
        let session = AppSession(authClient: authClient, tokenStore: tokenStore)
        return LoginViewModel(
            session: session,
            environment: AppEnvironment(
                apiBaseURL: URL(string: "http://127.0.0.1:8080")!,
                enforcesAcademicSuffixValidation: true
            )
        )
    }
}
