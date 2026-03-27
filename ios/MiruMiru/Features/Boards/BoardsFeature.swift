import SwiftUI

private enum BoardsRoute: Hashable {
    case board(BoardSummary)
    case post(Int64)
}

struct BoardsRootView: View {
    @ObservedObject private var session: AppSession
    @ObservedObject private var syncStore: BoardsSyncStore
    @StateObject private var viewModel: BoardsViewModel
    @Binding private var isTabBarHidden: Bool
    @Binding private var pendingPostId: Int64?
    private let client: BoardsClientProtocol
    private let isActive: Bool

    @State private var path: [BoardsRoute] = []

    init(
        session: AppSession,
        client: BoardsClientProtocol,
        syncStore: BoardsSyncStore,
        isTabBarHidden: Binding<Bool> = .constant(false),
        pendingPostId: Binding<Int64?> = .constant(nil),
        isActive: Bool = true
    ) {
        self.session = session
        self.syncStore = syncStore
        self.client = client
        self._isTabBarHidden = isTabBarHidden
        self._pendingPostId = pendingPostId
        self.isActive = isActive
        _viewModel = StateObject(wrappedValue: BoardsViewModel(client: client))
    }

    var body: some View {
        NavigationStack(path: $path) {
            BoardsHomeView(
                viewModel: viewModel,
                syncStore: syncStore,
                onBoardTap: { board in
                    path.append(.board(board))
                },
                onHotPostTap: { postId in
                    path.append(.post(postId))
                }
            )
            .navigationDestination(for: BoardsRoute.self) { route in
                switch route {
                case let .board(board):
                    BoardFeedView(
                        session: session,
                        client: client,
                        syncStore: syncStore,
                        board: board,
                        onPostTap: { postId in
                            path.append(.post(postId))
                        }
                    )
                case let .post(postId):
                    PostDetailView(
                        session: session,
                        client: client,
                        syncStore: syncStore,
                        postId: postId
                    )
                }
            }
        }
        .background(BoardsBackgroundView())
        .task(id: isActive) {
            guard isActive else { return }
            await viewModel.loadIfNeeded()
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
        .onAppear {
            navigateToPendingPostIfNeeded()
        }
        .onChange(of: pendingPostId) { _, _ in
            navigateToPendingPostIfNeeded()
        }
        .onChange(of: viewModel.invalidateStateIfNeeded()) { _, shouldInvalidate in
            guard shouldInvalidate else { return }
            session.invalidateSession()
        }
        .onChange(of: viewModel.hotPostsSnapshotForSync()) { _, hotPosts in
            if let hotPosts {
                syncStore.ingestHotPosts(hotPosts)
            }
        }
    }

    private func navigateToPendingPostIfNeeded() {
        guard let postId = pendingPostId else { return }
        path = [.post(postId)]
        pendingPostId = nil
    }
}

@MainActor
final class BoardsViewModel: ObservableObject {
    @Published private(set) var state: BoardsHomeState = .loading

    private let client: BoardsClientProtocol
    private var hasLoaded = false
    private var boards: [BoardSummary] = []
    private var hotPosts: [HotPostSummary] = []

    init(client: BoardsClientProtocol) {
        self.client = client
    }

    func loadIfNeeded() async {
        guard hasLoaded == false else { return }
        hasLoaded = true
        await load()
    }

    func reload() async {
        await load()
    }

    func filteredSections(query: String) -> [BoardSection] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let filteredBoards: [BoardSummary]

        if trimmed.isEmpty {
            filteredBoards = boards
        } else {
            filteredBoards = boards.filter {
                $0.name.localizedCaseInsensitiveContains(trimmed)
                    || $0.code.localizedCaseInsensitiveContains(trimmed)
            }
        }

        return BoardGroupingRule.sections(for: filteredBoards)
    }

    func filteredHotPosts(query: String) -> [HotPostSummary] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.isEmpty == false else { return hotPosts }

        return hotPosts.filter {
            $0.title.localizedCaseInsensitiveContains(trimmed)
                || $0.boardName.localizedCaseInsensitiveContains(trimmed)
        }
    }

    func invalidateStateIfNeeded() -> Bool {
        if case .failed(.invalidSession) = state {
            return true
        }
        return false
    }

    func hotPostsSnapshotForSync() -> [HotPostSummary]? {
        switch state {
        case let .loaded(content):
            return content.hotPosts
        case .empty:
            return []
        default:
            return nil
        }
    }

    func displayHotPosts(using syncStore: BoardsSyncStore, query: String) -> [HotPostSummary] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let projectedHotPosts = syncStore.projectHotPosts(hotPosts)
        guard trimmed.isEmpty == false else { return projectedHotPosts }

        return projectedHotPosts.filter {
            $0.title.localizedCaseInsensitiveContains(trimmed)
                || $0.boardName.localizedCaseInsensitiveContains(trimmed)
        }
    }

    private func load() async {
        state = .loading

        do {
            async let boardsTask = client.fetchBoards()
            async let hotPostsTask = client.fetchHotPosts()
            let (boards, hotPosts) = try await (boardsTask, hotPostsTask)

            self.boards = boards
            self.hotPosts = hotPosts

            let sections = BoardGroupingRule.sections(for: boards)
            if sections.isEmpty, hotPosts.isEmpty {
                state = .empty
            } else {
                state = .loaded(
                    BoardsHomeContent(
                        sections: sections,
                        hotPosts: hotPosts
                    )
                )
            }
        } catch let error as BoardsClientError {
            state = .failed(Self.map(error))
        } catch {
            state = .failed(.unexpected)
        }
    }

    private static func map(_ error: BoardsClientError) -> BoardsFailure {
        switch error {
        case .invalidSession: .invalidSession
        case .forbidden: .forbidden
        case .boardNotFound: .boardNotFound
        case .postNotFound: .postNotFound
        case .anonymousNotAllowed: .anonymousNotAllowed
        case .deletedPost: .deletedPost
        case .commentNotFound: .commentNotFound
        case .replyDepthNotAllowed: .replyDepthNotAllowed
        case .invalidCommentParent: .invalidCommentParent
        case .network: .network
        case .unexpected: .unexpected
        }
    }
}

