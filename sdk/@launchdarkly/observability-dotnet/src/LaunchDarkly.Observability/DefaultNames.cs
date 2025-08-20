namespace LaunchDarkly.Observability
{
    internal static class DefaultNames
    {
        public static string MeterNameOrDefault(string meterName)
        {
            return string.IsNullOrWhiteSpace(meterName) ? MeterName : meterName;
        }
        
        public static string ActivitySourceNameOrDefault(string sourceName)
        {
            return string.IsNullOrWhiteSpace(sourceName) ? ActivitySourceName : sourceName;
        }
        
        public static string LoggerNameOrDefault(string name)
        {
            return string.IsNullOrWhiteSpace(name) ? LoggerName : name;
        }
        
        private const string MeterName = "launchdarkly-plugin-default-metrics";
        private const string ActivitySourceName = "launchdarkly-plugin-default-activity";
        private const string LoggerName = "launchdarkly-plugin-default-logger";
    }
}
