package com.launchdarkly.observability.replay

import com.launchdarkly.observability.network.MatchConfigResponse
import com.launchdarkly.observability.network.SamplingConfigResponse
import com.launchdarkly.observability.network.SpanSamplingConfigResponse
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReplaySessionResponseParsingTest {

    @Test
    fun `initialize session response reads snake_case ids and sampling`() {
        val response = InitializeReplaySessionResponse.fromJson(
            JSONObject(
                """
                {"initializeSession": {
                    "secure_id": "secure-123",
                    "project_id": "42",
                    "sampling": {"spans": [{"name": {"matchValue": "span"}, "samplingRatio": 10}], "logs": null}
                }}
                """.trimIndent()
            )
        )

        assertEquals(
            InitializeReplaySessionResponse(
                initializeSession = InitializeSessionResponse(
                    secureId = "secure-123",
                    projectId = "42",
                    sampling = SamplingConfigResponse(
                        spans = listOf(
                            SpanSamplingConfigResponse(name = MatchConfigResponse(matchValue = "span"), samplingRatio = 10)
                        ),
                        logs = null
                    )
                )
            ),
            response
        )
    }

    @Test
    fun `initialize session response tolerates a null session`() {
        assertNull(InitializeReplaySessionResponse.fromJson(JSONObject("""{"initializeSession": null}""")).initializeSession)
    }

    @Test
    fun `identify and push payload responses read their scalar results`() {
        assertEquals("secure-123", IdentifySessionResponse.fromJson(JSONObject("""{"identifySession": "secure-123"}""")).identifySession)
        assertNull(IdentifySessionResponse.fromJson(JSONObject("""{"identifySession": null}""")).identifySession)
        assertEquals(7, PushPayloadResponse.fromJson(JSONObject("""{"pushPayload": 7}""")).pushPayload)
        assertNull(PushPayloadResponse.fromJson(JSONObject("{}")).pushPayload)
    }
}
