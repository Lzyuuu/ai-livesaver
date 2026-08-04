package io.github.lzyuuu.ailivesaver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldStoreContractsTest {
    @Test fun freshSchemaSupportsGroupDraftCandidatesAndCreativeLineage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("world-contract-test.db")
        WorldStore(context).use { store ->
            val groupId = store.savePersistedMessengerGroup(PersistedMessengerGroup(0, "Crew", "prompt", emptyList()))
            val updated = store.savePersistedMessengerGroup(PersistedMessengerGroup(groupId, "Crew 2", "prompt 2", emptyList()))
            assertEquals(groupId, updated)
            store.putBinderDraft(BinderDraft("draft", 1, "{}", 1))
            store.putBinderDraft(BinderDraft("draft", 2, "{x:1}", 2))
            assertEquals(2, store.getBinderDraft("draft")!!.step)
            store.saveBinderCandidate(BinderCandidate("candidate", "draft", "{}", false))
            store.confirmBinderCandidate("candidate")
            assertNotNull(store.getConfirmedBinderCandidate("draft"))
            val asset = CreativeAsset(0, "content://asset/1", "image", "local", "p", null, "", null, null, "ready", "", 3)
            val id = store.saveCreativeAsset(asset)
            assertTrue(store.queryCreativeAssets().any { it.id == id })
            assertEquals(id, store.getCreativeAsset(id)!!.id)
        }
    }
}
