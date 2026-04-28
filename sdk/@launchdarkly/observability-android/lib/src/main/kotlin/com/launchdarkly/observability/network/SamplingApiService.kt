package com.launchdarkly.observability.network

import com.launchdarkly.observability.sampling.SamplingConfig
import kotlinx.serialization.json.JsonPrimitive

/**
 * Service for fetching sampling configuration
 */
class SamplingApiService(
    private val graphqlClient: GraphQLClient
) {

    companion object {
        private val GET_SAMPLING_CONFIG_QUERY = """
            fragment MatchParts on MatchConfig {
                regexValue
                matchValue
            }

            query GetSamplingConfig(${'$'}organization_verbose_id: String!) {
                sampling(organization_verbose_id: ${'$'}organization_verbose_id) {
                    spans {
                        name {
                            ...MatchParts
                        }
                        attributes {
                            key {
                                ...MatchParts
                            }
                            attribute {
                                ...MatchParts
                            }
                        }
                        events {
                            name {
                                ...MatchParts
                            }
                            attributes {
                                key {
                                    ...MatchParts
                                }
                                attribute {
                                    ...MatchParts
                                }
                            }
                        }
                        samplingRatio
                    }
                    logs {
                        message {
                            ...MatchParts
                        }
                        severityText {
                            ...MatchParts
                        }
                        attributes {
                            key {
                                ...MatchParts
                            }
                            attribute {
                                ...MatchParts
                            }
                        }
                        samplingRatio
                    }
                }
            }
        """.trimIndent()
    }

    /**
     * Fetches sampling configuration from GraphQL endpoint
     * @param organizationVerboseId The organization verbose ID
     * @return SamplingConfig or null if not found/error
     */
    suspend fun getSamplingConfig(organizationVerboseId: String): SamplingConfig? {
        try {
            val variables = mapOf("organization_verbose_id" to JsonPrimitive(organizationVerboseId))
            val response = graphqlClient.execute(
                query = GET_SAMPLING_CONFIG_QUERY,
                variables = variables,
                dataSerializer = SamplingResponse.serializer()
            )

            if (response.errors?.isNotEmpty() == true) {
                return null
            }

            return response.data?.mapToEntity()
        } catch (e: Exception) {
            println("Error fetching sampling config: ${e.message}")
            return null
        }
    }

}
