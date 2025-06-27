# @launchdarkly/observability-react-native

A React Native plugin for LaunchDarkly that provides automatic OpenTelemetry instrumentation for traces, logs, metrics, and error tracking.

## Features

- 🔍 **Automatic Tracing**: Instruments network requests and custom traces
- 📊 **Metrics Collection**: Performance metrics, custom metrics, and device metrics
- 📝 **Structured Logging**: Automatic console instrumentation with session context
- 🐛 **Error Tracking**: Automatic error capture with stack traces
- 📱 **Session Management**: Automatic session tracking with device context
- ⚡ **Performance Monitoring**: App startup time, lifecycle events, and device info
- 🔧 **Highly Configurable**: Flexible configuration options

## Installation

```bash
npm install @launchdarkly/observability-react-native
```

### Peer Dependencies

Make sure you have these peer dependencies installed:

```bash
npm install @launchdarkly/react-native-client-sdk react-native expo-constants
```

### Additional Dependencies

The plugin requires these React Native dependencies:

```bash
npm install @react-native-async-storage/async-storage react-native-device-info react-native-get-random-values uuid react-native-exception-handler
```

## Usage

### Basic Setup

```typescript
import { LDClient } from '@launchdarkly/react-native-client-sdk';
import { Observability } from '@launchdarkly/observability-react-native';

// Initialize LaunchDarkly client with observability plugin
const client = new LDClient('your-mobile-key', user, {
  plugins: [
    new Observability({
      serviceName: 'my-react-native-app',
      serviceVersion: '1.0.0',
      otlpEndpoint: 'https://your-otlp-endpoint.com:4318',
      enableTracing: true,
      enableLogs: true,
      enableMetrics: true,
      enableErrorTracking: true,
      debug: false,
    }),
  ],
});
```

### Manual Instrumentation

After initializing the plugin, you can use the `LDObserve` API for manual instrumentation:

```typescript
import { LDObserve } from '@launchdarkly/observability-react-native';

// Record custom errors
try {
  // Your code here
} catch (error) {
  LDObserve.recordError(error, 'session-id', 'request-id', {
    customAttribute: 'value',
  });
}

// Record custom metrics
LDObserve.recordMetric({
  name: 'user_action',
  value: 1,
  attributes: {
    action: 'button_click',
    screen: 'home',
  },
});

// Record custom logs
LDObserve.recordLog('User performed action', 'info', 'session-id', 'request-id', {
  userId: 'user123',
  action: 'navigation',
});

// Set user ID for session tracking
await LDObserve.setUserId('user123');

// Trace custom operations
LDObserve.runWithHeaders('custom-operation', {}, (span) => {
  span.setAttribute('operation.type', 'data-processing');
  // Your operation code here
  return result;
});
```

## Configuration Options

### ReactNativeOptions

```typescript
interface ReactNativeOptions {
  /** OTLP endpoint URL (default: 'https://otlp.highlight.io:4318') */
  otlpEndpoint?: string;

  /** Service name (default: 'react-native-app') */
  serviceName?: string;

  /** Service version (default: '1.0.0') */
  serviceVersion?: string;

  /** Additional resource attributes */
  resourceAttributes?: ResourceAttributes;

  /** Enable console logging of telemetry (default: false) */
  enableConsoleLogging?: boolean;

  /** Enable automatic error tracking (default: true) */
  enableErrorTracking?: boolean;

  /** Enable performance monitoring (default: true) */
  enablePerformanceMonitoring?: boolean;

  /** Enable tracing (default: true) */
  enableTracing?: boolean;

  /** Enable metrics (default: true) */
  enableMetrics?: boolean;

  /** Enable logs (default: true) */
  enableLogs?: boolean;

  /** Custom headers for OTLP exports */
  customHeaders?: Record<string, string>;

  /** Session timeout in milliseconds (default: 30 minutes) */
  sessionTimeout?: number;

  /** Enable native crash reporting (default: true) */
  enableNativeCrashReporting?: boolean;

  /** Debug mode (default: false) */
  debug?: boolean;
}
```

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

## Contributing

Please see [CONTRIBUTING.md](../../CONTRIBUTING.md) for guidelines.

## License

Apache-2.0 - see [LICENSE](./LICENSE) file for details.
