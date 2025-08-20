using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Reflection;
using Microsoft.Extensions.Logging;
using NUnit.Framework;
using OpenTelemetry.Metrics;
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
                    Message = formatter.Invoke(state, exception)
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
        private MeterProvider _meterProvider;
        private ActivityListener _activityListener;
        private List<Activity> _exportedActivities;
        private List<Metric> _exportedMetrics;

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
            _exportedMetrics = new List<Metric>();

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

            // Set up MeterProvider with in-memory exporter
            _meterProvider = OpenTelemetry.Sdk.CreateMeterProviderBuilder()
                .AddMeter("test-service")
                .SetResourceBuilder(ResourceBuilder.CreateDefault()
                    .AddService("test-service", "test", "1.0.0"))
                .AddInMemoryExporter(_exportedMetrics)
                .Build();
        }

        [TearDown]
        public void TearDown()
        {
            _loggerProvider?.Dispose();
            _tracerProvider?.Dispose();
            _meterProvider?.Dispose();
            _activityListener?.Dispose();
            _exportedActivities?.Clear();
            _exportedMetrics?.Clear();
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
            Assert.DoesNotThrow(() => Observe.Initialize(_config, _loggerProvider));
        }

        [Test]
        public void Initialize_WithNullLoggerProvider_WorksCorrectly()
        {
            Assert.DoesNotThrow(() => Observe.Initialize(_config, null));
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

            // Reset meter provider to use default service name
            _meterProvider?.Dispose();
            _meterProvider = OpenTelemetry.Sdk.CreateMeterProviderBuilder()
                .AddMeter("launchdarkly-plugin-default-metrics") // Default meter name
                .SetResourceBuilder(ResourceBuilder.CreateDefault()
                    .AddService("launchdarkly-plugin-default-metrics", serviceVersion: "1.0.0"))
                .AddInMemoryExporter(_exportedMetrics)
                .Build();

            // Initialize with null service name
            Observe.Initialize(configWithNullServiceName, _loggerProvider);

            // Record a metric to verify it works with defaults
            Observe.RecordIncr("test-counter");

            // Force export
            _meterProvider.ForceFlush();

            // Verify metric was exported using the default meter
            Assert.That(_exportedMetrics, Is.Not.Empty, "Should have exported metrics with default meter");
            var metric = _exportedMetrics.FirstOrDefault(m => m.Name == "test-counter");
            Assert.That(metric, Is.Not.Null, "Should have exported test-counter metric using defaults");

            // Verify an activity can be created with a default activity source
            using (Observe.StartActivity("test-operation"))
            {
                // Activity might be null if no listener is registered for the default source,
                // but the important thing is it doesn't throw
            }

            // Verify logging works with default logger name
            Observe.RecordLog("Test message with defaults", LogLevel.Information, null);
            var logger = _loggerProvider.Loggers.FirstOrDefault();
            Assert.That(logger, Is.Not.Null, "Should have created a logger");
            Assert.That(logger.CategoryName, Is.EqualTo("launchdarkly-plugin-default-logger"),
                "Should use default logger name when service name is null");
        }

        [Test]
        public void Initialize_WithEmptyServiceName_UsesEmptyString()
        {
            var configWithEmptyServiceName = new ObservabilityConfig(
                otlpEndpoint: "https://test-endpoint.com",
                backendUrl: "https://test-backend.com",
                serviceName: "",
                environment: "test",
                serviceVersion: "1.0.0",
                sdkKey: "test-key"
            );

            // Note: We can't test meter creation with empty string because OpenTelemetry doesn't allow it
            // But we can test that Observe itself handles empty string without crashing

            // Initialize with empty service name
            Observe.Initialize(configWithEmptyServiceName, _loggerProvider);

            // Try to record a metric - the Meter constructor will use empty string
            // This should work because .NET's Meter class accepts empty string
            Assert.DoesNotThrow(() => Observe.RecordMetric("test.gauge", 42.0),
                "Should be able to record metric even with empty service name");

            // Verify logging uses empty string, not default  
            Observe.RecordLog("Test message with empty service name", LogLevel.Warning, null);
            var logger = _loggerProvider.Loggers.FirstOrDefault();
            Assert.That(logger, Is.Not.Null, "Should have created a logger");
            // Empty string is used as-is (not replaced with default) because the code uses ?? operator
            Assert.That(logger.CategoryName, Is.EqualTo(""),
                "Should use empty string as logger name when service name is empty (not null)");

            // Verify activity source also uses empty string
            // Note: Activity with empty source name might not be sampled by our listener
            // but it should not throw
            Assert.DoesNotThrow(() =>
            {
                using (Observe.StartActivity("test-operation"))
                {
                    // Activity might be null but shouldn't throw
                }
            }, "Should be able to start activity even with empty service name");
        }

        [Test]
        public void Initialize_WithWhitespaceServiceName_UsesWhitespaceString()
        {
            var configWithWhitespaceServiceName = new ObservabilityConfig(
                otlpEndpoint: "https://test-endpoint.com",
                backendUrl: "https://test-backend.com",
                serviceName: "   ",
                environment: "test",
                serviceVersion: "1.0.0",
                sdkKey: "test-key"
            );

            // Initialize with whitespace service name
            Observe.Initialize(configWithWhitespaceServiceName, _loggerProvider);

            // Try to record a metric - the Meter constructor will use whitespace string
            // This should work because .NET's Meter class accepts whitespace
            Assert.DoesNotThrow(() => Observe.RecordMetric("test.gauge", 42.0),
                "Should be able to record metric even with whitespace service name");

            // Verify logging uses whitespace string, not default  
            Observe.RecordLog("Test message with whitespace service name", LogLevel.Warning, null);
            var logger = _loggerProvider.Loggers.FirstOrDefault();
            Assert.That(logger, Is.Not.Null, "Should have created a logger");
            // Whitespace string is used as-is (not replaced with default) because the code uses ?? operator
            Assert.That(logger.CategoryName, Is.EqualTo("   "),
                "Should use whitespace string as logger name when service name is whitespace (not null)");
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
                Assert.That(events, Has.Count.EqualTo(initialEventCount + 1));
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
            Assert.That(_exportedActivities, Is.Not.Empty, "Should have created and exported an activity");
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
                    Assert.That(tags.Any(t => t.Key == "error.type" && t.Value?.ToString() == "validation"),
                        Is.True, "Should have error.type attribute");
                    Assert.That(tags.Any(t => t.Key == "error.code" && t.Value?.ToString() == "400"),
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

            // Record metrics
            Observe.RecordMetric("cpu.usage", 75.5);
            Observe.RecordMetric("memory.usage", 80.0);

            // Force export
            _meterProvider.ForceFlush();

            // Verify metrics were exported
            Assert.That(_exportedMetrics, Is.Not.Empty, "Should have exported metrics");

            // Find the cpu.usage metric
            var cpuMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "cpu.usage");
            Assert.That(cpuMetric, Is.Not.Null, "Should have exported cpu.usage metric");

            // Find the memory.usage metric
            var memoryMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "memory.usage");
            Assert.Multiple(() =>
            {
                Assert.That(memoryMetric, Is.Not.Null, "Should have exported memory.usage metric");

                // Verify the metric type is gauge
                Assert.That(cpuMetric.MetricType, Is.EqualTo(MetricType.DoubleGauge), "cpu.usage should be a gauge");
            });
            Assert.That(memoryMetric, Is.Not.Null, "Should have exported memory.usage metric");
            Assert.That(memoryMetric.MetricType, Is.EqualTo(MetricType.DoubleGauge), "memory.usage should be a gauge");
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

            // Record metric with attributes
            Observe.RecordMetric("cpu.usage", 75.5, attributes);

            // Force export
            _meterProvider.ForceFlush();

            // Verify metric was exported
            Assert.That(_exportedMetrics, Is.Not.Empty, "Should have exported metrics");

            var cpuMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "cpu.usage");
            Assert.That(cpuMetric, Is.Not.Null, "Should have exported cpu.usage metric");

            // Verify attributes were included
            var metricPoints = cpuMetric.GetMetricPoints();
            var metricPointsEnumerator = metricPoints.GetEnumerator();
            Assert.That(metricPointsEnumerator.MoveNext(), Is.True, "Should have metric points");

            var metricPoint = metricPointsEnumerator.Current;
            var tags = metricPoint.Tags;

            // Check if attributes are present
            object hostValue = null;
            object regionValue = null;
            foreach (var tag in tags)
            {
                switch (tag.Key)
                {
                    case "host":
                        hostValue = tag.Value;
                        break;
                    case "region":
                        regionValue = tag.Value;
                        break;
                }
            }

            Assert.Multiple(() =>
            {
                Assert.That(hostValue, Is.EqualTo("server-1"), "Should have host attribute");
                Assert.That(regionValue, Is.EqualTo("us-west-2"), "Should have region attribute");
            });
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

            // Record counters
            Observe.RecordCount("requests.total", 5);
            Observe.RecordCount("errors.total", 1);

            // Force export
            _meterProvider.ForceFlush();

            // Verify metrics were exported
            Assert.That(_exportedMetrics, Is.Not.Empty, "Should have exported metrics");

            // Find the counters
            var requestsMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "requests.total");
            var errorsMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "errors.total");

            Assert.Multiple(() =>
            {
                Assert.That(requestsMetric, Is.Not.Null, "Should have exported requests.total metric");
                Assert.That(errorsMetric, Is.Not.Null, "Should have exported errors.total metric");
            });

            Assert.That(requestsMetric, Is.Not.Null, "Should have exported requests.total metric");
            Assert.That(errorsMetric, Is.Not.Null, "Should have exported errors.total metric");
            Assert.Multiple(() =>
            {
                // Verify the metric type is counter
                Assert.That(requestsMetric.MetricType,
                    Is.EqualTo(MetricType.LongSumNonMonotonic).Or.EqualTo(MetricType.LongSum),
                    "requests.total should be a counter");
                Assert.That(errorsMetric.MetricType,
                    Is.EqualTo(MetricType.LongSumNonMonotonic).Or.EqualTo(MetricType.LongSum),
                    "errors.total should be a counter");
            });

            // Verify counter values
            var requestsPoints = requestsMetric.GetMetricPoints();
            var errorsPoints = errorsMetric.GetMetricPoints();

            var requestsEnumerator = requestsPoints.GetEnumerator();
            var errorsEnumerator = errorsPoints.GetEnumerator();

            Assert.Multiple(() =>
            {
                Assert.That(requestsEnumerator.MoveNext(), Is.True, "Should have request metric points");
                Assert.That(errorsEnumerator.MoveNext(), Is.True, "Should have error metric points");
            });

            var requestPoint = requestsEnumerator.Current;
            var errorPoint = errorsEnumerator.Current;

            Assert.Multiple(() =>
            {
                Assert.That(requestPoint.GetSumLong(), Is.EqualTo(5), "Requests counter should have value 5");
                Assert.That(errorPoint.GetSumLong(), Is.EqualTo(1), "Errors counter should have value 1");
            });
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

            // Record counter with attributes
            Observe.RecordCount("requests.total", 5, attributes);

            // Force export
            _meterProvider.ForceFlush();

            // Verify metric was exported
            Assert.That(_exportedMetrics, Is.Not.Empty, "Should have exported metrics");

            var requestsMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "requests.total");
            Assert.That(requestsMetric, Is.Not.Null, "Should have exported requests.total metric");

            // Verify attributes were included
            var metricPoints = requestsMetric.GetMetricPoints();
            var metricPointsEnumerator = metricPoints.GetEnumerator();
            Assert.That(metricPointsEnumerator.MoveNext(), Is.True, "Should have metric points");

            var metricPoint = metricPointsEnumerator.Current;
            var tags = metricPoint.Tags;

            // Check if attributes are present
            object methodValue = null;
            object statusValue = null;
            foreach (var tag in tags)
            {
                switch (tag.Key)
                {
                    case "method":
                        methodValue = tag.Value;
                        break;
                    case "status":
                        statusValue = tag.Value;
                        break;
                }
            }

            Assert.Multiple(() =>
            {
                Assert.That(methodValue, Is.EqualTo("GET"), "Should have method attribute");
                Assert.That(statusValue?.ToString(), Is.EqualTo("200"), "Should have status attribute");
            });
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

            // Record increments
            Observe.RecordIncr("page.views");
            Observe.RecordIncr("api.calls");
            Observe.RecordIncr("api.calls"); // Call twice to verify increment

            // Force export
            _meterProvider.ForceFlush();

            // Verify metrics were exported
            Assert.That(_exportedMetrics.Count, Is.GreaterThan(0), "Should have exported metrics");

            // Find the counters
            var pageViewsMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "page.views");
            var apiCallsMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "api.calls");

            Assert.Multiple(() =>
            {
                Assert.That(pageViewsMetric, Is.Not.Null, "Should have exported page.views metric");
                Assert.That(apiCallsMetric, Is.Not.Null, "Should have exported api.calls metric");
            });

            Assert.That(pageViewsMetric, Is.Not.Null);
            Assert.That(apiCallsMetric, Is.Not.Null);
            // Verify the values (RecordIncr should increment by 1 each time)
            var pageViewsPoints = pageViewsMetric.GetMetricPoints();
            var apiCallsPoints = apiCallsMetric.GetMetricPoints();

            var pageViewsEnumerator = pageViewsPoints.GetEnumerator();
            var apiCallsEnumerator = apiCallsPoints.GetEnumerator();

            Assert.Multiple(() =>
            {
                Assert.That(pageViewsEnumerator.MoveNext(), Is.True, "Should have page views metric points");
                Assert.That(apiCallsEnumerator.MoveNext(), Is.True, "Should have api calls metric points");
            });

            var pageViewsPoint = pageViewsEnumerator.Current;
            var apiCallsPoint = apiCallsEnumerator.Current;

            Assert.Multiple(() =>
            {
                Assert.That(pageViewsPoint.GetSumLong(), Is.EqualTo(1), "Page views should be incremented by 1");
                Assert.That(apiCallsPoint.GetSumLong(), Is.EqualTo(2),
                    "API calls should be incremented by 2 (called twice)");
            });
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

            // Record increment with attributes
            Observe.RecordIncr("page.views", attributes);

            // Force export
            _meterProvider.ForceFlush();

            // Verify metric was exported
            Assert.That(_exportedMetrics, Is.Not.Empty, "Should have exported metrics");

            var pageViewsMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "page.views");
            Assert.That(pageViewsMetric, Is.Not.Null, "Should have exported page.views metric");

            // Verify attributes were included
            var metricPoints = pageViewsMetric.GetMetricPoints();
            var metricPointsEnumerator = metricPoints.GetEnumerator();
            Assert.That(metricPointsEnumerator.MoveNext(), Is.True, "Should have metric points");

            var metricPoint = metricPointsEnumerator.Current;
            var tags = metricPoint.Tags;

            // Check if attributes are present
            object pageValue = null;
            object userTypeValue = null;
            foreach (var tag in tags)
            {
                switch (tag.Key)
                {
                    case "page":
                        pageValue = tag.Value;
                        break;
                    case "user_type":
                        userTypeValue = tag.Value;
                        break;
                }
            }

            Assert.Multiple(() =>
            {
                Assert.That(pageValue, Is.EqualTo("home"), "Should have page attribute");
                Assert.That(userTypeValue, Is.EqualTo("premium"), "Should have user_type attribute");
                Assert.That(metricPoint.GetSumLong(), Is.EqualTo(1), "Should have incremented by 1");
            });
        }

        [Test]
        public void RecordHistogram_WithValidName_RecordsHistogramValue()
        {
            Observe.Initialize(_config, _loggerProvider);

            // Record histogram values
            Observe.RecordHistogram("request.duration", 150.5);
            Observe.RecordHistogram("request.duration", 200.0);
            Observe.RecordHistogram("request.duration", 100.0);
            Observe.RecordHistogram("response.size", 1024.0);

            // Force export
            _meterProvider.ForceFlush();

            // Verify metrics were exported
            Assert.That(_exportedMetrics, Is.Not.Empty, "Should have exported metrics");

            // Find the histograms
            var durationMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "request.duration");
            var sizeMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "response.size");

            Assert.That(durationMetric, Is.Not.Null, "Should have exported request.duration metric");
            Assert.That(sizeMetric, Is.Not.Null, "Should have exported response.size metric");

            // Verify the metric type is histogram
            Assert.That(durationMetric.MetricType, Is.EqualTo(MetricType.Histogram),
                "request.duration should be a histogram");
            Assert.That(sizeMetric.MetricType, Is.EqualTo(MetricType.Histogram),
                "response.size should be a histogram");

            // Verify histogram data
            var durationPoints = durationMetric.GetMetricPoints();
            var durationEnumerator = durationPoints.GetEnumerator();
            Assert.That(durationEnumerator.MoveNext(), Is.True, "Should have duration metric points");

            var durationPoint = durationEnumerator.Current;
            var histogramData = durationPoint.GetHistogramSum();
            var histogramCount = durationPoint.GetHistogramCount();

            Assert.Multiple(() =>
            {
                // We recorded 3 values: 150.5, 200.0, 100.0
                Assert.That(histogramCount, Is.EqualTo(3), "Should have recorded 3 histogram values");
                Assert.That(histogramData, Is.EqualTo(450.5), "Sum should be 450.5 (150.5 + 200 + 100)");
            });
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

            // Record histogram with attributes
            Observe.RecordHistogram("request.duration", 150.5, attributes);
            Observe.RecordHistogram("request.duration", 75.0, attributes);

            // Force export
            _meterProvider.ForceFlush();

            // Verify metric was exported
            Assert.That(_exportedMetrics.Count, Is.GreaterThan(0), "Should have exported metrics");

            var durationMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "request.duration");
            Assert.That(durationMetric, Is.Not.Null, "Should have exported request.duration metric");

            // Verify attributes were included
            var metricPoints = durationMetric.GetMetricPoints();
            var metricPointsEnumerator = metricPoints.GetEnumerator();
            Assert.That(metricPointsEnumerator.MoveNext(), Is.True, "Should have metric points");

            var metricPoint = metricPointsEnumerator.Current;
            var tags = metricPoint.Tags;

            // Check if attributes are present
            object endpointValue = null;
            object methodValue = null;
            foreach (var tag in tags)
            {
                switch (tag.Key)
                {
                    case "endpoint":
                        endpointValue = tag.Value;
                        break;
                    case "method":
                        methodValue = tag.Value;
                        break;
                }
            }

            Assert.Multiple(() =>
            {
                Assert.That(endpointValue, Is.EqualTo("/api/users"), "Should have endpoint attribute");
                Assert.That(methodValue, Is.EqualTo("POST"), "Should have method attribute");
                Assert.That(metricPoint.GetHistogramCount(), Is.EqualTo(2), "Should have recorded 2 values");
                Assert.That(metricPoint.GetHistogramSum(), Is.EqualTo(225.5), "Sum should be 225.5 (150.5 + 75)");
            });
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

            // Record up/down counter values
            Observe.RecordUpDownCounter("active.connections", 5);
            Observe.RecordUpDownCounter("active.connections", -2);
            Observe.RecordUpDownCounter("active.connections", 3);

            // Force export
            _meterProvider.ForceFlush();

            // Verify metrics were exported
            Assert.That(_exportedMetrics, Is.Not.Empty, "Should have exported metrics");

            // Find the up/down counter
            var connectionsMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "active.connections");
            Assert.That(connectionsMetric, Is.Not.Null, "Should have exported active.connections metric");

            // Verify the metric type is up/down counter
            Assert.That(connectionsMetric.MetricType,
                Is.EqualTo(MetricType.LongSumNonMonotonic).Or.EqualTo(MetricType.LongSum),
                "active.connections should be an up/down counter");

            // Verify the value (5 - 2 + 3 = 6)
            var metricPoints = connectionsMetric.GetMetricPoints();
            var metricPointsEnumerator = metricPoints.GetEnumerator();
            Assert.That(metricPointsEnumerator.MoveNext(), Is.True, "Should have metric points");

            var metricPoint = metricPointsEnumerator.Current;
            Assert.That(metricPoint.GetSumLong(), Is.EqualTo(6),
                "Up/down counter should have net value of 6 (5 - 2 + 3)");
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

            // Record up/down counter with attributes
            Observe.RecordUpDownCounter("active.connections", 5, attributes);
            Observe.RecordUpDownCounter("active.connections", -1, attributes);

            // Force export
            _meterProvider.ForceFlush();

            // Verify metric was exported
            Assert.That(_exportedMetrics, Is.Not.Empty, "Should have exported metrics");

            var connectionsMetric = _exportedMetrics.FirstOrDefault(m => m.Name == "active.connections");
            Assert.That(connectionsMetric, Is.Not.Null, "Should have exported active.connections metric");

            // Verify attributes were included
            var metricPoints = connectionsMetric.GetMetricPoints();
            var metricPointsEnumerator = metricPoints.GetEnumerator();
            Assert.That(metricPointsEnumerator.MoveNext(), Is.True, "Should have metric points");

            var metricPoint = metricPointsEnumerator.Current;
            var tags = metricPoint.Tags;

            // Check if attributes are present
            object serverValue = null;
            object protocolValue = null;
            foreach (var tag in tags)
            {
                switch (tag.Key)
                {
                    case "server":
                        serverValue = tag.Value;
                        break;
                    case "protocol":
                        protocolValue = tag.Value;
                        break;
                }
            }

            Assert.Multiple(() =>
            {
                Assert.That(serverValue, Is.EqualTo("web-1"), "Should have server attribute");
                Assert.That(protocolValue, Is.EqualTo("http"), "Should have protocol attribute");
                Assert.That(metricPoint.GetSumLong(), Is.EqualTo(4), "Should have net value of 4 (5 - 1)");
            });
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
                Assert.Multiple(() =>
                {
                    Assert.That(activity.DisplayName, Is.EqualTo("test-operation"));
                    Assert.That(activity.Source.Name, Is.EqualTo("test-service"));
                });
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
                Assert.Multiple(() =>
                {
                    Assert.That(activity.DisplayName, Is.EqualTo("db-query"));
                    Assert.That(activity.Kind, Is.EqualTo(ActivityKind.Client));

                    // Verify attributes were added as tags
                    Assert.That(activity.GetTagItem("operation.type"), Is.EqualTo("database"));
                    Assert.That(activity.GetTagItem("table.name"), Is.EqualTo("users"));
                });
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
                using (Observe.StartActivity("test-operation"))
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
            const string message = "Test log message";

            Observe.RecordLog(message, LogLevel.Information, null);
            var logger = _loggerProvider.Loggers.FirstOrDefault();
            Assert.That(logger, Is.Not.Null);
            Assert.That(logger.LogEntries, Has.Count.EqualTo(1));
            Assert.Multiple(() =>
            {
                Assert.That(logger.LogEntries[0].Message, Is.EqualTo(message));
                Assert.That(logger.LogEntries[0].LogLevel, Is.EqualTo(LogLevel.Information));
            });
        }

        [Test]
        public void RecordLog_WithAttributes_LogsMessageWithAttributes()
        {
            Observe.Initialize(_config, _loggerProvider);
            const string message = "Test log with attributes";
            var attributes = new Dictionary<string, object>
            {
                ["user.id"] = "12345",
                ["request.id"] = "abc-def-ghi"
            };

            Observe.RecordLog(message, LogLevel.Warning, attributes);
            var logger = _loggerProvider.Loggers.FirstOrDefault();
            Assert.That(logger, Is.Not.Null);
            Assert.That(logger.LogEntries, Has.Count.EqualTo(1));
            Assert.Multiple(() =>
            {
                Assert.That(logger.LogEntries[0].Message, Is.EqualTo(message));
                Assert.That(logger.LogEntries[0].LogLevel, Is.EqualTo(LogLevel.Warning));
                Assert.That(logger.LogEntries[0].State, Is.EqualTo(attributes));
            });
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
            Assert.That(logger.LogEntries, Has.Count.EqualTo(4));
            Assert.Multiple(() =>
            {
                Assert.That(logger.LogEntries[0].LogLevel, Is.EqualTo(LogLevel.Debug));
                Assert.That(logger.LogEntries[1].LogLevel, Is.EqualTo(LogLevel.Information));
                Assert.That(logger.LogEntries[2].LogLevel, Is.EqualTo(LogLevel.Warning));
                Assert.That(logger.LogEntries[3].LogLevel, Is.EqualTo(LogLevel.Error));
            });
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
            Assert.That(logger.LogEntries, Has.Count.EqualTo(1));
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
            Assert.DoesNotThrow(() => Observe.RecordMetric("test.metric", 42.0));
            Assert.DoesNotThrow(() => Observe.RecordCount("test.counter", 1));
            Assert.DoesNotThrow(() => Observe.RecordHistogram("test.histogram", 150.0));
            Assert.DoesNotThrow(() => Observe.RecordUpDownCounter("test.updown", 1));
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
            const string metricName = "cpu.usage";

            // Record multiple values for the same metric
            Observe.RecordMetric(metricName, 50.0);
            Observe.RecordMetric(metricName, 75.0);
            Observe.RecordMetric(metricName, 90.0);

            // Force export
            _meterProvider.ForceFlush();

            // Verify metrics were exported
            Assert.That(_exportedMetrics, Is.Not.Empty, "Should have exported metrics");

            // Should have only one metric with the given name (reused instance)
            var cpuMetrics = _exportedMetrics.Where(m => m.Name == metricName).ToList();
            Assert.That(cpuMetrics, Has.Count.EqualTo(1), "Should have only one cpu.usage metric instance");

            // The last recorded value should be 90.0 for a gauge
            var metricPoints = cpuMetrics.First().GetMetricPoints();
            var metricPointsEnumerator = metricPoints.GetEnumerator();
            Assert.That(metricPointsEnumerator.MoveNext(), Is.True, "Should have metric points");

            var metricPoint = metricPointsEnumerator.Current;
            Assert.That(metricPoint.GetGaugeLastValueDouble(), Is.EqualTo(90.0),
                "Last gauge value should be 90.0");
        }

        [Test]
        public void RecordCount_WithSameName_ReusesCounterInstance()
        {
            Observe.Initialize(_config, _loggerProvider);
            const string counterName = "requests.total";

            // Record multiple values for the same counter
            Observe.RecordCount(counterName, 1);
            Observe.RecordCount(counterName, 5);
            Observe.RecordCount(counterName, 10);

            // Force export
            _meterProvider.ForceFlush();

            // Verify metrics were exported
            Assert.That(_exportedMetrics, Is.Not.Empty, "Should have exported metrics");

            // Should have only one metric with the given name (reused instance)
            var requestMetrics = _exportedMetrics.Where(m => m.Name == counterName).ToList();
            Assert.That(requestMetrics, Has.Count.EqualTo(1), "Should have only one requests.total metric instance");

            // The cumulative value should be 16 (1 + 5 + 10)
            var metricPoints = requestMetrics.First().GetMetricPoints();
            var metricPointsEnumerator = metricPoints.GetEnumerator();
            Assert.That(metricPointsEnumerator.MoveNext(), Is.True, "Should have metric points");

            var point = metricPointsEnumerator.Current;
            Assert.That(point.GetSumLong(), Is.EqualTo(16),
                "Counter cumulative value should be 16 (1 + 5 + 10)");
        }

        #endregion
    }
}
