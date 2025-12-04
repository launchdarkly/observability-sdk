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
        private val GET_SAMPLING_CONFIG_QUERY_FILE_PATH = "graphql/GetSamplingConfigQuery.graphql"
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
                queryFileName = GET_SAMPLING_CONFIG_QUERY_FILE_PATH,
                variables = variables,
                dataSerializer = SamplingResponse.serializer()
            )

            if (response.errors?.isNotEmpty() == true) {
                printErrors(response)
                return null
            }

            return response.data?.mapToEntity()
        } catch (e: Exception) {
            println("Error fetching sampling config: ${e.message}")
            return null
        }
    }

    private fun printErrors(response: GraphQLResponse<SamplingResponse>) {
        response.errors?.forEach { error ->
            println("GraphQL Error: ${error.message}")
            error.locations?.forEach { location ->
                println("  at line ${location.line}, column ${location.column}")
            }
        }
    }
}
