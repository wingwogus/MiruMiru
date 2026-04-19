import SwiftUI

struct LoginView: View {
    @ObservedObject private var session: AppSession
    @StateObject private var viewModel: LoginViewModel
    @State private var isShowingForgotPassword = false
    private let onSignupTap: () -> Void

    init(
        session: AppSession,
        initialEmail: String = "",
        onSignupTap: @escaping () -> Void
    ) {
        self.session = session
        self.onSignupTap = onSignupTap
        _viewModel = StateObject(wrappedValue: LoginViewModel(session: session, initialEmail: initialEmail))
    }

    var body: some View {
        GeometryReader { proxy in
            NavigationStack {
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        topBar
                        hero(for: proxy.size.height)

                        VStack(spacing: 24) {
                            if let banner = viewModel.sessionBanner {
                                bannerView(text: banner)
                            }
                            form
                            loginButton
                            bottomCTA
                                .padding(.top, max(40, min(140, proxy.size.height * 0.18)))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .top)
                    .padding(.horizontal, 24)
                    .padding(.top, 12)
                    .padding(.bottom, 32)
                    .frame(minHeight: proxy.size.height - 32, alignment: .top)
                }
                .background(background)
                .scrollDismissesKeyboard(.immediately)
                .navigationBarBackButtonHidden(true)
                .sheet(isPresented: $isShowingForgotPassword) {
                    ForgotPasswordPlaceholderView()
                }
                .task {
                    viewModel.handleAppear()
                }
            }
        }
    }

    private var background: some View {
        AppTheme.pageBackground.ignoresSafeArea()
    }

    private var topBar: some View {
        HStack {
            Spacer()
            Button {
                onSignupTap()
            } label: {
                HStack(spacing: 8) {
                    Text("Sign Up")
                    Image(systemName: "arrow.right")
                }
                .font(AppFont.bold(18, relativeTo: .headline))
                .foregroundStyle(AuthPalette.primaryStart)
            }
            .accessibilityIdentifier("signup_top_button")
        }
        .padding(.horizontal, 6)
        .padding(.top, 8)
        .padding(.bottom, 6)
    }

    private func hero(for availableHeight: CGFloat) -> some View {
        let topPadding = max(18, min(72, availableHeight * 0.08))
        let bottomPadding = max(24, min(44, availableHeight * 0.05))

        return VStack(spacing: 14) {
            Text("MiruMiru")
                .font(AppFont.extraBold(56, relativeTo: .largeTitle))
                .foregroundStyle(AppTheme.textPrimary)
                .minimumScaleFactor(0.7)
                .lineLimit(1)

            Text("Your university life, simplified.")
                .font(AppFont.medium(19, relativeTo: .title3))
                .foregroundStyle(AppTheme.textSecondary)
                .multilineTextAlignment(.center)
                .minimumScaleFactor(0.85)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, topPadding)
        .padding(.bottom, bottomPadding)
    }

    private var form: some View {
        VStack(spacing: 22) {
            AuthTextFieldRow(
                label: "EMAIL ADDRESS",
                systemImage: "envelope.fill",
                errorText: viewModel.emailError
            ) {
                TextField("example@university.edu", text: $viewModel.email)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.emailAddress)
                    .font(AppFont.medium(18, relativeTo: .body))
                    .foregroundStyle(AppTheme.textSecondary)
                    .accessibilityIdentifier("email_text_field")
            }

            VStack(spacing: 8) {
                HStack {
                    Text("PASSWORD")
                        .font(AppFont.semibold(16, relativeTo: .subheadline))
                        .tracking(2)
                        .foregroundStyle(AppTheme.textSecondary)
                    Spacer()
                    Button("Forgot Password?") {
                        isShowingForgotPassword = true
                    }
                    .font(AppFont.bold(15, relativeTo: .subheadline))
                    .foregroundStyle(Color(red: 0.20, green: 0.10, blue: 0.96))
                    .accessibilityIdentifier("forgot_password_button")
                }

                AuthTextFieldRow(
                    label: "",
                    systemImage: "lock.fill",
                    errorText: viewModel.passwordError
                ) {
                    Group {
                        if viewModel.isPasswordVisible {
                            TextField("••••••••", text: $viewModel.password)
                        } else {
                            SecureField("••••••••", text: $viewModel.password)
                        }
                    }
                    .font(AppFont.medium(18, relativeTo: .body))
                    .foregroundStyle(AppTheme.textSecondary)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .accessibilityIdentifier("password_text_field")

                    Spacer(minLength: 10)

                    Button {
                        viewModel.isPasswordVisible.toggle()
                    } label: {
                        Image(systemName: viewModel.isPasswordVisible ? "eye.slash.fill" : "eye.fill")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundStyle(AppTheme.textTertiary)
                    }
                    .accessibilityIdentifier("password_visibility_button")
                }
            }

            if let generalError = viewModel.generalError {
                Text(generalError)
                    .font(AppFont.medium(13, relativeTo: .footnote))
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 8)
                    .accessibilityIdentifier("general_error_text")
            }
        }
    }

    private var loginButton: some View {
        PrimaryActionButton(
            title: "Login",
            isLoading: viewModel.isLoading,
            isDisabled: viewModel.isButtonDisabled
        ) {
            Task {
                await viewModel.submit()
            }
        }
        .padding(.top, 10)
        .accessibilityIdentifier("login_button")
    }

    private var bottomCTA: some View {
        HStack(spacing: 6) {
            Text("Don't have an account?")
                .font(AppFont.medium(18, relativeTo: .body))
                .foregroundStyle(AppTheme.textSecondary)
            Button("Sign Up") {
                onSignupTap()
            }
            .font(AppFont.bold(18, relativeTo: .body))
            .foregroundStyle(AuthPalette.primaryStart)
            .accessibilityIdentifier("signup_bottom_button")
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 8)
        .padding(.vertical, 14)
    }

    private func bannerView(text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundStyle(AuthPalette.primaryStart)

            Text(text)
                .font(AppFont.semibold(13, relativeTo: .footnote))
                .foregroundStyle(AppTheme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            Spacer()

            Button {
                viewModel.dismissBanner()
            } label: {
                Image(systemName: "xmark")
                    .foregroundStyle(.secondary)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(AppTheme.surfaceSecondary)
        )
    }
}

struct LoginView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            LoginView(
                session: PreviewFactory.makeSession(
                    state: .unauthenticated,
                    bannerMessage: "Welcome back. Use your university account to continue."
                ),
                initialEmail: "kenta@tokyo.ac.jp",
                onSignupTap: {}
            )
            .previewDisplayName("Login - Light")

            LoginView(
                session: PreviewFactory.makeSession(
                    state: .unauthenticated,
                    bannerMessage: "Welcome back. Use your university account to continue."
                ),
                initialEmail: "kenta@tokyo.ac.jp",
                onSignupTap: {}
            )
            .preferredColorScheme(.dark)
            .previewDisplayName("Login - Dark")
        }
    }
}
