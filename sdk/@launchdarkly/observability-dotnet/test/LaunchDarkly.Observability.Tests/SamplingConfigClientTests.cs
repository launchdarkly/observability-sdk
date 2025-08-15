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

            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

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

            await _client.GetSamplingConfigAsync(TestOrganizationId);

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

                await _client.GetSamplingConfigAsync(TestOrganizationId, token);

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

            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

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
            Assert.Multiple(() =>
            {
                Assert.That(logMessage.Level, Is.EqualTo(LogLevel.Debug));
                Assert.That(logMessage.Name, Is.EqualTo("TestLogger.LaunchDarklyObservability"));
                Assert.That(logMessage.Message,
                    Is.EqualTo(
                        "Error in graphql response for sampling configuration: Test GraphQl error:[sampling, spans]:[Line:10Column:5]"));
            });
        }

        [Test]
        public async Task GetSamplingConfigAsync_WithMultipleGraphQlErrors_ShouldReturnConfig()
        {
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

            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.Multiple(() =>
            {
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
            var httpResponse = CreateErrorResponse(HttpStatusCode.InternalServerError);

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ReturnsAsync(httpResponse);

            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.Multiple(() =>
            {
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
            var expectedException = new HttpRequestException("Network error");

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ThrowsAsync(expectedException);

            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.Multiple(() =>
            {
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
            var expectedException = new TaskCanceledException("Request was canceled");

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ThrowsAsync(expectedException);

            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.Multiple(() =>
            {
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
            var invalidJsonResponse = new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("invalid json content", System.Text.Encoding.UTF8, "application/json")
            };

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ReturnsAsync(invalidJsonResponse);

            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.Multiple(() =>
            {
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
            var graphqlResponse = new SamplingConfigClient.GraphQlResponse
            {
                Data = null,
                Errors = new List<SamplingConfigClient.GraphQlError>()
            };

            var httpResponse = CreateSuccessResponse(graphqlResponse);

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ReturnsAsync(httpResponse);

            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.That(result, Is.Null);
        }

        [Test]
        public async Task GetSamplingConfigAsync_WithNullSampling_ShouldReturnNull()
        {
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

            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.That(result, Is.Null);
        }

        [Test]
        public async Task GetSamplingConfigAsync_WithEmptyResponse_ShouldReturnEmptyConfig()
        {
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

            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            Assert.That(result, Is.Not.Null);
            Assert.Multiple(() =>
            {
                Assert.That(result.Spans, Is.Empty);
                Assert.That(result.Logs, Is.Empty);
            });
        }

        #endregion

        [Test]
        public async Task GetSamplingConfigAsync_ShouldCallCorrectEndpoint()
        {
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

            var httpResponse = CreateSuccessResponse(responseData);

            _mockHttpClient
                .Setup(x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()))
                .ReturnsAsync(httpResponse);

            var result = await _client.GetSamplingConfigAsync(TestOrganizationId);

            // Assert
            Assert.That(result, Is.Not.Null);
            _mockHttpClient.Verify(
                x => x.PostAsync(TestBackendUrl, It.IsAny<HttpContent>(), It.IsAny<CancellationToken>()), Times.Once);
        }
    }
}
