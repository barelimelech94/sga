package com.bar.gym.domain

import com.bar.gym.data.SessionExercise
import com.bar.gym.data.SetLog
import kotlin.math.roundToInt

fun estimateSeconds(items: List<SessionExercise>): Int =
    items.sumOf { it.sets * (it.workSec + it.restSec) }

private fun doneSets(item: SessionExercise, logs: List<SetLog>) =
    logs.count { it.exerciseId == item.exerciseId }.coerceAtMost(item.sets)

fun remainingSeconds(items: List<SessionExercise>, logs: List<SetLog>): Int =
    items.sumOf { (it.sets - doneSets(it, logs)) * (it.workSec + it.restSec) }

fun nextUp(items: List<SessionExercise>, logs: List<SetLog>): SessionExercise? =
    items.sortedBy { it.position }.firstOrNull { doneSets(it, logs) < it.sets }

fun formatMinutes(seconds: Int): String = "${(seconds / 60.0).roundToInt()} min"
