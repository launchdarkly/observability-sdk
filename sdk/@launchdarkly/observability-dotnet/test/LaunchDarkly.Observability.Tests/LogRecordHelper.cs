using System.Collections.Generic;
using System.Linq;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using NUnit.Framework;
using OpenTelemetry.Logs;

namespace LaunchDarkly.Observability.Test
{
    public static class LogRecordHelper
    {
        /// <summary>
        /// Creates a LogRecord for testing
        /// </summary>
        public static LogRecord CreateTestLogRecord(LogLevel level, string message,
            Dictionary<string, object> attributes = null)
        {
            var services = new ServiceCollection();
            var records = new List<LogRecord>();

            services.AddOpenTelemetry().WithLogging(logging => { logging.AddInMemoryExporter(records); });

            var provider = services.BuildServiceProvider();
            var loggerProvider = provider.GetService<ILoggerProvider>();
            var withScope = loggerProvider as ISupportExternalScope;
            Assert.That(withScope, Is.Not.Null);
            withScope.SetScopeProvider(new LoggerExternalScopeProvider());
            var logger = loggerProvider.CreateLogger("test");

            // Log with attributes if provided - use the same pattern as CustomSamplerTests
            if (attributes != null && attributes.Count > 0)
            {
                logger.Log(level, new EventId(), attributes, null,
                    (objects, exception) => message);
            }
            else
            {
                logger.Log<object>(level, new EventId(), null, null,
                    (objects, exception) => message);
            }

            return records.First();
        }
    }
}
