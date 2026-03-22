import SwiftUI

struct SignupPlaceholderView: View {
    @StateObject private var viewModel: SignupViewModel
    private let verificationCodeSectionID = "verification_code_section"

    private let onClose: () -> Void
    private let onBackToLogin: (SignupCompletionRequest) -> Void

    init(
        client: SignupClientProtocol = UnavailableSignupClient(),
        onClose: @escaping () -> Void,
        onBackToLogin: @escaping (SignupCompletionRequest) -> Void
    ) {
        _viewModel = StateObject(wrappedValue: SignupViewModel(client: client))
        self.onClose = onClose
        self.onBackToLogin = onBackToLogin
    }

    var body: some View {
        GeometryReader { proxy in
            let metrics = SignupLayoutMetrics(size: proxy.size)

            NavigationStack {
                ScrollViewReader { reader in
                    ScrollView(showsIndicators: false) {
                        VStack(spacing: 0) {
                            header(metrics: metrics)
                            content(metrics: metrics)
                        }
                        .frame(maxWidth: .infinity, alignment: .top)
                        .padding(.horizontal, metrics.horizontalPadding)
                        .padding(.top, metrics.topPadding)
                        .padding(.bottom, metrics.bottomPadding)
                        .frame(
                            minHeight: max(proxy.size.height - metrics.topPadding - metrics.bottomPadding, 0),
                            alignment: .top
                        )
                    }
                    .background(background)
                    .scrollDismissesKeyboard(.immediately)
                    .navigationBarBackButtonHidden(true)
                    .onChange(of: viewModel.hasSentCode) { _, hasSentCode in
                        guard hasSentCode, viewModel.step == .verifyEmail else { return }

                        withAnimation(.easeInOut(duration: 0.25)) {
                            reader.scrollTo(verificationCodeSectionID, anchor: .center)
                        }
                    }
                    .onChange(of: viewModel.completionRequest) { _, completion in
                        guard let completion else { return }
                        onBackToLogin(completion)
                        viewModel.consumeCompletionRequest()
                    }
                }
            }
        }
    }

    private var background: some View {
        Color(.systemGroupedBackground)
            .ignoresSafeArea()
    }

    private func header(metrics: SignupLayoutMetrics) -> some View {
        VStack(spacing: metrics.headerSpacing) {
            HStack {
                Button {
                    if viewModel.step == .verifyEmail {
                        onClose()
                    } else {
                        viewModel.goBack()
                    }
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(Color(red: 0.12, green: 0.17, blue: 0.28))
                        .frame(width: 44, height: 44)
                }

                Spacer()

                Text(viewModel.step.title)
                    .font(AppFont.bold(18, relativeTo: .headline))
                    .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))

                Spacer()

                Color.clear
                    .frame(width: 44, height: 44)
            }
            .padding(.top, metrics.headerTopInset)