private struct BoardsHomeView: View {
    @ObservedObject var viewModel: BoardsViewModel
    @ObservedObject var syncStore: BoardsSyncStore
    let onBoardTap: (BoardSummary) -> Void
    let onHotPostTap: (Int64) -> Void

    @State private var query = ""

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 24) {
                boardsHeader

                BoardsSearchField(text: $query)

                switch viewModel.state {
                case .loading:
                    BoardsLoadingView()
                case .empty:
                    BoardsEmptyView(
                        title: "No boards yet",
                        message: "Boards will appear here once your university opens them."
                    )
                case let .failed(failure):
                    BoardsFailureCard(
                        title: "We couldn't load Boards",
                        message: failure.message,
                        buttonTitle: "Try Again",
                        action: {
                            Task { await viewModel.reload() }
                        }
                    )
                case .loaded:
                    loadedContent
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 18)
            .padding(.bottom, 28)
        }
        .background(BoardsBackgroundView())
        .navigationBarHidden(true)
    }

    @ViewBuilder
    private var loadedContent: some View {
        let hotPosts = viewModel.displayHotPosts(using: syncStore, query: query)
        let sections = viewModel.filteredSections(query: query)

        if hotPosts.isEmpty == false {
            TrendingPostsSection(
                posts: hotPosts,
                onTap: onHotPostTap
            )
        }

        if sections.isEmpty, hotPosts.isEmpty {
            BoardsEmptyView(
                title: "No results",
                message: "Try a different keyword for boards or trending posts."
            )
        } else {
            ForEach(sections) { section in
                BoardsSectionCard(
                    section: section,
                    onTap: onBoardTap
                )
            }
        }
    }

    private var boardsHeader: some View {
        HStack(spacing: 12) {
            Text("Boards")
                .font(AppFont.extraBold(30, relativeTo: .largeTitle))
                .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

            Spacer()

            Image(systemName: "bell.fill")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color(red: 0.56, green: 0.64, blue: 0.75))
                .frame(width: 38, height: 38)

            Image(systemName: "magnifyingglass")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color(red: 0.56, green: 0.64, blue: 0.75))
                .frame(width: 38, height: 38)
        }
    }
}

@MainActor
final class BoardFeedViewModel: ObservableObject {
    @Published private(set) var state: BoardFeedState = .loading

    let board: BoardSummary
    private let client: BoardsClientProtocol
    private var hasLoaded = false

    init(client: BoardsClientProtocol, board: BoardSummary) {
        self.client = client
        self.board = board
    }

    func loadIfNeeded() async {
        guard hasLoaded == false else { return }
        hasLoaded = true
        await load()
    }

    func reload() async {
        await load()
    }

    func invalidateStateIfNeeded() -> Bool {
        if case .failed(.invalidSession) = state {
            return true
        }
        return false
    }

    private func load() async {
        state = .loading

        do {
            let posts = try await client.fetchBoardPosts(boardId: board.id)
            state = posts.isEmpty ? .empty : .loaded(posts)
        } catch let error as BoardsClientError {
            state = .failed(Self.map(error))
        } catch {
            state = .failed(.unexpected)
        }
    }

    private static func map(_ error: BoardsClientError) -> BoardsFailure {
        switch error {
        case .invalidSession: .invalidSession
        case .forbidden: .forbidden
        case .boardNotFound: .boardNotFound
        case .postNotFound: .postNotFound
        case .anonymousNotAllowed: .anonymousNotAllowed
        case .deletedPost: .deletedPost
        case .commentNotFound: .commentNotFound
        case .replyDepthNotAllowed: .replyDepthNotAllowed
        case .invalidCommentParent: .invalidCommentParent
        case .network: .network
        case .unexpected: .unexpected
        }
    }
}

private struct BoardFeedView: View {
    @ObservedObject private var session: AppSession
    @ObservedObject private var syncStore: BoardsSyncStore
    @StateObject private var viewModel: BoardFeedViewModel
    private let client: BoardsClientProtocol
    let onPostTap: (Int64) -> Void

    @State private var isWritePresented = false

    init(
        session: AppSession,
        client: BoardsClientProtocol,
        syncStore: BoardsSyncStore,
        board: BoardSummary,
        onPostTap: @escaping (Int64) -> Void
    ) {
        self.session = session
        self.syncStore = syncStore
        self.client = client
        self.onPostTap = onPostTap
        _viewModel = StateObject(wrappedValue: BoardFeedViewModel(client: client, board: board))
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                switch viewModel.state {
                case .loading:
                    ProgressView("Loading posts...")
                        .font(AppFont.medium(15, relativeTo: .subheadline))
                        .tint(AuthPalette.primaryStart)
                        .padding(.top, 80)
                        .frame(maxWidth: .infinity, alignment: .center)
                case .empty:
                    BoardsEmptyView(
                        title: "No posts yet",
                        message: "Be the first to start a conversation in \(viewModel.board.name)."
                    )
                    .padding(.top, 40)
                case let .failed(failure):
                    BoardsFailureCard(
                        title: "We couldn't load this board",
                        message: failure.message,
                        buttonTitle: "Try Again",
                        action: {
                            Task { await viewModel.reload() }
                        }
                    )
                    .padding(.top, 24)
                case let .loaded(posts):
                    ForEach(syncStore.projectBoardPosts(posts)) { post in
                        Button {
                            onPostTap(post.id)
                        } label: {
                            BoardPostCard(post: post)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 120)
        }
        .background(BoardsBackgroundView())
        .navigationTitle(viewModel.board.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    isWritePresented = true
                } label: {
                    Image(systemName: "square.and.pencil")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(AuthPalette.primaryStart)
                }
            }
        }
        .task {
            await viewModel.loadIfNeeded()
        }
        .onChange(of: viewModel.invalidateStateIfNeeded()) { _, shouldInvalidate in
            guard shouldInvalidate else { return }
            session.invalidateSession()
        }
        .fullScreenCover(isPresented: $isWritePresented) {
            WritePostView(
                session: session,
                client: client,
                board: viewModel.board,
                onClose: {
                    isWritePresented = false
                },
                onCreated: {
                    isWritePresented = false
                    Task { await viewModel.reload() }
                }
            )
        }
    }
}

