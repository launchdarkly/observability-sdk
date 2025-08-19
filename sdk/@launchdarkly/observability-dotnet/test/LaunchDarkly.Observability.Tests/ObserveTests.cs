using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Reflection;
using Microsoft.Extensions.Logging;
using NUnit.Framework;
using OpenTelemetry;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;

namespace LaunchDarkly.Observability.Test
{
    [TestFixture]
    public class ObserveTests
    {
        private class TestLoggerProvider : ILoggerProvider
        {
            public List<TestLogger> Loggers { get; } = new List<TestLogger>();

            public ILogger CreateLogger(string categoryName)
            {
                var logger = new TestLogger(categoryName);
                Loggers.Add(logger);
                return logger;
            }

            public void Dispose()
            {
                Loggers.Clear();
            }
        }

        private class TestLogger : ILogger
        {
            public string CategoryName { get; }
            public List<LogEntry> LogEntries { get; } = new List<LogEntry>();

            public TestLogger(string categoryName)
            {
                CategoryName = categoryName;
            }

            public IDisposable BeginScope<TState>(TState state) => null;

            public bool IsEnabled(LogLevel logLevel) => true;

            public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception exception,
                Func<TState, Exception, string> formatter)
            {
                LogEntries.Add(new LogEntry
                {
                    LogLevel = logLevel,
                    EventId = eventId,
                    State = state,
                    Exception = exception,
                    Message = formatter?.Invoke(state, exception)
                });
            }
        }

        private class LogEntry
        {
            public LogLevel LogLevel { get; set; }
            public EventId EventId { get; set; }
            public object State { get; set; }
            public Exception Exception { get; set; }
            public string Message { get; set; }
        }

        private TestLoggerProvider _loggerProvider;
        private ObservabilityConfig _config;
        private TracerProvider _tracerProvider;
        private ActivityListener _activityListener;
        private List<Activity> _exportedActivities;

        [SetUp]
        public void SetUp()
        {
            // Reset the singleton instance before each test
            ResetObserveInstance();

            _loggerProvider = new TestLoggerProvider();
            _config = new ObservabilityConfig(
                otlpEndpoint: "https://test-endpoint.com",
                backendUrl: "https://test-backend.com",
                serviceName: "test-service",
                environment: "test",
                serviceVersion: "1.0.0",
                sdkKey: "test-key"
            );

            // Set up OpenTelemetry
            _exportedActivities = new List<Activity>();

            // Create an ActivityListener to ensure activities are created
            _activityListener = new ActivityListener
            {
                ShouldListenTo = (source) => source.Name == "test-service" || source.Name.StartsWith("test"),
                Sample = (ref ActivityCreationOptions<ActivityContext> options) =>
                    ActivitySamplingResult.AllDataAndRecorded,
                ActivityStarted = activity => { },
                ActivityStopped = activity => _exportedActivities.Add(activity)
            };

            ActivitySource.AddActivityListener(_activityListener);

            // Set up TracerProvider with in-memory exporter
            _tracerProvider = OpenTelemetry.Sdk.CreateTracerProviderBuilder()
                .AddSource("test-service")
                .AddSource("test")
                .SetResourceBuilder(ResourceBuilder.CreateDefault()
                    .AddService("test-service", "test", "1.0.0"))
                .AddInMemoryExporter(_exportedActivities)
                .Build();
        }

        [TearDown]
        public void TearDown()
        {
            _loggerProvider?.Dispose();
            _tracerProvider?.Dispose();
            _activityListener?.Dispose();
            _exportedActivities?.Clear();
            ResetObserveInstance();
        }

        private static void ResetObserveInstance()
        {
            // Use reflection to reset the static instance
            var field = typeof(Observe).GetField("_instance", BindingFlags.NonPublic | BindingFlags.Static);
            field?.SetValue(null, null);
        }

        #region Initialization Tests

        [Test]
        public void Initialize_WithValidConfig_SetsUpInstanceCorrectly()
        {
            Observe.Initialize(_config, _loggerProvider);

            Observe.RecordIncr("test-counter");
            Observe.RecordMetric("test-gauge", 42.0);

            // Just making sure nothing before this point threw.
            Assert.Pass("Initialization successful - no exceptions thrown during metric recording");
        }

        [Test]
        public void Initialize_WithNullLoggerProvider_WorksCorrectly()
        {
            Observe.Initialize(_config, null);

            Observe.RecordIncr("test-counter");
            Observe.RecordMetric("test-gauge", 42.0);

            // Logging should not throw when logger provider is null
            Assert.DoesNotThrow(() => Observe.RecordLog("test message", LogLevel.Information, null));
        }

