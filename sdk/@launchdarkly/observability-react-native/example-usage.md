# @launchdarkly/observability-react-native Example Usage

This example shows how to integrate the React Native observability plugin with LaunchDarkly SDK and use the manual instrumentation API.

## 1. Initialize LaunchDarkly Client with Observability Plugin

```typescript
import React from 'react';
import { View, Button, Alert } from 'react-native';
import { LDClient, LDUser } from '@launchdarkly/react-native-client-sdk';
import { Observability, LDObserve } from '@launchdarkly/observability-react-native';

const initializeLaunchDarkly = async () => {
  const user: LDUser = {
    key: 'user-123',
    name: 'Example User',
    email: 'user@example.com',
  };

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
        enablePerformanceMonitoring: true,
        customHeaders: {
          'x-api-key': 'your-api-key',
        },
        debug: __DEV__, // Enable debug mode only in development
      }),
    ],
  });

  await client.waitForInitialization();

  // Set user ID for session tracking
  await LDObserve.setUserId(user.key);

  return client;
};
```

## 2. Example React Component with Manual Instrumentation

```typescript
const ExampleScreen: React.FC = () => {
  const handleButtonPress = async () => {
    try {
      // Record a custom metric
      LDObserve.recordMetric({
        name: 'button_click',
        value: 1,
        attributes: {
          screen: 'example',
          button: 'primary',
        },
      });

      // Trace a custom operation
      const result = await LDObserve.runWithHeaders(
        'api-call',
        { 'x-user-id': 'user-123' },
        async (span) => {
          span.setAttribute('operation.type', 'data-fetch');

          // Simulate API call
          const response = await fetch('https://api.example.com/data');

          span.setAttribute('http.status_code', response.status);
          span.setAttribute('http.url', response.url);

          if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
          }

          return response.json();
        }
      );

      Alert.alert('Success', `Data loaded: ${JSON.stringify(result)}`);

      // Record success log
      LDObserve.recordLog(
        'Data fetch completed successfully',
        'info',
        {
          operation: 'data-fetch',
          resultCount: result.length || 0,
        }
      );

    } catch (error) {
      // Record error with context
      LDObserve.recordError(
        error as Error,
        undefined,
        undefined,
        {
          screen: 'example',
          operation: 'button-press',
          userAgent: 'react-native',
        }
      );

      Alert.alert('Error', `Operation failed: ${error.message}`);
    }
  };

  const handleMetricTest = () => {
    // Record different types of metrics
    LDObserve.recordCount({ name: 'user_actions', value: 1 });
    LDObserve.recordIncr({ name: 'screen_interactions', value: 1 });
    LDObserve.recordHistogram({
      name: 'response_time',
      value: Math.random() * 1000,
      attributes: { endpoint: 'test' }
    });
  };

  const handleErrorTest = () => {
    try {
      // Simulate an error
      throw new Error('This is a test error');
    } catch (error) {
      LDObserve.recordError(error as Error, undefined, undefined, {
        test: true,
        errorType: 'simulated',
      });
    }
  };

  const handleLogTest = () => {
    // Test different log levels
    LDObserve.recordLog('Debug message', 'debug', {
      component: 'ExampleScreen',
    });

    LDObserve.recordLog('Info message', 'info', {
      action: 'log-test',
    });

    LDObserve.recordLog('Warning message', 'warn', {
      severity: 'medium',
    });
  };

  return (
    <View style={{ flex: 1, justifyContent: 'center', padding: 20 }}>
      <Button title="Test API Call with Tracing" onPress={handleButtonPress} />
      <View style={{ height: 20 }} />
      <Button title="Test Metrics" onPress={handleMetricTest} />
      <View style={{ height: 20 }} />
      <Button title="Test Error Tracking" onPress={handleErrorTest} />
      <View style={{ height: 20 }} />
      <Button title="Test Logging" onPress={handleLogTest} />
    </View>
  );
};
```

## 3. App Component with LaunchDarkly Initialization

```typescript
const App: React.FC = () => {
  const [isInitialized, setIsInitialized] = React.useState(false);

  React.useEffect(() => {
    initializeLaunchDarkly()
      .then(() => {
        setIsInitialized(true);
        console.log('LaunchDarkly initialized with observability');
      })
      .catch((error) => {
        console.error('Failed to initialize LaunchDarkly:', error);
        // Even if LaunchDarkly fails, we can still show the app
        setIsInitialized(true);
      });
  }, []);

  if (!isInitialized) {
    return <View />; // Loading state
  }

  return <ExampleScreen />;
};

export default App;
```

## 4. Additional Utility Functions

### Flush Telemetry Data Before App Backgrounding

```typescript
export const flushTelemetryOnBackground = async () => {
  try {
    await LDObserve.flush();
    console.log('Telemetry data flushed successfully');
  } catch (error) {
    console.error('Failed to flush telemetry data:', error);
  }
};
```

### Get Current Session Information

```typescript
export const getCurrentSessionInfo = () => {
  const sessionInfo = LDObserve.getSessionInfo();
  console.log('Current session:', sessionInfo);
  return sessionInfo;
};
```

### Check if Observability is Properly Initialized

```typescript
export const checkObservabilityStatus = () => {
  const isInitialized = LDObserve.isInitialized();
  console.log('Observability initialized:', isInitialized);
  return isInitialized;
};
```

## Key Features Demonstrated

- **Tracing**: Using `LDObserve.runWithHeaders()` to trace API calls with custom spans
- **Metrics**: Recording different types of metrics (counts, histograms, increments)
- **Error Tracking**: Capturing and recording errors with contextual information
- **Logging**: Recording logs at different levels (debug, info, warn) with attributes
- **Session Management**: Setting user IDs and managing session information
- **Configuration**: Setting up the observability plugin with various options

## Usage Notes

1. Replace `'your-mobile-key'` with your actual LaunchDarkly mobile key
2. Update the `otlpEndpoint` to point to your OpenTelemetry collector
3. Configure `customHeaders` as needed for your authentication requirements
4. Set `debug: true` only in development environments
5. Consider calling `flushTelemetryOnBackground()` when your app goes to background
