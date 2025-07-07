# LaunchDarkly Observability SDK for React Native

A comprehensive observability solution for React Native applications using OpenTelemetry.

## Installation

### Basic Installation

```bash
npm install @launchdarkly/observability-react-native
# or
yarn add @launchdarkly/observability-react-native
```

### Required Dependencies

- `@react-native-async-storage/async-storage` - For session persistence

### Optional Dependencies

- `react-native-device-info` - Provides real device information (device ID, app version)
  - **If not installed:** SDK will generate and persist fallback device IDs
  - **Benefit:** More accurate device tracking and app version reporting

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
    })
  ]
);
```
