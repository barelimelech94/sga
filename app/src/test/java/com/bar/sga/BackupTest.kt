package com.bar.sga

import com.bar.sga.data.*
import org.junit.Assert.*
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

    private val ok = Backup(
        exercises = listOf(Exercise(1, "Bench", "Chest", 70.0, 4, 6, 120, 40)),
        sessions = listOf(Session(1, "A")),
        sessionExercises = listOf(SessionExercise(1, 1, 1, 0, 70.0, 4, 6, 120, 40)),
        workouts = listOf(Workout(1, 1, "A", 1000)),
        setLogs = listOf(SetLog(1, 1, 1, 1, 70.0, 6, 1500)),
    )

    @Test fun problemDetection() {
        assertNull(ok.problem())
        assertNotNull(ok.copy(version = 2).problem())
        assertNotNull(ok.copy(sessions = emptyList()).problem())
        assertNotNull(ok.copy(workouts = emptyList()).problem())
    }
}
