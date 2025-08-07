using System.Text.Json;

namespace SandboxAPI;

#region GraphQL Types

/// <summary>
/// GraphQL query variables for GetSamplingConfig
/// </summary>
public class GetSamplingConfigVariables
{
    public string OrganizationVerboseId { get; set; } = string.Empty;
}

/// <summary>
/// GraphQL query request structure
/// </summary>
public class GraphQLRequest
{
    public string Query { get; set; } = string.Empty;
    public GetSamplingConfigVariables Variables { get; set; } = new();
}

/// <summary>
/// GraphQL response structure
/// </summary>
public class GraphQLResponse
{
    public SamplingDataResponse? Data { get; set; }
    public List<GraphQLError>? Errors { get; set; }
}

/// <summary>
/// GraphQL error structure
/// </summary>
public class GraphQLError
{
    public string Message { get; set; } = string.Empty;
    public List<GraphQLLocation>? Locations { get; set; }
    public List<object>? Path { get; set; }
}

/// <summary>
/// GraphQL error location
/// </summary>
public class GraphQLLocation
{
    public int Line { get; set; }
    public int Column { get; set; }
}

/// <summary>
/// Data response wrapper
/// </summary>
public class SamplingDataResponse
{
    public SamplingConfig? Sampling { get; set; }
}

#endregion

/// <summary>
/// HTTP client for fetching sampling configuration from the backend
/// </summary>
public class SamplingConfigClient
{
    private readonly HttpClient _httpClient;
    private readonly string _backendUrl;
    
    private const string GraphQLQuery = @"
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

    public SamplingConfigClient(HttpClient httpClient, string backendUrl)
    {
        _httpClient = httpClient;
        _backendUrl = backendUrl;
    }

    public async Task<SamplingConfig?> GetSamplingConfigAsync(string organizationVerboseId, CancellationToken cancellationToken = default)
    {
        try
        {
            var request = new GraphQLRequest
            {
                Query = GraphQLQuery,
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

            var responseJson = await response.Content.ReadAsStringAsync(cancellationToken);
            var graphqlResponse = JsonSerializer.Deserialize<GraphQLResponse>(responseJson, new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
                PropertyNameCaseInsensitive = true
            });

            if (graphqlResponse?.Errors?.Any() == true)
            {
                var errorMessages = string.Join(", ", graphqlResponse.Errors.Select(e => e.Message));
                throw new InvalidOperationException($"GraphQL errors: {errorMessages}");
            }

            return graphqlResponse?.Data?.Sampling;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Failed to fetch sampling config: {ex.Message}");
            return null;
        }
    }
}
