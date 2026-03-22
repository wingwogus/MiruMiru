import Foundation

struct AppEnvironment: Equatable {
    let apiBaseURL: URL
    let enforcesAcademicSuffixValidation: Bool

    static func live(bundle: Bundle = .main) -> AppEnvironment {
        if
            let rawURL = bundle.object(forInfoDictionaryKey: "APIBaseURL") as? String,
            let url = URL(string: rawURL)
        {
            return AppEnvironment(
                apiBaseURL: url,
                enforcesAcademicSuffixValidation: true
            )
        }

#if DEBUG
        assertionFailure("Missing APIBaseURL in Info.plist. Falling back to localhost for Debug only.")
        return AppEnvironment(
            apiBaseURL: URL(string: "http://127.0.0.1:8080")!,
            enforcesAcademicSuffixValidation: true
        )
#else
        fatalError("Missing or invalid APIBaseURL in Info.plist.")
#endif
    }
}
