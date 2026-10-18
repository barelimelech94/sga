package com.bar.sga

import com.bar.sga.data.Workout
import com.bar.sga.domain.WorkoutLog
import com.bar.sga.domain.monthGrid
import com.bar.sga.domain.workoutDates
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class CalendarTest {
    @Test fun gridStartsOnFirstDayOfWeek() {
        // 1 Oct 2026 is a Thursday
        val sunday = monthGrid(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(4, sunday.indexOf(LocalDate.of(2026, 10, 1)))
        val monday = monthGrid(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(3, monday.indexOf(LocalDate.of(2026, 10, 1)))
        for (g in listOf(sunday, monday)) {
            assertEquals(0, g.size % 7)
            assertEquals(31, g.count { it != null })
        }
    }

    @Test fun gridWithNoPadding() {
        // Feb 2026 starts on a Sunday and has exactly four weeks
        val g = monthGrid(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)
        assertEquals(28, g.size)
        assertTrue(g.all { it != null })
    }

    @Test fun datesUseLocalZone() {
        val zone = ZoneId.of("Asia/Jerusalem")
        // 23:30 local on 17 Oct is 20:30 UTC: must count as the 17th
        val ms = ZonedDateTime.of(2026, 10, 17, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val log = WorkoutLog(Workout(id = 7, sessionId = 1, sessionName = "A", startedAt = ms, finishedAt = ms), emptyList())
        assertEquals(mapOf(7L to LocalDate.of(2026, 10, 17)), workoutDates(listOf(log), zone))
    }
}
