using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using LaunchDarkly.Logging;
using LaunchDarkly.Observability.Logging;
using LaunchDarkly.Observability.Sampling;
using Moq;
using NUnit.Framework;

namespace LaunchDarkly.Observability.Test
{
    /// <summary>
    /// Test log adapter that captures log messages for testing purposes
    /// </summary>
    public class TestLogAdapter : ILogAdapter
    {
        private readonly List<LogMessage> _messages = new List<LogMessage>();

        public class LogMessage
        {
            public LogLevel Level { get; set; }
            public string Name { get; set; }
            public string Message { get; set; }
        }

        public IReadOnlyList<LogMessage> Messages => _messages.AsReadOnly();

        private void Log(LogLevel level, object message, string loggerName)
        {
            _messages.Add(new LogMessage
            {
                Level = level,
                Name = loggerName,
                Message = message?.ToString()
            });
        }

        public IChannel NewChannel(string name)
        {
            return new TestChannel(this, name);
        }

        public void Clear()
        {
            _messages.Clear();
        }

        private class TestChannel : IChannel
        {
            private readonly TestLogAdapter _adapter;
            private readonly string _name;

            public TestChannel(TestLogAdapter adapter, string name)
            {
                _adapter = adapter;
                _name = name;
            }

            public bool IsEnabled(LogLevel level)
            {
                return true; // Always capture log messages for testing
            }

            public void Log(LogLevel level, object message)
            {
                _adapter.Log(level, message, _name);
            }

            public void Log(LogLevel level, string format, object param)
            {
                var message = string.Format(format, param);
                _adapter.Log(level, message, _name);
            }

            public void Log(LogLevel level, string format, object param1, object param2)
            {
                var message = string.Format(format, param1, param2);
                _adapter.Log(level, message, _name);
            }

            public void Log(LogLevel level, string format, params object[] allParams)
            {
                var message = string.Format(format, allParams);
                _adapter.Log(level, message, _name);
            }
        }
    }

    [TestFixture]
    public class SamplingConfigClientTests
    {
        private Mock<IHttpClient> _mockHttpClient;
        private SamplingConfigClient _client;
        private TestLogAdapter _logAdapter;
        private const string TestBackendUrl = "https://api.test.com/graphql";
        private const string TestOrganizationId = "test-org-123";

        #region Test Setup and Helpers

        [SetUp]
        public void SetUp()
        {
            _logAdapter = new TestLogAdapter();
            var logger = Logger.WithAdapter(_logAdapter, "TestLogger");
            DebugLogger.SetLogger(logger);

            _mockHttpClient = new Mock<IHttpClient>();
            _client = new SamplingConfigClient(_mockHttpClient.Object, TestBackendUrl);
        }

        [TearDown]
        public void TearDown()
        {
            _logAdapter?.Clear();
        }

        private static HttpResponseMessage CreateSuccessResponse(object responseData)
        {
            var json = JsonSerializer.Serialize(responseData, new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase
            });

            var response = new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(json, System.Text.Encoding.UTF8, "application/json")
            };

