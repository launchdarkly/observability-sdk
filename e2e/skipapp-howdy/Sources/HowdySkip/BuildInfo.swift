import Foundation

/// Best-effort "when was this build produced" timestamp shown on the Welcome screen.
///
/// Both platforms read the installed binary at runtime, so no build-time codegen is needed:
///
/// - **iOS:** the modification date of the app executable inside the bundle.
/// - **Android:** the installed package's `lastUpdateTime`, reached through a `#if SKIP`
///   Kotlin helper.
public enum BuildInfo {
    /// The compile/build timestamp, or `nil` when it cannot be determined.
    public static var compiledAt: Date? {
        #if os(Android)
        let millis = BuildInfoAndroid.lastUpdateMillis()
        guard millis > 0 else { return nil }
        return Date(timeIntervalSince1970: millis / 1000)
        #else
        guard let path = Bundle.main.executableURL?.path,
              let modified = try? FileManager.default.attributesOfItem(atPath: path)[.modificationDate] as? Date else {
            return nil
        }
        return modified
        #endif
    }

    /// The build timestamp formatted for display, or `nil` when it is unavailable.
    public static var displayString: String? {
        guard let date = compiledAt else { return nil }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: date)
    }
}

// MARK: - Android (transpiled to Kotlin by Skip)

#if SKIP
/// Kotlin-side package metadata, bridged into Fuse native Swift on Android.
///
/// The value crosses the bridge as a `Double` of epoch milliseconds, since only
/// bridgeable scalar types may appear in a signature ``BuildInfo`` calls.
public enum BuildInfoAndroid {
    /// The installed package's last update time, in milliseconds since the epoch. This is
    /// the closest Android analogue to "when this build landed on the device".
    public static func lastUpdateMillis() -> Double {
        let context = ProcessInfo.processInfo.androidContext
        let info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
        return Double(info.lastUpdateTime)
    }
}
#endif
