import XCTest
@testable import MiruMiru

@MainActor
final class SignupViewModelTests: XCTestCase {
    func testSendCodeStartsTimerOnlyAfterSuccess() async {
        let client = MockSignupClient()
        let viewModel = SignupViewModel(
            client: client,
            resendInterval: 3,
            sleep: { _ in
                try await Task.sleep(nanoseconds: 10_000_000_000)
            }
        )
        viewModel.email = "user@tokyo.ac.jp"

        await viewModel.sendCode()

        XCTAssertEqual(client.sentEmail, "user@tokyo.ac.jp")
        XCTAssertEqual(viewModel.resendRemainingSeconds, 3)
    }

    func testFailedSendCodeDoesNotStartTimer() async {
        let client = MockSignupClient()
        client.sendEmailCodeResult = .failure(AuthError.network)
        let viewModel = SignupViewModel(
            client: client,
            resendInterval: 180,
            sleep: { _ in }
        )
        viewModel.email = "user@tokyo.ac.jp"

        await viewModel.sendCode()

        XCTAssertEqual(viewModel.resendRemainingSeconds, 0)
        XCTAssertEqual(viewModel.generalError, "Unable to reach the server. Please try again.")
    }

    func testVerifyEmailAdvancesAndLoadsMajors() async {
        let client = MockSignupClient()
        client.fetchMajorsResult = .success([
            SignupMajorOption(id: 10, code: "CS", name: "Computer Science")
        ])
        let viewModel = SignupViewModel(client: client, sleep: { _ in })
        viewModel.email = "user@tokyo.ac.jp"
        viewModel.verificationCode = "123456"

        await viewModel.verifyEmailAndContinue()

        XCTAssertEqual(viewModel.step, .profileDetails)
        XCTAssertEqual(client.eventLog, ["verifyEmailCode", "fetchMajors"])
        XCTAssertEqual(viewModel.selectedMajorID, 10)
    }

    func testContinueFromProfileRequiresNicknameAndMajor() {
        let client = MockSignupClient()
        let viewModel = SignupViewModel(client: client)
        viewModel.email = "user@tokyo.ac.jp"
        viewModel.verificationCode = "123456"

        viewModel.continueFromProfile()

        XCTAssertEqual(viewModel.step, .verifyEmail)
    }

    func testCompleteSignupVerifiesNicknameThenSignsUp() async {
        let client = MockSignupClient()
        let viewModel = SignupViewModel(client: client)
        viewModel.email = "user@tokyo.ac.jp"
        viewModel.verificationCode = "123456"

        await viewModel.verifyEmailAndContinue()
        viewModel.nickname = "miru"
        viewModel.selectedMajorID = 11
        viewModel.continueFromProfile()
        viewModel.password = "password123!"
        viewModel.confirmPassword = "password123!"

        await viewModel.completeSignup()

        XCTAssertEqual(client.eventLog, ["verifyEmailCode", "fetchMajors", "verifyNickname", "signUp"])
        XCTAssertEqual(viewModel.completionRequest?.email, "user@tokyo.ac.jp")
    }

    func testCodeMismatchDoesNotChangeExistingTimer() async {
        let client = MockSignupClient()
        client.verifyEmailCodeResult = .failure(AuthError.unexpected)
        let viewModel = SignupViewModel(
            client: client,
            resendInterval: 180,
            sleep: { _ in
                try await Task.sleep(nanoseconds: 10_000_000_000)
            }
        )
        viewModel.email = "user@tokyo.ac.jp"

        await viewModel.sendCode()
        let timerAfterSend = viewModel.resendRemainingSeconds

        viewModel.verificationCode = "123456"
        await viewModel.verifyEmailAndContinue()

        XCTAssertEqual(viewModel.resendRemainingSeconds, timerAfterSend)
    }

    func testCompleteSignupRequiresMatchingPasswords() async {
        let client = MockSignupClient()
        let viewModel = SignupViewModel(client: client)
        viewModel.email = "user@tokyo.ac.jp"
        viewModel.verificationCode = "123456"

        await viewModel.verifyEmailAndContinue()
        viewModel.nickname = "miru"
        viewModel.selectedMajorID = 11
        viewModel.continueFromProfile()
        viewModel.password = "one"
        viewModel.confirmPassword = "two"

        await viewModel.completeSignup()

        XCTAssertEqual(client.eventLog, ["verifyEmailCode", "fetchMajors"])
        XCTAssertEqual(viewModel.confirmPasswordError, "Passwords do not match.")
    }
}
