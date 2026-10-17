package com.bar.sga.timer

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Keeps the "Resting" notification's progress bar moving: a notification bar can't animate itself,
 * and the app process is frozen in the background unless a foreground service is running.
 * Holds no data; RestTimer starts and stops it.
 */
class RestService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    private var endsAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() >= endsAt) { finish(); return }
            RestTimer.notifyOngoing(this@RestService, startedAt, endsAt)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val now = System.currentTimeMillis()
        val ends = intent?.getLongExtra(EXTRA_ENDS, 0L) ?: 0L
        // +30s keeps the original start, so the bar shrinks back instead of refilling to full
        if (intent?.getBooleanExtra(EXTRA_EXTEND, false) != true || startedAt == 0L) {
            startedAt = intent?.getLongExtra(EXTRA_START, now) ?: now
        }
        endsAt = ends
        try {
            // every startForegroundService() must be answered with startForeground(), even when stopping right away
            startForeground(RestTimer.ONGOING_ID, RestTimer.ongoingNotification(this, startedAt, endsAt.coerceAtLeast(now)))
        } catch (_: Exception) { }
        handler.removeCallbacks(tick)
        if (ends <= now) finish() else handler.postDelayed(tick, 1000)
        return START_NOT_STICKY
    }

    private fun finish() {
        handler.removeCallbacks(tick)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_START = "start"
        const val EXTRA_ENDS = "ends"
        const val EXTRA_EXTEND = "extend"
    }
}