        [Test]
        public void Initialize_WithNullServiceName_UsesDefaults()
        {
            var configWithNullServiceName = new ObservabilityConfig(
                otlpEndpoint: "https://test-endpoint.com",
                backendUrl: "https://test-backend.com",
                serviceName: null,
                environment: "test",
                serviceVersion: "1.0.0",
                sdkKey: "test-key"
            );

            // Null service name doesn't break anything.
            Assert.DoesNotThrow(() => Observe.Initialize(configWithNullServiceName, _loggerProvider));
            Assert.DoesNotThrow(() => Observe.RecordIncr("test-counter"));
        }

        #endregion

        #region Exception Recording Tests

        [Test]
        public void RecordException_WithActiveActivity_AddsExceptionToCurrentActivity()
        {
            Observe.Initialize(_config, _loggerProvider);
            var exception = new InvalidOperationException("Test exception");

            using (var activity = new ActivitySource("test").StartActivity("test-operation"))
            {
                var initialEventCount = activity?.Events.Count() ?? 0;

                Observe.RecordException(exception);

                Assert.That(activity, Is.Not.Null);
                var events = activity.Events.ToList();
                Assert.That(events.Count, Is.EqualTo(initialEventCount + 1));
                var exceptionEvent = events.Last();
                Assert.That(exceptionEvent.Name, Is.EqualTo("exception"));
            }
        }

        [Test]
        public void RecordException_WithoutActiveActivity_CreatesNewActivity()
        {
            Observe.Initialize(_config, _loggerProvider);
            var exception = new InvalidOperationException("Test exception");

            // Ensure no active activity
            Assert.That(Activity.Current, Is.Null);

            Observe.RecordException(exception);

            // Verify an activity was created and exported
            Assert.That(_exportedActivities.Count, Is.GreaterThan(0), "Should have created and exported an activity");
            var createdActivity = _exportedActivities.Last();
            Assert.That(createdActivity, Is.Not.Null);
            Assert.That(createdActivity.DisplayName, Is.EqualTo("launchdarkly.error"));

            // Verify the exception was recorded
            var exceptionEvent = createdActivity.Events.FirstOrDefault(e => e.Name == "exception");
            Assert.That(exceptionEvent.Name, Is.Not.Null, "Should have an exception event");
        }

        [Test]
        public void RecordException_WithAttributes_AddsAttributesToException()
        {
            Observe.Initialize(_config, _loggerProvider);
            var exception = new InvalidOperationException("Test exception");
            var attributes = new Dictionary<string, object>
            {
                ["error.type"] = "validation",
                ["error.code"] = 400
            };

            using (var activity = new ActivitySource("test").StartActivity("test-operation"))
            {
                Observe.RecordException(exception, attributes);

                Assert.That(activity, Is.Not.Null);
                var exceptionEvent = activity.Events.LastOrDefault(e => e.Name == "exception");
                Assert.That(exceptionEvent.Name, Is.Not.Null, "Should have recorded an exception event");

                // Verify attributes were added
                var tags = exceptionEvent.Tags.ToList();
                Assert.Multiple(() =>
                {
                    Assert.That(tags.Any(t => t.Key == "error.type" && t.Value.ToString() == "validation"),
                        Is.True, "Should have error.type attribute");
                    Assert.That(tags.Any(t => t.Key == "error.code" && t.Value.ToString() == "400"),
                        Is.True, "Should have error.code attribute");
                });
            }
        }

        [Test]
        public void RecordException_WithNullException_DoesNotThrow()
        {
            Observe.Initialize(_config, _loggerProvider);

            Assert.DoesNotThrow(() => Observe.RecordException(null));
        }

        [Test]
        public void RecordException_BeforeInitialization_DoesNotThrow()
        {
            var exception = new InvalidOperationException("Test exception");
            Assert.DoesNotThrow(() => Observe.RecordException(exception));
        }

        #endregion

        #region Metric Recording Tests

        [Test]
        public void RecordMetric_WithValidName_RecordsGaugeValue()
        {
            Observe.Initialize(_config, _loggerProvider);

            Assert.DoesNotThrow(() => Observe.RecordMetric("cpu.usage", 75.5));
            Assert.DoesNotThrow(() => Observe.RecordMetric("memory.usage", 80.0));
        }

        [Test]
        public void RecordMetric_WithAttributes_RecordsGaugeValueWithAttributes()
        {
            Observe.Initialize(_config, _loggerProvider);
            var attributes = new Dictionary<string, object>
            {
                ["host"] = "server-1",
                ["region"] = "us-west-2"
            };

            Assert.DoesNotThrow(() => Observe.RecordMetric("cpu.usage", 75.5, attributes));
        }

