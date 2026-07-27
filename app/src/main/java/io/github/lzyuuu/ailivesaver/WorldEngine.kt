package io.github.lzyuuu.ailivesaver

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import java.util.concurrent.TimeUnit

internal object WorldEngine {
    private const val JOB_ID = 0xA11
    private val eventIntervalMs = TimeUnit.HOURS.toMillis(6)

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, WorldJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(eventIntervalMs)
                .build(),
        )
    }

    fun onAppOpened(context: Context, onChanged: () -> Unit) {
        schedule(context)
        val preferences = preferences(context)
        val now = System.currentTimeMillis()
        val previousOpen = preferences.getLong("last_open", 0)
        preferences.edit { putLong("last_open", now) }
        if (previousOpen > 0 && now - previousOpen >= eventIntervalMs) {
            generate(context) { if (it) onChanged() }
        }
    }

    fun isEnabled(context: Context) = preferences(context).getBoolean("enabled", true)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean("enabled", enabled) }
        if (enabled) schedule(context)
        else context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }

    fun dailyBudget(context: Context) = preferences(context).getInt("daily_budget", 12)

    fun setDailyBudget(context: Context, budget: Int) {
        preferences(context).edit { putInt("daily_budget", budget.coerceIn(1, 48)) }
    }

    fun generate(context: Context, callback: (Boolean) -> Unit): Boolean {
        if (!isEnabled(context) || !consumeBudget(context, dryRun = true)) return false
        val store = WorldStore(context)
        val character = store.primaryCharacter() ?: return false
        val config = ProviderStore(context).load()
        if (!config.isValid()) return false
        val eventCount = preferences(context).getInt("event_count", 0)
        val npcTurn = eventCount % 4 == 3
        val author = if (npcTurn) "Noa · NPC" else character.name
        val system = if (npcTurn) {
            "You are a peripheral member of a fictional social world. Never claim a close bond."
        } else {
            "You are ${character.name}. ${character.persona}"
        }
        val prompt = if (npcTurn) {
            "Write one natural short social post that adds background community activity. " +
                "Keep it under 80 Chinese characters and do not address the user directly."
        } else {
            "Write one natural short social post for ${store.userName()} to discover later. " +
                "Keep the relationship central without sounding needy. Stay under 80 Chinese characters."
        }
        ProviderTextClient.complete(config, system, prompt) { result ->
            val created = result.fold(
                onSuccess = {
                    store.createPost("moment", author, "", it)
                    consumeBudget(context, dryRun = false)
                    preferences(context).edit {
                        putInt("event_count", eventCount + 1)
                        putLong("last_event", System.currentTimeMillis())
                    }
                    true
                },
                onFailure = { false },
            )
            store.close()
            callback(created)
        }
        return true
    }

    private fun consumeBudget(context: Context, dryRun: Boolean): Boolean {
        val preferences = preferences(context)
        val today = LocalDate.now().toString()
        val storedDay = preferences.getString("budget_day", null)
        val used = if (storedDay == today) preferences.getInt("budget_used", 0) else 0
        if (used >= dailyBudget(context)) return false
        if (!dryRun) {
            preferences.edit {
                putString("budget_day", today)
                putInt("budget_used", used + 1)
            }
        }
        return true
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences("world_engine", Context.MODE_PRIVATE)
}

internal class WorldJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val lastEvent = getSharedPreferences("world_engine", MODE_PRIVATE)
            .getLong("last_event", 0)
        if (System.currentTimeMillis() - lastEvent < TimeUnit.HOURS.toMillis(6)) return false
        return WorldEngine.generate(this) {
            jobFinished(params, false)
        }
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
