import Foundation
import SwiftUI

// MARK: - Models

struct MessagesViewer: Equatable, Sendable {
    let memberId: Int64
    let displayName: String
}

struct MessageStartRequest: Identifiable, Hashable, Equatable, Sendable {
    let postId: Int64
    let postTitle: String
    let postIsAnonymous: Bool
    let partnerMemberId: Int64?
    let targetDisplayName: String
    let targetIsAnonymous: Bool
    let requesterIsAnonymous: Bool

    var id: String {
        "\(postId)-\(partnerMemberId ?? -1)-\(targetDisplayName)"
    }
}

struct MessageRoomSummary: Identifiable, Hashable, Equatable, Sendable {
    let roomId: Int64
    let postId: Int64
    let postTitle: String
    let roomTitle: String
    let otherMemberId: Int64
    let counterpartDisplayName: String?
    let lastMessageId: Int64?
    let lastMessageContent: String?
    let lastMessageCreatedAt: String?
    let unreadCount: Int
    let myLastReadMessageId: Int64?
    let otherLastReadMessageId: Int64?
    let isAnonMe: Bool
    let isAnonOther: Bool

    var id: Int64 { roomId }

    var displayTitle: String {
        roomTitle
    }

    var previewText: String {
        lastMessageContent?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
            ? lastMessageContent!
            : "Start the conversation."
    }

    var timestampText: String {
        MessagesTimestampFormatter.inboxString(from: lastMessageCreatedAt)
    }

    var unreadBadgeText: String? {
        unreadCount > 0 ? "\(unreadCount)" : nil
    }

    var asRoomContext: MessageRoomContext {
        MessageRoomContext(
            roomId: roomId,
            postId: postId,
            postTitle: postTitle,
            roomTitle: roomTitle,
            otherMemberId: otherMemberId,
            counterpartDisplayName: counterpartDisplayName,
            isAnonMe: isAnonMe,
            isAnonOther: isAnonOther,
            myLastReadMessageId: myLastReadMessageId,
            otherLastReadMessageId: otherLastReadMessageId
        )
    }
}

struct MessageRoomContext: Hashable, Equatable, Identifiable, Sendable {
    let roomId: Int64
    let postId: Int64
    let postTitle: String
    let roomTitle: String
    let otherMemberId: Int64
    let counterpartDisplayName: String?
    let isAnonMe: Bool
    let isAnonOther: Bool
    var myLastReadMessageId: Int64?
    var otherLastReadMessageId: Int64?

    var id: Int64 { roomId }

    var displayTitle: String {
        roomTitle
    }

    var subtitle: String {
        counterpartDisplayName ?? "Post conversation"
    }
}

struct MessageItem: Identifiable, Hashable, Equatable, Sendable {
    let id: String
    let serverId: Int64?
    let roomId: Int64
    let senderId: Int64
    let content: String
    let createdAt: String
    let isPending: Bool

    var timeText: String {
        MessagesTimestampFormatter.messageString(from: createdAt)
    }

    var createdDate: Date? {
        RelativeTimestampFormatter.date(from: createdAt)
    }

    func belongsToViewer(_ viewerId: Int64) -> Bool {
        senderId == viewerId
    }
}

struct MessagesPage: Equatable, Sendable {
    let roomId: Int64
    let messages: [MessageItem]
    let myLastReadMessageId: Int64?
    let otherLastReadMessageId: Int64?
    let nextBeforeMessageId: Int64?
}

struct MessageRoomCreated: Equatable, Sendable {
    let roomId: Int64
    let postId: Int64
    let member1Id: Int64
    let member2Id: Int64
    let roomTitle: String
    let counterpartDisplayName: String?
    let isAnonMe: Bool
    let isAnonOther: Bool
    let created: Bool
}

enum MessagesInboxState: Equatable {
    case loading
    case loaded([MessageRoomSummary])
    case empty
    case failed(MessagesFailure)
}

enum MessagesRoomState: Equatable {
    case loading
    case loaded
    case failed(MessagesFailure)
}

enum MessagesClientError: Error, Equatable {
    case invalidSession
    case postNotFound
    case roomNotFound
    case messageNotFound
    case blockedConversation
    case forbidden
    case network
    case unexpected
}

enum MessagesFailure: Error, Equatable {
    case invalidSession
    case postNotFound
    case roomNotFound
    case messageNotFound
    case blockedConversation
    case forbidden
    case network
    case unexpected

    var message: String {
        switch self {
        case .invalidSession:
            return "Your session expired. Please log in again."
        case .postNotFound:
            return "We couldn't find that post anymore."
        case .roomNotFound:
            return "We couldn't find that conversation."
        case .messageNotFound:
            return "That message is no longer available."
        case .blockedConversation:
            return "This conversation is blocked."
        case .forbidden:
            return "You can't do that in this conversation."
        case .network:
            return "Unable to connect to Messages right now."
        case .unexpected:
            return "Something went wrong while loading Messages."
        }
    }
}

enum MessagesRealtimeEvent: Equatable, Sendable {
    case message(MessageItem)
    case read(roomId: Int64, readerId: Int64, lastReadMessageId: Int64)
    case unread(roomId: Int64, memberId: Int64, unreadCount: Int)
}

protocol MessagesClientProtocol: Sendable {
    func fetchViewer() async throws -> MessagesViewer
    func fetchRooms(limit: Int) async throws -> [MessageRoomSummary]
    func fetchBlockedMemberIds() async throws -> Set<Int64>
    func createRoom(postId: Int64, requesterIsAnonymous: Bool, partnerMemberId: Int64?) async throws -> MessageRoomCreated
    func fetchMessages(roomId: Int64, beforeMessageId: Int64?, limit: Int) async throws -> MessagesPage
    func sendMessage(roomId: Int64, content: String) async throws -> MessageItem
    func markRead(roomId: Int64, lastReadMessageId: Int64) async throws -> Int
    func blockMember(targetMemberId: Int64) async throws
    func unblockMember(targetMemberId: Int64) async throws
    func reportMember(targetMemberId: Int64, roomId: Int64, reason: String, detail: String?) async throws
}

protocol MessagesRealtimeClientProtocol: Sendable {
    func activate() async
    func deactivate() async
    func ensureUnreadSubscription() async
    func subscribeToRoom(_ roomId: Int64) async
    func unsubscribeFromRoom(_ roomId: Int64) async
}

// MARK: - API Client

final class MessagesAPIClient: MessagesClientProtocol, @unchecked Sendable {
    private let apiClient: APIClient
    private let authorizedExecutor: AuthorizedRequestExecutor
    private let encoder = JSONEncoder()

    init(
        apiClient: APIClient,
        tokenStore: TokenStore,
        authorizedExecutor: AuthorizedRequestExecutor? = nil
    ) {
        self.apiClient = apiClient
        self.authorizedExecutor = authorizedExecutor ?? AuthorizedRequestExecutor(apiClient: apiClient, tokenStore: tokenStore)
    }

    func fetchViewer() async throws -> MessagesViewer {
        let payload: ViewerResponse = try await requestPayload(path: "/api/v1/members/me")
        return payload.toDomain()
    }

    func fetchRooms(limit: Int = 30) async throws -> [MessageRoomSummary] {
        let payload: [RoomResponse] = try await requestPayload(path: "/api/v1/message-rooms?limit=\(limit)")
        return payload.map(\.toDomain)
    }

    func fetchBlockedMemberIds() async throws -> Set<Int64> {
        let payload: [BlockListItemResponse] = try await requestPayload(path: "/api/v1/chat/blocks")
        return Set(payload.map(\.targetMemberId))
    }

    func createRoom(postId: Int64, requesterIsAnonymous: Bool, partnerMemberId: Int64?) async throws -> MessageRoomCreated {
        let request = CreateRoomRequest(
            postId: postId,
            requesterIsAnonymous: requesterIsAnonymous,
            partnerMemberId: partnerMemberId
        )
        let payload: RoomCreatedResponse = try await requestPayload(
            path: "/api/v1/message-rooms",
            method: .post,
            body: try encoder.encode(request)
        )
        return payload.toDomain()
    }

    func fetchMessages(roomId: Int64, beforeMessageId: Int64?, limit: Int = 30) async throws -> MessagesPage {
        var path = "/api/v1/message-rooms/\(roomId)/messages?limit=\(limit)"
        if let beforeMessageId {
            path += "&beforeMessageId=\(beforeMessageId)"
        }
        let payload: MessagesResponse = try await requestPayload(path: path)
        return payload.toDomain()
    }

    func sendMessage(roomId: Int64, content: String) async throws -> MessageItem {
        let request = SendMessageRequest(content: content)
        let payload: ChatMessageResponse = try await requestPayload(
            path: "/api/v1/message-rooms/\(roomId)/messages",
            method: .post,
            body: try encoder.encode(request)
        )
        return payload.toDomain()
    }

