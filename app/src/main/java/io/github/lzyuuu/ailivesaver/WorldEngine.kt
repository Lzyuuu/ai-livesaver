package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.edit
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

internal fun chooseWorldTurn(
    eventCount: Int,
    proactiveMessages: Boolean,
    proactivePosts: Boolean,
    hasBackgroundPair: Boolean = false,
): String = when {
    eventCount % 5 == 4 -> "npc"
    eventCount % 7 == 6 && hasBackgroundPair -> "interaction"
    eventCount % 3 == 2 && proactiveMessages -> "message"
    proactivePosts -> "post"
    proactiveMessages -> "message"
    else -> "none"
}

internal fun budgetUsedForDay(storedDay: String?, currentDay: String, used: Int): Int =
    if (storedDay == currentDay) used else 0

internal fun allowsAutomaticInference(limit: Int, used: Int): Boolean =
    limit == 0 || used < limit

internal fun isDefaultQuietHour(hour: Int): Boolean = hour >= 23 || hour < 8

internal fun shouldReconstructWorld(
    previousOpen: Long,
    now: Long,
    lastEvent: Long,
    interval: Long,
): Boolean = interval > 0 &&
    previousOpen > 0 &&
    now >= previousOpen &&
    now - previousOpen >= interval &&
    (lastEvent <= 0 || (now >= lastEvent && now - lastEvent >= interval))

private val BASE_WORLD_SETTING_KEYS = setOf(
    "enabled",
    "activity",
    "daily_budget",
    "continuous",
    "notifications",
    "notification_preview",
    "do_not_disturb",
)
private val CHARACTER_SETTING_PATTERN =
    Regex("""character_\d+_(messages|posts|notifications)""")

internal fun isBackedUpWorldSetting(key: String): Boolean =
    key in BASE_WORLD_SETTING_KEYS || CHARACTER_SETTING_PATTERN.matches(key)

internal object WorldEngine {
    private const val JOB_ID = 0xA11
    private const val RELATIONSHIP_NOTIFICATION_ID = 0xA13
    private const val CONTINUOUS_CHANNEL = "continuous_world"
    private const val RELATIONSHIP_CHANNEL = "relationship_messages"

    fun eventIntervalMs(context: Context): Long = when (activity(context)) {
        "quiet" -> TimeUnit.HOURS.toMillis(12)
        "active" -> TimeUnit.HOURS.toMillis(3)
        else -> TimeUnit.HOURS.toMillis(6)
    }

