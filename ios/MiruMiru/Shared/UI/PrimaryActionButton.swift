import SwiftUI

struct PrimaryActionButton: View {
    let title: String
    let isLoading: Bool
    let isDisabled: Bool
    let height: CGFloat
    let cornerRadius: CGFloat
    let action: () -> Void

    init(
        title: String,
        isLoading: Bool,
        isDisabled: Bool,
        height: CGFloat = 84,
        cornerRadius: CGFloat = 22,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.isLoading = isLoading
        self.isDisabled = isDisabled
        self.height = height
        self.cornerRadius = cornerRadius
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            ZStack {
                Text(title)
                    .opacity(isLoading ? 0 : 1)

                if isLoading {
                    ProgressView()
                        .tint(.white)
                }
            }
            .font(AppFont.bold(20, relativeTo: .headline))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .background(
                LinearGradient(
                    colors: [
                        AuthPalette.primaryStart,
                        AuthPalette.primaryEnd
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .shadow(color: AuthPalette.primaryShadow, radius: 18, y: 10)
        }
        .buttonStyle(.plain)
        .disabled(isDisabled || isLoading)
        .opacity(isDisabled || isLoading ? 0.82 : 1)
    }
}

struct PrimaryActionButton_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 20) {
            PrimaryActionButton(
                title: "Continue",
                isLoading: false,
                isDisabled: false
            ) {}

            PrimaryActionButton(
                title: "Continue",
                isLoading: true,
                isDisabled: false
            ) {}
        }
        .padding(24)
    }
}