    func markRead(roomId: Int64, lastReadMessageId: Int64) async throws -> Int {
        let request = MarkReadRequest(lastReadMessageId: lastReadMessageId)
        let payload: ReadMarkedResponse = try await requestPayload(
            path: "/api/v1/message-rooms/\(roomId)/read",
            method: .patch,
            body: try encoder.encode(request)
        )
        return Int(payload.unreadCount)
    }

    func blockMember(targetMemberId: Int64) async throws {
        let request = BlockMemberRequest(targetMemberId: targetMemberId)
        let _: BlockMemberResponse = try await requestPayload(
            path: "/api/v1/chat/blocks",
            method: .post,
            body: try encoder.encode(request)
        )
    }

    func unblockMember(targetMemberId: Int64) async throws {
        let _: UnblockMemberResponse = try await requestPayload(
            path: "/api/v1/chat/blocks/\(targetMemberId)",
            method: .delete
        )
    }

    func reportMember(targetMemberId: Int64, roomId: Int64, reason: String, detail: String?) async throws {
        let request = ReportMemberRequest(
            targetMemberId: targetMemberId,
            roomId: roomId,
            messageId: nil,
            reason: reason,
            detail: detail
        )
        let _: ReportMemberResponse = try await requestPayload(
            path: "/api/v1/chat/reports",
            method: .post,
            body: try encoder.encode(request)
        )
    }

    private func requestPayload<Response: Decodable>(
        path: String,
        method: HTTPMethod = .get,
        body: Data? = nil
    ) async throws -> Response {
        do {
            let (data, _) = try await authorizedExecutor.send(
                path: path,
                method: method,
                body: body
            )
            let envelope = try apiClient.decode(APIResponseEnvelope<Response>.self, from: data)
            guard envelope.success, let payload = envelope.data else {
                throw MessagesClientError.unexpected
            }
            return payload
        } catch let error as APIClientError {
            throw map(apiError: error)
        } catch let error as MessagesClientError {
            throw error
        } catch {
            throw MessagesClientError.unexpected
        }
    }

    private func map(apiError: APIClientError) -> MessagesClientError {
        switch apiError {
        case .transport:
            return .network
        case let .server(statusCode, payload):
            if statusCode == 401 {
                return .invalidSession
            }
            if statusCode == 403 {
                if payload?.message == "chat_blocked_between_members" {
                    return .blockedConversation
                }
                return .forbidden
            }
            switch payload?.code {
            case "POST_001":
                return .postNotFound
            case "CHAT_001":
                return .roomNotFound
            case "CHAT_002":
                return .messageNotFound
            default:
                return .unexpected
            }
        default:
            return .unexpected
        }
    }
}

private extension MessagesAPIClient {
    struct ViewerResponse: Decodable {
        let memberId: Int64
        let email: String
        let nickname: String

        func toDomain() -> MessagesViewer {
            let trimmedNickname = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
            return MessagesViewer(
                memberId: memberId,
                displayName: trimmedNickname.isEmpty ? email : trimmedNickname
            )
        }
    }

    struct CreateRoomRequest: Encodable {
        let postId: Int64
        let requesterIsAnonymous: Bool
        let partnerMemberId: Int64?
    }

    struct SendMessageRequest: Encodable {
        let content: String
    }

    struct MarkReadRequest: Encodable {
        let lastReadMessageId: Int64
    }

    struct BlockMemberRequest: Encodable {
        let targetMemberId: Int64
    }

    struct ReportMemberRequest: Encodable {
        let targetMemberId: Int64
        let roomId: Int64
        let messageId: Int64?
        let reason: String
        let detail: String?
    }

    struct RoomResponse: Decodable {
        let roomId: Int64
        let postId: Int64
        let postTitle: String
        let roomTitle: String
        let otherMemberId: Int64
        let counterpartDisplayName: String?
        let lastMessageId: Int64?
        let lastMessageContent: String?
        let lastMessageCreatedAt: String?
        let unreadCount: Int
        let myLastReadMessageId: Int64?
        let otherLastReadMessageId: Int64?
        let isAnonMe: Bool
        let isAnonOther: Bool

        var toDomain: MessageRoomSummary {
            MessageRoomSummary(
                roomId: roomId,
                postId: postId,
                postTitle: postTitle,
                roomTitle: roomTitle,
                otherMemberId: otherMemberId,
                counterpartDisplayName: counterpartDisplayName,
                lastMessageId: lastMessageId,
                lastMessageContent: lastMessageContent,
                lastMessageCreatedAt: lastMessageCreatedAt,
                unreadCount: unreadCount,
                myLastReadMessageId: myLastReadMessageId,
                otherLastReadMessageId: otherLastReadMessageId,
                isAnonMe: isAnonMe,
                isAnonOther: isAnonOther
            )
        }
    }

    struct RoomCreatedResponse: Decodable {
        let roomId: Int64
        let postId: Int64
        let member1Id: Int64
        let member2Id: Int64
        let roomTitle: String
        let counterpartDisplayName: String?
        let isAnon1: Bool
        let isAnon2: Bool
        let created: Bool

        func toDomain() -> MessageRoomCreated {
            MessageRoomCreated(
                roomId: roomId,
                postId: postId,
                member1Id: member1Id,
                member2Id: member2Id,
                roomTitle: roomTitle,
                counterpartDisplayName: counterpartDisplayName,
                isAnonMe: isAnon1,
                isAnonOther: isAnon2,
                created: created
            )
        }
    }

    struct BlockMemberResponse: Decodable {
        let targetMemberId: Int64
        let blocked: Bool
        let created: Bool
    }

    struct UnblockMemberResponse: Decodable {
        let targetMemberId: Int64
        let unblocked: Bool
    }

    struct ReportMemberResponse: Decodable {
        let reportId: Int64
        let targetMemberId: Int64
        let blocked: Bool
        let blockCreated: Bool
    }

    struct BlockListItemResponse: Decodable {
        let targetMemberId: Int64
        let blockedAt: String?
    }

    struct ChatMessageResponse: Decodable {
        let id: Int64
        let roomId: Int64
        let senderId: Int64
        let content: String
        let createdAt: String?

        func toDomain() -> MessageItem {
            MessageItem(
                id: "server-\(id)",
                serverId: id,
                roomId: roomId,
                senderId: senderId,
                content: content,
                createdAt: createdAt ?? "",
                isPending: false
            )
        }
    }

    struct MessagesResponse: Decodable {
        let roomId: Int64
        let messages: [ChatMessageResponse]
        let requesterLastReadMessageId: Int64?
        let otherLastReadMessageId: Int64?
        let nextBeforeMessageId: Int64?

        func toDomain() -> MessagesPage {
            MessagesPage(
                roomId: roomId,
                messages: messages.map { $0.toDomain() },
                myLastReadMessageId: requesterLastReadMessageId,
                otherLastReadMessageId: otherLastReadMessageId,
                nextBeforeMessageId: nextBeforeMessageId
            )
        }
    }

    struct ReadMarkedResponse: Decodable {
        let roomId: Int64
        let readerId: Int64
        let lastReadMessageId: Int64
        let unreadCount: Int64
    }
}

// MARK: - Realtime

extension Notification.Name {
    static let messagesRealtimeEvent = Notification.Name("MessagesRealtimeEventNotification")
}

