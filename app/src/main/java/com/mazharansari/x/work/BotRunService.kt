package com.mazharansari.x.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.mazharansari.x.AxionApp
import com.mazharansari.x.MainActivity
import java.util.concurrent.ConcurrentHashMap

/**
 * One bot run's identity, keyed by run id — never by a screen or a ViewModel.
 * [ChatViewModel] records the run here the moment it registers the run in
 * [RunRegistry]; the map lives in the app process (a static), so leaving a
 * bot chat for another chat — or the app being backgrounded — never loses
 * which runs are bot runs. A run that is gone from [RunRegistry] is gone
 * from here too (see [pruneDead]); process death wipes both, and the
 * durable [com.mazharansari.x.data.TaskRunStore] record is what surfaces
 * that run as interrupted on the next start.
 */
data class BotRunInfo(
    val runId: String,
    val botId: String?,
    val botName: String,
    val conversationId: String,
    val title: String,
    val startedAt: Long,
)

/** What [com.mazharansari.x.ui.chat.BotRunningBanner] shows for one live bot run. */
data class BotRunState(
    val runId: String,
    val botId: String?,
    val botName: String,
    val title: String,
    val conversationId: String,
    val startedAt: Long,
    val workerCount: Int = 0,
)

/**
 * Background execution for bot runs (the Bots section).
 *
 * A bot run is an interactive agent run, not a deferrable job: it streams
 * tokens, waits on the user for approvals, and can run far longer than
 * WorkManager's ~10-minute worker budget — so WorkManager
 * ([ScheduledRunWorker], which stays the route for scheduled headless runs
 * only) is the wrong tool here. This is a foreground service instead, the
 * same pattern as [WorkService] (notification + partial wake lock), but
 * bot-scoped: it starts when a bot run starts, shows that bot's name and
 * task in the shade with a per-run Stop action, and stops when the last bot
 * run settles.
 *
 * Lifecycle, Android 14+ rules: declared in the manifest with
 * foregroundServiceType="dataSync" and the FOREGROUND_SERVICE_DATA_SYNC
 * permission (both already declared for [WorkService]); the service is
 * started with startForegroundService while the run starts in the
 * foreground, posts its notification at once, and returns START_STICKY —
 * if Android kills the service itself while a run is in flight, it
 * restarts with a null intent and re-attaches to the runs still live in
 * [RunRegistry] (see [refreshForeground]); if the whole process dies, the
 * runs died with it and the service stops instead of lingering. Force-stop
 * and reboot end everything, like every app (Android rule); the run's
 * TaskRuns record is what makes it resumable afterwards.
 */
