package io.github.lzyuuu.ailivesaver

import android.content.Context

internal data class BinderGeneration(
    val candidates: List<BinderCandidatePayload>,
    val providerName: String,
    val modelName: String,
)

/** Production Binder generation and confirmation boundary shared by Compose and device tests. */
internal object BinderOrchestrator {
    fun generate(
        context: Context,
        draftId: String,
        answers: BinderAnswers,
        callback: (Result<BinderGeneration>) -> Unit,
    ) {
        val appContext = context.applicationContext
        val providerStore = ProviderStore(appContext)
        val config = providerStore.loadFor(ProviderTask.World)
        val systemPrompt =
            "Generate 2 to 6 distinct fictional companion candidates. " +
                "The body string must be JSON matching " +
                "{candidates:[{name,persona,relationship,reasons:[string]}]}. " +
                "The candidates array must contain at least two items."

        fun request(candidateFormatAttempt: Int) {
            ProviderTextClient.completeStructured(
                config,
                providerStore.loadDefaultGeneration(),
                systemPrompt,
                answers.toJson(),
            ) { providerResult ->
                val generation = providerResult.mapCatching { response ->
                    val candidates = validateBinderCandidateList(response.text)
                    require(candidates.size >= 2) {
                        "Provider must return at least two Binder candidates"
                    }
                    WorldStore(appContext).use { store ->
                        store.putBinderDraft(
                            BinderDraft(
                                id = draftId,
                                step = 5,
                                payload = answers.toJson(),
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                    BinderGeneration(
                        candidates = candidates,
                        providerName = response.config.preset.displayName,
                        modelName = response.config.model,
                    )
                }
                val failure = generation.exceptionOrNull()
                if (failure is org.json.JSONException && candidateFormatAttempt == 0) {
                    // The outer structured envelope can be valid while its body contains
                    // truncated candidate JSON. Retry the shipped generation once, but do
                    // not retry valid one-candidate responses or persist an invalid draft.
                    request(candidateFormatAttempt + 1)
                } else {
                    callback(generation)
                }
            }
        }
        request(candidateFormatAttempt = 0)
    }

    fun confirm(
        context: Context,
        draftId: String,
        candidateIndex: Int,
        candidate: BinderCandidatePayload,
    ): Long {
        require(candidateIndex >= 0) { "Invalid Binder candidate index" }
        return WorldStore(context.applicationContext).use { store ->
            require(store.getBinderDraft(draftId)?.step == 5) {
                "Binder candidates have not been generated for this draft"
            }
            store.confirmBinderCandidateIdempotently(
                id = "$draftId-$candidateIndex",
                draftId = draftId,
                candidate = candidate,
            )
        }
    }
}
