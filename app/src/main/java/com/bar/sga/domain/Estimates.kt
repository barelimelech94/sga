package com.bar.sga.domain

import com.bar.sga.data.BodyGroup
import com.bar.sga.data.SessionExercise
import com.bar.sga.data.SetLog
import com.bar.sga.data.Workout
import kotlin.math.roundToInt

fun estimateSeconds(items: List<SessionExercise>): Int =
    items.sumOf { it.sets * (it.workSec + it.restSec) }

fun doneSets(item: SessionExercise, logs: List<SetLog>) =
    logs.count { it.exerciseId == item.exerciseId }.coerceAtMost(item.sets)

fun remainingSeconds(items: List<SessionExercise>, logs: List<SetLog>): Int =
    items.sumOf { (it.sets - doneSets(it, logs)) * (it.workSec + it.restSec) }

fun nextUp(items: List<SessionExercise>, logs: List<SetLog>): SessionExercise? =
    items.sortedBy { it.position }.firstOrNull { doneSets(it, logs) < it.sets }

fun roundMinutes(seconds: Int): Int = (seconds / 60.0).roundToInt()

fun formatKg(kg: Double): String = if (kg == Math.floor(kg) && !kg.isInfinite()) kg.toLong().toString() else kg.toString()

fun formatClock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Resume rule: the most recently logged exercise if it still has sets left, else the next planned one. */
fun defaultSelection(items: List<SessionExercise>, logs: List<SetLog>): SessionExercise? {
    val last = logs.maxWithOrNull(compareBy({ it.completedAt }, { it.id }))
    val lastItem = last?.let { l -> items.firstOrNull { it.exerciseId == l.exerciseId } }
    return if (lastItem != null && doneSets(lastItem, logs) < lastItem.sets) lastItem else nextUp(items, logs)
}

data class HistoryPoint(val workoutId: Long, val date: Long, val topWeightKg: Double, val sets: List<SetLog>)

/** One point per workout with logs, oldest first. Logs whose workout has no date are skipped. */
fun historyPoints(logs: List<SetLog>, workoutDates: Map<Long, Long>): List<HistoryPoint> =
    logs.filter { it.workoutId in workoutDates }.groupBy { it.workoutId }.map { (id, ls) ->
        HistoryPoint(id, workoutDates.getValue(id), ls.maxOf { it.weightKg }, ls.sortedBy { it.setNumber })
    }.sortedBy { it.date }

data class WorkoutLog(val workout: Workout, val exercises: List<Pair<Long, List<SetLog>>>) {
    val setCount get() = exercises.sumOf { it.second.size }
}

/** Keeps [workouts]' order. Each workout's exercises are in the order they were first done, sets in order done. */
fun workoutLogs(workouts: List<Workout>, logs: List<SetLog>): List<WorkoutLog> {
    val byWorkout = logs.groupBy { it.workoutId }
    return workouts.map { w ->
        val ls = byWorkout[w.id].orEmpty().sortedWith(compareBy({ it.completedAt }, { it.id }))
        WorkoutLog(w, ls.groupBy { it.exerciseId }.toList())
    }
}

/** Distinct groups a session trains, in enum order. [BodyGroup.OTHER] and exercises missing from [groups] are left out. */
fun sessionBodyGroups(items: List<SessionExercise>, groups: Map<Long, BodyGroup>): List<BodyGroup> =
    items.mapNotNull { groups[it.exerciseId] }.filter { it != BodyGroup.OTHER }.distinct().sorted()
