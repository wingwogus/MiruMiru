import SwiftUI

struct ForgotPasswordPlaceholderView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                Image(systemName: "key.fill")
                    .font(.system(size: 40))
                    .foregroundStyle(Color(red: 0.20, green: 0.10, blue: 0.96))

                Text("Password recovery is pending")
                    .font(AppFont.bold(22, relativeTo: .title3))

                Text("We'll connect this button to the real recovery flow in a follow-up milestone.")
                    .font(AppFont.regular(16, relativeTo: .body))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 30)

                Button("Close") {
                    dismiss()
                }
                .font(AppFont.semibold(16, relativeTo: .body))
                .buttonStyle(.borderedProminent)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(.systemGroupedBackground))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text("Forgot Password")
                        .font(AppFont.semibold(18, relativeTo: .headline))
                }
            }
        }
    }
}

struct ForgotPasswordPlaceholderView_Previews: PreviewProvider {
    static var previews: some View {
        ForgotPasswordPlaceholderView()
    }
}
