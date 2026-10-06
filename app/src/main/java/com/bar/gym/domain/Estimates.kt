package com.bar.gym.domain

import com.bar.gym.data.SessionExercise
import com.bar.gym.data.SetLog
import kotlin.math.roundToInt

fun estimateSeconds(items: List<SessionExercise>): Int =
    items.sumOf { it.sets * (it.workSec + it.restSec) }

fun doneSets(item: SessionExercise, logs: List<SetLog>) =
    logs.count { it.exerciseId == item.exerciseId }.coerceAtMost(item.sets)

fun remainingSeconds(items: List<SessionExercise>, logs: List<SetLog>): Int =
    items.sumOf { (it.sets - doneSets(it, logs)) * (it.workSec + it.restSec) }

fun nextUp(items: List<SessionExercise>, logs: List<SetLog>): SessionExercise? =
    items.sortedBy { it.position }.firstOrNull { doneSets(it, logs) < it.sets }

fun formatMinutes(seconds: Int): String = "${(seconds / 60.0).roundToInt()} min"

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
