# React Native OpenTelemetry Demo

This is a React Native application that demonstrates the LaunchDarkly Observability React Native SDK.

## Getting Started

1. **Install dependencies**:
   ```bash
   yarn install
   ```

2. **Start the OTel collector**:
   ```bash
   yarn otel:start
   ```

3. **Configure LaunchDarkly** (optional):
   - Adjust OTLP endpoint and configuration as needed

4. **Run the app** (remember to set your SDK key):
   ```bash
   export LAUNCHDARKLY_MOBILE_KEY=<your_mobile_sdk_key>

   # Start the Metro bundler
   yarn start

   # Build and run in a specific simulator
   yarn ios:run
   ```

## Configuration

### LaunchDarkly Setup

The observability plugin is configured in `lib/launchdarkly.ts`.