            Rectangle()
                .fill(Color(red: 0.90, green: 0.92, blue: 0.96))
                .frame(height: 1)
                .padding(.horizontal, -metrics.horizontalPadding)
        }
    }

    private func content(metrics: SignupLayoutMetrics) -> some View {
        VStack(alignment: .leading, spacing: metrics.contentSpacing) {
            let copy = viewModel.step.copy

            VStack(alignment: .leading, spacing: metrics.copySpacing) {
                Text(copy.heading)
                    .font(AppFont.extraBold(metrics.headingSize, relativeTo: .largeTitle))
                    .foregroundStyle(Color(red: 0.06, green: 0.10, blue: 0.21))
                    .minimumScaleFactor(0.78)
                    .lineLimit(2)
                    .padding(.top, metrics.headingTopPadding)

                Text(copy.body)
                    .font(AppFont.medium(metrics.bodySize, relativeTo: .title3))
                    .foregroundStyle(Color(red: 0.42, green: 0.50, blue: 0.62))
                    .fixedSize(horizontal: false, vertical: true)
            }

            if viewModel.step == .verifyEmail {
                verifyEmailContent(metrics: metrics)
            } else if viewModel.step == .profileDetails {
                profileContent(metrics: metrics)
            } else {
                passwordContent(metrics: metrics)
            }

            if let generalError = viewModel.generalError {
                Text(generalError)
                    .font(AppFont.medium(13, relativeTo: .footnote))
                    .foregroundStyle(.red)
                    .fixedSize(horizontal: false, vertical: true)
            }

            PrimaryActionButton(
                title: copy.actionTitle,
                isLoading: viewModel.isVerifyingCode || viewModel.isSubmitting,
                isDisabled: viewModel.isPrimaryButtonDisabled,
                height: metrics.ctaHeight,
                cornerRadius: metrics.controlCornerRadius
            ) {
                Task {
                    if viewModel.step == .verifyEmail {
                        await viewModel.verifyEmailAndContinue()
                    } else if viewModel.step == .profileDetails {
                        viewModel.continueFromProfile()
                    } else {
                        await viewModel.completeSignup()
                    }
                }
            }
            .padding(.top, metrics.ctaTopPadding)
        }
    }

    private func verifyEmailContent(metrics: SignupLayoutMetrics) -> some View {
        VStack(alignment: .leading, spacing: metrics.sectionSpacing) {
            noticeCard(metrics: metrics)

            VStack(alignment: .leading, spacing: 8) {
                Text("School Email Address")
                    .font(AppFont.bold(17, relativeTo: .headline))
                    .foregroundStyle(Color(red: 0.16, green: 0.22, blue: 0.33))

                HStack(spacing: 12) {
                    AuthTextFieldRow(
                        label: "",
                        systemImage: "envelope.fill",
                        errorText: viewModel.emailError,
                        minHeight: metrics.fieldHeight,
                        horizontalPadding: metrics.fieldHorizontalPadding,
                        cornerRadius: metrics.controlCornerRadius
                    ) {
                        TextField("example@university.edu", text: $viewModel.email)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.emailAddress)
                            .font(AppFont.medium(18, relativeTo: .body))
                            .foregroundStyle(Color(red: 0.39, green: 0.45, blue: 0.56))
                    }

                    Button {
                        Task {
                            await viewModel.sendCode()
                        }
                    } label: {
                        ZStack {
                            Text("Send")
                                .opacity(viewModel.isSendingCode ? 0 : 1)

                            if viewModel.isSendingCode {
                                ProgressView()
                                    .tint(.white)
                            }
                        }
                        .font(AppFont.bold(17, relativeTo: .headline))
                        .foregroundStyle(.white)
                        .frame(width: metrics.sendButtonWidth, height: metrics.fieldHeight)
                        .background(Color(red: 0.06, green: 0.10, blue: 0.21))
                        .clipShape(
                            RoundedRectangle(
                                cornerRadius: metrics.controlCornerRadius,
                                style: .continuous
                            )
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(viewModel.isSendingCode)
                    .opacity(viewModel.isSendingCode ? 0.86 : 1)
                }
            }

            if viewModel.hasSentCode {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("6-Digit Verification Code")
                            .font(AppFont.bold(17, relativeTo: .headline))
                            .foregroundStyle(Color(red: 0.16, green: 0.22, blue: 0.33))
                        Spacer()
                        Text(viewModel.resendLabel)
                            .font(AppFont.medium(15, relativeTo: .subheadline))
                            .foregroundStyle(.red)
                    }

                    Text("We sent a code to your school email. Enter it below to continue.")
                        .font(AppFont.medium(14, relativeTo: .footnote))
                        .foregroundStyle(Color(red: 0.42, green: 0.50, blue: 0.62))
                        .fixedSize(horizontal: false, vertical: true)

                    SignupCodeEntryField(
                        code: $viewModel.verificationCode,
                        boxHeight: metrics.codeBoxHeight,
                        boxSpacing: metrics.codeBoxSpacing,
                        cornerRadius: metrics.codeBoxCornerRadius
                    )

                    if let codeError = viewModel.codeError {
                        Text(codeError)
                            .font(AppFont.medium(13, relativeTo: .footnote))
                            .foregroundStyle(.red)
                    }

                    HStack(spacing: 6) {
                        Text("Didn't receive the email?")
                            .font(AppFont.medium(16, relativeTo: .body))
                            .foregroundStyle(Color(red: 0.64, green: 0.68, blue: 0.75))
                        Button("Resend") {
                            Task {
                                await viewModel.sendCode()
                            }
                        }
                        .font(AppFont.bold(16, relativeTo: .body))
                        .foregroundStyle(Color(red: 0.16, green: 0.22, blue: 0.33))
                        .disabled(viewModel.canResend == false)
                        .underline()
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                }
                .id(verificationCodeSectionID)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.hasSentCode)
    }

    private func profileContent(metrics: SignupLayoutMetrics) -> some View {
        VStack(spacing: metrics.sectionSpacing) {
            AuthTextFieldRow(
                label: "NICKNAME",
                systemImage: "person.fill",
                errorText: viewModel.nicknameError,
                minHeight: metrics.fieldHeight,
                horizontalPadding: metrics.fieldHorizontalPadding,
                cornerRadius: metrics.controlCornerRadius
            ) {
                TextField("How should we call you?", text: $viewModel.nickname)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .font(AppFont.medium(18, relativeTo: .body))
                    .foregroundStyle(Color(red: 0.39, green: 0.45, blue: 0.56))
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("MAJOR")
                    .font(AppFont.semibold(16, relativeTo: .subheadline))
                    .tracking(2)
                    .foregroundStyle(Color(red: 0.36, green: 0.45, blue: 0.59))

                Menu {
                    ForEach(viewModel.majorOptions) { option in
                        Button(option.name) {
                            viewModel.selectedMajorID = option.id
                        }
                    }
                } label: {
                    HStack(spacing: 14) {
                        Image(systemName: "book.closed.fill")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(Color(red: 0.59, green: 0.65, blue: 0.74))
                            .frame(width: 28)

                        Text(selectedMajorLabel)
                            .font(AppFont.medium(18, relativeTo: .body))
                            .foregroundStyle(Color(red: 0.39, green: 0.45, blue: 0.56))

                        Spacer()

                        Image(systemName: "chevron.down")
                            .foregroundStyle(Color(red: 0.59, green: 0.65, blue: 0.74))
                    }
                    .padding(.horizontal, metrics.fieldHorizontalPadding)
                    .frame(minHeight: metrics.fieldHeight)
                    .background(
                        RoundedRectangle(cornerRadius: metrics.controlCornerRadius, style: .continuous)
                            .fill(Color(red: 0.96, green: 0.97, blue: 0.99))
                    )
                }

                if let majorError = viewModel.majorError {
                    Text(majorError)
                        .font(AppFont.medium(13, relativeTo: .footnote))
                        .foregroundStyle(.red)
                        .padding(.horizontal, 8)
                }
            }
        }
    }

    private func passwordContent(metrics: SignupLayoutMetrics) -> some View {
        VStack(spacing: metrics.sectionSpacing) {
            passwordField(
                label: "Password",
                text: $viewModel.password,
                isVisible: $viewModel.isPasswordVisible,
                placeholder: "Enter password",
                errorText: viewModel.passwordError,
                metrics: metrics
            )

            passwordField(
                label: "Confirm Password",
                text: $viewModel.confirmPassword,
                isVisible: $viewModel.isConfirmPasswordVisible,
                placeholder: "Re-enter password",
                errorText: viewModel.confirmPasswordError,
                metrics: metrics
            )
        }
    }

    private func noticeCard(metrics: SignupLayoutMetrics) -> some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: "info.circle.fill")
                .font(.system(size: metrics.noticeIconSize))
                .foregroundStyle(Color(red: 0.17, green: 0.42, blue: 0.95))

            (
                Text("For security and community standards, re-verification is required every ")
                    .font(AppFont.medium(15, relativeTo: .body))
                +
                Text("6 months.")
                    .font(AppFont.bold(15, relativeTo: .body))
            )
                .foregroundStyle(Color(red: 0.12, green: 0.28, blue: 0.77))
        }
        .padding(metrics.noticePadding)
        .background(
            RoundedRectangle(cornerRadius: metrics.controlCornerRadius, style: .continuous)
                .fill(Color(red: 0.92, green: 0.96, blue: 1.0))
                .overlay(
                    RoundedRectangle(cornerRadius: metrics.controlCornerRadius, style: .continuous)
                        .stroke(Color(red: 0.82, green: 0.89, blue: 1.0), lineWidth: 1)
                )
        )
    }

    private var selectedMajorLabel: String {
        if let selectedMajorID = viewModel.selectedMajorID,
           let option = viewModel.majorOptions.first(where: { $0.id == selectedMajorID }) {
            return option.name
        }

        return viewModel.majorOptions.isEmpty ? "No majors available yet" : "Choose your major"
    }

    private func passwordField(
        label: String,
        text: Binding<String>,
        isVisible: Binding<Bool>,
        placeholder: String,
        errorText: String?,
        metrics: SignupLayoutMetrics
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label)
                .font(AppFont.bold(17, relativeTo: .headline))
                .foregroundStyle(Color(red: 0.16, green: 0.22, blue: 0.33))

            AuthTextFieldRow(
                label: "",
                systemImage: "lock.fill",
                errorText: errorText,
                minHeight: metrics.fieldHeight,
                horizontalPadding: metrics.fieldHorizontalPadding,
                cornerRadius: metrics.controlCornerRadius
            ) {
                Group {
                    if isVisible.wrappedValue {
                        TextField(placeholder, text: text)
                    } else {
                        SecureField(placeholder, text: text)
                    }
                }
                .font(AppFont.medium(18, relativeTo: .body))
                .foregroundStyle(Color(red: 0.39, green: 0.45, blue: 0.56))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()

                Spacer(minLength: 10)

                Button {
                    isVisible.wrappedValue.toggle()
                } label: {
                    Image(systemName: isVisible.wrappedValue ? "eye.slash.fill" : "eye.fill")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(Color(red: 0.59, green: 0.65, blue: 0.74))
                }
            }
        }
    }
}

