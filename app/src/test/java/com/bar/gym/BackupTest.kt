package com.bar.gym

import com.bar.gym.data.*
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTest {
    @Test fun roundTrip() {
        val b = Backup(
            exercises = listOf(Exercise(1, "Bench", "Chest", 70.5, 4, 6, 120, 40, "n", true)),
            sessions = listOf(Session(1, "A")),
            sessionExercises = listOf(SessionExercise(1, 1, 1, 0, 70.0, 4, 6, 120, 40)),
            workouts = listOf(Workout(1, 1, "A", 1000, null, 2000)),
            setLogs = listOf(SetLog(1, 1, 1, 1, 70.0, 6, 1500)),
        )
        assertEquals(b, backupFromJson(b.toJson()))
    }
}
