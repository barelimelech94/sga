package com.bar.sga.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The local date each workout started on, keyed by workout id. */
fun workoutDates(logs: List<WorkoutLog>, zone: ZoneId): Map<Long, LocalDate> =
    logs.associate { it.workout.id to Instant.ofEpochMilli(it.workout.startedAt).atZone(zone).toLocalDate() }

/**
 * Cells of a month grid, row by row, starting on [firstDay]. Null cells pad the first and last week,
 * so the size is always a multiple of 7.
 */
fun monthGrid(month: YearMonth, firstDay: DayOfWeek): List<LocalDate?> {
    val lead = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells = List<LocalDate?>(lead) { null } + days
    return cells + List((7 - cells.size % 7) % 7) { null }
}