private struct SignupCodeEntryField: View {
    @Binding var code: String
    let boxHeight: CGFloat
    let boxSpacing: CGFloat
    let cornerRadius: CGFloat
    @FocusState private var isFocused: Bool

    var body: some View {
        ZStack {
            TextField("", text: Binding(
                get: { code },
                set: { newValue in
                    code = String(newValue.filter(\.isNumber).prefix(6))
                }
            ))
            .keyboardType(.numberPad)
            .textContentType(.oneTimeCode)
            .focused($isFocused)
            .foregroundStyle(.clear)
            .accentColor(.clear)
            .frame(height: 0)
            .opacity(0.01)

            HStack(spacing: boxSpacing) {
                ForEach(0..<6, id: \.self) { index in
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                                .stroke(Color(red: 0.87, green: 0.89, blue: 0.93), lineWidth: 1.2)
                        )
                        .frame(height: boxHeight)
                        .overlay {
                            Text(character(at: index))
                                .font(AppFont.bold(28, relativeTo: .title2))
                                .foregroundStyle(Color(red: 0.16, green: 0.22, blue: 0.33))
                        }
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            isFocused = true
        }
        .onAppear {
            isFocused = true
        }
    }

    private func character(at index: Int) -> String {
        guard index < code.count else { return "" }
        let stringIndex = code.index(code.startIndex, offsetBy: index)
        return String(code[stringIndex])
    }
}

private struct SignupLayoutMetrics {
    let size: CGSize