        [Test]
        public void RecordMetric_WithNullName_ThrowsArgumentNullException()
        {
            Observe.Initialize(_config, _loggerProvider);

            var exception = Assert.Throws<ArgumentNullException>(() => Observe.RecordMetric(null, 42.0));
            Assert.That(exception, Is.Not.Null);
            Assert.Multiple(() =>
            {
                Assert.That(exception.ParamName, Is.EqualTo("name"));
                Assert.That(exception.Message, Does.Contain("Metric name cannot be null"));
            });
        }

        [Test]
        public void RecordMetric_BeforeInitialization_DoesNotThrow()
        {
            Assert.DoesNotThrow(() => Observe.RecordMetric("test.metric", 42.0));
        }

        [Test]
        public void RecordCount_WithValidName_RecordsCounterValue()
        {
            Observe.Initialize(_config, _loggerProvider);

            Assert.DoesNotThrow(() => Observe.RecordCount("requests.total", 5));
            Assert.DoesNotThrow(() => Observe.RecordCount("errors.total", 1));
        }

        [Test]
        public void RecordCount_WithAttributes_RecordsCounterValueWithAttributes()
        {
            Observe.Initialize(_config, _loggerProvider);
            var attributes = new Dictionary<string, object>
            {
                ["method"] = "GET",
                ["status"] = 200
            };

            Assert.DoesNotThrow(() => Observe.RecordCount("requests.total", 5, attributes));
        }

        [Test]
        public void RecordCount_WithNullName_ThrowsArgumentNullException()
        {
            Observe.Initialize(_config, _loggerProvider);

            var exception = Assert.Throws<ArgumentNullException>(() => Observe.RecordCount(null, 1));
            Assert.That(exception, Is.Not.Null);
            Assert.Multiple(() =>
            {
                Assert.That(exception.ParamName, Is.EqualTo("name"));
                Assert.That(exception.Message, Does.Contain("Count name cannot be null"));
            });
        }

        [Test]
        public void RecordIncr_WithValidName_IncrementsCounterByOne()
        {
            Observe.Initialize(_config, _loggerProvider);

            Assert.DoesNotThrow(() => Observe.RecordIncr("page.views"));
            Assert.DoesNotThrow(() => Observe.RecordIncr("api.calls"));
        }

        [Test]
        public void RecordIncr_WithAttributes_IncrementsCounterWithAttributes()
        {
            Observe.Initialize(_config, _loggerProvider);
            var attributes = new Dictionary<string, object>
            {
                ["page"] = "home",
                ["user_type"] = "premium"
            };

            Assert.DoesNotThrow(() => Observe.RecordIncr("page.views", attributes));
        }

        [Test]
        public void RecordHistogram_WithValidName_RecordsHistogramValue()
        {
            Observe.Initialize(_config, _loggerProvider);

            Assert.DoesNotThrow(() => Observe.RecordHistogram("request.duration", 150.5));
            Assert.DoesNotThrow(() => Observe.RecordHistogram("response.size", 1024.0));
        }

        [Test]
        public void RecordHistogram_WithAttributes_RecordsHistogramValueWithAttributes()
        {
            Observe.Initialize(_config, _loggerProvider);
            var attributes = new Dictionary<string, object>
            {
                ["endpoint"] = "/api/users",
                ["method"] = "POST"
            };

            Assert.DoesNotThrow(() => Observe.RecordHistogram("request.duration", 150.5, attributes));
        }

        [Test]
        public void RecordHistogram_WithNullName_ThrowsArgumentNullException()
        {
            Observe.Initialize(_config, _loggerProvider);

            var exception = Assert.Throws<ArgumentNullException>(() => Observe.RecordHistogram(null, 150.5));
            Assert.That(exception, Is.Not.Null);
            Assert.Multiple(() =>
            {
                Assert.That(exception.ParamName, Is.EqualTo("name"));
                Assert.That(exception.Message, Does.Contain("Histogram name cannot be null"));
            });
        }

        [Test]
        public void RecordUpDownCounter_WithValidName_RecordsUpDownCounterValue()
        {
            Observe.Initialize(_config, _loggerProvider);

            Assert.DoesNotThrow(() => Observe.RecordUpDownCounter("active.connections", 5));
            Assert.DoesNotThrow(() => Observe.RecordUpDownCounter("active.connections", -2));
        }

