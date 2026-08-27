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
    actorOverride: ResidentCharacter? = null,
    callback: (Boolean) -> Unit,
): Boolean {
    val store = existingStore ?: WorldStore(context)
    fun closeOwnedStore() {
        if (existingStore == null) store.close()
    }
    val actor = actorOverride
        ?: store.characters(includeDeparted = false).firstOrNull()
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
            ProviderStore(context).loadDefaultGeneration(),
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
                            // 发布执行前核对 Y 平台授权（spec-v451 §1）：抽屉关闭即静默不发。
                            check(postAllowed(store.chatControls(actor.id), Y_POST_KIND)) {
                                "Y posting authorization is revoked"
                            }
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
