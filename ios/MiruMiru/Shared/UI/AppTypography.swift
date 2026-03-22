import SwiftUI

enum AppFont {
    static func regular(_ size: CGFloat, relativeTo textStyle: Font.TextStyle = .body) -> Font {
        .custom("Pretendard-Regular", size: size, relativeTo: textStyle)
    }

    static func medium(_ size: CGFloat, relativeTo textStyle: Font.TextStyle = .body) -> Font {
        .custom("Pretendard-Medium", size: size, relativeTo: textStyle)
    }

    static func semibold(_ size: CGFloat, relativeTo textStyle: Font.TextStyle = .body) -> Font {
        .custom("Pretendard-SemiBold", size: size, relativeTo: textStyle)
    }

    static func bold(_ size: CGFloat, relativeTo textStyle: Font.TextStyle = .body) -> Font {
        .custom("Pretendard-Bold", size: size, relativeTo: textStyle)
    }

    static func extraBold(_ size: CGFloat, relativeTo textStyle: Font.TextStyle = .body) -> Font {
        .custom("Pretendard-ExtraBold", size: size, relativeTo: textStyle)
    }

    static func black(_ size: CGFloat, relativeTo textStyle: Font.TextStyle = .body) -> Font {
        .custom("Pretendard-Black", size: size, relativeTo: textStyle)
    }
}
