package io.github.lzyuuu.ailivesaver

import android.content.Context

/**
 * Generates and persists one Y post through the configured World Provider.
 *
 * The callback reports true only after a real Provider response is persisted with provenance.
 * Failures never create a local/template post.
 */
internal fun generateYPost(
    context: Context,
    customPrompt: String,
    existingStore: WorldStore? = null,
    callback: (Boolean) -> Unit,
): Boolean {
    val store = existingStore ?: WorldStore(context)
    fun closeOwnedStore() {
        if (existingStore == null) store.close()
    }
    val actor = store.characters(includeDeparted = false).firstOrNull()
        ?: store.primaryCharacter()
    if (actor == null) {
        closeOwnedStore()
        callback(false)
        return false
    }
    val config = ProviderStore(context).loadFor(ProviderTask.World)
    if (!config.supports(ProviderCapability.Structured)) {
        closeOwnedStore()
        callback(false)
        return false
    }
    return runCatching {
        ProviderTextClient.completeStructured(
            config,
            "You are ${actor.name}. ${actor.persona}",
            customPrompt.trim().ifBlank {
                "Write one short public broadcast under 60 Chinese characters."
            },
        ) { result ->
            val created = result.fold(
                onSuccess = { response ->
                    val body = response.text.trim()
                    if (body.isBlank() || body == "...") {
                        false
                    } else {
                        runCatching {
                            store.createPost(
                                kind = Y_POST_KIND,
                                authorName = actor.name,
                                title = "",
                                body = body,
                                authorKind = "resident",
                                authorCharacterId = actor.id,
                                providerName = response.config.preset.displayName,
                                modelName = response.config.model,
                                worldEventKind = Y_POST_KIND,
                            )
                        }.isSuccess
                    }
                },
                onFailure = { false },
            )
            closeOwnedStore()
            callback(created)
        }
        true
    }.getOrElse {
        closeOwnedStore()
        callback(false)
        false
    }
}