actor MessagesRealtimeClient: MessagesRealtimeClientProtocol {
    private enum ConnectionState {
        case idle
        case connecting
        case connected
    }

    private let environment: AppEnvironment
    private let tokenStore: TokenStore
    private let session: URLSession

    private var webSocketTask: URLSessionWebSocketTask?
    private var state: ConnectionState = .idle
    private var shouldMaintainConnection = false
    private var currentRoomId: Int64?
    private var unreadSubscribed = false
    private var reconnectAttempt = 0
    private var connectionSequence = 0

    init(
        environment: AppEnvironment,
        tokenStore: TokenStore,
        session: URLSession = .shared
    ) {
        self.environment = environment
        self.tokenStore = tokenStore
        self.session = session
    }

    func activate() async {
        shouldMaintainConnection = true
        await connectIfNeeded()
    }

    func deactivate() async {
        shouldMaintainConnection = false
        currentRoomId = nil
        unreadSubscribed = false
        reconnectAttempt = 0
        disconnect()
    }

    func ensureUnreadSubscription() async {
        unreadSubscribed = true
        guard state == .connected else { return }
        try? await sendSubscribeFrame(
            id: "unread",
            destination: "/user/queue/unread"
        )
    }

    func subscribeToRoom(_ roomId: Int64) async {
        let previousRoomId = currentRoomId
        currentRoomId = roomId

        guard state == .connected else { return }

        if let previousRoomId, previousRoomId != roomId {
            try? await sendUnsubscribeFrame(id: roomSubscriptionID(for: previousRoomId))
        }

        try? await sendSubscribeFrame(
            id: roomSubscriptionID(for: roomId),
            destination: "/sub/chat/rooms/\(roomId)"
        )
    }

    func unsubscribeFromRoom(_ roomId: Int64) async {
        guard currentRoomId == roomId else { return }
        currentRoomId = nil
        guard state == .connected else { return }
        try? await sendUnsubscribeFrame(id: roomSubscriptionID(for: roomId))
    }

    private func connectIfNeeded() async {
        guard shouldMaintainConnection else { return }
        guard state == .idle else { return }
        guard let accessToken = try? tokenStore.readSession()?.accessToken,
              accessToken.isEmpty == false,
              let url = makeSockJSWebSocketURL()
        else {
            return
        }

        state = .connecting
        connectionSequence += 1
        let sequence = connectionSequence

        let task = session.webSocketTask(with: url)
        webSocketTask = task
        task.resume()

        Task {
            await self.receiveLoop(sequence: sequence, accessToken: accessToken)
        }
    }

    private func receiveLoop(sequence: Int, accessToken: String) async {
        guard let task = webSocketTask else { return }

        do {
            while shouldMaintainConnection, sequence == connectionSequence {
                let message = try await task.receive()
                let text: String
                switch message {
                case let .string(value):
                    text = value
                case let .data(value):
                    text = String(decoding: value, as: UTF8.self)
                @unknown default:
                    continue
                }

                try await handleSockJSFrame(text, accessToken: accessToken)
            }
        } catch {
            // Reconnect is handled below.
        }

        guard sequence == connectionSequence else { return }
        state = .idle
        webSocketTask = nil
        if shouldMaintainConnection {
            await scheduleReconnect()
        }
    }

    private func handleSockJSFrame(_ payload: String, accessToken: String) async throws {
        guard payload.isEmpty == false else { return }

        if payload == "o" {
            try await sendConnectFrame(accessToken: accessToken)
            return
        }

        if payload == "h" {
            return
        }

        if payload.hasPrefix("c") {
            throw URLError(.networkConnectionLost)
        }

        guard payload.hasPrefix("a"), let data = payload.dropFirst().data(using: .utf8) else {
            return
        }

        let frames = try JSONDecoder().decode([String].self, from: data)
        for frameText in frames {
            try await handleSTOMPFrame(frameText)
        }
    }

    private func handleSTOMPFrame(_ rawFrame: String) async throws {
        guard let frame = STOMPFrameParser.parse(rawFrame) else { return }

        switch frame.command {
        case "CONNECTED":
            state = .connected
            reconnectAttempt = 0
            if unreadSubscribed {
                try await sendSubscribeFrame(id: "unread", destination: "/user/queue/unread")
            }
            if let currentRoomId {
                try await sendSubscribeFrame(
                    id: roomSubscriptionID(for: currentRoomId),
                    destination: "/sub/chat/rooms/\(currentRoomId)"
                )
            }
        case "MESSAGE":
            guard let event = MessagesRealtimeEnvelope.decode(from: frame.body) else { return }
            await MainActor.run {
                NotificationCenter.default.post(
                    name: .messagesRealtimeEvent,
                    object: nil,
                    userInfo: ["event": event]
                )
            }
        default:
            break
        }
    }

    private func sendConnectFrame(accessToken: String) async throws {
        let frame = """
        CONNECT
        accept-version:1.2
        heart-beat:0,0
        Authorization:Bearer \(accessToken)

        \u{0000}
        """
        try await sendSockJSMessage(frame)
    }

    private func sendSubscribeFrame(id: String, destination: String) async throws {
        let frame = """
        SUBSCRIBE
        id:\(id)
        destination:\(destination)
        ack:auto

        \u{0000}
        """
        try await sendSockJSMessage(frame)
    }

    private func sendUnsubscribeFrame(id: String) async throws {
        let frame = """
        UNSUBSCRIBE
        id:\(id)

        \u{0000}
        """
        try await sendSockJSMessage(frame)
    }

    private func sendSockJSMessage(_ stompFrame: String) async throws {
        guard let webSocketTask else { return }
        let wrapped = try JSONEncoder().encode([stompFrame])
        let text = String(decoding: wrapped, as: UTF8.self)
        try await webSocketTask.send(.string(text))
    }

    private func scheduleReconnect() async {
        let delay = min(8, max(1, 1 << min(reconnectAttempt, 3)))
        reconnectAttempt += 1
        try? await Task.sleep(nanoseconds: UInt64(delay) * 1_000_000_000)
        await connectIfNeeded()
    }

    private func disconnect() {
        webSocketTask?.cancel(with: .goingAway, reason: nil)
        webSocketTask = nil
        state = .idle
    }

    private func roomSubscriptionID(for roomId: Int64) -> String {
        "room-\(roomId)"
    }

    private func makeSockJSWebSocketURL() -> URL? {
        guard var components = URLComponents(url: environment.apiBaseURL, resolvingAgainstBaseURL: true) else {
            return nil
        }

        switch components.scheme {
        case "https":
            components.scheme = "wss"
        default:
            components.scheme = "ws"
        }

        let basePath = components.path == "/" ? "" : components.path
        let serverId = Int.random(in: 100...999)
        let sessionId = UUID().uuidString.replacingOccurrences(of: "-", with: "")
        components.path = basePath + "/ws/\(serverId)/\(sessionId)/websocket"
        components.query = nil
        return components.url
    }
}

private struct STOMPFrame {
    let command: String
    let body: String
}

private enum STOMPFrameParser {
    static func parse(_ raw: String) -> STOMPFrame? {
        let normalized = raw.replacingOccurrences(of: "\r\n", with: "\n")
        let trimmed = normalized.drop(while: { $0 == "\n" || $0 == "\r" })
        guard trimmed.isEmpty == false else { return nil }
        let parts = String(trimmed).components(separatedBy: "\n\n")
        guard let headerBlock = parts.first else { return nil }
        let lines = headerBlock.components(separatedBy: "\n")
        guard let command = lines.first, command.isEmpty == false else { return nil }
        let body = parts.dropFirst().joined(separator: "\n\n").trimmingCharacters(in: CharacterSet(charactersIn: "\u{0000}"))
        return STOMPFrame(command: command, body: body)
    }
}

private enum MessagesRealtimeEnvelope {
    struct ChatEventEnvelope: Decodable {
        let type: String
        let roomId: Int64
        let message: MessagePayload?
        let read: ReadPayload?
        let unread: UnreadPayload?
    }

    struct MessagePayload: Decodable {
        let id: Int64
        let roomId: Int64
        let senderId: Int64
        let content: String
        let createdAt: String?
    }

    struct ReadPayload: Decodable {
        let readerId: Int64
        let lastReadMessageId: Int64
    }

    struct UnreadPayload: Decodable {
        let memberId: Int64
        let unreadCount: Int
    }

    static func decode(from body: String) -> MessagesRealtimeEvent? {
        guard let data = body.data(using: .utf8),
              let envelope = try? JSONDecoder().decode(ChatEventEnvelope.self, from: data) else {
            return nil
        }

        switch envelope.type {
        case "MESSAGE":
            guard let message = envelope.message else { return nil }
            return .message(
                MessageItem(
                    id: "server-\(message.id)",
                    serverId: message.id,
                    roomId: message.roomId,
                    senderId: message.senderId,
                    content: message.content,
                    createdAt: message.createdAt ?? "",
                    isPending: false
                )
            )
        case "READ":
            guard let read = envelope.read else { return nil }
            return .read(
                roomId: envelope.roomId,
                readerId: read.readerId,
                lastReadMessageId: read.lastReadMessageId
            )
        case "UNREAD_COUNT":
            guard let unread = envelope.unread else { return nil }
            return .unread(
                roomId: envelope.roomId,
                memberId: unread.memberId,
                unreadCount: unread.unreadCount
            )
        default:
            return nil
        }
    }
}

// MARK: - View Models

@MainActor
final class MessagesInboxViewModel: ObservableObject {
    @Published private(set) var state: MessagesInboxState = .loading
    @Published private(set) var viewer: MessagesViewer?
    @Published private(set) var isStartingRoom = false
    @Published var actionMessage: String?

    private let client: MessagesClientProtocol
    private let realtimeClient: MessagesRealtimeClientProtocol
    private var hasLoaded = false
    private var rooms: [MessageRoomSummary] = []
    private var reloadTask: Task<Void, Never>?

    init(client: MessagesClientProtocol, realtimeClient: MessagesRealtimeClientProtocol) {
        self.client = client
        self.realtimeClient = realtimeClient
    }

