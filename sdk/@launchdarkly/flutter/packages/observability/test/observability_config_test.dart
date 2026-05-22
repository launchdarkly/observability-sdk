import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';

void main() {
  group('configWithDefaults', () {
    test('uses default OTLP endpoint when not provided', () {
      final config = configWithDefaults();

      expect(
        config.otlpEndpoint,
        'https://otel.observability.app.launchdarkly.com:4318',
      );
    });

    test('uses default backend URL when not provided', () {
      final config = configWithDefaults();

      expect(
        config.backendUrl,
        'https://pub.observability.app.launchdarkly.com',
      );
    });

    test('uses custom OTLP endpoint when provided', () {
      final config = configWithDefaults(
        otlpEndpoint: 'https://custom-otel.example.com:4318',
      );

      expect(config.otlpEndpoint, 'https://custom-otel.example.com:4318');
    });

    test('uses custom backend URL when provided', () {
      final config = configWithDefaults(
        backendUrl: 'https://custom-backend.example.com',
      );

      expect(config.backendUrl, 'https://custom-backend.example.com');
    });

    test('sets application name when provided', () {
      final config = configWithDefaults(applicationName: 'MyApp');

      expect(config.applicationName, 'MyApp');
    });

    test('sets application version when provided', () {
      final config = configWithDefaults(applicationVersion: '1.2.3');

      expect(config.applicationVersion, '1.2.3');
    });

    test('sets context friendly name function when provided', () {
      String? friendlyNameFunc(LDContext context) => 'TestUser';

      final config = configWithDefaults(contextFriendlyName: friendlyNameFunc);

      expect(config.contextFriendlyName, friendlyNameFunc);
    });

    test('leaves application name null when not provided', () {
      final config = configWithDefaults();

      expect(config.applicationName, isNull);
    });

    test('leaves application version null when not provided', () {
      final config = configWithDefaults();

      expect(config.applicationVersion, isNull);
    });

    test('leaves context friendly name null when not provided', () {
      final config = configWithDefaults();

      expect(config.contextFriendlyName, isNull);
    });

    test('combines custom and default values', () {
      final config = configWithDefaults(
        applicationName: 'MyApp',
        applicationVersion: '1.0.0',
        backendUrl: 'https://custom.example.com',
      );

      expect(config.applicationName, 'MyApp');
      expect(config.applicationVersion, '1.0.0');
      expect(config.backendUrl, 'https://custom.example.com');
      expect(
        config.otlpEndpoint,
        'https://otel.observability.app.launchdarkly.com:4318',
      );
    });
  });
}
