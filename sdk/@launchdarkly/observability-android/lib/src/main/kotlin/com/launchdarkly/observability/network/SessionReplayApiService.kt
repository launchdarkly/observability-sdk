package com.launchdarkly.observability.network

class SessionReplayApiService(
    private val graphqlClient: GraphQLClient
) {

    companion object {
        private val INITIALIZE_REPLAY_SESSION_QUERY_FILE_PATH = "graphql/InitializeReplaySession.graphql"
    }

    /**
     * Initializes a replay session
     * @param organizationVerboseId The organization verbose ID
     */
    suspend fun initializeReplaySession(organizationVerboseId: String) {
        try {
            val variables = mapOf("organization_verbose_id" to organizationVerboseId)
            val response = graphqlClient.execute(
                queryFileName = INITIALIZE_REPLAY_SESSION_QUERY_FILE_PATH,
                variables = variables,
                dataSerializer = InitializeReplaySessionResponse.serializer()
            )

            if (response.errors?.isNotEmpty() == true) {
                printErrors(response)
            }
        } catch (e: Exception) {
            println("Error fetching sampling config: ${e.message}")
        }
    }

    private fun printErrors(response: GraphQLResponse<InitializeReplaySessionResponse>) {
        response.errors?.forEach { error ->
            println("GraphQL Error: ${error.message}")
            error.locations?.forEach { location ->
                println("  at line ${location.line}, column ${location.column}")
            }
        }
    }
}
