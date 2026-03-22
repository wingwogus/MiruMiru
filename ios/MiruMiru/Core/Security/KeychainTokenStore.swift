import Foundation
import Security

enum TokenStoreError: Error {
    case unexpectedStatus(OSStatus)
    case invalidPayload

    var logMessage: String {
        switch self {
        case let .unexpectedStatus(status):
            return "status=\(status)"
        case .invalidPayload:
            return "invalid_payload"
        }
    }
}

final class KeychainTokenStore: TokenStore, @unchecked Sendable {
    private let service = "com.mirumiru.ios.auth"
    private let account = "auth-session"
    private let simulatorFallbackKey = "com.mirumiru.ios.auth.simulator-session"
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    func readSession() throws -> TokenPair? {
        #if targetEnvironment(simulator)
        return try readSimulatorSession()
        #else
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        switch status {
        case errSecSuccess:
            guard let data = item as? Data else {
                throw TokenStoreError.invalidPayload
            }
            return try decoder.decode(TokenPair.self, from: data)
        case errSecItemNotFound:
            return nil
        default:
            throw TokenStoreError.unexpectedStatus(status)
        }
        #endif
    }

    func saveSession(_ session: TokenPair) throws {
        #if targetEnvironment(simulator)
        try saveSimulatorSession(session)
        return
        #else
        let data = try encoder.encode(session)
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account
        ]

        let attributes: [CFString: Any] = [
            kSecValueData: data,
            kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]

        let status = SecItemAdd((query.merging(attributes) { _, new in new }) as CFDictionary, nil)

        if status == errSecDuplicateItem {
            let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
            guard updateStatus == errSecSuccess else {
                try? clearSession()
                throw TokenStoreError.unexpectedStatus(updateStatus)
            }
            return
        }

        guard status == errSecSuccess else {
            try? clearSession()
            throw TokenStoreError.unexpectedStatus(status)
        }
        #endif
    }

    func clearSession() throws {
        #if targetEnvironment(simulator)
        UserDefaults.standard.removeObject(forKey: simulatorFallbackKey)
        return
        #else
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account
        ]

        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw TokenStoreError.unexpectedStatus(status)
        }
        #endif
    }

    #if targetEnvironment(simulator)
    // Unsigned local simulator builds can fail Keychain writes, so use sandboxed storage there.
    private func readSimulatorSession() throws -> TokenPair? {
        guard let data = UserDefaults.standard.data(forKey: simulatorFallbackKey) else {
            return nil
        }

        do {
            return try decoder.decode(TokenPair.self, from: data)
        } catch {
            UserDefaults.standard.removeObject(forKey: simulatorFallbackKey)
            throw TokenStoreError.invalidPayload
        }
    }

    private func saveSimulatorSession(_ session: TokenPair) throws {
        let data = try encoder.encode(session)
        UserDefaults.standard.set(data, forKey: simulatorFallbackKey)
    }
    #endif
}
