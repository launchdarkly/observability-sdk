package com.launchdarkly.observability.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

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
    suspend fun initializeReplaySession(organizationVerboseId: String, sessionSecureId: String) {
        // TODO: need to come back and make these request arguments make sense
        try {
            val variables = mapOf(
                "organization_verbose_id" to JsonPrimitive(organizationVerboseId),
                "session_secure_id" to JsonPrimitive(sessionSecureId),
                "enable_strict_privacy" to JsonPrimitive(false),
                "enable_recording_network_contents" to JsonPrimitive(false),
                "clientVersion" to JsonPrimitive("0.1.0"),
                "firstloadVersion" to JsonPrimitive("0.1.0"),
                "clientConfig" to JsonPrimitive("{\"debug\":{\"clientInteractions\":true,\"domRecording\":true},\"privacySetting\":\"none\",\"serviceName\":\"observability-android\",\"backendUrl\":\"https://pub.observability.app.launchdarkly.com\",\"manualStart\":true,\"organizationID\":\"${organizationVerboseId}\",\"environment\":\"production\",\"sessionSecureID\":\"${sessionSecureId}\"}"),
                "environment" to JsonPrimitive("production"),
                "appVersion" to JsonPrimitive("0.1.0"),
                "serviceName" to JsonPrimitive("observability-android"),
                "fingerprint" to JsonPrimitive("fingerprint"),
                "client_id" to JsonPrimitive("observability-android"),
                "network_recording_domains" to JsonArray(emptyList()),
                "disable_session_recording" to JsonPrimitive(false),
                "privacy_setting" to JsonPrimitive("none"),
                "id" to JsonPrimitive("bogusId")
            )
            val response = graphqlClient.execute(
                queryFileName = INITIALIZE_REPLAY_SESSION_QUERY_FILE_PATH,
                variables = variables,
                dataSerializer = InitializeReplaySessionResponse.serializer()
            )

            // TODO: check graphql requests can generate errors when necessary and add error handling
            if (response.errors?.isNotEmpty() == true) {
                printErrors(response)
            }
        } catch (e: Exception) {
            println("Error fetching sampling config: ${e.message}")
        }
    }

    suspend fun pushPayload(organizationVerboseId: String, sessionSecureId: String, payloadId: String, events: List<Event>) {

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
