protocol TokenStore: Sendable {
    func readSession() throws -> TokenPair?
    func saveSession(_ session: TokenPair) throws
    func clearSession() throws
}