class BotRunService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_RUN -> {
                val runId = intent.getStringExtra(EXTRA_RUN_ID).orEmpty()
                if (runId.isNotEmpty()) cancelRun(runId)
                return refreshForeground()
            }
            ACTION_STOP_ALL -> {
                runCatching { (application as AxionApp).graph.runs.cancelAll() }
                infos.clear()
                releaseWake()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> return refreshForeground()
        }
    }

    override fun onDestroy() {
        releaseWake()
        super.onDestroy()
    }

    /**
     * Cancel one run by its run id: the registry job is the run itself, so
     * cancelling it settles the run (partial reply kept, TaskRuns record
     * finished as interrupted) exactly as if Stop had been tapped in chat.
     * Its Linux shell jobs go with it.
     */
    private fun cancelRun(runId: String) {
        runCatching {
            val g = (application as AxionApp).graph
            g.runs.active.value.firstOrNull { it.id == runId }?.job?.cancel()
            g.jobs.killForRun(runId)
        }
        noteRunFinished(runId)
    }

    /**
     * Re-attach to the bot runs still live in [RunRegistry] (the START_STICKY
     * restart path, and every refresh): stale entries are pruned, the
     * notification is re-posted with the live runs, and when nothing is
     * left the service stops instead of idling as a foreground service.
     */
    private fun refreshForeground(): Int {
        prune()
        val live = snapshot()
        if (live.isEmpty()) {
            releaseWake()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val n = buildNotification(live)
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION_ID, n)
        acquireWake()
        return START_STICKY
    }

    /** Drop bot-run entries whose run is no longer in [RunRegistry]. */
    private fun prune() {
        val activeIds = activeRunIds()
        val now = System.currentTimeMillis()
        val dead = snapshot().filter { it.runId !in activeIds && now - it.startedAt >= PRUNE_GRACE_MS }
        dead.forEach { infos.remove(it.runId) }
    }

    private fun activeRunIds(): Set<String> =
        runCatching { (application as AxionApp).graph.runs.active.value.map { it.id }.toSet() }.getOrDefault(emptySet())

    private fun workerCount(conversationId: String): Int =
        runCatching { (application as AxionApp).graph.bots.liveWorkerCount(conversationId) }.getOrDefault(0)

    private fun acquireWake() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "axion:bot-run").apply {
            setReferenceCounted(false)
            acquire(3L * 60 * 60 * 1000)
        }
    }

    private fun releaseWake() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun buildNotification(live: List<BotRunInfo>): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Bot runs", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val counts = live.associate { it.conversationId to workerCount(it.conversationId) }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else @Suppress("DEPRECATION") Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(notificationTitle(live))
            .setContentText(notificationText(live, counts).replace('\n', ' ').take(120))
            .setStyle(Notification.BigTextStyle().bigText(notificationText(live, counts)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
        if (live.size == 1) {
            val stopOne = PendingIntent.getService(
                this, live[0].runId.hashCode(),
                Intent(this, BotRunService::class.java).setAction(ACTION_STOP_RUN).putExtra(EXTRA_RUN_ID, live[0].runId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopOne)
        } else {
            val stopAll = PendingIntent.getService(
                this, 1, Intent(this, BotRunService::class.java).setAction(ACTION_STOP_ALL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop all", stopAll)
        }
        return b.build()
    }

    companion object {
        const val CHANNEL_ID = "bot_runs"
        const val NOTIFICATION_ID = 4404
        const val ACTION_START = "com.mazharansari.x.work.BotRunService.START"
        const val ACTION_REFRESH = "com.mazharansari.x.work.BotRunService.REFRESH"
        const val ACTION_STOP_RUN = "com.mazharansari.x.work.BotRunService.STOP_RUN"
        const val ACTION_STOP_ALL = "com.mazharansari.x.work.BotRunService.STOP_ALL"
        const val EXTRA_RUN_ID = "run_id"

        /**
         * Grace before a bot-run entry not (yet) in [RunRegistry] is pruned:
         * [ChatViewModel] notes the run just before it registers the run,
         * so a refresh in that instant must not drop it.
         */
        const val PRUNE_GRACE_MS = 10_000L

        private val infos = ConcurrentHashMap<String, BotRunInfo>()

        /** Record a bot run starting (call before registering it in [RunRegistry]). */
        fun noteRunStarted(info: BotRunInfo) {
            infos[info.runId] = info
        }

        /** Forget a bot run (call when it settles or is stopped). Idempotent. */
        fun noteRunFinished(runId: String) {
            infos.remove(runId)
        }

        fun info(runId: String): BotRunInfo? = infos[runId]

        fun isBotRun(runId: String): Boolean = infos.containsKey(runId)

        fun snapshot(): List<BotRunInfo> = infos.values.sortedBy { it.startedAt }

        internal fun clearAllForTest() {
            infos.clear()
        }

        /** "Aria is working" for one bot, "3 bots working" for several. */
        fun notificationTitle(live: List<BotRunInfo>): String =
            if (live.size == 1) "${live[0].botName} is working" else "${live.size} bots working"

        /** One line per live run: "Aria — Draft the menu · 2 workers". */
        fun notificationText(live: List<BotRunInfo>, workerCounts: Map<String, Int> = emptyMap()): String =
            live.joinToString("\n") { summaryLine(it, workerCounts[it.conversationId] ?: 0) }

        fun summaryLine(info: BotRunInfo, workerCount: Int = 0): String {
            val task = info.title.ifBlank { "Working" }
            return if (workerCount > 0) "${info.botName} — $task · $workerCount worker${if (workerCount == 1) "" else "s"}"
            else "${info.botName} — $task"
        }

        /**
         * Pure prune rule, unit-tested: keep a run that is still live in
         * [RunRegistry], or one noted so recently it may not be registered
         * yet; drop the rest.
         */
        fun pruneDead(infos: List<BotRunInfo>, activeIds: Set<String>, nowMs: Long = System.currentTimeMillis()): List<BotRunInfo> =
            infos.filter { it.runId in activeIds || nowMs - it.startedAt < PRUNE_GRACE_MS }

        private fun startAction(ctx: Context, action: String, runId: String? = null) {
            runCatching {
                val i = Intent(ctx, BotRunService::class.java).setAction(action)
                if (runId != null) i.putExtra(EXTRA_RUN_ID, runId)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }

        /** Start (or re-post) the service for one bot run. */
        fun startForRun(ctx: Context, runId: String) = startAction(ctx, ACTION_START, runId)

        /** Re-post the notification with the live runs, or stop when none are left. */
        fun refresh(ctx: Context) = startAction(ctx, ACTION_REFRESH)

        /** Stop one run by its run id (notification action, banner Stop, chat Stop). */
        fun stopRun(ctx: Context, runId: String) {
            runCatching { ctx.startService(Intent(ctx, BotRunService::class.java).setAction(ACTION_STOP_RUN).putExtra(EXTRA_RUN_ID, runId)) }
        }

        /** Stop every bot run. */
        fun stopAll(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, BotRunService::class.java).setAction(ACTION_STOP_ALL)) }
        }
    }
}
