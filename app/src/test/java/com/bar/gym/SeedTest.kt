package com.bar.gym

import com.bar.gym.data.SEED
import org.junit.Assert.*
import org.junit.Test

class SeedTest {
    @Test fun shape() {
        assertEquals(3, SEED.size)
        assertEquals(15, SEED.sumOf { it.exercises.size })
        assertTrue(SEED.flatMap { it.exercises }.all { it.bodyPart.isNotBlank() })
    }
}
