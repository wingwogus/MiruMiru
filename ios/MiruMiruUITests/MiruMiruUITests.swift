import XCTest

final class MiruMiruUITests: XCTestCase {
    func testLoginScreenControlsExist() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.textFields["email_text_field"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.secureTextFields["password_text_field"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["login_button"].exists)
        XCTAssertTrue(app.buttons["signup_top_button"].exists)
        XCTAssertTrue(app.buttons["signup_bottom_button"].exists)
        XCTAssertTrue(app.buttons["forgot_password_button"].exists)
    }
}
