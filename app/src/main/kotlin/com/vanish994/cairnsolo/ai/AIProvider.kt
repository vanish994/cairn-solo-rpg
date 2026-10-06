package com.vanish994.cairnsolo.ai

import com.vanish994.cairnsolo.game.MJContext

/** Narrative-only provider. It must never mutate GameState or resolve mechanics. */
interface AIProvider {
    suspend fun narrate(request: WardenRequest): Result<WardenResponse>
}

data class WardenRequest(
    val context: MJContext,
    val playerInput: String,
    val rulesEngineResult: String
)

data class WardenResponse(
    val narrative: String,
    val suggestedActions: List<String>,
    val intent: String?
)

class MockAIProvider : AIProvider {
    override suspend fun narrate(request: WardenRequest): Result<WardenResponse> =
        Result.success(
            WardenResponse(
                narrative = request.context.sceneDescription,
                suggestedActions = request.context.exits.take(3),
                intent = null
            )
        )
}

object AIProviderFactory {
    fun create(apiKey: String): AIProvider =
        if (apiKey.isBlank()) MockAIProvider() else GeminiFlashLiteProvider(apiKey)
}
