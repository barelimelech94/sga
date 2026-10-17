package com.bar.gym

import com.bar.gym.data.BodyGroup
import com.bar.gym.data.SEED
import com.bar.gym.data.bodyGroup
import org.junit.Assert.*
import org.junit.Test

class SeedTest {
    @Test fun shape() {
        assertEquals(3, SEED.size)
        assertEquals(15, SEED.sumOf { it.exercises.size })
        assertTrue(SEED.flatMap { it.exercises }.all { it.bodyPart.isNotBlank() })
        assertTrue(SEED.flatMap { it.exercises }.none { it.bodyGroup == BodyGroup.OTHER })
    }
}
