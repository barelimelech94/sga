package com.bar.gym.timer

import com.bar.gym.R
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when the rest alarm goes off. Touches no database: the in-app banner derives "Rest over" from restEndsAt. */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestTimer.ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
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
    }
}