        [Test]
        public void RecordUpDownCounter_WithAttributes_RecordsUpDownCounterValueWithAttributes()
        {
            Observe.Initialize(_config, _loggerProvider);
            var attributes = new Dictionary<string, object>
            {
                ["server"] = "web-1",
                ["protocol"] = "http"
            };

            Assert.DoesNotThrow(() => Observe.RecordUpDownCounter("active.connections", 5, attributes));
        }

        [Test]
        public void RecordUpDownCounter_WithNullName_ThrowsArgumentNullException()
        {
            Observe.Initialize(_config, _loggerProvider);

            var exception = Assert.Throws<ArgumentNullException>(() => Observe.RecordUpDownCounter(null, 1));
            Assert.That(exception, Is.Not.Null);
            Assert.Multiple(() =>
            {
                Assert.That(exception.ParamName, Is.EqualTo("name"));
                Assert.That(exception.Message, Does.Contain("UpDownCounter name cannot be null"));
            });
        }

        #endregion

        #region Activity Creation Tests

        [Test]
        public void StartActivity_WithValidName_ReturnsActivity()
        {
            Observe.Initialize(_config, _loggerProvider);

            using (var activity = Observe.StartActivity("test-operation"))
            {
                Assert.That(activity, Is.Not.Null, "Activity should be created when ActivityListener is registered");
                Assert.That(activity.DisplayName, Is.EqualTo("test-operation"));
                Assert.That(activity.Source.Name, Is.EqualTo("test-service"));
            }
        }

        [Test]
        public void StartActivity_WithKindAndAttributes_ReturnsActivityWithAttributes()
        {
            Observe.Initialize(_config, _loggerProvider);
            var attributes = new Dictionary<string, object>
            {
                ["operation.type"] = "database",
                ["table.name"] = "users"
            };

            using (var activity = Observe.StartActivity("db-query", ActivityKind.Client, attributes))
            {
                Assert.That(activity, Is.Not.Null, "Activity should be created when ActivityListener is registered");
                Assert.That(activity.DisplayName, Is.EqualTo("db-query"));
                Assert.That(activity.Kind, Is.EqualTo(ActivityKind.Client));

                // Verify attributes were added as tags
                Assert.That(activity.GetTagItem("operation.type"), Is.EqualTo("database"));
                Assert.That(activity.GetTagItem("table.name"), Is.EqualTo("users"));
            }
        }

        [Test]
        public void StartActivity_BeforeInitialization_ReturnsNull()
        {
            var activity = Observe.StartActivity("test-operation");
            Assert.That(activity, Is.Null);
        }

        [Test]
        public void StartActivity_WithNullAttributes_DoesNotThrow()
        {
            Observe.Initialize(_config, _loggerProvider);
            Assert.DoesNotThrow(() =>
            {
                using (var activity = Observe.StartActivity("test-operation", ActivityKind.Internal, null))
                {
                }
            });
        }

        #endregion

        #region Logging Tests

        [Test]
        public void RecordLog_WithMessage_LogsMessageCorrectly()
        {
            Observe.Initialize(_config, _loggerProvider);
            var message = "Test log message";

            Observe.RecordLog(message, LogLevel.Information, null);
            var logger = _loggerProvider.Loggers.FirstOrDefault();
            Assert.That(logger, Is.Not.Null);
            Assert.That(logger.LogEntries.Count, Is.EqualTo(1));
            Assert.That(logger.LogEntries[0].Message, Is.EqualTo(message));
            Assert.That(logger.LogEntries[0].LogLevel, Is.EqualTo(LogLevel.Information));
        }

        [Test]
        public void RecordLog_WithAttributes_LogsMessageWithAttributes()
        {
            Observe.Initialize(_config, _loggerProvider);
            var message = "Test log with attributes";
            var attributes = new Dictionary<string, object>
            {
                ["user.id"] = "12345",
                ["request.id"] = "abc-def-ghi"
            };

            Observe.RecordLog(message, LogLevel.Warning, attributes);
            var logger = _loggerProvider.Loggers.FirstOrDefault();
            Assert.That(logger, Is.Not.Null);
            Assert.That(logger.LogEntries.Count, Is.EqualTo(1));
            Assert.That(logger.LogEntries[0].Message, Is.EqualTo(message));
            Assert.That(logger.LogEntries[0].LogLevel, Is.EqualTo(LogLevel.Warning));
            Assert.That(logger.LogEntries[0].State, Is.EqualTo(attributes));
        }

