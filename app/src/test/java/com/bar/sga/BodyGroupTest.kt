package com.bar.sga

import com.bar.sga.data.BodyGroup
import com.bar.sga.data.BodyGroup.*
import com.bar.sga.data.Exercise
import com.bar.sga.data.SessionExercise
import com.bar.sga.data.bodyGroup
import com.bar.sga.domain.sessionBodyGroups
import org.junit.Assert.*
import org.junit.Test

class BodyGroupTest {
    @Test fun enumNames() {
        BodyGroup.entries.forEach { assertEquals(it, BodyGroup.from(it.name)) }
    }

    @Test fun oldSeedSpellings() {
        assertEquals(CHEST, BodyGroup.from("Chest"))
        assertEquals(SHOULDERS, BodyGroup.from("Shoulders"))
        assertEquals(TRICEPS, BodyGroup.from("Triceps"))
        assertEquals(LEGS, BodyGroup.from("Legs"))
        assertEquals(BACK, BodyGroup.from("Back"))
        assertEquals(REAR_DELTS, BodyGroup.from("Rear delts"))
        assertEquals(BICEPS, BodyGroup.from("Biceps"))
    }

    @Test fun caseSpacesAndHyphens() {
        assertEquals(CHEST, BodyGroup.from("chest"))
        assertEquals(CHEST, BodyGroup.from("  CHEST \n"))
        assertEquals(REAR_DELTS, BodyGroup.from("REAR_DELTS"))
        assertEquals(REAR_DELTS, BodyGroup.from(" rear-delts "))
        assertEquals(REAR_DELTS, BodyGroup.from("Rear Delts"))
    }

    @Test fun hebrew() {
        assertEquals(CHEST, BodyGroup.from("חזה"))
        assertEquals(BACK, BodyGroup.from("גב"))
        assertEquals(SHOULDERS, BodyGroup.from("כתפיים"))
        assertEquals(REAR_DELTS, BodyGroup.from("כתף אחורית"))
        assertEquals(BICEPS, BodyGroup.from("יד קדמית"))
        assertEquals(TRICEPS, BodyGroup.from("יד אחורית"))
        assertEquals(LEGS, BodyGroup.from("רגליים"))
        assertEquals(CORE, BodyGroup.from("בטן"))
        assertEquals(CHEST, BodyGroup.from(" חזה "))
    }

    @Test fun blankAndUnknownAreOther() {
        assertEquals(OTHER, BodyGroup.from(""))
        assertEquals(OTHER, BodyGroup.from("   "))
        assertEquals(OTHER, BodyGroup.from("Forearms"))
        assertEquals(OTHER, BodyGroup.from("OTHER"))
    }

    @Test fun exerciseExtension() {
        val e = Exercise(name = "x", bodyPart = "Rear delts", weightKg = 0.0, sets = 1, reps = 1, restSec = 0, workSec = 0)
        assertEquals(REAR_DELTS, e.bodyGroup)
    }

    private fun item(exerciseId: Long) =
        SessionExercise(sessionId = 1, exerciseId = exerciseId, position = 0, weightKg = 0.0, sets = 1, reps = 1, restSec = 0, workSec = 0)

    private val map = mapOf(1L to LEGS, 2L to CHEST, 3L to LEGS, 4L to OTHER, 5L to BICEPS)

    @Test fun sessionGroupsDistinctInEnumOrder() {
        assertEquals(listOf(CHEST, BICEPS, LEGS), sessionBodyGroups(listOf(item(1), item(5), item(2), item(3)), map))
        assertEquals(listOf(CHEST, BICEPS, LEGS), sessionBodyGroups(listOf(item(2), item(3), item(5), item(1)), map))
    }

    @Test fun sessionGroupsSkipOtherAndUnknown() {
        assertEquals(listOf(CHEST), sessionBodyGroups(listOf(item(4), item(2), item(99)), map))
        assertEquals(emptyList<BodyGroup>(), sessionBodyGroups(listOf(item(4), item(99)), map))
    }

    @Test fun sessionGroupsEmpty() {
        assertEquals(emptyList<BodyGroup>(), sessionBodyGroups(emptyList(), map))
        assertEquals(emptyList<BodyGroup>(), sessionBodyGroups(listOf(item(1)), emptyMap()))
    }
}
