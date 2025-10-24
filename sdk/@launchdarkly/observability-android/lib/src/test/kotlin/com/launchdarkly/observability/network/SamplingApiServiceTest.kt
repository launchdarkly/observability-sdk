package com.launchdarkly.observability.network

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertNotNull

class SamplingApiServiceTest {

    private val mockGraphqlClient = mockk<GraphQLClient>()
    private val service = SamplingApiService(mockGraphqlClient)

    @Nested
    @DisplayName("Get Sampling Config Tests")
    inner class GetSamplingConfigTests {

        @Test
        fun `should return sampling config when network response is successful with data`() = runTest {
            val organizationId = "test-org"
            val samplingResponse = SamplingResponse(
                sampling = SamplingConfigResponse(
                    spans = listOf(
                        SpanSamplingConfigResponse(
                            name = MatchConfigResponse(regexValue = ".*http.*"),
                            samplingRatio = 25
                        )
                    ),
                    logs = listOf(
                        LogSamplingConfigResponse(
                            message = MatchConfigResponse(matchValue = "error"),
                            severityText = MatchConfigResponse(regexValue = "ERROR|WARN"),
                            attributes = listOf(
                                AttributeMatchConfigResponse(
                                    key = MatchConfigResponse(matchValue = "service"),
                                    attribute = MatchConfigResponse(matchValue = "api")
                                )
                            ),
                            samplingRatio = 75
                        )
                    )
                )
            )
            val graphqlResponse = GraphQLResponse(
                data = samplingResponse,
                errors = null
            )

            coEvery {
                mockGraphqlClient.execute(
                    "graphql/GetSamplingConfigQuery.graphql",
                    mapOf("organization_verbose_id" to JsonPrimitive(organizationId)),
                    SamplingResponse.serializer()
                )
            } returns graphqlResponse

            val result = service.getSamplingConfig(organizationId)

            assertNotNull(result)
            assertEquals(samplingResponse.mapToEntity(), result)

            coVerify(exactly = 1) {
                mockGraphqlClient.execute(
                    "graphql/GetSamplingConfigQuery.graphql",
                    mapOf("organization_verbose_id" to JsonPrimitive(organizationId)),
                    SamplingResponse.serializer()
                )
            }
        }

        @Test
        fun `should return null when network response has errors`() = runTest {
            val organizationId = "test-org"
            val graphqlResponse = GraphQLResponse<SamplingResponse>(
                data = null,
                errors = listOf(
                    GraphQLError(message = "Organization not found")
                )
            )

            coEvery {
                mockGraphqlClient.execute<SamplingResponse>(any(), any(), any())
            } returns graphqlResponse

            val result = service.getSamplingConfig(organizationId)

            assertNull(result)
        }

        @Test
        fun `should return null when network response has no data and no errors`() = runTest {
            val organizationId = "test-org"
            val graphqlResponse = GraphQLResponse<SamplingResponse>(
                data = null,
                errors = emptyList()
            )

            coEvery {
                mockGraphqlClient.execute<SamplingResponse>(any(), any(), any())
            } returns graphqlResponse

            val result = service.getSamplingConfig(organizationId)

            assertNull(result)
        }

        @Test
        fun `should return null when sampling config data is null`() = runTest {
            val organizationId = "test-org"
            val samplingResponse = SamplingResponse(sampling = null)
            val graphqlResponse = GraphQLResponse(
                data = samplingResponse,
                errors = null
            )

            coEvery {
                mockGraphqlClient.execute<SamplingResponse>(any(), any(), any())
            } returns graphqlResponse

            val result = service.getSamplingConfig(organizationId)

            assertNull(result)
        }

        @Test
        fun `should return null when exception occurs`() = runTest {
            val organizationId = "test-org"
            val exception = RuntimeException("Network timeout")

            coEvery {
                mockGraphqlClient.execute<SamplingResponse>(any(), any(), any())
            } throws exception

            val result = service.getSamplingConfig(organizationId)

            assertNull(result)
        }
    }
}