@MainActor
final class PostDetailViewModel: ObservableObject {
    @Published private(set) var state: PostDetailState = .loading
    @Published private(set) var isSubmitting = false
    @Published var actionMessage: String?
    @Published var didDeletePost = false

    private let client: BoardsClientProtocol
    private let syncStore: BoardsSyncStore
    private let postId: Int64
    private var hasLoaded = false

    init(client: BoardsClientProtocol, syncStore: BoardsSyncStore, postId: Int64) {
        self.client = client
        self.syncStore = syncStore
        self.postId = postId
    }

    func loadIfNeeded() async {
        guard hasLoaded == false else { return }
        hasLoaded = true
        await load()
    }

    func reload() async {
        await load()
    }

    func toggleLike() async {
        guard case let .loaded(detail) = state, isSubmitting == false else { return }
        isSubmitting = true
        actionMessage = nil
        let updatedIsLiked = detail.isLikedByMe == false
        let updatedLikeCount = max(0, detail.likeCount + (updatedIsLiked ? 1 : -1))
        state = .loaded(
            PostDetailContent(
                postId: detail.postId,
                boardId: detail.boardId,
                boardCode: detail.boardCode,
                boardName: detail.boardName,
                title: detail.title,
                content: detail.content,
                authorDisplayName: detail.authorDisplayName,
                isAnonymous: detail.isAnonymous,
                isMine: detail.isMine,
                isLikedByMe: updatedIsLiked,
                likeCount: updatedLikeCount,
                commentCount: detail.commentCount,
                comments: detail.comments,
                images: detail.images,
                createdAt: detail.createdAt,
                updatedAt: detail.updatedAt
            )
        )
        syncStore.setLikeCount(postId: detail.postId, likeCount: updatedLikeCount)

        do {
            if detail.isLikedByMe {
                try await client.unlikePost(postId: detail.postId)
            } else {
                try await client.likePost(postId: detail.postId)
            }
            Task {
                await syncStore.refreshHotPosts(using: client)
            }
        } catch let error as BoardsClientError {
            state = .loaded(detail)
            syncStore.setLikeCount(postId: detail.postId, likeCount: detail.likeCount)
            handleActionError(error)
        } catch {
            state = .loaded(detail)
            syncStore.setLikeCount(postId: detail.postId, likeCount: detail.likeCount)
            actionMessage = BoardsFailure.unexpected.message
        }

        isSubmitting = false
    }

    func submitComment(content: String, parentId: Int64?, isAnonymous: Bool) async {
        let trimmed = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.isEmpty == false, isSubmitting == false else { return }
        isSubmitting = true
        actionMessage = nil

        do {
            _ = try await client.createComment(
                postId: postId,
                input: CreateCommentInput(
                    content: trimmed,
                    parentId: parentId,
                    isAnonymous: isAnonymous
                )
            )
            await load()
        } catch let error as BoardsClientError {
            handleActionError(error)
        } catch {
            actionMessage = BoardsFailure.unexpected.message
        }

        isSubmitting = false
    }

    func deleteComment(_ commentId: Int64) async {
        guard isSubmitting == false else { return }
        isSubmitting = true
        actionMessage = nil

        do {
            try await client.deleteComment(commentId: commentId)
            await load()
        } catch let error as BoardsClientError {
            handleActionError(error)
        } catch {
            actionMessage = BoardsFailure.unexpected.message
        }

        isSubmitting = false
    }

    func deletePost() async {
        guard isSubmitting == false else { return }
        isSubmitting = true
        actionMessage = nil

        do {
            try await client.deletePost(postId: postId)
            syncStore.markDeleted(postId: postId)
            Task {
                await syncStore.refreshHotPosts(using: client)
            }
            didDeletePost = true
        } catch let error as BoardsClientError {
            handleActionError(error)
        } catch {
            actionMessage = BoardsFailure.unexpected.message
        }

        isSubmitting = false
    }

    func clearActionMessage() {
        actionMessage = nil
    }

    func invalidateStateIfNeeded() -> Bool {
        if case .failed(.invalidSession) = state {
            return true
        }
        return false
    }

    private func load() async {
        state = .loading

        do {
            let detail = try await client.fetchPostDetail(postId: postId)
            state = .loaded(detail)
        } catch let error as BoardsClientError {
            state = .failed(Self.map(error))
        } catch {
            state = .failed(.unexpected)
        }
    }

    private func handleActionError(_ error: BoardsClientError) {
        let failure = Self.map(error)
        if failure == .invalidSession {
            state = .failed(.invalidSession)
            return
        }
        actionMessage = failure.message
    }

    private static func map(_ error: BoardsClientError) -> BoardsFailure {
        switch error {
        case .invalidSession: .invalidSession
        case .forbidden: .forbidden
        case .boardNotFound: .boardNotFound
        case .postNotFound: .postNotFound
        case .anonymousNotAllowed: .anonymousNotAllowed
        case .deletedPost: .deletedPost
        case .commentNotFound: .commentNotFound
        case .replyDepthNotAllowed: .replyDepthNotAllowed
        case .invalidCommentParent: .invalidCommentParent
        case .network: .network
        case .unexpected: .unexpected
        }
    }
}

private struct PostDetailView: View {
    @ObservedObject private var session: AppSession
    @StateObject private var viewModel: PostDetailViewModel

