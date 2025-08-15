using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;
using LaunchDarkly.Observability.Logging;

namespace LaunchDarkly.Observability.Sampling
{
    internal class SamplingConfigClient
    {
        #region GraphQL Types

        /// <summary>
        /// GraphQL query variables for GetSamplingConfig
        /// </summary>
        public class GetSamplingConfigVariables
        {
            [JsonPropertyName("organization_verbose_id")]
            public string OrganizationVerboseId { get; set; }
        }

        /// <summary>
        /// GraphQL query request structure
        /// </summary>
        public class GraphQlRequest
        {
            [JsonPropertyName("query")] public string Query { get; set; } = string.Empty;
            [JsonPropertyName("variables")] public GetSamplingConfigVariables Variables { get; set; }
        }

        /// <summary>
        /// GraphQL response structure
        /// </summary>
        public class GraphQlResponse
        {
            public SamplingDataResponse Data { get; set; }
            public List<GraphQlError> Errors { get; set; }
        }

        /// <summary>
        /// GraphQL error structure
        /// </summary>
        public class GraphQlError
        {
            public string Message { get; set; }
            public List<GraphQlLocation> Locations { get; set; }
            public List<object> Path { get; set; }
            public override string ToString()
            {
                return $"{Message}:{Path}:{Locations}";
            }
        }

        /// <summary>
        /// GraphQL error location
        /// </summary>
        public class GraphQlLocation
        {
            public int Line { get; set; }
            public int Column { get; set; }

            public override string ToString()
            {
                return $"Line:{Line}Column:{Column}";
            }
        }

        /// <summary>
        /// Data response wrapper
        /// </summary>
        public class SamplingDataResponse
        {
            public SamplingConfig Sampling { get; set; }
        }

        #endregion

        private readonly IHttpClient _httpClient;
        private readonly string _backendUrl;

        private const string GraphQlQuery = @"
        query GetSamplingConfig($organization_verbose_id: String!) {
            sampling(organization_verbose_id: $organization_verbose_id) {
                spans {
                    name {
                        regexValue
                        matchValue
                    }
                    attributes {
                        key {
                            regexValue
                            matchValue
                        }
                        attribute {
                            regexValue
                            matchValue
                        }
                    }
                    events {
                        name {
                            regexValue
                            matchValue
                        }
                        attributes {
                            key {
                                regexValue
                                matchValue
                            }
                            attribute {
                                regexValue
                                matchValue
                            }
                        }
                    }
                    samplingRatio
                }
                logs {
                    message {
                        regexValue
                        matchValue
                    }
                    severityText {
                        regexValue
                        matchValue
                    }
                    attributes {
                        key {
                            regexValue
                            matchValue
                        }
                        attribute {
                            regexValue
                            matchValue
                        }
                    }
                    samplingRatio
                }
            }
        }";

        /// <summary>
        /// Initializes a new instance of SamplingConfigClient with a custom HTTP client
        /// </summary>
        /// <param name="httpClient">The HTTP client interface implementation to use</param>
        /// <param name="backendUrl">The backend URL for GraphQL requests</param>
        public SamplingConfigClient(IHttpClient httpClient, string backendUrl)
        {
            _httpClient = httpClient ?? throw new ArgumentNullException(nameof(httpClient));
            _backendUrl = backendUrl ?? throw new ArgumentNullException(nameof(backendUrl));
        }

        /// <summary>
        /// Initializes a new instance of SamplingConfigClient with the default HTTP client wrapper
        /// </summary>
        /// <param name="httpClient">The HttpClient instance to wrap</param>
        /// <param name="backendUrl">The backend URL for GraphQL requests</param>
        public SamplingConfigClient(HttpClient httpClient, string backendUrl)
            : this(new HttpClientWrapper(httpClient), backendUrl)
        {
        }
        
        public async Task<SamplingConfig> GetSamplingConfigAsync(string organizationVerboseId, CancellationToken cancellationToken = default)
        {
            try
            {
                var request = new GraphQlRequest
                {
                    Query = GraphQlQuery,
                    Variables = new GetSamplingConfigVariables
                    {
                        OrganizationVerboseId = organizationVerboseId
                    }
                };

                var json = JsonSerializer.Serialize(request, new JsonSerializerOptions
                {
                    PropertyNamingPolicy = JsonNamingPolicy.CamelCase
                });

                var content = new StringContent(json, System.Text.Encoding.UTF8, "application/json");
            
                var response = await _httpClient.PostAsync(_backendUrl, content, cancellationToken);
                response.EnsureSuccessStatusCode();

                // ReadAsStringAsync is only supported in .Net 5.0+ and not .NetFramework/netstandard2.0.
                #if NET5_0_OR_GREATER
                var responseJson = await response.Content.ReadAsStringAsync(cancellationToken);
                #else
                var responseJson = await response.Content.ReadAsStringAsync();
                #endif
                
                var graphqlResponse = JsonSerializer.Deserialize<GraphQlResponse>(responseJson, new JsonSerializerOptions
                {
                    PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
                    PropertyNameCaseInsensitive = true
                });

                if (graphqlResponse?.Errors?.Count > 0)
                {
                    DebugLogger.DebugLog($"Error in graphql response for sampling configuration: {graphqlResponse?.Errors}");
                }

                return graphqlResponse?.Data?.Sampling;
            }
            catch (Exception ex)
            {
                DebugLogger.DebugLog($"Error fetching sampling configuration: {ex}");
                return null;
            }
        }
    }
}
