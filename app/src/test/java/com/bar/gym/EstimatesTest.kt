package com.bar.gym

import com.bar.gym.data.SEED
import com.bar.gym.data.SessionExercise
import com.bar.gym.data.SetLog
import com.bar.gym.domain.*
import org.junit.Assert.*
import org.junit.Test

class EstimatesTest {
    private fun items(s: Int) = SEED[s].exercises.mapIndexed { i, e ->
        SessionExercise(id = i + 1L, sessionId = s + 1L, exerciseId = i + 1L + s * 10L, position = i,
            weightKg = e.weightKg, sets = e.sets, reps = e.reps, restSec = e.restSec, workSec = e.workSec)
    }
    private fun log(it: SessionExercise, n: Int = 1) =
        SetLog(workoutId = 1, exerciseId = it.exerciseId, setNumber = n, weightKg = 1.0, reps = 1, completedAt = n.toLong())

    @Test fun estimates() {
        assertEquals(2560, estimateSeconds(items(0)))
        assertEquals(2720, estimateSeconds(items(1)))
        assertEquals(2720, estimateSeconds(items(2)))
        assertEquals("43 min", formatMinutes(2560))
    }

    @Test fun remaining() {
        val a = items(0)
        assertEquals(2400, remainingSeconds(a, listOf(log(a[0]))))
        val extra = (1..10).map { log(a[0], it) } // 4 planned, 10 logged
        assertEquals(2560 - 4 * 160, remainingSeconds(a, extra))
        assertEquals(0, remainingSeconds(a, a.flatMap { i -> (1..i.sets).map { log(i, it) } }))
    }

    @Test fun next() {
        val a = items(0)
        assertEquals(a[0], nextUp(a, emptyList()))
        val first = (1..4).map { log(a[0], it) }
        assertEquals(a[1], nextUp(a, first))
        assertNull(nextUp(a, a.flatMap { i -> (1..i.sets).map { log(i, it) } }))
    }

    @Test fun kg() {
        assertEquals("70", formatKg(70.0))
        assertEquals("12.5", formatKg(12.5))
        assertEquals("0", formatKg(0.0))
    }
}
