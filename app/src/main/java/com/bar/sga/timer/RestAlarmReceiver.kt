package com.bar.sga.timer

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.bar.sga.R

/** Fires when the rest alarm goes off: shows "Rest over" and plays the chime. Touches no database: the in-app banner derives "Rest over" from restEndsAt. */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestTimer.ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // stops the bar updates; the service may lag the alarm while the device dozes
        try { context.stopService(Intent(context, RestService::class.java)) } catch (_: Exception) { }
        nm.cancel(RestTimer.ONGOING_ID)
        val n = Notification.Builder(context, RestTimer.OVER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notif_rest_over))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setContentIntent(RestTimer.openAppIntent(context))
            .build()
        try { nm.notify(RestTimer.OVER_ID, n) } catch (_: Exception) { }
        // keep the process alive while the chime plays; the timeout is a backstop well under the ~10s limit
        val pending = goAsync()
        var finished = false
        val finish = { if (!finished) { finished = true; pending.finish() } }
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 5_000)
        RestTimer.playOverSound(context, finish)
    }
}
