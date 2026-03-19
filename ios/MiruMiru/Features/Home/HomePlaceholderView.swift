import SwiftUI

struct HomeView: View {
    @ObservedObject private var session: AppSession
    @StateObject private var viewModel: HomeViewModel
    private let isActive: Bool

    init(
        session: AppSession,
        client: HomeClientProtocol,
        isActive: Bool = true,
        nowProvider: @escaping @Sendable () -> Date = Date.init,
        calendar: Calendar = .current
    ) {
        self.session = session
        self.isActive = isActive
        _viewModel = StateObject(
            wrappedValue: HomeViewModel(
                client: client,
                nowProvider: nowProvider,
                calendar: calendar
            )
        )
    }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 28) {
                    switch viewModel.state {
                    case .loading:
                        loadingHeader
                        HomeLoadingCard()
                    case let .loaded(content):
                        HomeHeaderSection(profile: content.profile)
                        TodayClassesSection(
                            semesterTitle: content.semesterTitle,
                            rows: content.todayClasses
                        )
                    case let .empty(content):
                        HomeHeaderSection(profile: content.profile)
                        HomeEmptyCard(
                            title: content.state.title,
                            message: content.state.message,
                            semesterTitle: content.semesterTitle
                        )
                    case let .failed(failure):
                        failureCard(failure)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 32)
            }
            .background(background)
            .task(id: isActive) {
                guard isActive else { return }
                await viewModel.loadIfNeeded()
            }
            .onChange(of: viewModel.state) { _, _ in
                guard viewModel.invalidateStateIfNeeded() else { return }
                session.invalidateSession()
            }
        }
    }

    private var background: some View {
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

    private var loadingHeader: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack(spacing: 14) {
                Circle()
                    .fill(Color(red: 0.91, green: 0.94, blue: 0.99))
                    .frame(width: 58, height: 58)

                VStack(alignment: .leading, spacing: 8) {
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(Color(red: 0.91, green: 0.94, blue: 0.99))
                        .frame(width: 60, height: 14)

                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color(red: 0.86, green: 0.90, blue: 0.97))
                        .frame(width: 180, height: 22)
                }

                Spacer()

                Image(systemName: "bell.fill")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Color(red: 0.30, green: 0.38, blue: 0.52))
            }

            ProgressView("Loading your home...")
                .font(AppFont.medium(15, relativeTo: .subheadline))
                .tint(AuthPalette.primaryStart)
        }
    }

    private func failureCard(_ failure: HomeFailure) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Home")
                .font(AppFont.extraBold(32, relativeTo: .largeTitle))
                .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

            VStack(alignment: .leading, spacing: 12) {
                Text("We couldn't load your home")
                    .font(AppFont.bold(22, relativeTo: .title3))
                    .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                Text(failure.message)
                    .font(AppFont.medium(16, relativeTo: .body))
                    .foregroundStyle(Color(red: 0.42, green: 0.50, blue: 0.62))
                    .fixedSize(horizontal: false, vertical: true)

                PrimaryActionButton(
                    title: "Try Again",
                    isLoading: false,
                    isDisabled: false,
                    height: 64,
                    cornerRadius: 20
                ) {
                    Task {
                        await viewModel.reload()
                    }
                }
            }
            .padding(22)
            .background(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(Color.white)
                    .shadow(color: Color.black.opacity(0.05), radius: 16, y: 8)
            )
        }
        .padding(.top, 12)
    }
}

private struct HomeHeaderSection: View {
    let profile: HomeMemberProfile

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            HomeAvatar(initials: profile.avatarInitials)

