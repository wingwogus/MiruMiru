import SwiftUI

struct AuthTextFieldRow<Accessory: View>: View {
    let label: String
    let systemImage: String
    let errorText: String?
    let minHeight: CGFloat
    let horizontalPadding: CGFloat
    let cornerRadius: CGFloat
    @ViewBuilder let field: () -> Accessory

    init(
        label: String,
        systemImage: String,
        errorText: String?,
        minHeight: CGFloat = 84,
        horizontalPadding: CGFloat = 22,
        cornerRadius: CGFloat = 22,
        @ViewBuilder field: @escaping () -> Accessory
    ) {
        self.label = label
        self.systemImage = systemImage
        self.errorText = errorText
        self.minHeight = minHeight
        self.horizontalPadding = horizontalPadding
        self.cornerRadius = cornerRadius
        self.field = field
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label)
                .font(AppFont.semibold(16, relativeTo: .subheadline))
                .tracking(2)
                .foregroundStyle(AppTheme.textSecondary)

            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(AppTheme.textTertiary)
                    .frame(width: 28)

                field()
            }
            .padding(.horizontal, horizontalPadding)
            .frame(minHeight: minHeight)
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(AppTheme.surfaceSecondary)
                    .overlay(
                        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                            .stroke(errorText == nil ? Color.clear : Color.red.opacity(0.45), lineWidth: 1)
                    )
            )

            if let errorText {
                Text(errorText)
                    .font(AppFont.medium(13, relativeTo: .footnote))
                    .foregroundStyle(.red)
                    .padding(.horizontal, 8)
            }
        }
    }
}

struct AuthTextFieldRow_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            AuthTextFieldRowPreviewContent()
                .padding(24)
                .background(Color(.systemGroupedBackground))
                .previewDisplayName("Light")

            AuthTextFieldRowPreviewContent()
                .padding(24)
                .background(Color(.systemGroupedBackground))
                .preferredColorScheme(.dark)
                .previewDisplayName("Dark")
        }
    }
}

private struct AuthTextFieldRowPreviewContent: View {
    var body: some View {
        VStack(spacing: 20) {
            AuthTextFieldRow(
                label: "EMAIL ADDRESS",
                systemImage: "envelope.fill",
                errorText: nil
            ) {
                Text("preview@tokyo.ac.jp")
                    .font(AppFont.medium(18, relativeTo: .body))
                    .foregroundStyle(AppTheme.textSecondary)
                Spacer()
            }

            AuthTextFieldRow(
                label: "PASSWORD",
                systemImage: "lock.fill",
                errorText: "Password is required."
            ) {
                Text("••••••••")
                    .font(AppFont.medium(18, relativeTo: .body))
                    .foregroundStyle(AppTheme.textSecondary)
                Spacer()
            }
        }
    }
}
