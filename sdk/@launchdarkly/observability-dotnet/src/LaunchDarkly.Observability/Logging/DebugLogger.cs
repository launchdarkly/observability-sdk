using System.Threading;
using LaunchDarkly.Logging;

namespace LaunchDarkly.Observability.Logging
{
    internal static class DebugLogger
    {
        private static Logger _logger;

        /// <summary>
        /// Set 
        /// </summary>
        /// <param name="logger"></param>
        public static void SetLogger(Logger logger)
        {
            if (logger == null)
            {
                Volatile.Write(ref _logger, null);
                return;
            }

            Volatile.Write(ref _logger, logger.SubLogger("LaunchDarklyObservability"));
        }

        public static void DebugLog(string message)
        {
            Volatile.Read(ref _logger)?.Debug(message);
        }
    }
}
