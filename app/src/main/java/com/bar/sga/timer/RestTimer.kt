package com.bar.sga.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.bar.sga.MainActivity
import com.bar.sga.R
import com.bar.sga.data.GymDao
import com.bar.sga.data.Workout

/** Every change to the rest timer goes through here. Source of truth: Workout.restEndsAt. */
object RestTimer {
    const val ONGOING_ID = 1
    const val OVER_ID = 2
    const val ONGOING_CHANNEL = "rest_timer"
    const val OVER_CHANNEL = "rest_over_chime"
    private const val ALARM_REQUEST = 1
    private const val OPEN_REQUEST = 2

    /** Bundled chime (res/raw/rest_over.wav), so it doesn't depend on the phone's default notification sound. */
    private fun overSound(context: Context): Uri = Uri.parse("android.resource://${context.packageName}/${R.raw.rest_over}")
    // USAGE_ALARM plays at alarm volume, so it is audible with the ringer on vibrate or silent
    private val overAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    // Kept referenced so Ringtone's finalizer can't release its player mid-sound
    private var ringtone: Ringtone? = null

    /** Idempotent. Channel settings are immutable after first creation. */
    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // old ids: a channel's sound can't be changed in place
        nm.deleteNotificationChannel("rest_over")
        nm.deleteNotificationChannel("rest_over_alarm")
        nm.createNotificationChannel(
            NotificationChannel(ONGOING_CHANNEL, context.getString(R.string.channel_rest_timer), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(OVER_CHANNEL, context.getString(R.string.channel_rest_over), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(overSound(context), overAttrs)
            }
        )
    }

    /** Plays the "Rest over" sound once, for when the notification that normally plays it can't be shown. */
    fun playOverSound(context: Context) {
        try {
            // applicationContext: the Ringtone is held in a field and retains its Context, which must not be an Activity
            ringtone = RingtoneManager.getRingtone(context.applicationContext, overSound(context))?.apply { audioAttributes = overAttrs; play() }
        } catch (_: Exception) { }
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, OPEN_REQUEST,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, ALARM_REQUEST,
        Intent(context, RestAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun post(context: Context, endsAt: Long) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.cancel(OVER_ID) // stale "rest over" from a previous rest
        val n = Notification.Builder(context, ONGOING_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notif_resting))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endsAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(context))
            .build()
        try { nm?.notify(ONGOING_ID, n) } catch (_: Exception) { }
    }

    private fun schedule(context: Context, endsAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi)
        }
    }

    /** Starts (or restarts) the rest countdown ending at [endsAt] epoch millis. */
    suspend fun start(context: Context, dao: GymDao, workout: Workout, endsAt: Long) {
        // re-read so a stale [workout] never overwrites newer fields
        val w = dao.getWorkout(workout.id) ?: return
        dao.updateWorkout(w.copy(restEndsAt = endsAt))
        post(context, endsAt)
        schedule(context, endsAt)
    }

    /** Clears the rest countdown. */
    suspend fun cancel(context: Context, dao: GymDao, workout: Workout) {
        val w = dao.getWorkout(workout.id) ?: return
        dao.updateWorkout(w.copy(restEndsAt = null))
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
        context.getSystemService(NotificationManager::class.java)?.run {
            cancel(ONGOING_ID)
            cancel(OVER_ID)
        }
    }
}