    func activate() async {
        await realtimeClient.activate()
        await realtimeClient.ensureUnreadSubscription()
        if hasLoaded {
            await reload()
        } else {
            await loadIfNeeded()
        }
    }

    func deactivate() async {
        reloadTask?.cancel()
        reloadTask = nil
        await realtimeClient.deactivate()
    }

    func loadIfNeeded() async {
        guard hasLoaded == false else { return }
        hasLoaded = true
        await loadRooms()
    }

    func reload() async {
        await loadRooms()
    }

    func displayedRooms(query: String) -> [MessageRoomSummary] {
        guard case let .loaded(rooms) = state else { return [] }
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.isEmpty == false else { return rooms }
        return rooms.filter {
            $0.displayTitle.localizedCaseInsensitiveContains(trimmed)
                || $0.postTitle.localizedCaseInsensitiveContains(trimmed)
                || $0.previewText.localizedCaseInsensitiveContains(trimmed)
        }
    }

    func startChat(using request: MessageStartRequest) async -> MessageRoomContext? {
        isStartingRoom = true
        actionMessage = nil
        defer { isStartingRoom = false }

        do {
            try await loadViewerIfNeeded()
            let created = try await client.createRoom(
                postId: request.postId,
                requesterIsAnonymous: request.requesterIsAnonymous,
                partnerMemberId: request.partnerMemberId
            )
            await loadRooms()
            if let matchedRoom = rooms.first(where: { $0.roomId == created.roomId }) {
                return matchedRoom.asRoomContext
            }
            let viewerId = viewer?.memberId ?? 0
            let otherMemberId = created.member1Id == viewerId ? created.member2Id : created.member1Id
            return MessageRoomContext(
                roomId: created.roomId,
                postId: created.postId,
                postTitle: request.postTitle,
                roomTitle: created.roomTitle,
                otherMemberId: otherMemberId,
                counterpartDisplayName: created.counterpartDisplayName,
                isAnonMe: created.isAnonMe,
                isAnonOther: created.isAnonOther,
                myLastReadMessageId: nil,
                otherLastReadMessageId: nil
            )
        } catch let error as MessagesClientError {
            actionMessage = Self.map(error).message
            return nil
        } catch {
            actionMessage = MessagesFailure.unexpected.message
            return nil
        }
    }

    func handleRealtimeEvent(_ event: MessagesRealtimeEvent) {
        switch event {
        case let .unread(roomId, memberId, unreadCount):
            guard memberId == viewer?.memberId else { return }
            patchUnreadCount(roomId: roomId, unreadCount: unreadCount)
            scheduleReload()
        case let .message(message):
            if let roomIndex = rooms.firstIndex(where: { $0.roomId == message.roomId }) {
                var room = rooms[roomIndex]
                room = MessageRoomSummary(
                    roomId: room.roomId,
                    postId: room.postId,
                    postTitle: room.postTitle,
                    roomTitle: room.roomTitle,
                    otherMemberId: room.otherMemberId,
                    counterpartDisplayName: room.counterpartDisplayName,
                    lastMessageId: message.serverId,
                    lastMessageContent: message.content,
                    lastMessageCreatedAt: message.createdAt,
                    unreadCount: room.unreadCount,
                    myLastReadMessageId: room.myLastReadMessageId,
                    otherLastReadMessageId: room.otherLastReadMessageId,
                    isAnonMe: room.isAnonMe,
                    isAnonOther: room.isAnonOther
                )
                rooms.remove(at: roomIndex)
                rooms.insert(room, at: 0)
                state = .loaded(rooms)
            }
            scheduleReload()
        case let .read(roomId, readerId, lastReadMessageId):
            guard let viewer else { return }
            if let roomIndex = rooms.firstIndex(where: { $0.roomId == roomId }) {
                var room = rooms[roomIndex]
                room = MessageRoomSummary(
                    roomId: room.roomId,
                    postId: room.postId,
                    postTitle: room.postTitle,
                    roomTitle: room.roomTitle,
                    otherMemberId: room.otherMemberId,
                    counterpartDisplayName: room.counterpartDisplayName,
                    lastMessageId: room.lastMessageId,
                    lastMessageContent: room.lastMessageContent,
                    lastMessageCreatedAt: room.lastMessageCreatedAt,
                    unreadCount: readerId == viewer.memberId ? 0 : room.unreadCount,
                    myLastReadMessageId: readerId == viewer.memberId ? lastReadMessageId : room.myLastReadMessageId,
                    otherLastReadMessageId: readerId == viewer.memberId ? room.otherLastReadMessageId : lastReadMessageId,
                    isAnonMe: room.isAnonMe,
                    isAnonOther: room.isAnonOther
                )
                rooms[roomIndex] = room
                state = .loaded(rooms)
            }
            scheduleReload()
        }
    }

    private func loadRooms() async {
        state = .loading
        actionMessage = nil

        do {
            try await loadViewerIfNeeded()
            let fetchedRooms = try await client.fetchRooms(limit: 30)
            rooms = fetchedRooms
            state = fetchedRooms.isEmpty ? .empty : .loaded(fetchedRooms)
        } catch let error as MessagesClientError {
            state = .failed(Self.map(error))
        } catch {
            state = .failed(.unexpected)
        }
    }

    private func loadViewerIfNeeded() async throws {
        guard viewer == nil else { return }
        viewer = try await client.fetchViewer()
    }

    private func patchUnreadCount(roomId: Int64, unreadCount: Int) {
        guard let roomIndex = rooms.firstIndex(where: { $0.roomId == roomId }) else { return }
        let room = rooms[roomIndex]
        rooms[roomIndex] = MessageRoomSummary(
            roomId: room.roomId,
            postId: room.postId,
            postTitle: room.postTitle,
            roomTitle: room.roomTitle,
            otherMemberId: room.otherMemberId,
            counterpartDisplayName: room.counterpartDisplayName,
            lastMessageId: room.lastMessageId,
            lastMessageContent: room.lastMessageContent,
            lastMessageCreatedAt: room.lastMessageCreatedAt,
            unreadCount: unreadCount,
            myLastReadMessageId: room.myLastReadMessageId,
            otherLastReadMessageId: room.otherLastReadMessageId,
            isAnonMe: room.isAnonMe,
            isAnonOther: room.isAnonOther
        )
        state = .loaded(rooms)
    }

    private func scheduleReload() {
        reloadTask?.cancel()
        reloadTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 500_000_000)
            await self?.reload()
        }
    }

    private static func map(_ error: MessagesClientError) -> MessagesFailure {
        switch error {
        case .invalidSession: .invalidSession
        case .postNotFound: .postNotFound
        case .roomNotFound: .roomNotFound
        case .messageNotFound: .messageNotFound
        case .blockedConversation: .blockedConversation
        case .forbidden: .forbidden
        case .network: .network
        case .unexpected: .unexpected
        }
    }
}

@MainActor
final class ChatRoomViewModel: ObservableObject {
    @Published private(set) var state: MessagesRoomState = .loading
    @Published private(set) var messages: [MessageItem] = []
    @Published private(set) var roomContext: MessageRoomContext
    @Published private(set) var hasMore = true
    @Published var composerText = ""
    @Published var actionMessage: String?
    @Published private(set) var isSending = false
    @Published private(set) var isLoadingMore = false
    @Published private(set) var isCounterpartBlockedByMe = false

    private let client: MessagesClientProtocol
    private let realtimeClient: MessagesRealtimeClientProtocol
    let viewerId: Int64
    private let pageSize = 30
    private var nextBeforeMessageId: Int64?
    private var hasLoaded = false

    init(
        context: MessageRoomContext,
        client: MessagesClientProtocol,
        realtimeClient: MessagesRealtimeClientProtocol,
        viewerId: Int64
    ) {
        self.roomContext = context
        self.client = client
        self.realtimeClient = realtimeClient
        self.viewerId = viewerId
    }

    func activate() async {
        await realtimeClient.subscribeToRoom(roomContext.roomId)
        await loadIfNeeded()
    }

    func deactivate() async {
        await realtimeClient.unsubscribeFromRoom(roomContext.roomId)
    }

    func loadIfNeeded() async {
        guard hasLoaded == false else { return }
        hasLoaded = true
        await loadLatestMessages()
    }

    func reload() async {
        hasLoaded = false
        await loadIfNeeded()
    }

    func loadOlderMessages() async {
        guard isLoadingMore == false, hasMore, let nextBeforeMessageId else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }

        do {
            let page = try await client.fetchMessages(
                roomId: roomContext.roomId,
                beforeMessageId: nextBeforeMessageId,
                limit: pageSize
            )

            let existingIds = Set(messages.compactMap(\.serverId))
            let older = page.messages.filter { message in
                guard let serverId = message.serverId else { return true }
                return existingIds.contains(serverId) == false
            }
            messages.insert(contentsOf: older, at: 0)
            self.nextBeforeMessageId = page.nextBeforeMessageId
            hasMore = older.count >= pageSize && page.nextBeforeMessageId != nil
        } catch let error as MessagesClientError {
            actionMessage = Self.map(error).message
        } catch {
            actionMessage = MessagesFailure.unexpected.message
        }
    }

    func sendMessage() async {
        guard isCounterpartBlockedByMe == false else {
            actionMessage = "You blocked this user. You can still view the conversation."
            return
        }

        let trimmed = composerText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.isEmpty == false else { return }

        let pendingId = "pending-\(UUID().uuidString)"
        let pendingMessage = MessageItem(
            id: pendingId,
            serverId: nil,
            roomId: roomContext.roomId,
            senderId: viewerId,
            content: trimmed,
            createdAt: ISO8601DateFormatter().string(from: Date()),
            isPending: true
        )

        composerText = ""
        actionMessage = nil
        isSending = true
        messages.append(pendingMessage)

        do {
            let sent = try await client.sendMessage(roomId: roomContext.roomId, content: trimmed)
            replacePendingMessage(id: pendingId, with: sent)
        } catch let error as MessagesClientError {
            messages.removeAll { $0.id == pendingId }
            actionMessage = Self.map(error).message
        } catch {
            messages.removeAll { $0.id == pendingId }
            actionMessage = MessagesFailure.unexpected.message
        }

        isSending = false
    }

    func blockCounterpart() async {
        actionMessage = nil

        do {
            try await client.blockMember(targetMemberId: roomContext.otherMemberId)
            isCounterpartBlockedByMe = true
            actionMessage = "This user has been blocked. Sending is now disabled in this room."
        } catch let error as MessagesClientError {
            actionMessage = Self.map(error).message
        } catch {
            actionMessage = MessagesFailure.unexpected.message
        }
    }

    func unblockCounterpart() async {
        actionMessage = nil

        do {
            try await client.unblockMember(targetMemberId: roomContext.otherMemberId)
            isCounterpartBlockedByMe = false
            actionMessage = "This user has been unblocked. Sending is available again."
        } catch let error as MessagesClientError {
            actionMessage = Self.map(error).message
        } catch {
            actionMessage = MessagesFailure.unexpected.message
        }
    }

    func reportCounterpart(reason: String, detail: String?) async {
        actionMessage = nil

        do {
            try await client.reportMember(
                targetMemberId: roomContext.otherMemberId,
                roomId: roomContext.roomId,
                reason: reason,
                detail: detail
            )
            isCounterpartBlockedByMe = true
            actionMessage = "Report submitted. Sending is now disabled in this room."
        } catch let error as MessagesClientError {
            actionMessage = Self.map(error).message
        } catch {
            actionMessage = MessagesFailure.unexpected.message
        }
    }

    func handleRealtimeEvent(_ event: MessagesRealtimeEvent) {
        switch event {
        case let .message(message):
            guard message.roomId == roomContext.roomId else { return }
            insertRealtimeMessage(message)
            if message.belongsToViewer(viewerId) == false {
                Task { await markNewestVisiblePartnerMessageAsReadIfNeeded() }
            }
        case let .read(roomId, readerId, lastReadMessageId):
            guard roomId == roomContext.roomId else { return }
            if readerId == viewerId {
                roomContext.myLastReadMessageId = max(roomContext.myLastReadMessageId ?? 0, lastReadMessageId)
            } else {
                roomContext.otherLastReadMessageId = max(roomContext.otherLastReadMessageId ?? 0, lastReadMessageId)
            }
        case .unread:
            break
        }
    }

    private func loadLatestMessages() async {
        state = .loading
        actionMessage = nil

        do {
            async let pageTask = client.fetchMessages(
                roomId: roomContext.roomId,
                beforeMessageId: nil,
                limit: pageSize
            )
            async let blockedTask = client.fetchBlockedMemberIds()
            let (page, blockedMemberIds) = try await (pageTask, blockedTask)

            messages = page.messages
            roomContext.myLastReadMessageId = page.myLastReadMessageId
            roomContext.otherLastReadMessageId = page.otherLastReadMessageId
            nextBeforeMessageId = page.nextBeforeMessageId
            hasMore = page.messages.count >= pageSize && page.nextBeforeMessageId != nil
            isCounterpartBlockedByMe = blockedMemberIds.contains(roomContext.otherMemberId)
            state = .loaded
            await markNewestVisiblePartnerMessageAsReadIfNeeded()
        } catch let error as MessagesClientError {
            state = .failed(Self.map(error))
        } catch {
            state = .failed(.unexpected)
        }
    }

    private func markNewestVisiblePartnerMessageAsReadIfNeeded() async {
        let latestPartnerMessage = messages
            .reversed()
            .first { $0.belongsToViewer(viewerId) == false }

        guard let latestPartnerMessageId = latestPartnerMessage?.serverId
        else {
            return
        }

        let currentLastRead = roomContext.myLastReadMessageId ?? 0
        guard latestPartnerMessageId > currentLastRead else { return }

        do {
            _ = try await client.markRead(
                roomId: roomContext.roomId,
                lastReadMessageId: latestPartnerMessageId
            )
            roomContext.myLastReadMessageId = latestPartnerMessageId
        } catch {
            // Keep room usable; inbox refresh will reconcile later.
        }
    }

    private func replacePendingMessage(id: String, with actual: MessageItem) {
        if let existingIndex = messages.firstIndex(where: { $0.serverId == actual.serverId }) {
            messages[existingIndex] = actual
            messages.removeAll { $0.id == id }
            return
        }
        if let pendingIndex = messages.firstIndex(where: { $0.id == id }) {
            messages[pendingIndex] = actual
        } else {
            messages.append(actual)
        }
    }

    private func insertRealtimeMessage(_ message: MessageItem) {
        if let existingId = message.serverId,
           let existingIndex = messages.firstIndex(where: { $0.serverId == existingId }) {
            messages[existingIndex] = message
            return
        }

        if message.belongsToViewer(viewerId),
           let pendingIndex = messages.firstIndex(where: { $0.isPending && $0.content == message.content }) {
            messages[pendingIndex] = message
            return
        }

        messages.append(message)
        messages.sort { lhs, rhs in
            switch (lhs.createdDate, rhs.createdDate) {
            case let (left?, right?):
                return left < right
            default:
                return lhs.id < rhs.id
            }
        }
    }

    private static func map(_ error: MessagesClientError) -> MessagesFailure {
        switch error {
        case .invalidSession: .invalidSession
        case .postNotFound: .postNotFound
        case .roomNotFound: .roomNotFound
        case .messageNotFound: .messageNotFound
        case .blockedConversation: .blockedConversation
        case .forbidden: .forbidden
        case .network: .network
        case .unexpected: .unexpected
        }
    }
}

// MARK: - Feature Root

private enum MessagesRoute: Hashable {
    case room(MessageRoomContext)
}

struct MessagesRootView: View {
    @ObservedObject private var session: AppSession
    @StateObject private var viewModel: MessagesInboxViewModel
    @Binding private var isTabBarHidden: Bool
    @Binding private var pendingStartRequest: MessageStartRequest?
    private let client: MessagesClientProtocol
    private let realtimeClient: MessagesRealtimeClientProtocol
    private let isActive: Bool

    @State private var path: [MessagesRoute] = []
    @State private var query = ""

    init(
        session: AppSession,
        client: MessagesClientProtocol,
        realtimeClient: MessagesRealtimeClientProtocol,
        isTabBarHidden: Binding<Bool> = .constant(false),
        pendingStartRequest: Binding<MessageStartRequest?> = .constant(nil),
        isActive: Bool = true
    ) {
        self.session = session
        self.client = client
        self.realtimeClient = realtimeClient
        self._isTabBarHidden = isTabBarHidden
        self._pendingStartRequest = pendingStartRequest
        self.isActive = isActive
        _viewModel = StateObject(wrappedValue: MessagesInboxViewModel(client: client, realtimeClient: realtimeClient))
    }