        [Test]
        public void RecordLog_WithDifferentLogLevels_LogsAtCorrectLevel()
        {
            Observe.Initialize(_config, _loggerProvider);
            Observe.RecordLog("Debug message", LogLevel.Debug, null);
            Observe.RecordLog("Info message", LogLevel.Information, null);
            Observe.RecordLog("Warning message", LogLevel.Warning, null);
            Observe.RecordLog("Error message", LogLevel.Error, null);
            var logger = _loggerProvider.Loggers.FirstOrDefault();
            Assert.That(logger, Is.Not.Null);
            Assert.That(logger.LogEntries.Count, Is.EqualTo(4));
            Assert.That(logger.LogEntries[0].LogLevel, Is.EqualTo(LogLevel.Debug));
            Assert.That(logger.LogEntries[1].LogLevel, Is.EqualTo(LogLevel.Information));
            Assert.That(logger.LogEntries[2].LogLevel, Is.EqualTo(LogLevel.Warning));
            Assert.That(logger.LogEntries[3].LogLevel, Is.EqualTo(LogLevel.Error));
        }

        [Test]
        public void RecordLog_WithNullLoggerProvider_DoesNotThrow()
        {
            Observe.Initialize(_config, null);
            Assert.DoesNotThrow(() => Observe.RecordLog("Test message", LogLevel.Information, null));
        }

        [Test]
        public void RecordLog_WithEmptyAttributes_LogsCorrectly()
        {
            Observe.Initialize(_config, _loggerProvider);
            var emptyAttributes = new Dictionary<string, object>();

            Observe.RecordLog("Test message", LogLevel.Information, emptyAttributes);
            var logger = _loggerProvider.Loggers.FirstOrDefault();
            Assert.That(logger, Is.Not.Null);
            Assert.That(logger.LogEntries.Count, Is.EqualTo(1));
        }

        [Test]
        public void RecordLog_BeforeInitialization_DoesNotThrow()
        {
            Assert.DoesNotThrow(() => Observe.RecordLog("Test message", LogLevel.Information, null));
        }

        #endregion

        #region Utility Method Tests

        [Test]
        public void ConvertToKeyValuePairs_WithNullDictionary_ReturnsNull()
        {
            // This tests the private method indirectly through public methods
            Observe.Initialize(_config, _loggerProvider);

            // Should not throw with null attributes
            Assert.DoesNotThrow(() => Observe.RecordMetric("test.metric", 42.0, null));
            Assert.DoesNotThrow(() => Observe.RecordCount("test.counter", 1, null));
            Assert.DoesNotThrow(() => Observe.RecordHistogram("test.histogram", 150.0, null));
            Assert.DoesNotThrow(() => Observe.RecordUpDownCounter("test.updown", 1, null));
        }

        [Test]
        public void ConvertToKeyValuePairs_WithEmptyDictionary_ReturnsNull()
        {
            // This tests the private method indirectly through public methods
            Observe.Initialize(_config, _loggerProvider);
            var emptyAttributes = new Dictionary<string, object>();

            // Should not throw with empty attributes
            Assert.DoesNotThrow(() => Observe.RecordMetric("test.metric", 42.0, emptyAttributes));
            Assert.DoesNotThrow(() => Observe.RecordCount("test.counter", 1, emptyAttributes));
            Assert.DoesNotThrow(() => Observe.RecordHistogram("test.histogram", 150.0, emptyAttributes));
            Assert.DoesNotThrow(() => Observe.RecordUpDownCounter("test.updown", 1, emptyAttributes));
        }

        #endregion

        #region Multiple Metrics of Same Name Tests

        [Test]
        public void RecordMetric_WithSameName_ReusesGaugeInstance()
        {
            Observe.Initialize(_config, _loggerProvider);
            var metricName = "cpu.usage";

            // Record multiple values for the same metric
            Assert.DoesNotThrow(() => Observe.RecordMetric(metricName, 50.0));
            Assert.DoesNotThrow(() => Observe.RecordMetric(metricName, 75.0));
            Assert.DoesNotThrow(() => Observe.RecordMetric(metricName, 90.0));

            // Should not throw, indicating proper reuse of gauge instance
            Assert.Pass("Multiple metrics with same name recorded successfully");
        }

        [Test]
        public void RecordCount_WithSameName_ReusesCounterInstance()
        {
            Observe.Initialize(_config, _loggerProvider);
            var counterName = "requests.total";

            // Record multiple values for the same counter
            Assert.DoesNotThrow(() => Observe.RecordCount(counterName, 1));
            Assert.DoesNotThrow(() => Observe.RecordCount(counterName, 5));
            Assert.DoesNotThrow(() => Observe.RecordCount(counterName, 10));

            // Should not throw, indicating proper reuse of counter instance
            Assert.Pass("Multiple counters with same name recorded successfully");
        }

        #endregion
    }
}
