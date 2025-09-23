package com.launchdarkly.observability.network

import com.launchdarkly.observability.replay.SessionInitializationEntity
import kotlinx.serialization.Serializable

/**
 * GraphQL response models for initialize replay session
 */
@Serializable
data class InitializeReplaySessionResponse(
    val initializeSession: InitializeSessionResponse?
)

@Serializable
data class InitializeSessionResponse(
    val secure_id: String? = null,
    val project_id: String? = null,
    val sampling: SamplingConfigResponse? = null
) {
    fun mapToEntity(): SessionInitializationEntity? {
        return SessionInitializationEntity(
            secureId = secure_id,
            projectId = project_id,
            sampling = sampling?.mapToEntity()
        )
    }
}