    var body: some View {
        NavigationStack(path: $path) {
            MessagesInboxView(
                viewModel: viewModel,
                query: $query,
                onRoomTap: { room in
                    path.append(.room(room.asRoomContext))
                }
            )
            .navigationDestination(for: MessagesRoute.self) { route in
                switch route {
                case let .room(context):
                    if let viewerId = viewModel.viewer?.memberId {
                        ChatRoomView(
                            session: session,
                            context: context,
                            client: client,
                            realtimeClient: realtimeClient,
                            viewerId: viewerId
                        )
                    } else {
                        ProgressView("Loading conversation...")
                    }
                }
            }
        }
        .background(MessagesBackgroundView())
        .task(id: isActive) {
            if isActive {
                await viewModel.activate()
                await consumePendingStartRequestIfNeeded()
            } else {
                await viewModel.deactivate()
            }
        }
        .onAppear {
            isTabBarHidden = path.isEmpty == false
        }
        .onChange(of: path) { _, newValue in
            isTabBarHidden = newValue.isEmpty == false
        }
        .onChange(of: isActive) { _, newValue in
            if newValue == false {
                isTabBarHidden = false
            }
        }
        .onDisappear {
            isTabBarHidden = false
        }
        .onChange(of: pendingStartRequest) { _, _ in
            Task { await consumePendingStartRequestIfNeeded() }
        }
        .onReceive(NotificationCenter.default.publisher(for: .messagesRealtimeEvent)) { notification in
            guard let event = notification.userInfo?["event"] as? MessagesRealtimeEvent else { return }
            viewModel.handleRealtimeEvent(event)
        }
        .onChange(of: inboxShouldInvalidateSession()) { _, shouldInvalidate in
            guard shouldInvalidate else { return }
            session.invalidateSession()
        }
    }

    private func consumePendingStartRequestIfNeeded() async {
        guard isActive, let pendingStartRequest else { return }
        defer { self.pendingStartRequest = nil }
        if let context = await viewModel.startChat(using: pendingStartRequest) {
            path = [.room(context)]
        }
    }

    private func inboxShouldInvalidateSession() -> Bool {
        if case .failed(.invalidSession) = viewModel.state {
            return true
        }
        return false
    }
}

// MARK: - Inbox UI

private struct MessagesInboxView: View {
    @ObservedObject var viewModel: MessagesInboxViewModel
    @Binding var query: String
    let onRoomTap: (MessageRoomSummary) -> Void

    var body: some View {
        GeometryReader { geometry in
            switch viewModel.state {
            case .empty:
                emptyInboxLayout(in: geometry.size)
            default:
                ScrollView(showsIndicators: false) {
                    scrollContent
                }
            }
        }
    }

    @ViewBuilder
    private var inboxChrome: some View {
        header

        MessagesSearchField(text: $query)

        if let actionMessage = viewModel.actionMessage {
            MessagesInlineBanner(message: actionMessage) {
                viewModel.actionMessage = nil
            }
        }
    }

    @ViewBuilder
    private var scrollContent: some View {
        VStack(alignment: .leading, spacing: 20) {
            inboxChrome

            switch viewModel.state {
            case .loading:
                ProgressView("Loading messages...")
                    .font(AppFont.medium(15, relativeTo: .subheadline))
                    .tint(AuthPalette.primaryStart)
                    .frame(maxWidth: .infinity, minHeight: 180)
            case let .failed(failure):
                MessagesFailureCard(
                    title: "We couldn't load Messages",
                    message: failure.message,
                    buttonTitle: "Try Again",
                    action: {
                        Task { await viewModel.reload() }
                    }
                )
            case .loaded:
                let rooms = viewModel.displayedRooms(query: query)
                if rooms.isEmpty {
                    MessagesEmptyCard(
                        title: "No matching conversations",
                        message: "Try a different keyword."
                    )
                } else {
                    LazyVStack(spacing: 0) {
                        ForEach(rooms) { room in
                            Button {
                                onRoomTap(room)
                            } label: {
                                MessageRoomRow(room: room)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                }
            case .empty:
                EmptyView()
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 22)
        .padding(.bottom, AuthenticatedLayoutMetrics.rootContentBottomSpacing)
    }

    private func emptyInboxLayout(in size: CGSize) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            inboxChrome

            Spacer(minLength: 20)

            MessagesEmptyCard(
                title: "No conversations yet",
                message: "Start a conversation from a post to see it here."
            )
            .frame(maxWidth: .infinity)

            Spacer(minLength: AuthenticatedLayoutMetrics.rootContentBottomSpacing)
        }
        .padding(.horizontal, 20)
        .padding(.top, 22)
        .padding(.bottom, AuthenticatedLayoutMetrics.rootContentBottomSpacing)
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private var header: some View {
        HStack {
            Text("Messages")
                .font(AppFont.extraBold(30, relativeTo: .largeTitle))
                .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

            Spacer()

            Image(systemName: "magnifyingglass")
                .font(.system(size: 24, weight: .medium))
                .foregroundStyle(Color(red: 0.35, green: 0.42, blue: 0.54))
        }
    }
}

private struct MessagesSearchField: View {
    @Binding var text: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Color(red: 0.58, green: 0.65, blue: 0.74))

            TextField("Search conversations...", text: $text)
                .font(AppFont.medium(18, relativeTo: .body))
                .foregroundStyle(Color(red: 0.16, green: 0.21, blue: 0.30))
        }
        .padding(.horizontal, 18)
        .frame(height: 56)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color(red: 0.96, green: 0.97, blue: 0.99))
        )
    }
}

private struct MessageRoomRow: View {
    let room: MessageRoomSummary

    private var usesAnonymousCounterpartLabel: Bool {
        room.counterpartDisplayName?.hasPrefix("익명") == true
    }

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            Circle()
                .fill(Color(red: 0.92, green: 0.94, blue: 0.97))
                .frame(width: 62, height: 62)
                .overlay {
                    Image(systemName: usesAnonymousCounterpartLabel ? "questionmark" : "person.fill")
                        .font(.system(size: 21, weight: .medium))
                        .foregroundStyle(Color(red: 0.62, green: 0.67, blue: 0.76))
                }

            VStack(alignment: .leading, spacing: 6) {
                Text(room.displayTitle)
                    .font(AppFont.bold(18, relativeTo: .headline))
                    .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))
                    .lineLimit(1)

                Text(room.previewText)
                    .font(AppFont.medium(15, relativeTo: .body))
                    .foregroundStyle(Color(red: 0.39, green: 0.45, blue: 0.56))
                    .lineLimit(1)
            }

            Spacer(minLength: 12)

            VStack(alignment: .trailing, spacing: 12) {
                Text(room.timestampText)
                    .font(AppFont.medium(13, relativeTo: .caption))
                    .foregroundStyle(Color(red: 0.62, green: 0.67, blue: 0.76))

                if let unreadBadgeText = room.unreadBadgeText {
                    Text(unreadBadgeText)
                        .font(AppFont.bold(13, relativeTo: .caption))
                        .foregroundStyle(.white)
                        .frame(minWidth: 28, minHeight: 28)
                        .padding(.horizontal, 6)
                        .background(
                            Circle()
                                .fill(AuthPalette.primaryStart)
                        )
                } else {
                    Color.clear
                        .frame(width: 28, height: 28)
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 15)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color(red: 0.93, green: 0.95, blue: 0.98))
                .frame(height: 1)
                .padding(.leading, 84)
        }
    }
}

// MARK: - Room UI

private struct ChatRoomView: View {
    @ObservedObject private var session: AppSession
    @StateObject private var viewModel: ChatRoomViewModel
    @State private var showBlockConfirmation = false
    @State private var showReportSheet = false

