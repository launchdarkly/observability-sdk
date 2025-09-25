package com.launchdarkly.observability.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SessionReplayApiService(
    private val graphqlClient: GraphQLClient
) {
    private val json: Json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    companion object {
        private val INITIALIZE_REPLAY_SESSION_QUERY_FILE_PATH = "graphql/InitializeReplaySession.graphql"
        private val PUSH_PAYLOAD_QUERY_FILE_PATH = "graphql/PushPayload.graphql"
        private val IDENTIFY_REPLAY_SESSION_QUERY_FILE_PATH = "graphql/IdentifyReplaySession.graphql"
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

    /**
     * Identifies a replay session with user information
     * @param sessionSecureId The session secure ID
     * @param userIdentifier The user identifier (defaults to "unknown")
     * @param userObject Optional user object with key-value pairs
     */
    suspend fun identifyReplaySession(
        sessionSecureId: String,
        userIdentifier: String = "unknown",
        userObject: JsonElement = JsonNull
    ) {
        try {
            val variables = mapOf(
                "session_secure_id" to JsonPrimitive(sessionSecureId),
                "user_identifier" to JsonPrimitive(userIdentifier),
                "user_object" to userObject
            )
            
            val response = graphqlClient.execute(
                queryFileName = IDENTIFY_REPLAY_SESSION_QUERY_FILE_PATH,
                variables = variables,
                dataSerializer = IdentifySessionResponse.serializer()
            )

            if (response.errors?.isNotEmpty() == true) {
                printErrors(response)
            }
        } catch (e: Exception) {
            println("Error identifying replay session: ${e.message}")
        }
    }

    /**
     * Pushes payload data to the replay session
     * @param organizationVerboseId The organization verbose ID
     * @param sessionSecureId The session secure ID
     * @param payloadId The payload ID
     * @param events The list of events to push
     */
    suspend fun pushPayload(organizationVerboseId: String, sessionSecureId: String, payloadId: String, events: List<Event>) {
        try {
            val variables = mapOf(
                "session_secure_id" to JsonPrimitive(sessionSecureId),
                "payload_id" to JsonPrimitive(payloadId),
                "events" to json.encodeToJsonElement(ReplayEventsInput.serializer(), ReplayEventsInput(events)),
                "messages" to JsonPrimitive("{\"messages\":[]}"),
                "resources" to JsonPrimitive("{\"resources\":[]}"),
                "web_socket_events" to JsonPrimitive("{\"webSocketEvents\":[]}"),
                "errors" to JsonArray(emptyList()),
                "is_beacon" to JsonPrimitive(false),
                "has_session_unloaded" to JsonPrimitive(false),
                "highlight_logs" to JsonPrimitive("")
            )
            
            val response = graphqlClient.execute(
                queryFileName = PUSH_PAYLOAD_QUERY_FILE_PATH,
                variables = variables,
                dataSerializer = PushPayloadResponse.serializer()
            )

            if (response.errors?.isNotEmpty() == true) {
                printErrors(response)
            }
        } catch (e: Exception) {
            println("Error pushing payload: ${e.message}")
        }
    }

    private fun <T> printErrors(response: GraphQLResponse<T>) {
        response.errors?.forEach { error ->
            println("GraphQL Error: ${error.message}")
            error.locations?.forEach { location ->
                println("  at line ${location.line}, column ${location.column}")
            }
        }
    }
}