    @Environment(\.dismiss) private var dismiss
    @State private var commentText = ""
    @State private var isAnonymous = true
    @State private var replyTarget: PostCommentItem?
    @State private var commentDeletionTarget: PostCommentItem?
    @State private var showDeletePostPrompt = false

    init(
        session: AppSession,
        client: BoardsClientProtocol,
        syncStore: BoardsSyncStore,
        postId: Int64
    ) {
        self.session = session
        _viewModel = StateObject(wrappedValue: PostDetailViewModel(client: client, syncStore: syncStore, postId: postId))
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                switch viewModel.state {
                case .loading:
                    ProgressView("Loading post...")
                        .font(AppFont.medium(15, relativeTo: .subheadline))
                        .tint(AuthPalette.primaryStart)
                        .padding(.top, 80)
                        .frame(maxWidth: .infinity, alignment: .center)
                case let .failed(failure):
                    BoardsFailureCard(
                        title: "We couldn't load this post",
                        message: failure.message,
                        buttonTitle: "Try Again",
                        action: {
                            Task { await viewModel.reload() }
                        }
                    )
                    .padding(.top, 24)
                case let .loaded(detail):
                    postHeader(detail)

                    Text(detail.title)
                        .font(AppFont.extraBold(26, relativeTo: .title))
                        .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))
                        .fixedSize(horizontal: false, vertical: true)

                    Text(detail.content)
                        .font(AppFont.medium(19, relativeTo: .body))
                        .foregroundStyle(Color(red: 0.18, green: 0.22, blue: 0.30))
                        .lineSpacing(6)
                        .fixedSize(horizontal: false, vertical: true)

                    if detail.images.isEmpty == false {
                        VStack(spacing: 14) {
                            ForEach(detail.images.sorted(by: { $0.displayOrder < $1.displayOrder })) { image in
                                RemotePostImageView(urlString: image.imageUrl)
                            }
                        }
                    }

                    postActions(detail)

                    Divider()
                        .padding(.vertical, 4)

                    VStack(alignment: .leading, spacing: 16) {
                        Text("Comments")
                            .font(AppFont.bold(22, relativeTo: .title3))
                            .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                        if detail.comments.isEmpty {
                            BoardsEmptyView(
                                title: "No comments yet",
                                message: "Start the conversation with the first comment."
                            )
                        } else {
                            ForEach(detail.comments) { comment in
                                CommentThreadView(
                                    comment: comment,
                                    onReply: { replyTarget = comment },
                                    onDelete: { target in
                                        commentDeletionTarget = target
                                    }
                                )
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 120)
        }
        .background(BoardsBackgroundView())
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            commentComposer
        }
        .toolbar {
            ToolbarItem(placement: .principal) {
                if case let .loaded(detail) = viewModel.state {
                    Text(detail.boardName)
                        .font(AppFont.bold(21, relativeTo: .headline))
                        .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))
                }
            }

            ToolbarItem(placement: .topBarTrailing) {
                if case let .loaded(detail) = viewModel.state, detail.isMine {
                    Button {
                        showDeletePostPrompt = true
                    } label: {
                        Image(systemName: "ellipsis.vertical")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(Color(red: 0.35, green: 0.42, blue: 0.54))
                    }
                }
            }
        }
        .task {
            await viewModel.loadIfNeeded()
        }
        .onChange(of: viewModel.invalidateStateIfNeeded()) { _, shouldInvalidate in
            guard shouldInvalidate else { return }
            session.invalidateSession()
        }
        .onChange(of: viewModel.didDeletePost) { _, didDelete in
            guard didDelete else { return }
            dismiss()
        }
        .confirmationDialog(
            "Delete this post?",
            isPresented: $showDeletePostPrompt,
            titleVisibility: .visible
        ) {
            Button("Delete Post", role: .destructive) {
                Task { await viewModel.deletePost() }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This action can't be undone.")
        }
        .alert("Delete this comment?", isPresented: Binding(
            get: { commentDeletionTarget != nil },
            set: { if $0 == false { commentDeletionTarget = nil } }
        )) {
            Button("Delete", role: .destructive) {
                if let target = commentDeletionTarget {
                    Task { await viewModel.deleteComment(target.commentId) }
                }
                commentDeletionTarget = nil
            }
            Button("Cancel", role: .cancel) {
                commentDeletionTarget = nil
            }
        } message: {
            Text("This action can't be undone.")
        }
    }

    private func postHeader(_ detail: PostDetailContent) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Circle()
                .fill(Color(red: 0.95, green: 0.96, blue: 0.98))
                .frame(width: 48, height: 48)
                .overlay {
                    Image(systemName: "person.fill")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(Color(red: 0.60, green: 0.64, blue: 0.72))
                }

            VStack(alignment: .leading, spacing: 4) {
                Text(detail.authorDisplayName)
                    .font(AppFont.bold(18, relativeTo: .headline))
                    .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                Text(detail.relativeCreatedAt)
                    .font(AppFont.medium(14, relativeTo: .subheadline))
                    .foregroundStyle(Color(red: 0.52, green: 0.58, blue: 0.68))
            }

            Spacer()

            if detail.isAnonymous {
                Text("ANON")
                    .font(AppFont.bold(12, relativeTo: .caption))
                    .foregroundStyle(AuthPalette.primaryStart)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(
                        Capsule(style: .continuous)
                            .fill(AuthPalette.primaryStart.opacity(0.12))
                    )
            }
        }
    }

    private func postActions(_ detail: PostDetailContent) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            if let message = viewModel.actionMessage {
                BoardsMessageBanner(message: message) {
                    viewModel.clearActionMessage()
                }
            }

            HStack(spacing: 24) {
                Button {
                    Task { await viewModel.toggleLike() }
                } label: {
                    Label("\(detail.likeCount)", systemImage: detail.isLikedByMe ? "heart.fill" : "heart")
                        .font(AppFont.semibold(17, relativeTo: .body))
                        .foregroundStyle(detail.isLikedByMe ? Color.red : Color(red: 0.35, green: 0.42, blue: 0.54))
                }
                .buttonStyle(.plain)

                Label("\(detail.commentCount)", systemImage: "message")
                    .font(AppFont.semibold(17, relativeTo: .body))
                    .foregroundStyle(Color(red: 0.24, green: 0.31, blue: 0.90))
            }
            .padding(.top, 2)
        }
    }

    private var commentComposer: some View {
        VStack(spacing: 8) {
            if let activeReplyTarget = replyTarget {
                HStack {
                    Text("Replying to \(activeReplyTarget.authorDisplayName)")
                        .font(AppFont.semibold(13, relativeTo: .caption))
                        .foregroundStyle(AuthPalette.primaryStart)

                    Spacer()

                    Button("Cancel") {
                        replyTarget = nil
                    }
                    .font(AppFont.medium(13, relativeTo: .caption))
                    .foregroundStyle(Color(red: 0.50, green: 0.57, blue: 0.67))
                }
                .padding(.horizontal, 18)
            }

            HStack(spacing: 12) {
                Button {
                    isAnonymous.toggle()
                } label: {
                    Text(isAnonymous ? "ANON" : "NAME")
                        .font(AppFont.bold(13, relativeTo: .caption))
                        .foregroundStyle(isAnonymous ? AuthPalette.primaryStart : Color(red: 0.47, green: 0.55, blue: 0.66))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 8)
                        .background(
                            Capsule(style: .continuous)
                                .fill(isAnonymous ? AuthPalette.primaryStart.opacity(0.12) : Color(red: 0.94, green: 0.95, blue: 0.98))
                        )
                }
                .buttonStyle(.plain)

                TextField("Write a comment...", text: $commentText, axis: .vertical)
                    .font(AppFont.medium(16, relativeTo: .body))
                    .lineLimit(1...4)

                Button {
                    let targetParentId = replyTarget?.commentId
                    let pendingText = commentText
                    commentText = ""
                    replyTarget = nil
                    Task {
                        await viewModel.submitComment(
                            content: pendingText,
                            parentId: targetParentId,
                            isAnonymous: isAnonymous
                        )
                    }
                } label: {
                    Image(systemName: "paperplane.fill")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 42, height: 42)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(commentText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Color(red: 0.82, green: 0.85, blue: 0.91) : AuthPalette.primaryStart)
                        )
                }
                .buttonStyle(.plain)
                .disabled(commentText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(Color.white)
                    .shadow(color: Color.black.opacity(0.05), radius: 12, y: 2)
            )
            .padding(.horizontal, 14)
            .padding(.bottom, 8)
        }
        .background(.ultraThinMaterial)
    }
}

