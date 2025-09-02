package com.launchdarkly.observability.network

import com.launchdarkly.observability.sampling.SamplingConfig

/**
 * Service for fetching sampling configuration
 */
class SamplingApiService(
    private val graphqlClient: GraphQLClient
) {

    /**
     * Fetches sampling configuration from GraphQL endpoint
     * @param organizationVerboseId The organization verbose ID
     * @return SamplingConfig or null if not found/error
     */
    suspend fun getSamplingConfig(organizationVerboseId: String): SamplingConfig? {
        try {
            val query = "GetSamplingConfigQuery.graphql"
            val variables = mapOf("organization_verbose_id" to organizationVerboseId)

            val response = graphqlClient.execute<SamplingResponse>(query, variables)

            if (response.errors?.isNotEmpty() == true) {
                graphqlClient.printErrors(response)
                return null
            }

            return response.data?.mapToEntity()
        } catch (e: Exception) {
            println("Error fetching sampling config: ${e.message}")
            return null
        }
    }
}
