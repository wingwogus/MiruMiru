import Foundation

final class BoardsAPIClient: BoardsClientProtocol, @unchecked Sendable {
    private let apiClient: APIClient
    private let tokenStore: TokenStore
    private let encoder = JSONEncoder()

    init(apiClient: APIClient, tokenStore: TokenStore) {
        self.apiClient = apiClient
        self.tokenStore = tokenStore
    }

    func fetchBoards() async throws -> [BoardSummary] {
        let payload: [BoardResponse] = try await requestPayload(path: "/api/v1/boards/me")
        return payload.map(\.toDomain)
    }

    func fetchHotPosts() async throws -> [HotPostSummary] {
        let payload: [HotPostResponse] = try await requestPayload(path: "/api/v1/posts/hot")
        return payload.map(\.toDomain)
    }

    func fetchBoardPosts(boardId: Int64) async throws -> [BoardPostSummary] {
        let payload: [BoardPostResponse] = try await requestPayload(path: "/api/v1/boards/\(boardId)/posts")
        return payload.map(\.toDomain)
    }

    func fetchPostDetail(postId: Int64) async throws -> PostDetailContent {
        let payload: PostDetailResponse = try await requestPayload(path: "/api/v1/posts/\(postId)")
        return payload.toDomain()
    }

    func createPost(boardId: Int64, input: CreatePostInput) async throws -> Int64 {
        let request = CreatePostRequest(
            title: input.title,
            content: input.content,
            isAnonymous: input.isAnonymous,
            images: []
        )
        let body = try encoder.encode(request)
        let payload: CreatePostResponse = try await requestPayload(
            path: "/api/v1/boards/\(boardId)/posts",
            method: .post,
            body: body
        )
        return payload.postId
    }

    func likePost(postId: Int64) async throws {
        try await sendEmpty(path: "/api/v1/posts/\(postId)/likes", method: .post)
    }

    func unlikePost(postId: Int64) async throws {
        try await sendEmpty(path: "/api/v1/posts/\(postId)/likes", method: .delete)
    }

    func createComment(postId: Int64, input: CreateCommentInput) async throws -> Int64 {
        let request = CreateCommentRequest(
            content: input.content,
            parentId: input.parentId,
            isAnonymous: input.isAnonymous
        )
        let body = try encoder.encode(request)
        let payload: CreateCommentResponse = try await requestPayload(
            path: "/api/v1/posts/\(postId)/comments",
            method: .post,
            body: body
        )
        return payload.commentId
    }

    func deleteComment(commentId: Int64) async throws {
        try await sendEmpty(path: "/api/v1/comments/\(commentId)", method: .delete)
    }

    func deletePost(postId: Int64) async throws {
        try await sendEmpty(path: "/api/v1/posts/\(postId)", method: .delete)
    }

    private func requestPayload<Response: Decodable>(
        path: String,
        method: HTTPMethod = .get,
        body: Data? = nil
    ) async throws -> Response {
        let accessToken = try readAccessToken()

        do {
            let (data, _) = try await apiClient.send(
                path: path,
                method: method,
                body: body,
                accessToken: accessToken
            )
            let envelope = try apiClient.decode(APIResponseEnvelope<Response>.self, from: data)
            guard envelope.success, let payload = envelope.data else {
                throw BoardsClientError.unexpected
            }
            return payload
        } catch let error as APIClientError {
            throw map(apiError: error)
        } catch let error as BoardsClientError {
            throw error
        } catch {
            throw BoardsClientError.unexpected
        }
    }

    private func sendEmpty(path: String, method: HTTPMethod) async throws {
        let accessToken = try readAccessToken()

        do {
            let (data, _) = try await apiClient.send(
                path: path,
                method: method,
                accessToken: accessToken
            )
            let envelope = try apiClient.decode(APIResponseEnvelope<EmptyPayload>.self, from: data)
            guard envelope.success else {
                throw BoardsClientError.unexpected
            }
        } catch let error as APIClientError {
            throw map(apiError: error)
        } catch let error as BoardsClientError {
            throw error
        } catch {
            throw BoardsClientError.unexpected
        }
    }

    private func readAccessToken() throws -> String {
        do {
            guard let session = try tokenStore.readSession(),
                  session.accessToken.isEmpty == false else {
                throw BoardsClientError.invalidSession
            }
            return session.accessToken
        } catch let error as BoardsClientError {
            throw error
        } catch {
            throw BoardsClientError.invalidSession
        }
    }

