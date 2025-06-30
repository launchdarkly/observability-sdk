# React Native OpenTelemetry Demo

This is a React Native application that demonstrates the LaunchDarkly Observability React Native SDK.

## Getting Started

1. **Install dependencies**:
   ```bash
   yarn install
   ```

2. **Start the OTel collector** (and view logs):
   ```bash
   yarn otel:start
   ```

3. **Configure LaunchDarkly** (optional):
   - Adjust OTLP endpoint and configuration as needed

4. **Start the development server**:
   ```bash
   # Make sure to set your SDK key when starting the app
   LAUNCHDARKLY_MOBILE_KEY=<your_mobile_sdk_key> yarn ios
   ```

## Configuration

### LaunchDarkly Setup

The observability plugin is configured in `lib/launchdarkly.ts`.
