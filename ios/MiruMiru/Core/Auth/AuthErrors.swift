import Foundation

enum AuthFailureContext: Equatable {
    case login
    case restoreProbe
    case reissue
    case signup
}

enum AuthError: Error, Equatable {
    case fieldValidation(field: String, reason: String)
    case invalidCredentials
    case invalidSession
    case duplicateEmail
    case duplicateNickname
    case emailNotVerified
    case authCodeNotFound
    case authCodeMismatch
    case unregisteredUniversity
    case invalidMajorSelection
    case sessionPersistenceFailure
    case network
    case unexpected
}
