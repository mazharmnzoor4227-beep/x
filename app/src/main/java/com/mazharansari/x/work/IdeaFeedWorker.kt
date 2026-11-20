package com.mazharansari.x.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mazharansari.x.AxionApp
import com.mazharansari.x.MainActivity
import com.mazharansari.x.agent.Agent
import com.mazharansari.x.agent.AgentSink
import com.mazharansari.x.agent.Intent
import com.mazharansari.x.agent.Profile
import com.mazharansari.x.data.Artifact
import com.mazharansari.x.data.IdeaFeed
import com.mazharansari.x.data.Step
import com.mazharansari.x.llm.UserItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Idea store daily refresh (2026-11-20): generates one small batch of
 * fresh ideas (at most [IdeaFeed.BATCH_SIZE] a day) and files them in the
 * Ideas tab's store section.
 *
 * It reuses the existing agent path — the same model rotation as a
 * scheduled run ([ScheduledRunWorker.headlessAttempts]) and the same
 * [Agent.run] — and runs on the existing WorkManager machinery: a
 * 24-hour periodic job armed by [ScheduleEngine.armIdeaFeed] (fires
 * without the user opening the app) plus a one-time job for the manual
 * "New ideas" refresh from the Ideas screen. No second scheduler exists.
 *
 * Honesty rules, enforced here and in [IdeaFeed]:
 * - The agent run gets NO tools at all, and [Intent.CHAT] — it only
 *   generates text. In particular the save_idea tool is NOT offered, so a
 *   generated idea can never land in the user's own list by accident; the
 *   worker parses the answer and files it as a store idea itself.
 * - Only ideas that pass the quality bar and are not duplicates (of the
 *   user's ideas, of older store ideas, or of each other) are saved.
 * - Every failure path records the reason in the store
 *   ([com.mazharansari.x.data.IdeasStore.recordRefreshFailure]) and keeps
 *   yesterday's ideas — a failed refresh is visible on the screen, never
 *   silent, and never presented as success. No placeholder ideas are ever
 *   invented: a failed day simply adds nothing.
 */
class IdeaFeedWorker(appCtx: Context, params: WorkerParameters) : CoroutineWorker(appCtx, params) {

    override suspend fun doWork(): Result {
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val app = applicationContext as? AxionApp ?: return Result.failure()
        val g = app.graph
        val store = g.ideas
        store.recordRefreshStarted(System.currentTimeMillis())

        val attempts = ScheduledRunWorker.headlessAttempts(g)
        if (attempts.isEmpty()) {
            store.recordRefreshFailure(NO_MODEL_ERROR, System.currentTimeMillis())
            return Result.failure()
        }

        val settings = g.settings.state.value
        val existing = store.ideas.value
        val prompt = IdeaFeed.buildPrompt(existing.map { it.text })
        val text = StringBuilder()
        val sink = object : AgentSink {
            override fun text(delta: String) { synchronized(text) { text.append(delta) } }
            override fun reasoning(delta: String) {}
            override fun textToThoughts() {}
            override fun step(step: Step) {}
            override fun artifact(a: Artifact) {}
            override fun model(label: String) {}
        }
        val answer: String
        try {
            val agent = Agent(
                g.workspace, g.skills,
                baseTools = emptyList(),
                memory = g.memory, conversations = g.chats,
                appCtx = applicationContext,
            )
            answer = withTimeout(RUN_BUDGET_MS) {
                agent.run(
                    emptyList(), UserItem(prompt),
                    attempts = { attempts }, sink = sink,
                    profile = Profile(settings.assistantName.ifBlank { "X" }, settings.userName, settings.replyLanguage, settings.instructions),
                    intent = Intent.CHAT,
                )
            }
        } catch (e: TimeoutCancellationException) {
            store.recordRefreshFailure(TIMEOUT_ERROR, System.currentTimeMillis())
            return Result.failure()
        } catch (e: CancellationException) {
            // WorkManager stopped the worker: let the cancellation stand
            // (the periodic job tries again tomorrow; the attempt already
            // shows as "getting new ideas" until then). No fake outcome.
            throw e
        } catch (e: Throwable) {
            store.recordRefreshFailure(e.message?.take(200) ?: GENERATION_ERROR, System.currentTimeMillis())
            return Result.failure()
        }

        val raw = answer.ifBlank { synchronized(text) { text.toString() } }
        if (raw.isBlank()) {
            store.recordRefreshFailure(EMPTY_ERROR, System.currentTimeMillis())
            return Result.failure()
        }
        val fresh = IdeaFeed.selectNewIdeas(raw, existing.map { it.text })
        if (fresh.isEmpty()) {
            store.recordRefreshFailure(ALL_DUPLICATES_ERROR, System.currentTimeMillis())
            return Result.failure()
        }
        val added = store.addStoreIdeas(fresh, System.currentTimeMillis())
        if (added == 0) {
            store.recordRefreshFailure(ALL_DUPLICATES_ERROR, System.currentTimeMillis())
            return Result.failure()
        }
        store.recordRefreshSuccess(added, System.currentTimeMillis())
        // A daily batch is worth a quiet note; a manual refresh is not —
        // the user is looking at the screen already.
        if (!manual) runCatching { notify(applicationContext, added) }
        return Result.success()
    }

    private fun notify(ctx: Context, added: Int) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CH, "Idea store", NotificationManager.IMPORTANCE_DEFAULT))
        val open = android.content.Intent(ctx, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, NOTIFY_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = "$added new idea${if (added == 1) "" else "s"} in the Idea store — tap to see them."
        nm.notify(NOTIFY_ID, NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("New ideas in the Idea store")
            .setContentText(body)
            .setContentIntent(pi).setAutoCancel(true).build())
    }

    companion object {
        /** Unique periodic work name for the daily refresh (see ScheduleEngine). */
        const val WORK_NAME_DAILY = "x-idea-feed-daily"

        /** Unique one-time work name for a manual refresh from the Ideas screen. */
        const val WORK_NAME_NOW = "x-idea-feed-now"

        /** Input-data flag: true when the user tapped refresh (no notification). */
        const val KEY_MANUAL = "manual"

        /** Five short ideas need far less than a scheduled run's 9 minutes. */
        const val RUN_BUDGET_MS = 3L * 60L * 1000L

        const val CH = "idea_store"
        const val NOTIFY_ID = 0x1DEA

        /** No model at all on the device (no key, no local model, no free fallback). */
        const val NO_MODEL_ERROR = "No AI model is available on this phone right now, so no new ideas could be generated. Add a key in Connectors or install a local model, then tap New ideas."

        /** The generation run hit its time budget. */
        const val TIMEOUT_ERROR = "Generating ideas took too long and stopped — yesterday's ideas are still here."

        /** The agent answered, but with no usable text. */
        const val EMPTY_ERROR = "X came back with no ideas this time — yesterday's ideas are still here."

        /** The batch held nothing new: every line was a repeat or too vague. */
        const val ALL_DUPLICATES_ERROR = "The ideas X came back with were all repeats or too vague to use, so nothing new was added."

        /** A generation error with no message of its own. */
        const val GENERATION_ERROR = "Ideas could not be generated — yesterday's ideas are still here."
    }
}