            return response;
        }

        private static HttpResponseMessage CreateErrorResponse(HttpStatusCode statusCode)
        {
            return new HttpResponseMessage(statusCode)
            {
                Content = new StringContent("Error", System.Text.Encoding.UTF8, "application/json")
            };
        }

        private static SamplingConfig CreateTestSamplingConfig()
        {
            return new SamplingConfig
            {
                Spans = new List<SamplingConfig.SpanSamplingConfig>
                {
                    new SamplingConfig.SpanSamplingConfig
                    {
                        Name = new SamplingConfig.MatchConfig { MatchValue = "test-span" },
                        SamplingRatio = 50
                    }
                },
                Logs = new List<SamplingConfig.LogSamplingConfig>
                {
                    new SamplingConfig.LogSamplingConfig
                    {
                        Message = new SamplingConfig.MatchConfig { MatchValue = "test-log" },
                        SamplingRatio = 25
                    }
                }
            };
        }

        #endregion

        #region Constructor Tests

        [Test]
        public void Constructor_WithHttpClient_ShouldWrapWithHttpClientWrapper()
        {
            // Arrange
            var httpClient = new HttpClient();

            // Act & Assert - Should not throw
            var client = new SamplingConfigClient(httpClient, TestBackendUrl);
            Assert.That(client, Is.Not.Null);
        }

        [Test]
        public void Constructor_WithIHttpClient_ShouldUseProvidedClient()
        {
            // Arrange
            var mockClient = new Mock<IHttpClient>();

            // Act & Assert - Should not throw
            var client = new SamplingConfigClient(mockClient.Object, TestBackendUrl);
            Assert.That(client, Is.Not.Null);
        }

        [Test]
        public void Constructor_WithNullIHttpClient_ShouldThrowArgumentNullException()
        {
            // Act & Assert
            Assert.Throws<ArgumentNullException>(() =>
                new SamplingConfigClient((IHttpClient)null, TestBackendUrl));
        }

        [Test]
        public void Constructor_WithNullHttpClient_ShouldThrowArgumentNullException()
        {
            // Act & Assert
            Assert.Throws<ArgumentNullException>(() =>
                new SamplingConfigClient((HttpClient)null, TestBackendUrl));
        }

        [Test]
        public void Constructor_WithNullBackendUrl_ShouldThrowArgumentNullException()
        {
            // Act & Assert
            Assert.Throws<ArgumentNullException>(() =>
                new SamplingConfigClient(_mockHttpClient.Object, null));
        }

        #endregion

        #region Successful Response Tests

        [Test]
        public async Task GetSamplingConfigAsync_WithValidResponse_ShouldReturnSamplingConfig()
        {
            // Arrange
            var expectedConfig = CreateTestSamplingConfig();
            var graphqlResponse = new SamplingConfigClient.GraphQlResponse
            {
                Data = new SamplingConfigClient.SamplingDataResponse
                {
                    Sampling = expectedConfig
                },
                Errors = new List<SamplingConfigClient.GraphQlError>()
            };

            var httpResponse = CreateSuccessResponse(graphqlResponse);

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ReturnsAsync(httpResponse);

            // Act
            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            // Assert
            Assert.That(result, Is.Not.Null);
            Assert.Multiple(() =>
            {
                Assert.That(result.Spans, Has.Count.EqualTo(1));
                Assert.That(result.Logs, Has.Count.EqualTo(1));
            });
            Assert.Multiple(() =>
            {
                Assert.That(result.Spans[0].SamplingRatio, Is.EqualTo(50));
                Assert.That(result.Logs[0].SamplingRatio, Is.EqualTo(25));

                // Verify no unexpected log messages
                Assert.That(_logAdapter.Messages, Is.Empty);
            });
        }

        [Test]
        public async Task GetSamplingConfigAsync_ShouldSendCorrectGraphQlRequest()
        {
            // Arrange
            var graphqlResponse = new SamplingConfigClient.GraphQlResponse
            {
                Data = new SamplingConfigClient.SamplingDataResponse
                {
                    Sampling = CreateTestSamplingConfig()
                },
                Errors = new List<SamplingConfigClient.GraphQlError>()
            };

            var httpResponse = CreateSuccessResponse(graphqlResponse);
            HttpContent capturedContent = null;

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .Callback<string, HttpContent, CancellationToken>((url, content, token) => capturedContent = content)
                .ReturnsAsync(httpResponse);

            // Act
            await _client.GetSamplingConfigAsync(TestOrganizationId);

            // Assert
            Assert.That(capturedContent, Is.Not.Null);
            var requestJson = await capturedContent.ReadAsStringAsync();
            var request = JsonSerializer.Deserialize<SamplingConfigClient.GraphQlRequest>(requestJson,
                new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase });
            Assert.Multiple(() =>
            {
                Assert.That(request.Query, Is.Not.Null.And.Not.Empty);
                Assert.That(request.Variables.OrganizationVerboseId, Is.EqualTo(TestOrganizationId));
            });
            Assert.That(request.Query, Contains.Substring("GetSamplingConfig"));
            Assert.That(request.Query,
                Contains.Substring("sampling(organization_verbose_id: $organization_verbose_id)"));
        }

        [Test]
        public async Task GetSamplingConfigAsync_WithCancellationToken_ShouldPassTokenToHttpClient()
        {
            // Arrange
            using (var cts = new CancellationTokenSource())
            {
                var token = cts.Token;

                var graphqlResponse = new SamplingConfigClient.GraphQlResponse
                {
                    Data = new SamplingConfigClient.SamplingDataResponse
                    {
                        Sampling = CreateTestSamplingConfig()
                    }
                };

                var httpResponse = CreateSuccessResponse(graphqlResponse);

                _mockHttpClient
                    .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), token))
                    .ReturnsAsync(httpResponse);

                // Act
                await _client.GetSamplingConfigAsync(TestOrganizationId, token);

                // Assert
                _mockHttpClient.Verify(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), token), Times.Once);

                // Verify no unexpected log messages
                Assert.That(_logAdapter.Messages, Is.Empty);
            }
        }

        #endregion

        #region GraphQl Error Response Tests

        [Test]
        public async Task GetSamplingConfigAsync_WithGraphQlErrors_ShouldReturnConfig()
        {
            // Arrange
            var expectedConfig = CreateTestSamplingConfig();
            var graphqlResponse = new SamplingConfigClient.GraphQlResponse
            {
                Data = new SamplingConfigClient.SamplingDataResponse
                {
                    Sampling = expectedConfig
                },
                Errors = new List<SamplingConfigClient.GraphQlError>
                {
                    new SamplingConfigClient.GraphQlError
                    {
                        Message = "Test GraphQl error",
                        Path = new List<object> { "sampling", "spans" },
                        Locations = new List<SamplingConfigClient.GraphQlLocation>
                        {
                            new SamplingConfigClient.GraphQlLocation { Line = 10, Column = 5 }
                        }
                    }
                }
            };

            var httpResponse = CreateSuccessResponse(graphqlResponse);

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ReturnsAsync(httpResponse);

            // Act
            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            // Assert
            Assert.That(result, Is.Not.Null);
            Assert.Multiple(() =>
            {
                Assert.That(result.Spans, Has.Count.EqualTo(expectedConfig.Spans.Count));
                Assert.That(result.Logs, Has.Count.EqualTo(expectedConfig.Logs.Count));
            });
            Assert.Multiple(() =>
            {
                Assert.That(result.Spans[0].SamplingRatio, Is.EqualTo(expectedConfig.Spans[0].SamplingRatio));
                Assert.That(result.Logs[0].SamplingRatio, Is.EqualTo(expectedConfig.Logs[0].SamplingRatio));

                // Verify that GraphQl errors were logged
                Assert.That(_logAdapter.Messages, Has.Count.EqualTo(1));
            });
            var logMessage = _logAdapter.Messages[0];
            Assert.That(logMessage.Level, Is.EqualTo(LogLevel.Debug));
            Assert.That(logMessage.Name, Is.EqualTo("TestLogger.LaunchDarklyObservability"));
            Assert.That(logMessage.Message,
                Is.EqualTo(
                    "Error in graphql response for sampling configuration: Test GraphQl error:[sampling, spans]:[Line:10Column:5]"));
        }

        [Test]
        public async Task GetSamplingConfigAsync_WithMultipleGraphQlErrors_ShouldReturnConfig()
        {
            // Arrange
            var expectedConfig = CreateTestSamplingConfig();
            var graphqlResponse = new SamplingConfigClient.GraphQlResponse
            {
                Data = new SamplingConfigClient.SamplingDataResponse
                {
                    Sampling = expectedConfig
                },
                Errors = new List<SamplingConfigClient.GraphQlError>
                {
                    new SamplingConfigClient.GraphQlError
                    {
                        Message = "First error",
                        Path = new List<object> { "sampling" }
                    },
                    new SamplingConfigClient.GraphQlError
                    {
                        Message = "Second error",
                        Path = new List<object> { "config" }
                    }
                }
            };

            var httpResponse = CreateSuccessResponse(graphqlResponse);

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ReturnsAsync(httpResponse);

            // Act
            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.Multiple(() =>
            {
                // Assert
                Assert.That(result, Is.Not.Null);

                // Verify that GraphQl errors were logged
                Assert.That(_logAdapter.Messages, Has.Count.EqualTo(1));
            });
            var logMessage = _logAdapter.Messages[0];
            Assert.Multiple(() =>
            {
                Assert.That(logMessage.Level, Is.EqualTo(LogLevel.Debug));
                Assert.That(logMessage.Name, Is.EqualTo("TestLogger.LaunchDarklyObservability"));
                Assert.That(logMessage.Message,
                    Is.EqualTo(
                        "Error in graphql response for sampling configuration: [First error:[sampling]:[], Second error:[config]:[]]"));
            });
        }

        #endregion

        #region HTTP Error and Exception Tests

        [Test]
        public async Task GetSamplingConfigAsync_WithHttpError_ShouldReturnNull()
        {
            // Arrange
            var httpResponse = CreateErrorResponse(HttpStatusCode.InternalServerError);

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ReturnsAsync(httpResponse);

            // Act
            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.Multiple(() =>
            {
                // Assert
                Assert.That(result, Is.Null);

                // Verify that the error was logged
                Assert.That(_logAdapter.Messages, Has.Count.EqualTo(1));
            });
            var logMessage = _logAdapter.Messages[0];
            Assert.Multiple(() =>
            {
                Assert.That(logMessage.Level, Is.EqualTo(LogLevel.Debug));
                Assert.That(logMessage.Name, Is.EqualTo("TestLogger.LaunchDarklyObservability"));
                Assert.That(logMessage.Message, Contains.Substring("Error fetching sampling configuration"));
            });
        }

        [Test]
        public async Task GetSamplingConfigAsync_WithHttpClientException_ShouldReturnNull()
        {
            // Arrange
            var expectedException = new HttpRequestException("Network error");

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ThrowsAsync(expectedException);

            // Act
            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.Multiple(() =>
            {
                // Assert
                Assert.That(result, Is.Null);

                // Verify that the exception was logged
                Assert.That(_logAdapter.Messages, Has.Count.EqualTo(1));
            });
            var logMessage = _logAdapter.Messages[0];
            Assert.Multiple(() =>
            {
                Assert.That(logMessage.Level, Is.EqualTo(LogLevel.Debug));
                Assert.That(logMessage.Name, Is.EqualTo("TestLogger.LaunchDarklyObservability"));
                Assert.That(logMessage.Message, Contains.Substring("Error fetching sampling configuration"));
            });
            Assert.That(logMessage.Message, Contains.Substring("HttpRequestException"));
        }

        [Test]
        public async Task GetSamplingConfigAsync_WithTaskCanceledException_ShouldReturnNull()
        {
            // Arrange
            var expectedException = new TaskCanceledException("Request was canceled");

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ThrowsAsync(expectedException);

            // Act
            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.Multiple(() =>
            {
                // Assert
                Assert.That(result, Is.Null);

                // Verify that the cancellation exception was logged
                Assert.That(_logAdapter.Messages, Has.Count.EqualTo(1));
            });
            var logMessage = _logAdapter.Messages[0];
            Assert.Multiple(() =>
            {
                Assert.That(logMessage.Level, Is.EqualTo(LogLevel.Debug));
                Assert.That(logMessage.Name, Is.EqualTo("TestLogger.LaunchDarklyObservability"));
                Assert.That(logMessage.Message, Contains.Substring("Error fetching sampling configuration"));
            });
            Assert.That(logMessage.Message, Contains.Substring("TaskCanceledException"));
        }

        [Test]
        public async Task GetSamplingConfigAsync_WithJsonException_ShouldReturnNull()
        {
            // Arrange
            var invalidJsonResponse = new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("invalid json content", System.Text.Encoding.UTF8, "application/json")
            };

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ReturnsAsync(invalidJsonResponse);

            // Act
            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.Multiple(() =>
            {
                // Assert
                Assert.That(result, Is.Null);

                // Verify that the JSON parsing exception was logged
                Assert.That(_logAdapter.Messages, Has.Count.EqualTo(1));
            });
            var logMessage = _logAdapter.Messages[0];
            Assert.Multiple(() =>
            {
                Assert.That(logMessage.Level, Is.EqualTo(LogLevel.Debug));
                Assert.That(logMessage.Name, Is.EqualTo("TestLogger.LaunchDarklyObservability"));
                Assert.That(logMessage.Message, Contains.Substring("Error fetching sampling configuration"));
            });
            Assert.That(logMessage.Message, Contains.Substring("JsonException"));
        }

        #endregion

        #region Edge Cases and Null Response Tests

        [Test]
        public async Task GetSamplingConfigAsync_WithNullData_ShouldReturnNull()
        {
            // Arrange
            var graphqlResponse = new SamplingConfigClient.GraphQlResponse
            {
                Data = null,
                Errors = new List<SamplingConfigClient.GraphQlError>()
            };

            var httpResponse = CreateSuccessResponse(graphqlResponse);

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ReturnsAsync(httpResponse);

            // Act
            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            // Assert
            Assert.That(result, Is.Null);
        }

        [Test]
        public async Task GetSamplingConfigAsync_WithNullSampling_ShouldReturnNull()
        {
            // Arrange
            var graphqlResponse = new SamplingConfigClient.GraphQlResponse
            {
                Data = new SamplingConfigClient.SamplingDataResponse
                {
                    Sampling = null
                },
                Errors = new List<SamplingConfigClient.GraphQlError>()
            };

            var httpResponse = CreateSuccessResponse(graphqlResponse);

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ReturnsAsync(httpResponse);

            // Act
            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            // Assert
            Assert.That(result, Is.Null);
        }

        [Test]
        public async Task GetSamplingConfigAsync_WithEmptyResponse_ShouldReturnEmptyConfig()
        {
            // Arrange
            var emptyConfig = new SamplingConfig
            {
                Spans = new List<SamplingConfig.SpanSamplingConfig>(),
                Logs = new List<SamplingConfig.LogSamplingConfig>()
            };

            var graphqlResponse = new SamplingConfigClient.GraphQlResponse
            {
                Data = new SamplingConfigClient.SamplingDataResponse
                {
                    Sampling = emptyConfig
                },
                Errors = new List<SamplingConfigClient.GraphQlError>()
            };

            var httpResponse = CreateSuccessResponse(graphqlResponse);

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ReturnsAsync(httpResponse);

            // Act
            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            // Assert
            Assert.That(result, Is.Not.Null);
            Assert.Multiple(() =>
            {
                Assert.That(result.Spans, Is.Empty);
                Assert.That(result.Logs, Is.Empty);
            });
        }

        #endregion

        #region Integration with HttpClientWrapper Tests

        [Test]
        public async Task GetSamplingConfigAsync_WithRealHttpClientWrapper_ShouldCallCorrectEndpoint()
        {
            // Arrange
            var httpClient = new HttpClient(new TestHttpMessageHandler());
            var client = new SamplingConfigClient(httpClient, TestBackendUrl);

            // Act & Assert - Should not throw, handler will verify the request
            var result = await client.GetSamplingConfigAsync(TestOrganizationId);

            // The TestHttpMessageHandler will verify the correct URL was called
            Assert.That(result, Is.Not.Null);
        }

        private class TestHttpMessageHandler : HttpMessageHandler
        {
            protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,
                CancellationToken cancellationToken)
            {
                Assert.Multiple(() =>
                {
                    // Verify the request was sent to the correct URL
                    if (request.RequestUri != null)
                        Assert.That(request.RequestUri.ToString(), Is.EqualTo(TestBackendUrl));
                    Assert.That(request.Method, Is.EqualTo(HttpMethod.Post));
                    Assert.That(request.Content, Is.Not.Null);
                });

                // Return a valid response
                var responseData = new SamplingConfigClient.GraphQlResponse
                {
                    Data = new SamplingConfigClient.SamplingDataResponse
                    {
                        Sampling = new SamplingConfig
                        {
                            Spans = new List<SamplingConfig.SpanSamplingConfig>(),
                            Logs = new List<SamplingConfig.LogSamplingConfig>()
                        }
                    },
                    Errors = new List<SamplingConfigClient.GraphQlError>()
                };

                var json = JsonSerializer.Serialize(responseData, new JsonSerializerOptions
                {
                    PropertyNamingPolicy = JsonNamingPolicy.CamelCase
                });

                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
                {
                    Content = new StringContent(json, System.Text.Encoding.UTF8, "application/json")
                });
            }
        }

        #endregion
    }
}
