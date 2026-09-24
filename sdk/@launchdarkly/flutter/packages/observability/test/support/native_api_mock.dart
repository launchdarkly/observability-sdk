import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/messages.g.dart'
    as wire;

const _channelPrefix =
    'dev.flutter.pigeon.launchdarkly_flutter_observability.LDNativeApi.';

const _methods = [
  'start',
  'exportSpans',
  'recordLog',
  'track',
  'identify',
  'trackScreenView',
  'trackClick',
  'setEmbedderClickHandling',
  'shutdown',
];

/// Answers every `LDNativeApi` pigeon channel and records the calls it sees,
/// keyed by method name, each with its decoded argument list.
final class NativeApiMock {
  final calls = <String, List<List<Object?>>>{};

  List<List<Object?>> callsTo(String method) => calls[method] ?? const [];

  void install() {
    for (final method in _methods) {
      _messenger.setMockDecodedMessageHandler(_channel(method), (
        message,
      ) async {
        calls
            .putIfAbsent(method, () => [])
            .add((message as List<Object?>?) ?? const []);
        return <Object?>[
          method == 'start' ? wire.LDStartResult(nativeVersion: 'test') : null,
        ];
      });
    }
  }

  void uninstall() {
    for (final method in _methods) {
      _messenger.setMockDecodedMessageHandler(_channel(method), null);
    }
  }

  static TestDefaultBinaryMessenger get _messenger =>
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  static BasicMessageChannel<Object?> _channel(String method) =>
      BasicMessageChannel<Object?>(
        '$_channelPrefix$method',
        wire.LDNativeApi.pigeonChannelCodec,
      );
}
