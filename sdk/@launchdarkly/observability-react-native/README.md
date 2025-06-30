# LaunchDarkly Observability SDK for React Native

A comprehensive observability solution for React Native applications using OpenTelemetry.

## Installation

### Basic Installation

```bash
# or
```

### Required Dependencies

- `@react-native-async-storage/async-storage` - For session persistence

### Optional Dependencies

For enhanced functionality, you can install these optional dependencies:

```bash
npm install react-native-device-info
# or
yarn add react-native-device-info
```

**Optional Dependencies:**

- `react-native-device-info` - Provides real device information (device ID, app version)
  - **If not installed:** SDK will generate and persist fallback device IDs
  - **Benefit:** More accurate device tracking and app version reporting

### Expo Installation

For Expo projects, also install:

```bash
expo install expo-constants
```

## Usage

```typescript
import { LDClient } from '@launchdarkly/react-native-client-sdk';
import { Observability } from '@launchdarkly/observability-react-native';

const client = new LDClient(
  mobileKey,
  user,
  {
    // ... other options
  },
  [
    new Observability({
      serviceName: 'my-react-native-app',
      serviceVersion: '1.0.0',
      enableTracing: true,
      enableMetrics: true,
      enableLogs: true,
    })
  ]
);
```

## Features

- **Session Management**: Automatic session tracking with configurable timeouts
- **Error Tracking**: Automatic error capture and reporting
- **Performance Monitoring**: HTTP request instrumentation
- **Custom Metrics**: Record custom metrics and events
- **Distributed Tracing**: Full OpenTelemetry tracing support
- **Flexible Installation**: Works with or without optional dependencies

## Configuration Options

```typescript
interface ReactNativeOptions {
  serviceName?: string;           // Default: 'react-native-app'
  serviceVersion?: string;        // Default: '1.0.0'
  otlpEndpoint?: string;         // Default: 'https://otlp.highlight.io:4318'
  enableTracing?: boolean;        // Default: true
  enableMetrics?: boolean;        // Default: true
  enableLogs?: boolean;          // Default: true
  enableErrorTracking?: boolean;  // Default: true
  sessionTimeout?: number;        // Default: 30 minutes
  debug?: boolean;               // Default: false
}
```

## Dependency Strategy

This SDK uses a **progressive enhancement** approach:

1. **Core functionality** works with minimal dependencies
2. **Enhanced features** unlock with optional dependencies
3. **Graceful degradation** when optional dependencies are missing
4. **No breaking changes** if you don't install optional dependencies

This means you can start with a lightweight installation and add features as needed.

## Automatic Instrumentation

The plugin automatically instruments:

### Network Requests
- Fetch API calls with trace propagation
- Request/response attributes and timing

### Error Handling
- Unhandled JavaScript errors
- Native crashes (when using full React Native build)
- Promise rejections

### Performance Metrics
- App startup time
- App lifecycle events (foreground/background)
- Device information (platform, version, screen size)

### Session Management
- Automatic session creation and management
- Device identification
- User association
- Session duration tracking

### Logging
- Console methods (log, error, warn, info, debug)
- Structured logging with session context
- Log level mapping

## Data Structure

### Session Context
Every telemetry event includes session context:

```typescript
{
  sessionId: string;
  userId?: string;
  deviceId: string;
  sessionDuration: number;
  appVersion: string;
  platform: string;
  installationId: string;
}
```

### Resource Attributes
Automatic resource attributes include:

- `service.name`: Your app's service name
- `service.version`: Your app's version
- `session.id`: Current session ID
- `device.platform`: iOS or Android
- `app.version`: App version from device info
- `session.device_id`: Unique device identifier
- `session.installation_id`: Unique installation identifier

## API Reference

### LDObserve

The main API for manual instrumentation:

#### Error Tracking
- `recordError(error, sessionId?, requestId?, metadata?, options?)`

#### Metrics
- `recordMetric(metric)` - Generic metric
- `recordCount(metric)` - Counter metric
- `recordIncr(metric)` - Increment by 1
- `recordHistogram(metric)` - Histogram metric
- `recordUpDownCounter(metric)` - Up/down counter

#### Logging
- `recordLog(message, level, sessionId?, requestId?, metadata?)`

#### Tracing
- `runWithHeaders(name, headers, callback, options?)` - Execute with span
- `startWithHeaders(spanName, headers, options?)` - Start span manually

#### Session Management
- `setUserId(userId)` - Associate user with session
- `getSessionInfo()` - Get current session information

#### Utilities
- `parseHeaders(headers)` - Extract context from headers
- `setAttributes(attributes)` - Set resource attributes
- `flush()` - Force flush telemetry data
- `stop()` - Stop observability client
- `isInitialized()` - Check initialization status

## Platform Support

- iOS 11.0+
- Android API level 21+
- React Native 0.70+
- Expo SDK 49+

## Troubleshooting

### Common Issues

1. **Native crash reporting not working in Expo Go**
   - Native crash reporting requires a full React Native build
   - Use `expo run:ios` or `expo run:android` for testing

2. **Permissions for device info**
   - Some device information requires permissions on Android
   - The plugin gracefully handles missing permissions

3. **Large bundle size**
   - The plugin includes OpenTelemetry dependencies
   - Consider using metro configuration to optimize bundle size

### Debug Mode

Enable debug mode to see detailed logging:

```typescript
new Observability({
  debug: true,
  enableConsoleLogging: true,
})
```

### Metro Configuration for OpenTelemetry Compatibility

If you encounter issues with Node.js built-ins (crypto, fs, os, etc.) being imported in your React Native app, you need to configure Metro to use browser-compatible versions of OpenTelemetry packages.

Add or merge the following configuration to your `metro.config.js` file:

```javascript
const defaultResolver = require('metro-resolver');

module.exports = {
  resolver: {
    resolveRequest: (context, realModuleName, platform, moduleName) => {
      const resolved = defaultResolver.resolve(
        {
          ...context,
          resolveRequest: null,
        },
        moduleName,
        platform,
      );

      // Force OpenTelemetry packages to use browser platform versions
      // instead of Node.js versions, which avoids Node.js built-ins
      if (
        resolved.type === 'sourceFile' &&
        resolved.filePath.includes('@opentelemetry')
      ) {
        resolved.filePath = resolved.filePath.replace(
          'platform/node',
          'platform/browser',
        );
        resolved.filePath = resolved.filePath.replace(
          'platform\\node',
          'platform\\browser',
        );
        return resolved;
      }

      return resolved;
    },
  },
  transformer: {
    getTransformOptions: async () => ({
      transform: {
        experimentalImportSupport: false,
        inlineRequires: true,
      },
    }),
  },
};
```

This configuration ensures that OpenTelemetry packages use their browser-compatible versions, which don't import Node.js built-ins like `crypto`, `fs`, `os`, etc.

For more information about this approach, see the [Splunk OpenTelemetry React Native documentation](https://github.com/signalfx/splunk-otel-react-native#instrument-lower-versions).

## Option 2: Alternative Rollup Configuration

If you prefer to continue using the bundled approach instead of raw source distribution, you can try externalizing OpenTelemetry packages as well. However, this approach is not recommended as it requires users to install all OpenTelemetry dependencies separately.

## Contributing

Please see [CONTRIBUTING.md](../../CONTRIBUTING.md) for guidelines.

## License

Apache-2.0 - see [LICENSE](./LICENSE) file for details.