    var isCompactHeight: Bool { size.height < 780 }
    var horizontalPadding: CGFloat { size.width < 390 ? 20 : 24 }
    var topPadding: CGFloat { isCompactHeight ? 8 : 12 }
    var bottomPadding: CGFloat { isCompactHeight ? 20 : 32 }
    var headerSpacing: CGFloat { isCompactHeight ? 16 : 22 }
    var headerTopInset: CGFloat { isCompactHeight ? 2 : 6 }
    var contentSpacing: CGFloat { isCompactHeight ? 20 : 26 }
    var copySpacing: CGFloat { isCompactHeight ? 14 : 18 }
    var headingTopPadding: CGFloat { isCompactHeight ? 18 : 28 }
    var headingSize: CGFloat { isCompactHeight ? 30 : 34 }
    var bodySize: CGFloat { isCompactHeight ? 17 : 19 }
    var sectionSpacing: CGFloat { isCompactHeight ? 18 : 24 }
    var fieldHeight: CGFloat { isCompactHeight ? 72 : 84 }
    var fieldHorizontalPadding: CGFloat { isCompactHeight ? 18 : 22 }
    var controlCornerRadius: CGFloat { isCompactHeight ? 20 : 22 }
    var sendButtonWidth: CGFloat { size.width < 390 ? 82 : 94 }
    var codeBoxHeight: CGFloat { isCompactHeight ? 78 : 98 }
    var codeBoxSpacing: CGFloat { size.width < 390 ? 8 : 12 }
    var codeBoxCornerRadius: CGFloat { isCompactHeight ? 16 : 18 }
    var noticePadding: CGFloat { isCompactHeight ? 16 : 18 }
    var noticeIconSize: CGFloat { isCompactHeight ? 22 : 24 }
    var ctaHeight: CGFloat { isCompactHeight ? 72 : 84 }
    var ctaTopPadding: CGFloat { isCompactHeight ? 8 : 14 }
}

struct SignupPlaceholderView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            SignupPlaceholderView(
                client: PreviewSignupClient(),
                onClose: {},
                onBackToLogin: { _ in }
            )
            .previewDisplayName("Verify Email")

            VStack {
                SignupCodeEntryField(
                    code: .constant("123456"),
                    boxHeight: 92,
                    boxSpacing: 10,
                    cornerRadius: 20
                )
            }
            .padding(24)
            .background(Color(.systemGroupedBackground))
            .previewDisplayName("Code Entry")
        }
    }
}