            VStack(alignment: .leading, spacing: 3) {
                Text("Hello,")
                    .font(AppFont.medium(17, relativeTo: .body))
                    .foregroundStyle(Color(red: 0.42, green: 0.50, blue: 0.62))

                Text(profile.displayName)
                    .font(AppFont.extraBold(30, relativeTo: .largeTitle))
                    .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))
                    .lineLimit(2)
                    .minimumScaleFactor(0.82)

                Text(profile.secondaryText)
                    .font(AppFont.medium(15, relativeTo: .subheadline))
                    .foregroundStyle(Color(red: 0.42, green: 0.50, blue: 0.62))
                    .lineLimit(2)
            }

            Spacer(minLength: 16)

            Button {} label: {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "bell.fill")
                        .font(.system(size: 21, weight: .semibold))
                        .foregroundStyle(Color(red: 0.30, green: 0.38, blue: 0.52))
                        .frame(width: 42, height: 42)

                    Circle()
                        .fill(AuthPalette.primaryStart)
                        .frame(width: 8, height: 8)
                        .offset(x: -6, y: 6)
                }
            }
            .buttonStyle(.plain)
            .accessibilityHidden(true)
        }
    }
}

private struct HomeAvatar: View {
    let initials: String

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Circle()
                .stroke(AuthPalette.primaryStart, lineWidth: 2)
                .frame(width: 58, height: 58)

            Circle()
                .fill(Color(red: 0.98, green: 0.90, blue: 0.82))
                .frame(width: 48, height: 48)
                .overlay {
                    Text(initials)
                        .font(AppFont.bold(18, relativeTo: .headline))
                        .foregroundStyle(Color(red: 0.16, green: 0.19, blue: 0.24))
                }

            Circle()
                .fill(Color(red: 0.29, green: 0.84, blue: 0.46))
                .frame(width: 11, height: 11)
                .overlay {
                    Circle()
                        .stroke(Color.white, lineWidth: 2)
                }
                .offset(x: 2, y: 2)
        }
    }
}

private struct TodayClassesSection: View {
    let semesterTitle: String
    let rows: [TodayClassRow]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Today's Classes")
                    .font(AppFont.extraBold(28, relativeTo: .title2))
                    .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                Spacer()

                Text(semesterTitle)
                    .font(AppFont.semibold(15, relativeTo: .subheadline))
                    .foregroundStyle(AuthPalette.primaryStart)
            }

            VStack(spacing: 18) {
                ForEach(rows) { row in
                    HomeClassCard(row: row)
                }
            }
            .padding(18)
            .background(
                RoundedRectangle(cornerRadius: 32, style: .continuous)
                    .fill(Color(red: 0.96, green: 0.97, blue: 1.0))
            )
        }
    }
}

private struct HomeClassCard: View {
    let row: TodayClassRow
    @State private var cardHeight: CGFloat = Self.minimumCardHeight

    private static let minimumCardHeight: CGFloat = 122

    var body: some View {
        HStack(alignment: .top, spacing: 18) {
            ZStack {
                Rectangle()
                    .fill(Color(red: 0.86, green: 0.89, blue: 0.96))
                    .frame(width: 2, height: max(cardHeight - 58, 26))

                VStack(spacing: 0) {
                    Text(row.startTime)
                        .font(AppFont.bold(23, relativeTo: .title3))
                        .foregroundStyle(AuthPalette.primaryStart)
                        .monospacedDigit()
                        .lineLimit(1)

                    Spacer(minLength: 0)

                    Text(row.endTime)
                        .font(AppFont.semibold(15, relativeTo: .subheadline))
                        .foregroundStyle(Color(red: 0.37, green: 0.44, blue: 0.56))
                        .monospacedDigit()
                        .lineLimit(1)
                }
            }
            .frame(width: 76, height: cardHeight, alignment: .center)

            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .center, spacing: 10) {
                    if let badge = row.badge {
                        Text(badge.rawValue)
                            .font(AppFont.bold(12, relativeTo: .caption))
                            .foregroundStyle(AuthPalette.primaryStart)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(
                                Capsule()
                                    .fill(AuthPalette.primaryStart.opacity(0.10))
                            )
                    }

                    Spacer()

                    Text(row.location)
                        .font(AppFont.medium(14, relativeTo: .subheadline))
                        .foregroundStyle(Color(red: 0.45, green: 0.53, blue: 0.66))
                        .lineLimit(1)
                        .minimumScaleFactor(0.82)
                }
                .frame(minHeight: 28)

                VStack(alignment: .leading, spacing: 6) {
                    Text(row.title)
                        .font(AppFont.bold(20, relativeTo: .headline))
                        .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))
                        .fixedSize(horizontal: false, vertical: true)