    init(
        session: AppSession,
        context: MessageRoomContext,
        client: MessagesClientProtocol,
        realtimeClient: MessagesRealtimeClientProtocol,
        viewerId: Int64
    ) {
        self.session = session
        _viewModel = StateObject(
            wrappedValue: ChatRoomViewModel(
                context: context,
                client: client,
                realtimeClient: realtimeClient,
                viewerId: viewerId
            )
        )
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    if viewModel.hasMore {
                        Button {
                            Task { await viewModel.loadOlderMessages() }
                        } label: {
                            Text(viewModel.isLoadingMore ? "Loading earlier..." : "Load earlier messages")
                                .font(AppFont.semibold(14, relativeTo: .caption))
                                .foregroundStyle(AuthPalette.primaryStart)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                                .background(
                                    Capsule(style: .continuous)
                                        .fill(Color.white)
                                )
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 8)
                    }

                    ForEach(groupedMessages, id: \.id) { item in
                        switch item {
                        case let .day(label):
                            Text(label)
                                .font(AppFont.semibold(15, relativeTo: .caption))
                                .foregroundStyle(Color(red: 0.58, green: 0.64, blue: 0.73))
                                .padding(.horizontal, 18)
                                .padding(.vertical, 8)
                                .background(
                                    Capsule(style: .continuous)
                                        .fill(Color.white)
                                        .shadow(color: Color.black.opacity(0.06), radius: 12, y: 2)
                                )
                        case let .message(message, showTimestamp):
                            MessageBubbleRow(
                                message: message,
                                isMine: message.belongsToViewer(viewModel.viewerId),
                                showTimestamp: showTimestamp
                            )
                            .id(message.id)
                        }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 18)
                .padding(.bottom, AuthenticatedLayoutMetrics.accessoryContentBottomSpacing)
            }
            .background(MessagesBackgroundView())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 2) {
                        Text(viewModel.roomContext.displayTitle)
                            .font(AppFont.bold(19, relativeTo: .headline))
                            .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                        Text(viewModel.roomContext.subtitle)
                            .font(AppFont.medium(12, relativeTo: .caption))
                            .foregroundStyle(Color(red: 0.58, green: 0.64, blue: 0.73))
                    }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button(role: viewModel.isCounterpartBlockedByMe ? nil : .destructive) {
                            showBlockConfirmation = true
                        } label: {
                            Label(
                                viewModel.isCounterpartBlockedByMe ? "Unblock User" : "Block User",
                                systemImage: viewModel.isCounterpartBlockedByMe ? "hand.raised.slash.fill" : "hand.raised.fill"
                            )
                        }

                        Button {
                            showReportSheet = true
                        } label: {
                            Label("Report User", systemImage: "flag.fill")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .font(.system(size: 19, weight: .semibold))
                            .foregroundStyle(Color(red: 0.35, green: 0.42, blue: 0.54))
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                roomComposer
            }
            .task {
                await viewModel.activate()
            }
            .onDisappear {
                Task { await viewModel.deactivate() }
            }
            .onReceive(NotificationCenter.default.publisher(for: .messagesRealtimeEvent)) { notification in
                guard let event = notification.userInfo?["event"] as? MessagesRealtimeEvent else { return }
                viewModel.handleRealtimeEvent(event)
            }
            .onChange(of: viewModel.messages.count) { _, _ in
                guard let lastId = viewModel.messages.last?.id else { return }
                withAnimation(.easeOut(duration: 0.2)) {
                    proxy.scrollTo(lastId, anchor: .bottom)
                }
            }
            .onAppear {
                if let lastId = viewModel.messages.last?.id {
                    proxy.scrollTo(lastId, anchor: .bottom)
                }
            }
            .alert(
                "Session expired",
                isPresented: Binding(
                    get: {
                        if case .failed(.invalidSession) = viewModel.state {
                            return true
                        }
                        return false
                    },
                    set: { _ in }
                )
            ) {
                Button("OK") {
                    session.invalidateSession()
                }
            } message: {
                Text("Please log in again.")
            }
            .confirmationDialog(
                viewModel.isCounterpartBlockedByMe ? "Unblock this user?" : "Block this user?",
                isPresented: $showBlockConfirmation,
                titleVisibility: .visible
            ) {
                if viewModel.isCounterpartBlockedByMe {
                    Button("Unblock User") {
                        Task { await viewModel.unblockCounterpart() }
                    }
                } else {
                    Button("Block User", role: .destructive) {
                        Task { await viewModel.blockCounterpart() }
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text(
                    viewModel.isCounterpartBlockedByMe
                        ? "Sending will be enabled again in this room."
                        : "You will still be able to view this room, but sending will be disabled."
                )
            }
            .sheet(isPresented: $showReportSheet) {
                ReportConversationSheet(
                    counterpartName: viewModel.roomContext.subtitle,
                    onClose: {
                        showReportSheet = false
                    },
                    onSubmit: { reason, detail in
                        showReportSheet = false
                        Task { await viewModel.reportCounterpart(reason: reason, detail: detail) }
                    }
                )
            }
        }
    }

    private var groupedMessages: [MessageTimelineItem] {
        var items: [MessageTimelineItem] = []
        var previousDayKey: String?

        for (index, message) in viewModel.messages.enumerated() {
            let dayKey = MessagesTimestampFormatter.dayKey(from: message.createdAt)
            if dayKey != previousDayKey {
                items.append(.day(MessagesTimestampFormatter.dayLabel(from: message.createdAt)))
                previousDayKey = dayKey
            }

            let nextMessage = viewModel.messages.indices.contains(index + 1)
                ? viewModel.messages[index + 1]
                : nil
            let currentMinuteKey = MessagesTimestampFormatter.minuteKey(from: message.createdAt)
            let nextMinuteKey = nextMessage.map { MessagesTimestampFormatter.minuteKey(from: $0.createdAt) }
            let showTimestamp = currentMinuteKey != nextMinuteKey

            items.append(.message(message, showTimestamp: showTimestamp))
        }

        return items
    }

    private var roomComposer: some View {
        VStack(spacing: 8) {
            if let actionMessage = viewModel.actionMessage {
                MessagesInlineBanner(message: actionMessage) {
                    viewModel.actionMessage = nil
                }
                .padding(.horizontal, 16)
            }

            HStack(spacing: 14) {
                Image(systemName: "plus")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Color(red: 0.60, green: 0.66, blue: 0.75))

                TextField("Write a message...", text: $viewModel.composerText, axis: .vertical)
                    .font(AppFont.medium(17, relativeTo: .body))
                    .lineLimit(1...4)
                    .disabled(viewModel.isCounterpartBlockedByMe)

                Button {
                    Task { await viewModel.sendMessage() }
                } label: {
                    Image(systemName: "paperplane.fill")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 40, height: 40)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(
                                    viewModel.composerText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                    ? Color(red: 0.82, green: 0.85, blue: 0.91)
                                    : AuthPalette.primaryStart
                                )
                        )
                }
                .buttonStyle(.plain)
                .disabled(
                    viewModel.composerText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        || viewModel.isSending
                        || viewModel.isCounterpartBlockedByMe
                )
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 13)
            .background(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(Color.white)
                    .shadow(color: Color.black.opacity(0.06), radius: 14, y: 2)
            )
            .padding(.horizontal, 14)
            .padding(.bottom, 8)
        }
        .background(.ultraThinMaterial)
    }
}

private struct ReportConversationSheet: View {
    let counterpartName: String
    let onClose: () -> Void
    let onSubmit: (String, String?) -> Void

    @State private var reason = ""
    @State private var detail = ""

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 20) {
                Text("Report \(counterpartName)")
                    .font(AppFont.extraBold(26, relativeTo: .title2))
                    .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                TextField("Reason", text: $reason)
                    .textInputAutocapitalization(.sentences)
                    .font(AppFont.medium(17, relativeTo: .body))
                    .padding(.horizontal, 16)
                    .frame(height: 52)
                    .background(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(Color.white)
                    )

                TextEditor(text: $detail)
                    .font(AppFont.medium(16, relativeTo: .body))
                    .frame(minHeight: 140)
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .fill(Color.white)
                    )

                Spacer()

                PrimaryActionButton(
                    title: "Submit Report",
                    isLoading: false,
                    isDisabled: reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                    height: 56,
                    cornerRadius: 18
                ) {
                    onSubmit(
                        reason.trimmingCharacters(in: .whitespacesAndNewlines),
                        detail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : detail.trimmingCharacters(in: .whitespacesAndNewlines)
                    )
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 24)
            .background(MessagesBackgroundView())
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close", action: onClose)
                        .font(AppFont.medium(16, relativeTo: .body))
                        .foregroundStyle(Color(red: 0.46, green: 0.54, blue: 0.66))
                }
            }
        }
    }
}

private enum MessageTimelineItem: Identifiable {
    case day(String)
    case message(MessageItem, showTimestamp: Bool)

    var id: String {
        switch self {
        case let .day(label):
            return "day-\(label)"
        case let .message(message, _):
            return message.id
        }
    }
}

private struct MessageBubbleRow: View {
    let message: MessageItem
    let isMine: Bool
    let showTimestamp: Bool

    var body: some View {
        VStack(alignment: isMine ? .trailing : .leading, spacing: 6) {
            HStack {
                if isMine { Spacer(minLength: 54) }

                Text(message.content)
                    .font(AppFont.medium(17, relativeTo: .body))
                    .foregroundStyle(isMine ? Color.white : Color(red: 0.13, green: 0.16, blue: 0.24))
                    .lineSpacing(3)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .background(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .fill(isMine ? AuthPalette.primaryStart : Color.white)
                            .shadow(color: Color.black.opacity(0.05), radius: 14, y: 2)
                    )
                    .opacity(message.isPending ? 0.75 : 1)

                if isMine == false { Spacer(minLength: 54) }
            }

            if showTimestamp {
                Text(message.timeText)
                    .font(AppFont.medium(12, relativeTo: .caption))
                    .foregroundStyle(Color(red: 0.62, green: 0.67, blue: 0.76))
                    .padding(.horizontal, 6)
            }
        }
        .padding(.bottom, showTimestamp ? 12 : 4)
    }
}

// MARK: - Helpers

enum MessagesTimestampFormatter {
    static func inboxString(from raw: String?) -> String {
        guard let raw,
              let date = RelativeTimestampFormatter.date(from: raw) else {
            return ""
        }

        let calendar = Calendar.current
        if calendar.isDateInToday(date) {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = .current
            formatter.dateFormat = "h:mm a"
            return formatter.string(from: date)
        }
        if calendar.isDateInYesterday(date) {
            return "Yesterday"
        }

        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "MMM d"
        return formatter.string(from: date)
    }

