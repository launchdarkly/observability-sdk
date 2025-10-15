package com.launchdarkly.observability.replay

import android.util.Log
import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.network.GraphQLClient
import com.launchdarkly.observability.network.GraphQLResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

// TODO: O11Y-627 - Refactor logging handling in this class
class SessionReplayApiService(
    private val graphqlClient: GraphQLClient,
    val serviceName: String,
    val serviceVersion: String,
) {
    private val json: Json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    companion object {
        private val INITIALIZE_REPLAY_SESSION_QUERY_FILE_PATH = "graphql/InitializeReplaySession.graphql"
        private val IDENTIFY_REPLAY_SESSION_QUERY_FILE_PATH = "graphql/IdentifyReplaySession.graphql"
        private val PUSH_PAYLOAD_QUERY_FILE_PATH = "graphql/PushPayload.graphql"
    }

    /**
     * Initializes a replay session
     * @param organizationVerboseId The organization verbose ID
     */
    suspend fun initializeReplaySession(organizationVerboseId: String, sessionSecureId: String) {
        try {
            val variables = mapOf(
                "organization_verbose_id" to JsonPrimitive(organizationVerboseId),
                "session_secure_id" to JsonPrimitive(sessionSecureId),
                "enable_strict_privacy" to JsonPrimitive(false),
                "enable_recording_network_contents" to JsonPrimitive(false),
                "clientVersion" to JsonPrimitive(BuildConfig.OBSERVABILITY_SDK_VERSION),
                "firstloadVersion" to JsonPrimitive(BuildConfig.OBSERVABILITY_SDK_VERSION),
                "clientConfig" to JsonPrimitive("{}"), // TODO: O11Y-631 - remove hardcoded params
                "environment" to JsonPrimitive(""), // TODO: O11Y-631 - remove hardcoded params
                "appVersion" to JsonPrimitive(serviceVersion),
                "serviceName" to JsonPrimitive(serviceName),
                "fingerprint" to JsonPrimitive(""), // TODO: O11Y-631 - remove hardcoded params
                "client_id" to JsonPrimitive(""), // TODO: O11Y-631 - remove hardcoded params
                "network_recording_domains" to JsonArray(emptyList()),
                "privacy_setting" to JsonPrimitive("none"), // TODO: O11Y-631 - remove hardcoded params
                "id" to JsonPrimitive("") // TODO: O11Y-631 - remove hardcoded params
            )
            val response = graphqlClient.execute(
                queryFileName = INITIALIZE_REPLAY_SESSION_QUERY_FILE_PATH,
                variables = variables,
                dataSerializer = InitializeReplaySessionResponse.serializer()
            )

            // TODO: O11Y-624 - check graphql requests can generate errors when necessary and add error handling
            if (response.errors?.isNotEmpty() == true) {
                printErrors(response)
            }
        } catch (e: Exception) {
            Log.e("SessionReplayApiService", "Error initializing replay session: ${e.message}")
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
        userIdentifier: String = "", // TODO: O11Y-631 - remove hardcoded params
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
            Log.e("SessionReplayApiService", "Error identifying replay session: ${e.message}")

        }
    }

    /**
     * Pushes session replay events
     * @param sessionSecureId The session secure ID
     * @param payloadId The payload ID
     * @param events The list of events to push
     */
    suspend fun pushPayload(sessionSecureId: String, payloadId: String, events: List<Event>) {
        try {
            val variables = mapOf(
                "session_secure_id" to JsonPrimitive(sessionSecureId),
                "payload_id" to JsonPrimitive(payloadId),
                "events" to json.encodeToJsonElement(ReplayEventsInput.serializer(), ReplayEventsInput(events)),
                "messages" to JsonPrimitive("{\"messages\":[]}"),
                "resources" to JsonPrimitive("{\"resources\":[]}"),
                "web_socket_events" to JsonPrimitive("{\"webSocketEvents\":[]}"),
                "errors" to JsonArray(emptyList()),
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
            Log.e("SessionReplayApiService", "Error pushing payload: ${e.message}")
        }
    }

    private fun <T> printErrors(response: GraphQLResponse<T>) {
        response.errors?.forEach { error ->
            Log.e("SessionReplayApiService", "GraphQL Error: ${error.message}")
            error.locations?.forEach { location ->
                Log.e("SessionReplayApiService", "  at line ${location.line}, column ${location.column}")
            }
        }
    }
}
