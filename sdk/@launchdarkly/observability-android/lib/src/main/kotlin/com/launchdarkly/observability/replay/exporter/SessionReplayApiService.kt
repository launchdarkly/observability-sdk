package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.network.ErrorRecoverability
import com.launchdarkly.observability.network.GraphQLClient
import com.launchdarkly.observability.network.GraphQLClientException
import com.launchdarkly.observability.network.RecoverableFailure
import com.launchdarkly.observability.replay.Event
import com.launchdarkly.observability.replay.IdentifySessionResponse
import com.launchdarkly.observability.replay.InitializeReplaySessionResponse
import com.launchdarkly.observability.replay.PushPayloadResponse
import com.launchdarkly.observability.replay.ReplayEventsInput
import com.launchdarkly.observability.replay.asJsonWritable
import org.json.JSONObject

class SessionReplayApiService(
    private val graphqlClient: GraphQLClient,
    val serviceName: String,
    val serviceVersion: String,
) {
    companion object {
        private val INITIALIZE_REPLAY_SESSION_QUERY = """
            fragment MatchParts on MatchConfig {
                regexValue
                matchValue
            }

            mutation initializeSession(
                ${'$'}session_secure_id: String!
                ${'$'}organization_verbose_id: String!
                ${'$'}enable_strict_privacy: Boolean!
                ${'$'}privacy_setting: String!
                ${'$'}enable_recording_network_contents: Boolean!
                ${'$'}clientVersion: String!
                ${'$'}firstloadVersion: String!
                ${'$'}clientConfig: String!
                ${'$'}environment: String!
                ${'$'}id: String!
                ${'$'}appVersion: String
                ${'$'}serviceName: String!
                ${'$'}client_id: String!
                ${'$'}network_recording_domains: [String!]
            ) {
                initializeSession(
                    session_secure_id: ${'$'}session_secure_id
                    organization_verbose_id: ${'$'}organization_verbose_id
                    enable_strict_privacy: ${'$'}enable_strict_privacy
                    enable_recording_network_contents: ${'$'}enable_recording_network_contents
                    clientVersion: ${'$'}clientVersion
                    firstloadVersion: ${'$'}firstloadVersion
                    clientConfig: ${'$'}clientConfig
                    environment: ${'$'}environment
                    appVersion: ${'$'}appVersion
                    serviceName: ${'$'}serviceName
                    fingerprint: ${'$'}id
                    client_id: ${'$'}client_id
                    network_recording_domains: ${'$'}network_recording_domains
                    privacy_setting: ${'$'}privacy_setting
                ) {
                    secure_id
                    project_id
                    sampling {
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
            }
        """.trimIndent()

        private val IDENTIFY_REPLAY_SESSION_QUERY = """
            mutation identifySession(
                ${'$'}session_secure_id: String!
                ${'$'}user_identifier: String!
                ${'$'}user_object: Any
            ) {
                identifySession(
                    session_secure_id: ${'$'}session_secure_id
                    user_identifier: ${'$'}user_identifier
                    user_object: ${'$'}user_object
                )
            }
        """.trimIndent()

        private val PUSH_PAYLOAD_QUERY = """
            mutation PushPayload(
                ${'$'}session_secure_id: String!
                ${'$'}payload_id: ID!
                ${'$'}events: ReplayEventsInput!
                ${'$'}messages: String!
                ${'$'}resources: String!
                ${'$'}web_socket_events: String!
                ${'$'}errors: [ErrorObjectInput]!
            ) {
                pushPayload(
                    session_secure_id: ${'$'}session_secure_id
                    payload_id: ${'$'}payload_id
                    events: ${'$'}events
                    messages: ${'$'}messages
                    resources: ${'$'}resources
                    web_socket_events: ${'$'}web_socket_events
                    errors: ${'$'}errors
                )
            }
        """.trimIndent()
    }

    /**
     * Initializes a replay session
     * @param organizationVerboseId The organization verbose ID
     */
    suspend fun initializeReplaySession(organizationVerboseId: String, sessionSecureId: String) {
        val variables = mapOf(
            "organization_verbose_id" to organizationVerboseId,
            "session_secure_id" to sessionSecureId,
            "enable_strict_privacy" to false,
            "enable_recording_network_contents" to false,
            "clientVersion" to BuildConfig.OBSERVABILITY_SDK_VERSION,
            "firstloadVersion" to BuildConfig.OBSERVABILITY_SDK_VERSION,
            "clientConfig" to "{}", // TODO: O11Y-631 - remove hardcoded params
            "environment" to "", // TODO: O11Y-631 - remove hardcoded params
            "appVersion" to serviceVersion,
            "serviceName" to serviceName,
            "fingerprint" to "", // TODO: O11Y-631 - remove hardcoded params
            "client_id" to "observability-android",
            "network_recording_domains" to emptyList<String>(),
            "privacy_setting" to "none", // TODO: O11Y-631 - remove hardcoded params
            "id" to "" // TODO: O11Y-631 - remove hardcoded params
        )
        execute(
            operation = "initializeReplaySession",
            query = INITIALIZE_REPLAY_SESSION_QUERY,
            variables = variables,
            dataParser = InitializeReplaySessionResponse::fromJson
        )
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
        userObject: Map<String, String>? = null
    ) {
        val variables = mapOf(
            "session_secure_id" to sessionSecureId,
            "user_identifier" to userIdentifier,
            "user_object" to userObject
        )
        execute(
            operation = "identifyReplaySession",
            query = IDENTIFY_REPLAY_SESSION_QUERY,
            variables = variables,
            dataParser = IdentifySessionResponse::fromJson
        )
    }

    /**
     * Convenience overload to identify a session using an IdentifyItemPayload.
     */
    suspend fun identifyReplaySession(
        sessionSecureId: String,
        identifyEvent: IdentifyItemPayload
    ) {
        val userIdentifier = identifyEvent.attributes["key"] ?: "unknown"
        val userObject = identifyEvent.attributes
        identifyReplaySession(
            sessionSecureId = sessionSecureId,
            userIdentifier = userIdentifier,
            userObject = userObject
        )
    }

    /**
     * Pushes session replay events
     * @param sessionSecureId The session secure ID
     * @param payloadId The payload ID
     * @param events The list of events to push
     */
    suspend fun pushPayload(sessionSecureId: String, payloadId: String, events: List<Event>) {
        val events = events.sortedBy { it.timestamp }
        val variables = mapOf(
            "session_secure_id" to sessionSecureId,
            "payload_id" to payloadId,
            "events" to ReplayEventsInput(events).asJsonWritable(),
            "messages" to "{\"messages\":[]}",
            "resources" to "{\"resources\":[]}",
            "web_socket_events" to "{\"webSocketEvents\":[]}",
            "errors" to emptyList<String>(),
        )

        execute(
            operation = "pushPayload",
            query = PUSH_PAYLOAD_QUERY,
            variables = variables,
            dataParser = PushPayloadResponse::fromJson
        )
    }

    /**
     * Runs [query] and discards the response data, which none of these mutations reports anything useful
     * in, naming the operation in whatever it throws.
     */
    private suspend fun <T> execute(
        operation: String,
        query: String,
        variables: Map<String, Any?>,
        dataParser: (JSONObject) -> T,
    ) {
        try {
            graphqlClient.execute(query = query, variables = variables, dataParser = dataParser)
        } catch (e: GraphQLClientException) {
            throw SessionReplayApiException(operation, e)
        }
    }
}

/**
 * A failed session replay operation, named. Adds the operation to the failure the client threw and takes
 * its recoverability from it, so no verdict is derived twice.
 */
internal class SessionReplayApiException(
    operation: String,
    cause: GraphQLClientException,
) : RuntimeException("$operation failed: ${cause.message}", cause), RecoverableFailure {
    override val isRecoverable: Boolean = ErrorRecoverability.isErrorRecoverable(cause)
}