    private func map(apiError: APIClientError) -> BoardsClientError {
        switch apiError {
        case .transport:
            return .network
        case let .server(statusCode, payload):
            if statusCode == 401 {
                return .invalidSession
            }
            if statusCode == 403 {
                return .forbidden
            }

            switch payload?.code {
            case "BOARD_001":
                return .boardNotFound
            case "POST_001":
                return .postNotFound
            case "POST_002":
                return .anonymousNotAllowed
            case "POST_005":
                return .deletedPost
            case "COMMENT_001":
                return .commentNotFound
            case "COMMENT_002":
                return .replyDepthNotAllowed
            case "COMMENT_003":
                return .invalidCommentParent
            default:
                return .unexpected
            }
        default:
            return .unexpected
        }
    }
}

private extension BoardsAPIClient {
    struct BoardResponse: Decodable {
        let boardId: Int64
        let code: String
        let name: String
        let isAnonymousAllowed: Bool

        var toDomain: BoardSummary {
            BoardSummary(
                id: boardId,
                code: code,
                name: name,
                isAnonymousAllowed: isAnonymousAllowed
            )
        }
    }

    struct HotPostResponse: Decodable {
        let postId: Int64
        let boardId: Int64
        let boardCode: String
        let boardName: String
        let title: String
        let authorDisplayName: String
        let isAnonymous: Bool
        let likeCount: Int
        let commentCount: Int
        let createdAt: String

        var toDomain: HotPostSummary {
            HotPostSummary(
                id: postId,
                boardId: boardId,
                boardCode: boardCode,
                boardName: boardName,
                title: title,
                authorDisplayName: authorDisplayName,
                isAnonymous: isAnonymous,
                likeCount: likeCount,
                commentCount: commentCount,
                createdAt: createdAt
            )
        }
    }

    struct BoardPostResponse: Decodable {
        let postId: Int64
        let title: String
        let authorDisplayName: String
        let isAnonymous: Bool
        let likeCount: Int
        let commentCount: Int
        let createdAt: String

        var toDomain: BoardPostSummary {
            BoardPostSummary(
                id: postId,
                title: title,
                authorDisplayName: authorDisplayName,
                isAnonymous: isAnonymous,
                likeCount: likeCount,
                commentCount: commentCount,
                createdAt: createdAt
            )
        }
    }

    struct PostImageResponse: Decodable {
        let imageUrl: String
        let displayOrder: Int

        var toDomain: PostImageSummary {
            PostImageSummary(imageUrl: imageUrl, displayOrder: displayOrder)
        }
    }

    struct CommentResponse: Decodable {
        let commentId: Int64
        let parentId: Int64?
        let content: String
        let authorDisplayName: String
        let isAnonymous: Bool
        let isMine: Bool
        let isDeleted: Bool
        let createdAt: String
        let children: [CommentResponse]

        func toDomain() -> PostCommentItem {
            PostCommentItem(
                commentId: commentId,
                parentId: parentId,
                content: content,
                authorDisplayName: authorDisplayName,
                isAnonymous: isAnonymous,
                isMine: isMine,
                isDeleted: isDeleted,
                createdAt: createdAt,
                children: children.map { $0.toDomain() }
            )
        }
    }

    struct PostDetailResponse: Decodable {
        let postId: Int64
        let boardId: Int64
        let boardCode: String
        let boardName: String
        let title: String
        let content: String
        let authorDisplayName: String
        let isAnonymous: Bool
        let isMine: Bool
        let isLikedByMe: Bool
        let likeCount: Int
        let commentCount: Int
        let comments: [CommentResponse]
        let images: [PostImageResponse]
        let createdAt: String
        let updatedAt: String

        func toDomain() -> PostDetailContent {
            PostDetailContent(
                postId: postId,
                boardId: boardId,
                boardCode: boardCode,
                boardName: boardName,
                title: title,
                content: content,
                authorDisplayName: authorDisplayName,
                isAnonymous: isAnonymous,
                isMine: isMine,
                isLikedByMe: isLikedByMe,
                likeCount: likeCount,
                commentCount: commentCount,
                comments: comments.map { $0.toDomain() },
                images: images.map(\.toDomain),
                createdAt: createdAt,
                updatedAt: updatedAt
            )
        }
    }

    struct CreatePostRequest: Encodable {
        let title: String
        let content: String
        let isAnonymous: Bool
        let images: [CreatePostImageRequest]
    }

    struct CreatePostImageRequest: Encodable {
        let imageUrl: String
        let displayOrder: Int
    }

    struct CreatePostResponse: Decodable {
        let postId: Int64
    }

    struct CreateCommentRequest: Encodable {
        let content: String
        let parentId: Int64?
        let isAnonymous: Bool
    }

    struct CreateCommentResponse: Decodable {
        let commentId: Int64
    }
}

