using System.Threading;
using LaunchDarkly.Logging;

namespace LaunchDarkly.Observability.Logging
{
    internal static class DebugLogger
    {
        private static Logger _logger;
        public static void SetLogger(Logger logger)
        {
            Volatile.Write(ref _logger, logger.SubLogger("LaunchDarklyObservability"));
        }

        public static void DebugLog(string message)
        {
            Volatile.Read(ref _logger)?.Debug(message);
        }
    }
}
