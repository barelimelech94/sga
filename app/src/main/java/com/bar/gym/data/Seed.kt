package com.bar.gym.data

data class SeedSession(val name: String, val exercises: List<Exercise>)

private fun ex(name: String, body: String, kg: Int, sets: Int, reps: Int) =
    Exercise(name = name, bodyPart = body, weightKg = kg.toDouble(), sets = sets, reps = reps, restSec = 120, workSec = 40)

val SEED: List<SeedSession> = listOf(
    SeedSession(
        "A - Chest, Shoulders, Triceps", listOf(
            ex("Bench press - barbell", "Chest", 70, 4, 6),
            ex("Pec fly - machine", "Chest", 60, 3, 8),
            ex("Shoulder press - machine", "Shoulders", 30, 3, 10),
            ex("Dips", "Triceps", 0, 3, 10),
            ex("Triceps pushdown - cable", "Triceps", 15, 3, 12),
        )
    ),
    SeedSession(
        "B - Legs", listOf(
            ex("Squat - barbell", "Legs", 60, 4, 6),
            ex("Romanian deadlift", "Legs", 60, 3, 10),
            ex("Split squat / Lunges", "Legs", 40, 3, 10),
            ex("Leg curl - machine", "Legs", 35, 3, 12),
            ex("Calf raise", "Legs", 90, 4, 15),
        )
    ),
    SeedSession(
        "C - Back, Rear delts, Biceps", listOf(
            ex("Weighted pull-up / Lat pulldown", "Back", 80, 4, 6),
            ex("Chest-supported row", "Back", 70, 4, 10),
            ex("Rear delt fly", "Rear delts", 20, 3, 12),
            ex("Biceps curl", "Biceps", 14, 3, 8),
            ex("Cable curl", "Biceps", 30, 3, 10),
        )
    ),
)