@MainActor
final class WritePostViewModel: ObservableObject {
    @Published var title = ""
    @Published var content = ""
    @Published var isAnonymous = false
    @Published private(set) var isSubmitting = false
    @Published var actionMessage: String?

    private let client: BoardsClientProtocol

    init(client: BoardsClientProtocol) {
        self.client = client
    }

    func submit(boardId: Int64) async throws -> Int64 {
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedContent = content.trimmingCharacters(in: .whitespacesAndNewlines)

        guard trimmedTitle.isEmpty == false else {
            actionMessage = "Please enter a title."
            throw BoardsFailure.unexpected
        }

        guard trimmedContent.isEmpty == false else {
            actionMessage = "Please enter some content."
            throw BoardsFailure.unexpected
        }

        isSubmitting = true
        actionMessage = nil
        defer { isSubmitting = false }

        do {
            return try await client.createPost(
                boardId: boardId,
                input: CreatePostInput(
                    title: trimmedTitle,
                    content: trimmedContent,
                    isAnonymous: isAnonymous
                )
            )
        } catch let error as BoardsClientError {
            let failure = Self.map(error)
            actionMessage = failure.message
            throw failure
        } catch {
            actionMessage = BoardsFailure.unexpected.message
            throw BoardsFailure.unexpected
        }
    }

    private static func map(_ error: BoardsClientError) -> BoardsFailure {
        switch error {
        case .invalidSession: .invalidSession
        case .forbidden: .forbidden
        case .boardNotFound: .boardNotFound
        case .postNotFound: .postNotFound
        case .anonymousNotAllowed: .anonymousNotAllowed
        case .deletedPost: .deletedPost
        case .commentNotFound: .commentNotFound
        case .replyDepthNotAllowed: .replyDepthNotAllowed
        case .invalidCommentParent: .invalidCommentParent
        case .network: .network
        case .unexpected: .unexpected
        }
    }
}

private struct WritePostView: View {
    @ObservedObject private var session: AppSession
    @StateObject private var viewModel: WritePostViewModel
    let board: BoardSummary
    let onClose: () -> Void
    let onCreated: () -> Void

