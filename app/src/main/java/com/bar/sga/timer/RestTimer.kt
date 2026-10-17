package com.bar.sga.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import com.bar.sga.MainActivity
import com.bar.sga.R
import com.bar.sga.data.GymDao
import com.bar.sga.data.Workout

/** Every change to the rest timer goes through here. Source of truth: Workout.restEndsAt. */
object RestTimer {
    const val ONGOING_ID = 1
    const val OVER_ID = 2
    const val ONGOING_CHANNEL = "rest_timer"
    const val OVER_CHANNEL = "rest_over_alert"
    private const val ALARM_REQUEST = 1
    private const val OPEN_REQUEST = 2
    private const val BAR_MAX = 1000

    // USAGE_ALARM plays on the alarm stream, which ringer silent/vibrate doesn't mute
    private val overAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    // Kept referenced so the player isn't garbage-collected mid-sound
    private var player: MediaPlayer? = null

    /** Idempotent. Channel settings are immutable after first creation. */
    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // old ids: a channel's sound can't be changed in place
        nm.deleteNotificationChannel("rest_over")
        nm.deleteNotificationChannel("rest_over_alarm")
        nm.deleteNotificationChannel("rest_over_chime")
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
                // silent: the app plays the chime itself (playOverSound), because notification sounds
                // are muted by ringer mode, Do Not Disturb and per-app sound settings on many phones
                setSound(null, null)
            }
        )
    }

    /**
     * Plays the "Rest over" chime (res/raw/rest_over.wav) directly at alarm volume, briefly ducking any music.
     * [onDone] runs once on the main thread when playback ends or fails.
     */
    fun playOverSound(context: Context, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        val am = app.getSystemService(AudioManager::class.java)
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(overAttrs).build()
        var done = false
        fun end(mp: MediaPlayer?) {
            if (done) return
            done = true
            mp?.release()
            if (player === mp) player = null
            am?.abandonAudioFocusRequest(focus)
            onDone()
        }
        var mp: MediaPlayer? = null
        try {
            val sessionId = am?.generateAudioSessionId() ?: AudioManager.AUDIO_SESSION_ID_GENERATE
            mp = MediaPlayer.create(app, R.raw.rest_over, overAttrs, sessionId) ?: return end(null)
            player?.release()
            player = mp
            mp.setOnCompletionListener { end(it) }
            mp.setOnErrorListener { p, _, _ -> end(p); true }
            am?.requestAudioFocus(focus)
            mp.start()
        } catch (_: Exception) { end(mp) }
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

    /** The "Resting" notification: big countdown that ticks on its own, plus a bar of the rest left. */
    fun ongoingNotification(context: Context, startedAt: Long, endsAt: Long): Notification {
        val now = System.currentTimeMillis()
        val total = (endsAt - startedAt).coerceAtLeast(1)
        val left = (endsAt - now).coerceIn(0, total)
        val base = SystemClock.elapsedRealtime() + left
        val progress = (BAR_MAX * left / total).toInt()
        fun views(layout: Int) = RemoteViews(context.packageName, layout).apply {
            setChronometer(R.id.rest_clock, base, null, true)
            setChronometerCountDown(R.id.rest_clock, true)
            setProgressBar(R.id.rest_bar, BAR_MAX, progress, false)
        }
        return Notification.Builder(context, ONGOING_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notif_resting))
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(views(R.layout.notif_rest))
            .setCustomBigContentView(views(R.layout.notif_rest_big))
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(context))
            .build()
    }

    /** Re-posts the "Resting" notification so the bar moves. Called every second by RestService. */
    fun notifyOngoing(context: Context, startedAt: Long, endsAt: Long) {
        try { context.getSystemService(NotificationManager::class.java)?.notify(ONGOING_ID, ongoingNotification(context, startedAt, endsAt)) } catch (_: Exception) { }
    }

    private fun post(context: Context, startedAt: Long, endsAt: Long, extend: Boolean) {
        ensureChannels(context)
        context.getSystemService(NotificationManager::class.java)?.cancel(OVER_ID) // stale "rest over" from a previous rest
        val intent = Intent(context, RestService::class.java)
            .putExtra(RestService.EXTRA_START, startedAt)
            .putExtra(RestService.EXTRA_ENDS, endsAt)
            .putExtra(RestService.EXTRA_EXTEND, extend)
        try {
            context.startForegroundService(intent)
        } catch (_: Exception) {
            // not allowed from the background: show it without the moving bar
            notifyOngoing(context, startedAt, endsAt)
        }
    }

    private fun stopService(context: Context) {
        try { context.stopService(Intent(context, RestService::class.java)) } catch (_: Exception) { }
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
        val now = System.currentTimeMillis()
        val extend = (w.restEndsAt ?: 0L) > now // +30s on a running rest
        dao.updateWorkout(w.copy(restEndsAt = endsAt))
        post(context, now, endsAt, extend)
        schedule(context, endsAt)
    }

    /** Clears the rest countdown. */
    suspend fun cancel(context: Context, dao: GymDao, workout: Workout) {
        val w = dao.getWorkout(workout.id) ?: return
        dao.updateWorkout(w.copy(restEndsAt = null))
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
        stopService(context)
        context.getSystemService(NotificationManager::class.java)?.run {
            cancel(ONGOING_ID)
            cancel(OVER_ID)
        }
    }
}