                    Text(row.professor)
                        .font(AppFont.medium(17, relativeTo: .body))
                        .foregroundStyle(Color(red: 0.56, green: 0.62, blue: 0.72))
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .frame(minHeight: Self.minimumCardHeight, alignment: .top)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .fill(Color.white)
                    .shadow(color: Color.black.opacity(0.04), radius: 14, y: 7)
            )
            .overlay(alignment: .leading) {
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .fill(row.badge == .now ? AuthPalette.primaryStart : Color.clear)
                    .frame(width: 4)
            }
            .background {
                GeometryReader { proxy in
                    Color.clear
                        .preference(key: HomeClassCardHeightKey.self, value: proxy.size.height)
                }
            }
        }
        .onPreferenceChange(HomeClassCardHeightKey.self) { newHeight in
            guard newHeight > 0 else { return }
            cardHeight = max(newHeight, Self.minimumCardHeight)
        }
    }
}

private struct HomeClassCardHeightKey: PreferenceKey {
    static let defaultValue: CGFloat = 122

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

private struct HomeEmptyCard: View {
    let title: String
    let message: String
    let semesterTitle: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Today's Classes")
                    .font(AppFont.extraBold(28, relativeTo: .title2))
                    .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                Spacer()

                if let semesterTitle {
                    Text(semesterTitle)
                        .font(AppFont.semibold(15, relativeTo: .subheadline))
                        .foregroundStyle(AuthPalette.primaryStart)
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                Text(title)
                    .font(AppFont.bold(22, relativeTo: .title3))
                    .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                Text(message)
                    .font(AppFont.medium(16, relativeTo: .body))
                    .foregroundStyle(Color(red: 0.42, green: 0.50, blue: 0.62))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(22)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(Color(red: 0.96, green: 0.97, blue: 1.0))
            )
        }
    }
}

private struct HomeLoadingCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Today's Classes")
                .font(AppFont.extraBold(28, relativeTo: .title2))
                .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

            VStack(spacing: 14) {
                ForEach(0..<2, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .fill(Color.white)
                        .frame(height: 128)
                        .overlay {
                            RoundedRectangle(cornerRadius: 28, style: .continuous)
                                .stroke(Color(red: 0.92, green: 0.94, blue: 0.98), lineWidth: 1)
                        }
                }
            }
        }
    }
}

struct HomeView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            HomeView(
                session: PreviewFactory.makeSession(state: .authenticated),
                client: PreviewHomeClient.loaded(),
                nowProvider: { PreviewHomeData.previewNow }
            )
            .previewDisplayName("Home Loaded - Standard")

            HomeView(
                session: PreviewFactory.makeSession(state: .authenticated),
                client: PreviewHomeClient(scenario: .loadedLongTitle),
                nowProvider: { PreviewHomeData.previewNow }
            )
            .previewDisplayName("Home Loaded - Long Title")

            HomeView(
                session: PreviewFactory.makeSession(state: .authenticated),
                client: PreviewHomeClient(scenario: .noClassesToday),
                nowProvider: { PreviewHomeData.previewNow }
            )
            .previewDisplayName("Home No Classes")

            HomeView(
                session: PreviewFactory.makeSession(state: .authenticated),
                client: PreviewHomeClient(scenario: .networkFailure),
                nowProvider: { PreviewHomeData.previewNow }
            )
            .previewDisplayName("Home Failure")
        }
    }
}