    fun schedule(context: Context) {
        if (!isEnabled(context)) return
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val interval = eventIntervalMs(context)
        if (scheduler.getPendingJob(JOB_ID)?.intervalMillis == interval) return
        scheduler.cancel(JOB_ID)
        scheduler.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, WorldJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(interval)
                .setPersisted(false)
                .build(),
        )
    }

    fun onAppOpened(context: Context, onChanged: () -> Unit) {
        if (continuous(context)) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
            startContinuous(context)
        } else {
            schedule(context)
        }
        resumeSocialResponses(context, onChanged)
        val preferences = preferences(context)
        val now = System.currentTimeMillis()
        val previousOpen = preferences.getLong("last_open", 0)
        preferences.edit { putLong("last_open", now) }
        val interval = eventIntervalMs(context)
        if (shouldReconstructWorld(previousOpen, now, preferences.getLong("last_event", 0), interval)) {
            generate(context, reconstructed = true) { if (it) onChanged() }
        }
    }

    fun isEnabled(context: Context) = preferences(context).getBoolean("enabled", true)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean("enabled", enabled) }
        if (enabled) {
            if (continuous(context)) startContinuous(context) else schedule(context)
        }
        else {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
            stopContinuous(context)
        }
    }

    fun activity(context: Context) = preferences(context).getString("activity", "natural")!!

    fun setActivity(context: Context, value: String) {
        preferences(context).edit { putString("activity", value) }
        if (continuous(context)) {
            stopContinuous(context)
            startContinuous(context)
        } else {
            schedule(context)
        }
    }

    fun dailyBudget(context: Context) = preferences(context).getInt("daily_budget", 20)

    fun setDailyBudget(context: Context, budget: Int) {
        preferences(context).edit { putInt("daily_budget", budget.coerceIn(0, 100)) }
    }

    fun budgetUsed(context: Context): Int {
        val preferences = preferences(context)
        return budgetUsedForDay(
            preferences.getString("budget_day", null),
            LocalDate.now().toString(),
            preferences.getInt("budget_used", 0),
        )
    }

    fun budgetExhausted(context: Context): Boolean {
        val budget = dailyBudget(context)
        return budget > 0 && budgetUsed(context) >= budget
    }

    fun taskPaused(context: Context) = preferences(context).getBoolean("task_paused", false)

    fun lastFailure(context: Context) =
        preferences(context).getString("last_failure", "").orEmpty()

    fun resumeTasks(context: Context) {
        preferences(context).edit {
            putBoolean("task_paused", false)
            putInt("failure_count", 0)
            remove("last_failure")
        }
    }

    fun continuous(context: Context) = preferences(context).getBoolean("continuous", false)

    fun setContinuous(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean("continuous", enabled) }
        if (enabled) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
            startContinuous(context)
        } else {
            stopContinuous(context)
            schedule(context)
        }
    }

    fun notificationsEnabled(context: Context) =
        preferences(context).getBoolean("notifications", false)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean("notifications", enabled) }
    }

    fun notificationPreview(context: Context) =
        preferences(context).getBoolean("notification_preview", false)

    fun setNotificationPreview(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean("notification_preview", enabled) }
    }

    fun doNotDisturb(context: Context) =
        preferences(context).getBoolean("do_not_disturb", true)

    fun setDoNotDisturb(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean("do_not_disturb", enabled) }
    }

    fun backupSettings(context: Context): Map<String, *> =
        preferences(context).all.filterKeys(::isBackedUpWorldSetting)

    fun restoreSettings(context: Context, values: Map<String, *>) {
        val preferences = preferences(context)
        preferences.edit(commit = true) {
            preferences.all.keys.filter(::isBackedUpWorldSetting).forEach(::remove)
            values.filterKeys(::isBackedUpWorldSetting).forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                }
            }
        }
        resumeAutomation(context)
    }

    fun resetRuntime(context: Context) {
        val settings = preferences(context).all.filterKeys(BASE_WORLD_SETTING_KEYS::contains)
        preferences(context).edit(commit = true) { clear() }
        restoreSettings(context, settings)
    }

    fun clearAll(context: Context) {
        suspendAutomation(context)
        preferences(context).edit(commit = true) { clear() }
    }

    fun suspendAutomation(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        stopContinuous(context)
    }

    fun resumeAutomation(context: Context) {
        if (!isEnabled(context)) return
        if (continuous(context)) startContinuous(context) else schedule(context)
    }

    fun proactiveMessages(context: Context, characterId: Long) =
        preferences(context).getBoolean("character_${characterId}_messages", true)

    fun setProactiveMessages(context: Context, characterId: Long, enabled: Boolean) {
        preferences(context).edit { putBoolean("character_${characterId}_messages", enabled) }
    }

    fun proactivePosts(context: Context, characterId: Long) =
        preferences(context).getBoolean("character_${characterId}_posts", true)

    fun setProactivePosts(context: Context, characterId: Long, enabled: Boolean) {
        preferences(context).edit { putBoolean("character_${characterId}_posts", enabled) }
    }

    fun characterNotifications(context: Context, character: ResidentCharacter) =
        preferences(context).getBoolean(
            "character_${character.id}_notifications",
            character.attentionTier == "special_focus",
        )

    fun setCharacterNotifications(context: Context, characterId: Long, enabled: Boolean) {
        preferences(context).edit {
            putBoolean("character_${characterId}_notifications", enabled)
        }
    }

    fun generate(
        context: Context,
        reconstructed: Boolean = false,
        callback: (Boolean) -> Unit,
    ): Boolean {
        if (!isEnabled(context) || !hasBudget(context) || taskPaused(context)) return false
        val store = WorldStore(context)
        val character = store.primaryCharacter()
        val config = ProviderStore(context).loadFor(ProviderTask.World)
        if (
            character == null ||
            !config.isValid() ||
            !config.capabilities.supports(ProviderCapability.Structured)
        ) {
            store.close()
            return false
        }
        val eventCount = preferences(context).getInt("event_count", 0)
        val activeCharacters = store.characters(includeDeparted = false)
        val turn = chooseWorldTurn(
            eventCount,
            proactiveMessages(context, character.id),
            proactivePosts(context, character.id),
            hasBackgroundPair = activeCharacters.any { it.id != character.id },
        )
        val npcTurn = turn == "npc"
        val messageTurn = turn == "message"
        val interactionCharacter = if (turn == "interaction") {
            val otherCharacters = activeCharacters.filter { it.id != character.id }
            otherCharacters[(eventCount / 7) % otherCharacters.size]
        } else {
            null
        }
        val npc = if (npcTurn) {
            val profiles = listOf(
                "Noa" to "偶尔参与城市话题与日常闲聊的临时世界成员。",
                "Lin" to "喜欢记录公共空间细节的临时世界成员。",
                "Yuki" to "会在主题讨论中留下简短观察的临时世界成员。",
            )
            val profile = profiles[(eventCount / 5) % profiles.size]
            store.ensureNpc(profile.first, profile.second).also {
                store.retireOtherNpcs(it.id)
            }
        } else {
            null
        }
        val npcForumTarget = if (npc != null && eventCount % 10 == 9) {
            store.posts("forum", "active").firstOrNull()
        } else {
            null
        }
        if (turn == "none") {
            store.close()
            return false
        }
        val system = when {
            npc != null ->
                "You are ${npc.name}, a peripheral member of a fictional social world. " +
                    "Never claim a close bond with the user."
            interactionCharacter != null ->
                "You are ${character.name}. ${character.persona}\n" +
                    "You are having a low-key public exchange with " +
                    "${interactionCharacter.name}, who is another resident character. " +
                    "Do not address the user directly or imply a private bond with them."
            else -> buildString {
                append("You are ${character.name}. ${character.persona}")
                store.worldFacts().take(20).forEach { append("\nShared world fact: ${it.body}") }
                store.characterCognition(character.id).take(20).forEach {
                    append("\nPrivate character knowledge or belief: ${it.body}")
                }
                store.memberWorldContext("user").let {
                    if (it.location.isNotBlank()) append("\nUser-disclosed location: ${it.location}")
                    if (it.timeZone.isNotBlank()) append("\nUser-disclosed time zone: ${it.timeZone}")
                }
                store.memberWorldContext("character:${character.id}").let {
                    if (it.location.isNotBlank()) append("\nYour current location: ${it.location}")
                    if (it.timeZone.isNotBlank()) append("\nYour current time zone: ${it.timeZone}")
                }
            }
        }
        val interactionTarget = interactionCharacter?.let { target ->
            store.posts("moment").firstOrNull { it.authorCharacterId == target.id }
        }
        val prompt = when {
            npcForumTarget != null ->
                "Write one concise public reply to this forum topic as a peripheral participant. " +
                    "Do not dominate or address the user as a close friend.\n\n" +
                    "${npcForumTarget.title}\n${npcForumTarget.body}"
            npc != null ->
                "Write one natural short public post adding background community activity. " +
                    "Keep it under 80 Chinese characters and do not address the user directly."
            interactionCharacter != null -> buildString {
                append("Write one concise public Moment from ${character.name} that naturally responds to ")
                append("${interactionCharacter.name}'s perspective. Do not address the user or ask them to reply. ")
                append("Keep it under 80 Chinese characters.")
                interactionTarget?.let {
                    append("\nTheir recent public Moment was:\n${it.body}")
                }
            }
            messageTurn ->
                "Write one brief proactive private message to ${store.userName()}. " +
                    "Make it relationship-relevant and give the user a natural reason to reply."
            else ->
                "Write one natural short social post for ${store.userName()} to discover later. " +
                    "Keep the relationship central without sounding needy. Stay under 80 Chinese characters."
        }
        ProviderTextClient.completeStructured(config, system, prompt) { result ->
            val created = result.fold(
                onSuccess = { body ->
                    when {
                        npcForumTarget != null -> {
                            val npcName = requireNotNull(npc).name
                            store.addComment(
                                npcForumTarget.id,
                                body,
                                authorName = npcName,
                                authorKind = "npc",
                            )
                            store.addWorldEvent(
                                "npc_forum_reply",
                                body.take(120),
                                npcName,
                                needsResponse = false,
                                sourcePostId = npcForumTarget.id,
                                providerName = config.preset.displayName,
                                modelName = config.model,
                            )
                        }
                        npc != null -> store.createPost(
                                "moment",
                                npc.name,
                                "",
                                body,
                                authorKind = "npc",
                                providerName = config.preset.displayName,
                                modelName = config.model,
                                worldEventKind = if (reconstructed) {
                                    "reconstructed_moment"
                                } else {
                                    "moment"
                                },
                            )
                        interactionCharacter != null -> store.createPost(
                            "moment",
                            character.name,
                            "",
                            body,
                            authorKind = "resident",
                            authorCharacterId = character.id,
                            providerName = config.preset.displayName,
                            modelName = config.model,
                            worldEventKind = if (reconstructed) {
                                "reconstructed_character_interaction"
                            } else {
                                "character_interaction"
                            },
                            eventNeedsResponse = false,
                        )
                        messageTurn -> {
                            store.addMessage(character.id, "assistant", body)
                            store.addWorldEvent(
                                kind = if (reconstructed) "reconstructed_message" else "message",
                                summary = body.take(120),
                                actorName = character.name,
                                needsResponse = true,
                                providerName = config.preset.displayName,
                                modelName = config.model,
                            )
                            notifyRelationship(context, character, body)
                        }
                        else -> store.createPost(
                            "moment",
                            character.name,
                            "",
                            body,
                            authorKind = "resident",
                            authorCharacterId = character.id,
                            providerName = config.preset.displayName,
                            modelName = config.model,
                            worldEventKind = if (reconstructed) "reconstructed_moment" else "moment",
                        )
                    }
                    consumeBudget(context)
                    recordSuccess(context)
                    preferences(context).edit {
                        putInt("event_count", eventCount + 1)
                        putLong("last_event", System.currentTimeMillis())
                    }
                    true
                },
                onFailure = {
                    recordFailure(context, it.message.orEmpty())
                    false
                },
            )
            store.close()
            callback(created)
        }
        return true
    }

    fun respondToPost(
        context: Context,
        postId: Long,
        kind: String,
        body: String,
        audience: String,
        audienceCharacterIds: String,
        queuedJobId: Long? = null,
        callback: (Boolean) -> Unit,
    ): Boolean {
        if (!hasBudget(context) || taskPaused(context)) return false
        val store = WorldStore(context)
        val allowedIds = audienceCharacterIds
            .split(",")
            .mapNotNull(String::toLongOrNull)
            .toSet()
        val character = store.characters(includeDeparted = false).firstOrNull {
            audience != "selected" || it.id in allowedIds
        }
        val config = ProviderStore(context).loadFor(ProviderTask.World)
        if (
            character == null ||
            !config.isValid() ||
            !config.capabilities.supports(ProviderCapability.Structured)
        ) {
            store.close()
            return false
        }
        val prompt = if (kind == "forum") {
            "Reply thoughtfully to this public discussion topic. Stay under 180 Chinese characters."
        } else {
            "Write one warm, natural flat comment on this social update. " +
                "Stay under 80 Chinese characters."
        }
        ProviderTextClient.completeStructured(
            config,
            "You are ${character.name}. ${character.persona}",
            "$prompt\n\nUser post:\n$body",
        ) { result ->
            val created = result.fold(
                onSuccess = { reply ->
                    store.addComment(
                        postId,
                        reply,
                        authorName = character.name,
                        authorKind = "resident",
                        authorCharacterId = character.id,
                    )
                    store.addWorldEvent(
                        "${kind}_response",
                        reply.take(120),
                        character.name,
                        needsResponse = true,
                        sourcePostId = postId,
                        providerName = config.preset.displayName,
                        modelName = config.model,
                    )
                    consumeBudget(context)
                    recordSuccess(context)
                    queuedJobId?.let { queuedId ->
                        WorldStore(context).use { queuedStore ->
                            queuedStore.removeSocialResponse(queuedId)
                        }
                    }
                    true
                },
                onFailure = { failure ->
                    recordFailure(context, failure.message.orEmpty())
                    if (failure is java.io.IOException &&
                        isTransientProviderFailure(failure.message.orEmpty())
                    ) {
                        WorldStore(context).use { queuedStore ->
                            if (queuedJobId == null) {
                                queuedStore.enqueueSocialResponse(
                                    postId,
                                    kind,
                                    body,
                                    audience,
                                    audienceCharacterIds,
                                )
                            } else {
                                queuedStore.incrementSocialResponseAttempts(queuedJobId)
                            }
                        }
                    }
                    false
                },
            )
            store.close()
            callback(created)
        }
        return true
    }

    private fun resumeSocialResponses(context: Context, onChanged: () -> Unit) {
        if (!isOnline(context)) return
        val job = WorldStore(context).use { it.socialResponseJobs(1).firstOrNull() } ?: return
        respondToPost(
            context = context,
            postId = job.postId,
            kind = job.kind,
            body = job.body,
            audience = job.audience,
            audienceCharacterIds = job.audienceCharacterIds,
            queuedJobId = job.id,
        ) { success ->
            if (success) onChanged()
        }
    }

    private fun isOnline(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun rewriteSocialPost(
        context: Context,
        post: SocialPost,
        callback: (Boolean) -> Unit,
    ): Boolean {
        if (post.authorKind == "user") return false
        val config = ProviderStore(context).loadFor(ProviderTask.World)
        if (
            !config.isValid() ||
            !config.capabilities.supports(ProviderCapability.Structured)
        ) return false
        val store = WorldStore(context)
        val character = post.authorCharacterId?.let { id ->
            store.characters().firstOrNull { it.id == id }
        }
        val system = if (character == null) {
            "Rewrite a fictional social post while preserving its author's role and intent."
        } else {
            "You are ${character.name}. ${character.persona}"
        }
        ProviderTextClient.completeStructured(
            config,
            system,
            "Rewrite this post as a distinct alternative. Keep the same facts, do not mention rewriting, " +
                "and stay under 120 Chinese characters.\n\n${post.body}",
        ) { result ->
            val updated = result.fold(
                onSuccess = {
                    store.rewriteAiPost(
                        post.id,
                        it,
                        config.preset.displayName,
                        config.model,
                    )
                    true
                },
                onFailure = { false },
            )
            store.close()
            callback(updated)
        }
        return true
    }

    fun continuousNotification(context: Context): Notification {
        ensureChannels(context)
        return Notification.Builder(context, CONTINUOUS_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.continuous_world_notification))
            .setContentIntent(appIntent(context))
            .setOngoing(true)
            .build()
    }

    private fun notifyRelationship(
        context: Context,
        character: ResidentCharacter,
        body: String,
    ) {
        if (
            !notificationsEnabled(context) ||
            !characterNotifications(context, character) ||
            isQuietNow(context) ||
            (
                Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                )
        ) {
            return
        }
        ensureChannels(context)
        val text = if (notificationPreview(context)) {
            body
        } else {
            context.getString(R.string.private_new_message, character.name)
        }
        context.getSystemService(NotificationManager::class.java).notify(
            RELATIONSHIP_NOTIFICATION_ID,
            Notification.Builder(context, RELATIONSHIP_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(character.name)
                .setContentText(text)
                .setContentIntent(appIntent(context))
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build(),
        )
    }

    private fun isQuietNow(context: Context): Boolean {
        if (!doNotDisturb(context)) return false
        return isDefaultQuietHour(LocalTime.now().hour)
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CONTINUOUS_CHANNEL,
                context.getString(R.string.world_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                RELATIONSHIP_CHANNEL,
                context.getString(R.string.relationship_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private fun appIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun startContinuous(context: Context) {
        if (!isEnabled(context)) return
        context.startForegroundService(Intent(context, ContinuousWorldService::class.java))
    }

    private fun stopContinuous(context: Context) {
        context.stopService(Intent(context, ContinuousWorldService::class.java))
    }

    private fun hasBudget(context: Context): Boolean {
        val budget = dailyBudget(context)
        return allowsAutomaticInference(budget, budgetUsed(context))
    }

    private fun consumeBudget(context: Context) {
        val preferences = preferences(context)
        val today = LocalDate.now().toString()
        preferences.edit {
            putString("budget_day", today)
            putInt("budget_used", budgetUsed(context) + 1)
        }
    }

    private fun recordSuccess(context: Context) {
        preferences(context).edit {
            putInt("failure_count", 0)
            remove("last_failure")
        }
    }

    private fun recordFailure(context: Context, message: String) {
        val preferences = preferences(context)
        val failureCount = preferences.getInt("failure_count", 0) + 1
        val code = Regex("""HTTP (\d{3})""").find(message)?.groupValues?.get(1)
        val summary = code?.let { "Provider HTTP $it" } ?: "Provider 网络连接失败"
        val fatal = code in setOf("401", "403", "404") || failureCount >= 3
        preferences.edit {
            putInt("failure_count", failureCount)
            putString("last_failure", summary)
            if (fatal) putBoolean("task_paused", true)
        }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences("world_engine", Context.MODE_PRIVATE)
}

internal class WorldJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val lastEvent = getSharedPreferences("world_engine", MODE_PRIVATE)
            .getLong("last_event", 0)
        if (System.currentTimeMillis() - lastEvent < WorldEngine.eventIntervalMs(this)) return false
        return WorldEngine.generate(this) { jobFinished(params, false) }
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

internal class ContinuousWorldService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            WorldEngine.generate(this@ContinuousWorldService) {}
            handler.postDelayed(this, WorldEngine.eventIntervalMs(this@ContinuousWorldService))
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(0xA12, WorldEngine.continuousNotification(this))
        handler.postDelayed(tick, WorldEngine.eventIntervalMs(this))
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null
}
