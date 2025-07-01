module.exports = ({ config }) => {
  return {
    ...config,
    extra: {
      ...(config.extra ?? {}),
      sdkKey: process.env.LAUNCHDARKLY_MOBILE_KEY,
    },
  }
}
