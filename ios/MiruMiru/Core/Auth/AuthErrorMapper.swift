struct AuthErrorMapper {
    static func map(
        apiError: APIClientError,
        context: AuthFailureContext
    ) -> AuthError {
        switch apiError {
        case .transport:
            return .network
        case let .server(_, payload):
            guard let payload else { return .unexpected }

            if payload.code == "COMMON_001",
               case let .validation(field, reason, _) = payload.detail {
                return .fieldValidation(field: field, reason: reason)
            }

            switch payload.code {
            case "AUTH_001":
                switch context {
                case .login:
                    return .invalidCredentials
                case .restoreProbe, .reissue:
                    return .invalidSession
                case .signup:
                    return .unexpected
                }
            case "AUTH_003", "AUTH_008":
                return .duplicateEmail
            case "AUTH_004":
                return .duplicateNickname
            case "AUTH_005":
                return .emailNotVerified
            case "AUTH_006":
                return .authCodeNotFound
            case "AUTH_007":
                return .authCodeMismatch
            case "AUTH_010":
                return .unregisteredUniversity
            case "AUTH_011":
                return .invalidMajorSelection
            default:
                return .unexpected
            }
        default:
            return .unexpected
        }
    }
}