    init(
        session: AppSession,
        client: BoardsClientProtocol,
        board: BoardSummary,
        onClose: @escaping () -> Void,
        onCreated: @escaping () -> Void
    ) {
        self.session = session
        self.board = board
        self.onClose = onClose
        self.onCreated = onCreated
        _viewModel = StateObject(wrappedValue: WritePostViewModel(client: client))
    }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 26) {
                    if let message = viewModel.actionMessage {
                        BoardsMessageBanner(message: message) {
                            viewModel.actionMessage = nil
                        }
                    }

                    VStack(alignment: .leading, spacing: 14) {
                        Text("SELECT BOARD")
                            .font(AppFont.bold(13, relativeTo: .caption))
                            .foregroundStyle(Color(red: 0.45, green: 0.53, blue: 0.65))
                            .tracking(1.6)

                        HStack {
                            Text(board.name)
                                .font(AppFont.medium(18, relativeTo: .body))
                                .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                            Spacer()

                            Image(systemName: "chevron.down")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(AuthPalette.primaryStart)
                        }
                        .padding(.horizontal, 20)
                        .frame(height: 72)
                        .background(
                            RoundedRectangle(cornerRadius: 22, style: .continuous)
                                .fill(Color(red: 0.96, green: 0.97, blue: 0.99))
                        )
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        TextField("Title", text: $viewModel.title)
                            .font(AppFont.extraBold(26, relativeTo: .title))
                            .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                        Divider()

                        TextField("Share your thoughts with the community...", text: $viewModel.content, axis: .vertical)
                            .font(AppFont.medium(20, relativeTo: .body))
                            .foregroundStyle(Color(red: 0.19, green: 0.24, blue: 0.33))
                            .lineLimit(10...20)
                            .frame(minHeight: 260, alignment: .topLeading)
                    }

                    Button {} label: {
                        VStack(spacing: 10) {
                            Image(systemName: "camera.badge.plus")
                                .font(.system(size: 28, weight: .semibold))
                            Text("Add Image")
                                .font(AppFont.bold(14, relativeTo: .body))
                            Text("Coming soon")
                                .font(AppFont.medium(12, relativeTo: .caption))
                        }
                        .foregroundStyle(Color(red: 0.63, green: 0.69, blue: 0.78))
                        .frame(width: 138, height: 138)
                        .background(
                            RoundedRectangle(cornerRadius: 24, style: .continuous)
                                .stroke(style: StrokeStyle(lineWidth: 2, dash: [8, 6]))
                                .foregroundStyle(Color(red: 0.87, green: 0.90, blue: 0.96))
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(true)

                    VStack(alignment: .leading, spacing: 18) {
                        HStack(spacing: 14) {
                            Circle()
                                .fill(Color(red: 0.92, green: 0.89, blue: 1.0))
                                .frame(width: 54, height: 54)
                                .overlay {
                                    Image(systemName: "eye.slash.fill")
                                        .font(.system(size: 20, weight: .semibold))
                                        .foregroundStyle(AuthPalette.primaryStart)
                                }

                            VStack(alignment: .leading, spacing: 4) {
                                Text("Post Anonymously")
                                    .font(AppFont.bold(18, relativeTo: .headline))
                                    .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                                Text("Your identity will be hidden")
                                    .font(AppFont.medium(15, relativeTo: .subheadline))
                                    .foregroundStyle(Color(red: 0.46, green: 0.54, blue: 0.66))
                            }

                            Spacer()

                            Toggle("", isOn: $viewModel.isAnonymous)
                                .labelsHidden()
                                .tint(AuthPalette.primaryStart)
                                .disabled(board.isAnonymousAllowed == false)
                        }

                        if board.isAnonymousAllowed == false {
                            Text("Anonymous posting isn't available in this board.")
                                .font(AppFont.medium(13, relativeTo: .caption))
                                .foregroundStyle(Color(red: 0.55, green: 0.60, blue: 0.69))
                        }
                    }
                    .padding(18)
                    .background(
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .fill(Color(red: 0.97, green: 0.98, blue: 1.0))
                    )
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 40)
            }
            .background(BoardsBackgroundView())
            .navigationTitle("Write Post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: onClose) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(Color(red: 0.31, green: 0.38, blue: 0.50))
                    }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task {
                            do {
                                _ = try await viewModel.submit(boardId: board.id)
                                onCreated()
                            } catch let error as BoardsFailure {
                                if error == .invalidSession {
                                    session.invalidateSession()
                                }
                            } catch {
                                return
                            }
                        }
                    } label: {
                        Text(viewModel.isSubmitting ? "Posting..." : "Post")
                            .font(AppFont.bold(16, relativeTo: .headline))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 24)
                            .frame(height: 44)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(
                                        viewModel.isSubmitting
                                            ? Color(red: 0.70, green: 0.73, blue: 0.81)
                                            : AuthPalette.primaryStart
                                    )
                            )
                    }
                    .buttonStyle(.plain)
                    .disabled(viewModel.isSubmitting)
                }
            }
        }
    }
}

private struct BoardsSearchField: View {
    @Binding var text: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Color(red: 0.56, green: 0.63, blue: 0.74))

            TextField("Search topics or posts...", text: $text)
                .font(AppFont.medium(17, relativeTo: .body))
                .foregroundStyle(Color(red: 0.19, green: 0.24, blue: 0.33))
        }
        .padding(.horizontal, 18)
        .frame(height: 52)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(red: 0.96, green: 0.97, blue: 0.99))
        )
    }
}

private struct TrendingPostsSection: View {
    let posts: [HotPostSummary]
    let onTap: (Int64) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Label {
                    Text("Trending Posts")
                        .font(AppFont.extraBold(20, relativeTo: .title3))
                        .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))
                } icon: {
                    Image(systemName: "flame.fill")
                        .foregroundStyle(Color(red: 0.98, green: 0.32, blue: 0.26))
                }

                Spacer()

                Text("View All")
                    .font(AppFont.semibold(15, relativeTo: .subheadline))
                    .foregroundStyle(AuthPalette.primaryStart)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(posts) { post in
                        Button {
                            onTap(post.id)
                        } label: {
                            TrendingPostCard(post: post)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.trailing, 4)
            }
        }
    }
}

