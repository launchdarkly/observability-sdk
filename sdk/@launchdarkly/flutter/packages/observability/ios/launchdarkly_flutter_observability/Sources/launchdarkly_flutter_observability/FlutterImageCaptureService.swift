import Flutter
import Foundation
import LaunchDarklySessionReplay
import UIKit

final class FlutterImageCaptureService: ImageCaptureServicing {
    private let channel: FlutterMethodChannel
    private let maskTextInputs: Bool
    private let maskLabels: Bool
    private let maskImages: Bool
    private let maskWebViews: Bool
    private let minimumAlpha: Double
    /// Render scale the Dart side should capture at (1.0 = 1x). Kept in sync
    /// with the scale the native exporter records so frames are not oversized.
    private let scale: Double
    @MainActor
    private var shouldCapture = false

    init(
        channel: FlutterMethodChannel,
        maskTextInputs: Bool,
        maskLabels: Bool,
        maskImages: Bool,
        maskWebViews: Bool,
        minimumAlpha: Double,
        scale: Double
    ) {
        self.channel = channel
        self.maskTextInputs = maskTextInputs
        self.maskLabels = maskLabels
        self.maskImages = maskImages
        self.maskWebViews = maskWebViews
        self.minimumAlpha = minimumAlpha
        self.scale = scale
    }

    @MainActor
    func captureRawFrame(yield: @escaping (RawFrame?) async -> Void) {
        shouldCapture = true
        channel.invokeMethod(
            "captureFrame",
            arguments: [
                "maskTextInputs": maskTextInputs,
                "maskLabels": maskLabels,
                "maskImages": maskImages,
                "maskWebViews": maskWebViews,
                "minimumAlpha": minimumAlpha,
                "scale": scale,
            ]
        ) { [weak self] result in
            Task { @MainActor in
                guard let self, self.shouldCapture else {
                    await yield(nil)
                    return
                }
                await yield(self.rawFrame(from: result))
            }
        }
    }

    @MainActor
    func interuptCapture() {
        shouldCapture = false
    }

    private func rawFrame(from result: Any?) -> RawFrame? {
        guard
            let payload = result as? [String: Any],
            let data = payload["bytes"] as? FlutterStandardTypedData,
            let width = (payload["width"] as? NSNumber)?.intValue,
            let height = (payload["height"] as? NSNumber)?.intValue,
            let image = Self.image(fromRGBA: data.data, width: width, height: height)
        else {
            return nil
        }

        let timestamp = (payload["timestamp"] as? NSNumber)
            .map { TimeInterval(truncating: $0) / 1_000.0 }
            ?? Date().timeIntervalSince1970
        let orientation = (payload["orientation"] as? NSNumber)?.intValue ?? 0

        return RawFrame(
            image: image,
            timestamp: timestamp,
            orientation: orientation,
            areas: []
        )
    }

    /// Wraps Flutter's raw RGBA bytes (8 bits/channel, premultiplied alpha,
    /// row-major) into a `UIImage` at scale 1, matching the device-pixel
    /// resolution the Dart side rendered at. Avoids decoding a PNG on the wire.
    private static func image(fromRGBA data: Data, width: Int, height: Int) -> UIImage? {
        guard width > 0, height > 0 else { return nil }

        let bytesPerRow = width * 4
        guard data.count >= bytesPerRow * height else { return nil }

        guard let provider = CGDataProvider(data: data as CFData) else { return nil }
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue)

        guard let cgImage = CGImage(
            width: width,
            height: height,
            bitsPerComponent: 8,
            bitsPerPixel: 32,
            bytesPerRow: bytesPerRow,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: bitmapInfo,
            provider: provider,
            decode: nil,
            shouldInterpolate: false,
            intent: .defaultIntent
        ) else {
            return nil
        }

        return UIImage(cgImage: cgImage, scale: 1.0, orientation: .up)
    }
}
