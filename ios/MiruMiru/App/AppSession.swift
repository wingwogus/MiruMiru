import Foundation
import OSLog

enum AuthSessionState: Equatable {
    case launch
    case restoring
    case unauthenticated
    case submitting
    case authenticated
    case invalidSession
}

@MainActor
final class AppSession: ObservableObject {
    @Published private(set) var state: AuthSessionState = .launch
    @Published private(set) var bannerMessage: String?

    private let logger = Logger(subsystem: "com.mirumiru.ios", category: "auth")
    private let authClient: AuthClientProtocol
    private let tokenStore: TokenStore
    private var hasBootstrapped = false

    init(
        authClient: AuthClientProtocol,
        tokenStore: TokenStore,
        initialState: AuthSessionState = .launch,
        initialBannerMessage: String? = nil,
        hasBootstrapped: Bool = false
    ) {
        self.authClient = authClient
        self.tokenStore = tokenStore
        self.state = initialState
        self.bannerMessage = initialBannerMessage
        self.hasBootstrapped = hasBootstrapped
    }

    func bootstrap() async {
        guard hasBootstrapped == false else { return }
        hasBootstrapped = true

        do {
            guard let session = try tokenStore.readSession() else {
                logger.notice("bootstrap_no_stored_session")
                state = .unauthenticated
                return
            }

            logger.notice("bootstrap_restore_probe_started")
            state = .restoring

            do {
                try await authClient.validateRestoredSession(accessToken: session.accessToken)
                logger.notice("bootstrap_restore_probe_succeeded")
                state = .authenticated
            } catch let error as AuthError {
                if error == .invalidSession {
                    await attemptSessionReissue(using: session)
                } else {
                    handleRestoreFailure(error)
                }
            } catch {
                handleRestoreFailure(.unexpected)
            }
        } catch let error as TokenStoreError {
            logger.error("bootstrap_token_read_failed: \(error.logMessage, privacy: .public)")
            try? tokenStore.clearSession()
            bannerMessage = "We couldn't restore your previous session."
            state = .unauthenticated
        } catch {
            logger.error("bootstrap_token_read_failed")
            try? tokenStore.clearSession()
            bannerMessage = "We couldn't restore your previous session."
            state = .unauthenticated
        }
    }

    func login(email: String, password: String) async throws {
        bannerMessage = nil
        state = .submitting

        do {
            let tokens = try await authClient.login(email: email, password: password)
            logger.notice("login_response_ok")
            try tokenStore.saveSession(tokens)
            logger.notice("login_token_persisted")
            state = .authenticated
            logger.notice("auth_route_applied")
        } catch let error as AuthError {
            state = .unauthenticated
            throw error
        } catch let error as TokenStoreError {
            logger.error("token_store_failed: \(error.logMessage, privacy: .public)")
            state = .unauthenticated
            throw AuthError.sessionPersistenceFailure
        } catch {
            logger.error("login_unexpected_failure")
            state = .unauthenticated
            throw AuthError.unexpected
        }
    }

    func logoutToLogin(message: String? = nil) {
        try? tokenStore.clearSession()
        bannerMessage = message
        state = .unauthenticated
    }

    func invalidateSession(message: String = "Your session expired. Please log in again.") {
        logoutToLogin(message: message)
    }

    func markInvalidSessionHandled() {
        guard state == .invalidSession else { return }
        state = .unauthenticated
    }

    func dismissBanner() {
        bannerMessage = nil
    }

    private func handleRestoreFailure(_ error: AuthError) {
        switch error {
        case .invalidSession:
            logger.notice("bootstrap_invalid_session")
            try? tokenStore.clearSession()
            bannerMessage = "Your session expired. Please log in again."
            state = .invalidSession
        case .network, .unexpected:
            logger.error("bootstrap_restore_transient_failure")
            bannerMessage = "We couldn't restore your session."
            state = .unauthenticated
        default:
            logger.error("bootstrap_restore_failed")
            try? tokenStore.clearSession()
            bannerMessage = "We couldn't restore your session."
            state = .invalidSession
        }
    }

    private func attemptSessionReissue(using session: TokenPair) async {
        do {
            logger.notice("bootstrap_reissue_started")
            let refreshedSession = try await authClient.reissue(
                accessToken: session.accessToken,
                refreshToken: session.refreshToken
            )
            try tokenStore.saveSession(refreshedSession)
            logger.notice("bootstrap_reissue_succeeded")
            state = .authenticated
        } catch let error as AuthError {
            handleRestoreFailure(error)
        } catch let error as TokenStoreError {
            logger.error("bootstrap_reissue_token_store_failed: \(error.logMessage, privacy: .public)")
            try? tokenStore.clearSession()
            bannerMessage = "We couldn't restore your previous session."
            state = .unauthenticated
        } catch {
            handleRestoreFailure(.unexpected)
        }
    }
}