private struct TrendingPostCard: View {
    let post: HotPostSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("HOT")
                    .font(AppFont.bold(11, relativeTo: .caption))
                    .foregroundStyle(Color(red: 0.93, green: 0.28, blue: 0.26))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        Capsule(style: .continuous)
                            .fill(Color(red: 1.0, green: 0.93, blue: 0.93))
                    )

                Spacer()
            }

            Text(post.title)
                .font(AppFont.bold(18, relativeTo: .headline))
                .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))
                .lineLimit(3)
                .multilineTextAlignment(.leading)

            Spacer(minLength: 8)

            HStack {
                Text(post.relativeCreatedAt)
                    .font(AppFont.medium(14, relativeTo: .caption))
                    .foregroundStyle(Color(red: 0.46, green: 0.54, blue: 0.66))

                Spacer()

                Label("\(post.likeCount)", systemImage: "heart.fill")
                    .font(AppFont.medium(14, relativeTo: .caption))
                    .foregroundStyle(Color.red)

                Label("\(post.commentCount)", systemImage: "message.fill")
                    .font(AppFont.medium(14, relativeTo: .caption))
                    .foregroundStyle(Color(red: 0.28, green: 0.27, blue: 0.88))
            }
        }
        .padding(20)
        .frame(width: 318, height: 182, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Color.white)
                .shadow(color: Color.black.opacity(0.05), radius: 14, y: 6)
        )
    }
}

private struct BoardsSectionCard: View {
    let section: BoardSection
    let onTap: (BoardSummary) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(section.kind.title)
                .font(AppFont.bold(13, relativeTo: .caption))
                .foregroundStyle(Color(red: 0.53, green: 0.60, blue: 0.71))
                .tracking(1.6)

            VStack(spacing: 0) {
                ForEach(Array(section.boards.enumerated()), id: \.element.id) { index, item in
                    Button {
                        onTap(item.board)
                    } label: {
                        HStack(spacing: 14) {
                            Circle()
                                .fill(iconBackgroundColor(item.iconBackground))
                                .frame(width: 48, height: 48)
                                .overlay {
                                    Image(systemName: item.iconSystemName)
                                        .font(.system(size: 20, weight: .semibold))
                                        .foregroundStyle(iconForegroundColor(item.iconBackground))
                                }

                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.board.name)
                                    .font(AppFont.bold(18, relativeTo: .headline))
                                    .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))
                                    .multilineTextAlignment(.leading)

                                Text(item.subtitle)
                                    .font(AppFont.medium(15, relativeTo: .subheadline))
                                    .foregroundStyle(Color(red: 0.46, green: 0.54, blue: 0.66))
                                    .multilineTextAlignment(.leading)
                            }

                            Spacer()

                            if let badge = item.accentBadge {
                                Text(badge)
                                    .font(AppFont.bold(12, relativeTo: .caption))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 5)
                                    .background(
                                        Capsule(style: .continuous)
                                            .fill(Color.red)
                                    )
                            }

                            Image(systemName: "chevron.right")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(Color(red: 0.76, green: 0.80, blue: 0.87))
                        }
                        .padding(.horizontal, 18)
                        .padding(.vertical, 18)
                    }
                    .buttonStyle(.plain)

                    if index < section.boards.count - 1 {
                        Divider()
                            .padding(.leading, 80)
                    }
                }
            }
            .background(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(Color.white)
                    .shadow(color: Color.black.opacity(0.04), radius: 12, y: 4)
            )
        }
    }

    private func iconBackgroundColor(_ key: String) -> Color {
        switch key {
        case "official":
            return Color(red: 0.92, green: 0.92, blue: 1.0)
        case "department":
            return Color(red: 0.95, green: 0.90, blue: 1.0)
        case "free":
            return Color(red: 0.87, green: 0.98, blue: 0.92)
        case "secret":
            return Color(red: 0.92, green: 0.94, blue: 0.97)
        case "freshman":
            return Color(red: 1.0, green: 0.96, blue: 0.80)
        case "club":
            return Color(red: 0.99, green: 0.91, blue: 0.96)
        case "event":
            return Color(red: 0.91, green: 0.93, blue: 1.0)
        case "career":
            return Color(red: 1.0, green: 0.94, blue: 0.86)
        default:
            return Color(red: 0.93, green: 0.95, blue: 0.99)
        }
    }

    private func iconForegroundColor(_ key: String) -> Color {
        switch key {
        case "official":
            return Color(red: 0.30, green: 0.23, blue: 0.89)
        case "department":
            return Color(red: 0.56, green: 0.18, blue: 0.95)
        case "free":
            return Color(red: 0.07, green: 0.64, blue: 0.39)
        case "secret":
            return Color(red: 0.32, green: 0.38, blue: 0.49)
        case "freshman":
            return Color(red: 0.84, green: 0.60, blue: 0.06)
        case "club":
            return Color(red: 0.90, green: 0.17, blue: 0.55)
        case "event":
            return Color(red: 0.28, green: 0.32, blue: 0.91)
        case "career":
            return Color(red: 0.87, green: 0.42, blue: 0.07)
        default:
            return AuthPalette.primaryStart
        }
    }
}

private struct BoardPostCard: View {
    let post: BoardPostSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Circle()
                    .fill(Color(red: 0.95, green: 0.96, blue: 0.98))
                    .frame(width: 40, height: 40)
                    .overlay {
                        Image(systemName: "person.fill")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(Color(red: 0.62, green: 0.66, blue: 0.74))
                    }

                VStack(alignment: .leading, spacing: 3) {
                    Text(post.authorDisplayName)
                        .font(AppFont.bold(16, relativeTo: .headline))
                        .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                    Text(post.relativeCreatedAt)
                        .font(AppFont.medium(13, relativeTo: .caption))
                        .foregroundStyle(Color(red: 0.52, green: 0.58, blue: 0.68))
                }

                Spacer()
            }

            Text(post.title)
                .font(AppFont.extraBold(22, relativeTo: .title3))
                .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 16) {
                Label("\(post.likeCount)", systemImage: "heart.fill")
                    .font(AppFont.medium(14, relativeTo: .caption))
                    .foregroundStyle(Color.red)

                Label("\(post.commentCount)", systemImage: "message.fill")
                    .font(AppFont.medium(14, relativeTo: .caption))
                    .foregroundStyle(Color(red: 0.28, green: 0.27, blue: 0.88))
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Color.white)
                .shadow(color: Color.black.opacity(0.05), radius: 12, y: 5)
        )
    }
}

private struct RemotePostImageView: View {
    let urlString: String

