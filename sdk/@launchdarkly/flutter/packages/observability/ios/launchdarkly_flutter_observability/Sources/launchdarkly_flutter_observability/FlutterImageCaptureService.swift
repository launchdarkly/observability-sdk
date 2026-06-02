import Flutter
import Foundation
import LaunchDarklySessionReplay
import UIKit

final class FlutterImageCaptureService: ImageCaptureServicing {
    private let channel: FlutterMethodChannel
    private let maskTextInputs: Bool
    @MainActor
    private var shouldCapture = false

    init(channel: FlutterMethodChannel, maskTextInputs: Bool) {
        self.channel = channel
        self.maskTextInputs = maskTextInputs
    }

    @MainActor
    func captureRawFrame(yield: @escaping (RawFrame?) async -> Void) {
        shouldCapture = true
        channel.invokeMethod(
            "captureFrame",
            arguments: ["maskTextInputs": maskTextInputs]
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
            let image = UIImage(data: data.data)
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
}
