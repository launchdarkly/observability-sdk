const OTLP_HTTP = 'https://otel.observability.app.launchdarkly.com:4318'
const OTLP_HTTP_DEV = 'http://localhost:4318'

module.exports = ({ config }) => {
  return {
    ...config,
    extra: {
      ...(config.extra ?? {}),
      sdkKey: process.env.LAUNCHDARKLY_MOBILE_KEY ?? '',
      otel: {
        endpoint: process.env.OTEL_ENDPOINT ?? (process.env.NODE_ENV === 'development' ? OTLP_HTTP_DEV : OTLP_HTTP),
        serviceName: process.env.OTEL_SERVICE_NAME ?? "react-native-otel",
      },
    },
  }
}
