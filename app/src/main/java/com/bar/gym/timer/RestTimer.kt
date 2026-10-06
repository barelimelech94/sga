package com.bar.gym.timer

import android.content.Context
import com.bar.gym.data.GymDao
import com.bar.gym.data.Workout

/** Every change to the rest timer goes through here. Source of truth: Workout.restEndsAt. */
object RestTimer {
    /** Starts (or restarts) the rest countdown ending at [endsAt] epoch millis. */
    suspend fun start(context: Context, dao: GymDao, workout: Workout, endsAt: Long) {
        // re-read so a stale [workout] never overwrites newer fields
        val w = dao.getWorkout(workout.id) ?: return
        dao.updateWorkout(w.copy(restEndsAt = endsAt))
        // step 5: notification + alarm
    }

    /** Clears the rest countdown. */
    suspend fun cancel(context: Context, dao: GymDao, workout: Workout) {
        val w = dao.getWorkout(workout.id) ?: return
        dao.updateWorkout(w.copy(restEndsAt = null))
        // step 5: cancel notification + alarm
    }
}
