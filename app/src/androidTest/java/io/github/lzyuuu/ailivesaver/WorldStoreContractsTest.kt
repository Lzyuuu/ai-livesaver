package io.github.lzyuuu.ailivesaver

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldStoreContractsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "world-contract-${UUID.randomUUID()}.db"

    @After
    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun freshSchemaSupportsGroupMembershipAndRollback() {
        WorldStore(context, databaseName).use { store ->
            val first = createCharacter(store, "One")
            val second = createCharacter(store, "Two")
            val groupId = store.savePersistedMessengerGroup(PersistedMessengerGroup(0, "Crew", "prompt", listOf(first, first, second)))
            assertEquals(listOf(first, second), store.loadPersistedMessengerGroup(groupId)!!.memberIds)
            store.savePersistedMessengerGroup(PersistedMessengerGroup(groupId, "Crew 2", "prompt 2", listOf(second)))
            assertEquals(listOf(second), store.loadPersistedMessengerGroup(groupId)!!.memberIds)
            try {
                store.savePersistedMessengerGroup(PersistedMessengerGroup(groupId, "Broken", "", listOf(999999)))
            } catch (_: Exception) {
                // FK failure must roll back both row and membership replacement.
            }
            assertEquals("Crew 2", store.loadPersistedMessengerGroup(groupId)!!.name)
            assertEquals(listOf(second), store.loadPersistedMessengerGroup(groupId)!!.memberIds)
        }
    }

    @Test
    fun v23UpgradePreservesExistingDataAndCreatesNewTables() {
        WorldStore(context, databaseName).use { store ->
            val characterId = createCharacter(store, "Legacy")
            store.writableDatabase.execSQL("PRAGMA user_version = 23")
            assertTrue(characterId > 0)
        }
        WorldStore(context, databaseName).use { store ->
            assertEquals(WORLD_DATABASE_VERSION, store.writableDatabase.version)
            assertEquals("Legacy", store.writableDatabase.query("characters", arrayOf("name"), "id=?", arrayOf("1"), null, null, null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            })
            assertNotNull(store.writableDatabase.query("messenger_groups", null, null, null, null, null, null))
        }
    }

    @Test
    fun v24UpgradePreservesWorldDataAndCreatesStoreTable() {
        WorldStore(context, databaseName).use { store ->
            val characterId = createCharacter(store, "LegacyStore")
            store.writableDatabase.execSQL("PRAGMA user_version = 24")
            assertTrue(characterId > 0)
        }
        WorldStore(context, databaseName).use { store ->
            assertEquals(WORLD_DATABASE_VERSION, store.writableDatabase.version)
            assertEquals("LegacyStore", store.writableDatabase.query("characters", arrayOf("name"), "id=?", arrayOf("1"), null, null, null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            })
            assertNotNull(store.writableDatabase.query("app_install", null, null, null, null, null, null))
            // 商店自装决策：升级后默认无预装行。
            assertEquals(0, store.countAppInstalls())
        }
    }

    @Test
    fun storeInstallSurvivesReopenAndIsClearedWhenDatabaseDeleted() {
        WorldStore(context, databaseName).use { store ->
            store.saveAppInstall(PersistedAppInstall("y", InstallStatus.INSTALLED, 10L, true, 1, "1.0", 11L))
            assertEquals(1, store.countAppInstalls())
        }
        WorldStore(context, databaseName).use { store ->
            val y = store.loadAppInstall("y")!!
            assertEquals(InstallStatus.INSTALLED, y.status)
            assertTrue(y.onHome)
            assertEquals(1, y.homeOrder)
        }
        assertTrue(context.deleteDatabase(databaseName))
        WorldStore(context, databaseName).use { store ->
            assertEquals(0, store.countAppInstalls())
            assertNull(store.loadAppInstall("y"))
        }
    }

    @Test
    fun storeInstallStatePersistsHomeOrderAndDelete() {
        WorldStore(context, databaseName).use { store ->
            store.saveAppInstall(PersistedAppInstall("y", InstallStatus.INSTALLED, 10L, true, 2, "1.0", 11L))
            store.saveAppInstall(PersistedAppInstall("rebbit", InstallStatus.INSTALLING, null, false, 0, "1.0", 12L))
            assertEquals(2, store.countAppInstalls())

            val y = store.loadAppInstall("y")!!
            assertEquals(InstallStatus.INSTALLED, y.status)
            assertEquals(10L, y.installedAt)
            assertTrue(y.onHome)
            assertEquals(2, y.homeOrder)

            val home = store.loadHomeApps()
            assertEquals(1, home.size)
            assertEquals("y", home[0].appId)

            assertTrue(store.deleteAppInstall("y"))
            assertFalse(store.deleteAppInstall("y"))
            assertNull(store.loadAppInstall("y"))
            assertEquals(1, store.countAppInstalls())
        }
    }

    @Test
    fun binderConfirmationIsUniqueAndIdempotent() {
        WorldStore(context, databaseName).use { store ->
            store.putBinderDraft(BinderDraft("draft", 1, "{}", 1))
            store.saveBinderCandidate(BinderCandidate("a", "draft", "a", false))
            store.saveBinderCandidate(BinderCandidate("b", "draft", "b", false))
            store.confirmBinderCandidate("a")
            store.confirmBinderCandidate("a")
            assertEquals("a", store.getConfirmedBinderCandidate("draft")!!.id)
            store.confirmBinderCandidate("b")
            assertEquals("b", store.getConfirmedBinderCandidate("draft")!!.id)
        }
    }

    @Test
    fun creativeSupportsLineageStatusFilterDeleteNullableAndIdempotentUri() {
        WorldStore(context, databaseName).use { store ->
            val source = store.saveCreativeAsset(CreativeAsset(0, "uri:source", "image", "local", "p", null, "", null, null, "failed", "network", 1))
            val target = store.saveCreativeAsset(CreativeAsset(0, "uri:target", "image", "local", "p2", null, "", source, null, "ready", "", 2))
            assertEquals(target, store.saveCreativeAsset(CreativeAsset(0, "uri:target", "image", "local", "changed", null, "", source, null, "ready", "", 3)))
            assertEquals(source, store.getCreativeAsset(target)!!.sourceId)
            store.updateCreativeAssetStatus(source, "ready")
            assertEquals(2, store.queryCreativeAssets(status = "ready").size)
            assertTrue(store.deleteCreativeAsset(source))
            assertFalse(store.deleteCreativeAsset(source))
            assertNull(store.getCreativeAsset(source))
        }
    }

    @Test
    fun v27UpgradeMigratesExistingFactsIntoDefaultSharedBook() {
        WorldStore(context, databaseName).use { store ->
            // 把 world_facts 塑造成 v27 形态（无 book/group 列），并塞入既有条目。
            store.writableDatabase.execSQL("ALTER TABLE world_facts RENAME TO world_facts_v28")
            store.writableDatabase.execSQL(
                """
                CREATE TABLE world_facts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    body TEXT NOT NULL,
                    pinned INTEGER NOT NULL DEFAULT 0,
                    keywords TEXT NOT NULL DEFAULT '',
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            store.writableDatabase.execSQL(
                "INSERT INTO world_facts (body, pinned, keywords, enabled, created_at) VALUES ('老车站通往废弃线路', 0, '老车站', 1, 1)",
            )
            store.writableDatabase.execSQL("PRAGMA user_version = 27")
        }
        WorldStore(context, databaseName).use { store ->
            assertEquals(WORLD_DATABASE_VERSION, store.writableDatabase.version)
            val fact = store.worldFacts().single()
            assertEquals("老车站通往废弃线路", fact.body)
            assertEquals("老车站", fact.keywords)
            assertTrue(fact.enabled)
            // 迁移默认分组：既有条目落入默认书册「已分享」、未分组。
            assertEquals(LOREBOOK_SHARED_BOOK, fact.book)
            assertEquals("", fact.group)
            // 迁移幂等：压回 27 后重放升级仍安全。
            store.writableDatabase.execSQL("PRAGMA user_version = 27")
        }
        WorldStore(context, databaseName).use { store ->
            assertEquals(WORLD_DATABASE_VERSION, store.writableDatabase.version)
            val fact = store.worldFacts().single()
            assertEquals(LOREBOOK_SHARED_BOOK, fact.book)
            assertEquals("", fact.group)
            assertEquals(listOf(LOREBOOK_SHARED_BOOK), store.lorebookBooks())
        }
    }

    @Test
    fun lorebookBooksAndGroupsSupportCrudFlow() {
        WorldStore(context, databaseName).use { store ->
            store.addWorldFact("共享细雨", book = LOREBOOK_SHARED_BOOK)
            store.addWorldFact("根知识一", book = LOREBOOK_ROOT_BOOK, group = "设定")
            store.addWorldFact("根知识二", book = LOREBOOK_ROOT_BOOK)
            store.addWorldFact("地点旧站", book = LOREBOOK_SHARED_BOOK, group = "地点")
            // 读取：书册与书内分组均去重有序。
            assertEquals(listOf(LOREBOOK_SHARED_BOOK, LOREBOOK_ROOT_BOOK), store.lorebookBooks())
            assertEquals(listOf("设定"), store.lorebookGroups(LOREBOOK_ROOT_BOOK))
            assertEquals(listOf("地点"), store.lorebookGroups(LOREBOOK_SHARED_BOOK))
            // 更新：条目移动书册、重命名分组。
            val rootOne = store.worldFacts().first { it.body == "根知识一" }
            store.setWorldFactGroup(rootOne.id, "人物")
            assertEquals(listOf("人物"), store.lorebookGroups(LOREBOOK_ROOT_BOOK))
            store.setWorldFactBook(rootOne.id, LOREBOOK_SHARED_BOOK)
            assertEquals(LOREBOOK_SHARED_BOOK, store.worldFacts().first { it.id == rootOne.id }.book)
            // 重命名书册（合并条目）。
            assertEquals(1, store.renameLorebookBook(LOREBOOK_ROOT_BOOK, "根之书"))
            assertEquals(listOf(LOREBOOK_SHARED_BOOK, "根之书"), store.lorebookBooks())
            // 删除书册：条目迁回默认书册，不丢数据。
            assertEquals(1, store.deleteLorebookBook("根之书"))
            assertEquals(listOf(LOREBOOK_SHARED_BOOK), store.lorebookBooks())
            assertEquals(4, store.worldFacts().size)
            assertTrue(store.worldFacts().all { it.book == LOREBOOK_SHARED_BOOK })
            // 注入语义不因分组改变：同批条目全部参与选择。
            assertEquals(4, selectLorebookFacts(store.worldFacts(), "对话").size)
        }
    }

    private fun createCharacter(store: WorldStore, name: String): Long = store.writableDatabase.insertOrThrow("characters", null, ContentValues().apply {
        put("name", name)
        put("persona", "persona")
        put("created_at", 1)
    })
}
