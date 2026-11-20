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

/**
 * Phase C (v0.8.0) — background work: keeps chat and Build runs alive while the owner is in
 * another screen, another app, or has swiped X away.
 *
 * The runs themselves live in ChatViewModel / the app graph (they already outlive any single
 * screen); this service's one job is to keep the PROCESS alive while [RunRegistry] says work
 * is in flight: a foreground notification ("X is working — …") plus a partial wake lock.
 * It starts on the registry's 0→1 edge and stops on the 1→0 edge, so it never idles.
 *
 * Honest platform limits: Settings → Force stop ends everything (Android rule, no app can
 * override it), and a phone reboot mid-run does not auto-resume — the partial reply is saved
 * and marked interrupted, as today. What START_STICKY buys: if Android kills the SERVICE
 * itself while work is in flight, it restarts and re-posts the notification; if the whole
 * PROCESS dies, the durable TaskRuns record (not this service) is what makes the run
 * resumable with one tap — a run cannot continue without its process.
 */
class WorkService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) {
            runCatching { (application as AxionApp).graph.runs.cancelAll() }
            releaseWake()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP_RUN) {
            // Bot background work: stop ONE run by its run id — every other
            // run in flight keeps working. Cancelling the registry job is
            // the run itself, so its own cleanup settles it (partial reply
            // kept, TaskRuns record finished as interrupted).
            val runId = intent.getStringExtra(EXTRA_RUN_ID).orEmpty()
            if (runId.isNotEmpty()) {
                // The registry unregisters the job when its cleanup finishes,
                // which lags this call — so decide "last run" now, before the
                // cancel, or the notification would briefly re-post.
                val soleRun = currentHandles().let { it.size == 1 && it[0].id == runId }
                runCatching { (application as AxionApp).graph.runs.active.value.firstOrNull { it.id == runId }?.job?.cancel() }
                runCatching { (application as AxionApp).graph.jobs.killForRun(runId) }
                com.mazharansari.x.work.BotRunService.noteRunFinished(runId)
                if (soleRun) {
                    releaseWake()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        val handles = currentHandles()
        if (handles.isEmpty()) {
            // Nothing in flight (stale start, or the stop above ended the
            // last run): do not linger as a foreground service.
            releaseWake()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val n = buildNotification(handles)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, n)
        acquireWake()
        // Phase 2 (background): work is in flight — if Android kills the service, restart
        // it so the notification (and the wake lock) come back with the run. The stop-all
        // and stale-start paths below still return NOT_STICKY on purpose.
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWake()
        super.onDestroy()
    }

    private fun currentHandles(): List<RunRegistry.Handle> =
        runCatching { (application as AxionApp).graph.runs.active.value }.getOrDefault(emptyList())

    private fun acquireWake() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "axion:work").apply {
            setReferenceCounted(false)
            // Backstop only: runs unregister as they settle, and the service stops with them;
            // the timeout just guarantees a leaked lock can never drain the battery overnight.
            acquire(3L * 60 * 60 * 1000)
        }
    }

    private fun releaseWake() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun buildNotification(handles: List<RunRegistry.Handle>): Notification {
        val titles = handles.map { it.title }
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH, "Background work", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (titles.size == 1) titles[0] else "${titles.size} tasks running"
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else @Suppress("DEPRECATION") Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("X is working")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(titles.joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
        if (handles.size == 1) {
            // One run in flight: the action stops that run only (bot runs
            // use the same run-id keying — see BotRunService).
            val stopOne = PendingIntent.getService(
                this, handles[0].id.hashCode(),
                Intent(this, WorkService::class.java).setAction(ACTION_STOP_RUN).putExtra(EXTRA_RUN_ID, handles[0].id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopOne)
        } else {
            val stopAll = PendingIntent.getService(
                this, 1, Intent(this, WorkService::class.java).setAction(ACTION_STOP_ALL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop all", stopAll)
        }
        return b.build()
    }

    companion object {
        private const val CH = "work"
        private const val ID = 4403
        private const val ACTION_STOP_ALL = "axion.work.STOP_ALL"
        private const val ACTION_STOP_RUN = "axion.work.STOP_RUN"
        private const val EXTRA_RUN_ID = "run_id"

        /** Re-post the notification with the current run titles (called on registry edges). */
        fun refresh(ctx: Context) {
            runCatching {
                val i = Intent(ctx, WorkService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }

        fun start(ctx: Context) = refresh(ctx)

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, WorkService::class.java)) }
        }

        /** Stop one run by its run id, leaving every other run in flight alone. */
        fun stopRun(ctx: Context, runId: String) {
            runCatching { ctx.startService(Intent(ctx, WorkService::class.java).setAction(ACTION_STOP_RUN).putExtra(EXTRA_RUN_ID, runId)) }
        }
    }
}