    static func messageString(from raw: String) -> String {
        guard let date = RelativeTimestampFormatter.date(from: raw) else {
            return ""
        }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "h:mm a"
        return formatter.string(from: date)
    }

    static func dayLabel(from raw: String) -> String {
        guard let date = RelativeTimestampFormatter.date(from: raw) else {
            return "Today"
        }
        let calendar = Calendar.current
        if calendar.isDateInToday(date) {
            return "Today"
        }
        if calendar.isDateInYesterday(date) {
            return "Yesterday"
        }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "MMM d"
        return formatter.string(from: date)
    }

    static func dayKey(from raw: String) -> String {
        guard let date = RelativeTimestampFormatter.date(from: raw) else {
            return "today"
        }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    static func minuteKey(from raw: String) -> String {
        guard let date = RelativeTimestampFormatter.date(from: raw) else {
            return raw
        }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd-HH-mm"
        return formatter.string(from: date)
    }
}

// MARK: - Preview Support

struct PreviewMessagesClient: MessagesClientProtocol {
    enum Scenario {
        case loaded
        case empty
    }

    let scenario: Scenario

    static func loaded() -> PreviewMessagesClient {
        PreviewMessagesClient(scenario: .loaded)
    }

    func fetchViewer() async throws -> MessagesViewer {
        MessagesViewer(memberId: 1, displayName: "Kenta")
    }

    func fetchRooms(limit: Int) async throws -> [MessageRoomSummary] {
        switch scenario {
        case .empty:
            return []
        case .loaded:
            return PreviewMessagesData.rooms
        }
    }

    func fetchBlockedMemberIds() async throws -> Set<Int64> {
        []
    }

    func createRoom(postId: Int64, requesterIsAnonymous: Bool, partnerMemberId: Int64?) async throws -> MessageRoomCreated {
        MessageRoomCreated(
            roomId: 101,
            postId: postId,
            member1Id: 1,
            member2Id: partnerMemberId ?? 22,
            roomTitle: "게시글 \(postId)",
            counterpartDisplayName: requesterIsAnonymous ? "익명 1" : "Kenta",
            isAnonMe: requesterIsAnonymous,
            isAnonOther: true,
            created: true
        )
    }

    func fetchMessages(roomId: Int64, beforeMessageId: Int64?, limit: Int) async throws -> MessagesPage {
        MessagesPage(
            roomId: roomId,
            messages: PreviewMessagesData.messages,
            myLastReadMessageId: 3,
            otherLastReadMessageId: 2,
            nextBeforeMessageId: nil
        )
    }

    func sendMessage(roomId: Int64, content: String) async throws -> MessageItem {
        MessageItem(
            id: "server-999",
            serverId: 999,
            roomId: roomId,
            senderId: 1,
            content: content,
            createdAt: ISO8601DateFormatter().string(from: Date()),
            isPending: false
        )
    }

    func markRead(roomId: Int64, lastReadMessageId: Int64) async throws -> Int {
        0
    }

    func blockMember(targetMemberId: Int64) async throws {}

    func unblockMember(targetMemberId: Int64) async throws {}

    func reportMember(targetMemberId: Int64, roomId: Int64, reason: String, detail: String?) async throws {}
}

actor PreviewMessagesRealtimeClient: MessagesRealtimeClientProtocol {
    func activate() async {}
    func deactivate() async {}
    func ensureUnreadSubscription() async {}
    func subscribeToRoom(_ roomId: Int64) async {}
    func unsubscribeFromRoom(_ roomId: Int64) async {}
}

enum PreviewMessagesData {
    static let rooms: [MessageRoomSummary] = [
        MessageRoomSummary(
            roomId: 101,
            postId: 1,
            postTitle: "Anyone want to study together for the midterms?",
            roomTitle: "Anyone want to study together for the midterms?",
            otherMemberId: 22,
            counterpartDisplayName: "익명 1",
            lastMessageId: 3,
            lastMessageContent: "Does 3 PM work for you?",
            lastMessageCreatedAt: "2026-03-28T13:48:00",
            unreadCount: 2,
            myLastReadMessageId: 1,
            otherLastReadMessageId: 2,
            isAnonMe: false,
            isAnonOther: true
        ),
        MessageRoomSummary(
            roomId: 102,
            postId: 4,
            postTitle: "Looking for notes from economics class",
            roomTitle: "Looking for notes from economics class",
            otherMemberId: 23,
            counterpartDisplayName: "user-23",
            lastMessageId: 8,
            lastMessageContent: "I sent you the notes from today's lecture.",
            lastMessageCreatedAt: "2026-03-28T11:30:00",
            unreadCount: 0,
            myLastReadMessageId: 8,
            otherLastReadMessageId: 8,
            isAnonMe: false,
            isAnonOther: false
        )
    ]

    static let messages: [MessageItem] = [
        MessageItem(id: "server-1", serverId: 1, roomId: 101, senderId: 22, content: "Hey! Are you still looking for a study group for Calculus? I'm really struggling with vector fields.", createdAt: "2026-03-28T13:45:00", isPending: false),
        MessageItem(id: "server-2", serverId: 2, roomId: 101, senderId: 1, content: "Yes, absolutely! I saw your comment on the board. I have some practice exams we could go through together.", createdAt: "2026-03-28T13:46:00", isPending: false),
        MessageItem(id: "server-3", serverId: 3, roomId: 101, senderId: 22, content: "That sounds perfect. Friday afternoon works for me. Does 3 PM work for you?", createdAt: "2026-03-28T13:48:00", isPending: false)
    ]
}

struct MessagesRootView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            MessagesRootView(
                session: PreviewFactory.makeSession(state: .authenticated),
                client: PreviewMessagesClient.loaded(),
                realtimeClient: PreviewMessagesRealtimeClient(),
                isActive: true
            )
            .previewDisplayName("Messages Inbox")

            MessagesRootView(
                session: PreviewFactory.makeSession(state: .authenticated),
                client: PreviewMessagesClient(scenario: .empty),
                realtimeClient: PreviewMessagesRealtimeClient(),
                isActive: true
            )
            .previewDisplayName("Messages Inbox - Empty")

            NavigationStack {
                ChatRoomView(
                    session: PreviewFactory.makeSession(state: .authenticated),
                    context: PreviewMessagesData.rooms[0].asRoomContext,
                    client: PreviewMessagesClient.loaded(),
                    realtimeClient: PreviewMessagesRealtimeClient(),
                    viewerId: 1
                )
            }
            .previewDisplayName("Messages Room")

            MessagesRootView(
                session: PreviewFactory.makeSession(state: .authenticated),
                client: PreviewMessagesClient.loaded(),
                realtimeClient: PreviewMessagesRealtimeClient(),
                isActive: true
            )
            .preferredColorScheme(.dark)
            .previewDisplayName("Messages Inbox - Dark")
        }
    }
}

// MARK: - Shared Messages UI

private struct MessagesInlineBanner: View {
    let message: String
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "exclamationmark.circle.fill")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color(red: 0.92, green: 0.43, blue: 0.19))

            Text(message)
                .font(AppFont.medium(14, relativeTo: .subheadline))
                .foregroundStyle(Color(red: 0.29, green: 0.34, blue: 0.43))
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 8)

            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color(red: 0.57, green: 0.62, blue: 0.71))
                    .frame(width: 28, height: 28)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(red: 1.0, green: 0.97, blue: 0.93))
        )
    }
}

private struct MessagesFailureCard: View {
    let title: String
    let message: String
    let buttonTitle: String
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title)
                .font(AppFont.bold(22, relativeTo: .title3))
                .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

            Text(message)
                .font(AppFont.medium(16, relativeTo: .body))
                .foregroundStyle(Color(red: 0.44, green: 0.52, blue: 0.64))

            PrimaryActionButton(
                title: buttonTitle,
                isLoading: false,
                isDisabled: false,
                height: 58,
                cornerRadius: 18,
                action: action
            )
        }
        .padding(22)
        .background(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(Color.white)
                .shadow(color: Color.black.opacity(0.05), radius: 14, y: 5)
        )
    }
}

private struct MessagesEmptyCard: View {
    let title: String
    let message: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(AppFont.bold(20, relativeTo: .title3))
                .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

            Text(message)
                .font(AppFont.medium(16, relativeTo: .body))
                .foregroundStyle(Color(red: 0.46, green: 0.54, blue: 0.66))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Color.white)
                .shadow(color: Color.black.opacity(0.04), radius: 10, y: 4)
        )
    }
}

private struct MessagesBackgroundView: View {
    var body: some View {
        LinearGradient(
            colors: [
                Color.white,
                Color(red: 0.97, green: 0.98, blue: 1.0)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}