    var body: some View {
        AsyncImage(url: URL(string: urlString)) { phase in
            switch phase {
            case let .success(image):
                image
                    .resizable()
                    .scaledToFill()
            case .failure:
                placeholder
            case .empty:
                ProgressView()
                    .tint(AuthPalette.primaryStart)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(red: 0.97, green: 0.97, blue: 0.98))
            @unknown default:
                placeholder
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 280)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private var placeholder: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Color(red: 0.97, green: 0.97, blue: 0.98))

            VStack(spacing: 12) {
                Image(systemName: "photo")
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(Color(red: 0.72, green: 0.76, blue: 0.84))

                Text("Image unavailable")
                    .font(AppFont.medium(15, relativeTo: .subheadline))
                    .foregroundStyle(Color(red: 0.57, green: 0.62, blue: 0.71))
            }
        }
    }
}

private struct CommentThreadView: View {
    let comment: PostCommentItem
    let onReply: () -> Void
    let onDelete: (PostCommentItem) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CommentBubbleView(
                comment: comment,
                canReply: comment.isDeleted == false,
                onReply: onReply,
                onDelete: {
                    onDelete(comment)
                }
            )

            if comment.children.isEmpty == false {
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(comment.children) { child in
                        CommentBubbleView(
                            comment: child,
                            canReply: false,
                            onReply: {},
                            onDelete: {
                                onDelete(child)
                            }
                        )
                    }
                }
                .padding(.leading, 22)
            }
        }
    }
}

private struct CommentBubbleView: View {
    let comment: PostCommentItem
    let canReply: Bool
    let onReply: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Circle()
                    .fill(Color(red: 0.95, green: 0.96, blue: 0.98))
                    .frame(width: 34, height: 34)
                    .overlay {
                        Text(comment.isDeleted ? "?" : String(comment.authorDisplayName.prefix(1)))
                            .font(AppFont.bold(14, relativeTo: .caption))
                            .foregroundStyle(Color(red: 0.55, green: 0.60, blue: 0.70))
                    }

                VStack(alignment: .leading, spacing: 4) {
                    Text(comment.authorDisplayName)
                        .font(AppFont.bold(15, relativeTo: .subheadline))
                        .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                    Text(comment.relativeCreatedAt)
                        .font(AppFont.medium(12, relativeTo: .caption))
                        .foregroundStyle(Color(red: 0.57, green: 0.62, blue: 0.71))
                }

                Spacer()

                HStack(spacing: 10) {
                    if canReply {
                        Button("REPLY", action: onReply)
                            .font(AppFont.bold(11, relativeTo: .caption))
                            .foregroundStyle(Color(red: 0.58, green: 0.62, blue: 0.72))
                    }

                    if comment.isMine && comment.isDeleted == false {
                        Button("DELETE", action: onDelete)
                            .font(AppFont.bold(11, relativeTo: .caption))
                            .foregroundStyle(Color(red: 0.85, green: 0.25, blue: 0.24))
                    }
                }
            }

            Text(comment.content)
                .font(AppFont.medium(17, relativeTo: .body))
                .foregroundStyle(comment.isDeleted ? Color(red: 0.58, green: 0.62, blue: 0.72) : Color(red: 0.14, green: 0.18, blue: 0.26))
                .lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(comment.children.isEmpty ? Color.white : Color(red: 0.97, green: 0.98, blue: 1.0))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(Color(red: 0.93, green: 0.95, blue: 0.98), lineWidth: 1)
        )
    }
}

private struct WritePostMetaChip: View {
    let systemImage: String
    let title: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: systemImage)
                .font(.system(size: 18, weight: .semibold))
            Text(title)
                .font(AppFont.medium(15, relativeTo: .subheadline))
        }
        .foregroundStyle(Color(red: 0.42, green: 0.49, blue: 0.62))
    }
}

private struct BoardsMessageBanner: View {
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

private struct BoardsLoadingView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            ProgressView("Loading boards...")
                .font(AppFont.medium(15, relativeTo: .subheadline))
                .tint(AuthPalette.primaryStart)

            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Color.white)
                .frame(height: 180)
                .shadow(color: Color.black.opacity(0.04), radius: 12, y: 4)

            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Color.white)
                .frame(height: 220)
                .shadow(color: Color.black.opacity(0.04), radius: 12, y: 4)
        }
    }
}

private struct BoardsFailureCard: View {
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

private struct BoardsEmptyView: View {
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

private struct BoardsBackgroundView: View {
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

struct BoardsRootView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            BoardsRootView(
                session: PreviewFactory.makeSession(state: .authenticated),
                client: PreviewBoardsClient(scenario: .loaded),
                syncStore: BoardsSyncStore()
            )
            .previewDisplayName("Boards Home - Loaded")

            BoardsRootView(
                session: PreviewFactory.makeSession(state: .authenticated),
                client: PreviewBoardsClient(scenario: .empty),
                syncStore: BoardsSyncStore()
            )
            .previewDisplayName("Boards Home - Empty")

            BoardFeedView(
                session: PreviewFactory.makeSession(state: .authenticated),
                client: PreviewBoardsClient(scenario: .loaded),
                syncStore: BoardsSyncStore(),
                board: PreviewBoardsData.freeBoard,
                onPostTap: { _ in }
            )
            .previewDisplayName("Board Feed - Loaded")

            PostDetailView(
                session: PreviewFactory.makeSession(state: .authenticated),
                client: PreviewBoardsClient(scenario: .loaded),
                syncStore: BoardsSyncStore(),
                postId: 2001
            )
            .previewDisplayName("Post Detail - Loaded")

            WritePostView(
                session: PreviewFactory.makeSession(state: .authenticated),
                client: PreviewBoardsClient(scenario: .loaded),
                board: PreviewBoardsData.freeBoard,
                onClose: {},
                onCreated: {}
            )
            .previewDisplayName("Write Post")
        }
    }
}
