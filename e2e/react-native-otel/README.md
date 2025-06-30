# React Native OpenTelemetry Demo

This is a React Native application built with Expo SDK 53 that demonstrates the LaunchDarkly Observability React Native SDK. The app showcases automatic instrumentation for tracing, logging, metrics, and error tracking.

## Features

- 🔍 **Automatic Tracing**: Network requests and custom operation tracing
- 📊 **Metrics Collection**: Performance metrics, custom metrics, and device metrics
- 📝 **Structured Logging**: Automatic console instrumentation with session context
- 🐛 **Error Tracking**: Automatic error capture with stack traces and custom error recording
- 📱 **Session Management**: Automatic session tracking with device context
- ⚡ **Performance Monitoring**: App lifecycle events and device information

## Architecture

This app uses:
- **Expo SDK 53** with React Native 0.79
- **New Architecture** enabled by default
- **LaunchDarkly React Native SDK v10**
- **LaunchDarkly Observability React Native SDK**

## Prerequisites

- Node.js 18+ and Yarn
- iOS Simulator or Android Emulator
- Expo CLI (`npx expo-cli@latest`)

## Getting Started

1. **Install dependencies** (from the monorepo root):
   ```bash
   yarn install
   ```

2. **Start the development server** (with mobile SDK key):
   ```bash
   LAUNCHDARKLY_MOBILE_KEY=<your_mobile_sdk_key> yarn ios
   ```

3. **Run on your platform**:
   - Follow instructions to run on whatever platform

4. **Configure LaunchDarkly** (optional):
   - Adjust OTLP endpoint and configuration as needed

## Demo Features

The app includes several interactive demonstrations:

### 1. Session Information
- Displays current session ID, user ID, device ID, app version, and platform
- Shows automatic session tracking capabilities

### 2. Test Observability Features
- **Record Test Error**: Demonstrates custom error recording with metadata
- **Make Network Request**: Shows automatic network request tracing and logging
- **Record Custom Metrics**: Examples of various metric types (count, histogram, etc.)
- **Set Random User ID**: Demonstrates user association with sessions

### 3. Automatic Instrumentation
- Console logging is automatically hooked and sent as structured logs
- Network requests are automatically traced
- Errors are automatically captured with stack traces
- App lifecycle events are tracked

## Configuration

### LaunchDarkly Setup

The observability plugin is configured in `lib/launchdarkly.ts`:

```typescript
new Observability({
  serviceName: 'react-native-otel-demo',
  serviceVersion: Constants.expoConfig?.version || '1.0.0',
  otlpEndpoint: Constants.expoConfig?.extra?.sdkKey,
  enableTracing: true,
  enableLogs: true,
  enableMetrics: true,
  enableErrorTracking: true,
  enablePerformanceMonitoring: true,
  enableConsoleLogging: true,
  debug: __DEV__,
  customHeaders: {
    'x-service-name': 'react-native-otel-demo',
  },
  sessionTimeout: 30 * 60 * 1000, // 30 minutes
})
```

### OTLP Endpoint Configuration

By default, the app uses `https://otlp.highlight.io:4318` as the OTLP endpoint. You can:

1. Use your own OpenTelemetry collector
2. Use a different observability platform (Jaeger, Zipkin, etc.)
3. Use LaunchDarkly's own observability endpoints

Update the `otlpEndpoint` and `customHeaders` in the configuration as needed.

## Development Console

Check your development console to see:

- **Structured Logs**: All console messages with session context
- **Trace Information**: Span details and trace hierarchy
- **Error Details**: Stack traces and error metadata
- **Performance Metrics**: Request durations and custom metrics
- **Session Events**: User actions and lifecycle events

## Observability API Usage

### Manual Instrumentation

```typescript
import { LDObserve } from '@launchdarkly/observability-react-native';

// Record custom errors
LDObserve.recordError(error, sessionId, requestId, metadata);

// Record custom metrics
LDObserve.recordMetric({
  name: 'user_action',
  value: 1,
  attributes: { action: 'button_click', screen: 'home' }
});

// Record custom logs
LDObserve.recordLog('User action', 'info', sessionId, requestId, metadata);

// Trace custom operations
LDObserve.runWithHeaders('operation-name', {}, (span) => {
  span.setAttribute('custom.attribute', 'value');
  // Your operation code here
});

// Set user ID for session tracking
await LDObserve.setUserId('user123');
```

### Session Management

```typescript
// Get current session information
const sessionInfo = await LDObserve.getSessionInfo();

// Set user ID
await LDObserve.setUserId('user123');

// Check if observability is initialized
const isReady = LDObserve.isInitialized();
```

## Project Structure

```
e2e/react-native-otel/
├── app/                    # App screens and navigation
│   ├── (tabs)/
│   │   ├── index.tsx       # Main demo screen
│   │   └── explore.tsx     # Secondary screen
│   └── _layout.tsx         # Root layout with LaunchDarkly initialization
├── lib/
│   └── launchdarkly.ts     # LaunchDarkly and Observability configuration
├── components/             # Reusable UI components
├── assets/                 # Images and other assets
├── app.json               # Expo configuration
├── package.json           # Dependencies and scripts
└── README.md              # This file
```

## Dependencies

### Core Dependencies
- `@launchdarkly/react-native-client-sdk`: LaunchDarkly feature flags
- `@launchdarkly/observability-react-native`: Observability instrumentation
- `expo`: Expo SDK and tooling

### Required Peer Dependencies
- `@react-native-async-storage/async-storage`: Persistent storage
- `react-native-device-info`: Device information

## Troubleshooting

### Common Issues

1. **LaunchDarkly not initialized**:
   - Check that you've set a valid mobile key
   - Ensure internet connectivity

2. **Observability data not appearing**:
   - Verify OTLP endpoint configuration
   - Check network connectivity
   - Enable debug mode in configuration

3. **Build errors**:
   - Run `yarn install` from the monorepo root
   - Clear Metro cache: `npx expo start --clear`

### Debug Mode

Enable debug mode by setting `debug: true` in the Observability configuration. This will log detailed information about:
- Telemetry data being sent
- Network requests to OTLP endpoints
- Session management
- Error details

## New Architecture Support

This app is configured to use React Native's New Architecture by default (enabled in `app.json`). The LaunchDarkly Observability SDK is fully compatible with the New Architecture.

## Contributing

To add new observability features or fix issues:

1. Make changes to the relevant files
2. Test on both iOS and Android
3. Verify observability data is being generated correctly
4. Update this README if needed

## Learn More

- [LaunchDarkly React Native SDK Documentation](https://docs.launchdarkly.com/sdk/client-side/react/react-native)
- [LaunchDarkly Observability SDK Documentation](../../../sdk/@launchdarkly/observability-react-native/README.md)
- [Expo Documentation](https://docs.expo.dev/)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
