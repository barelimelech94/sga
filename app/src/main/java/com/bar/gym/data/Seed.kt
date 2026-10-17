package com.bar.gym.data

data class SeedSession(val name: String, val exercises: List<Exercise>)

private fun ex(name: String, body: BodyGroup, kg: Int, sets: Int, reps: Int) =
    Exercise(name = name, bodyPart = body.name, weightKg = kg.toDouble(), sets = sets, reps = reps, restSec = 120, workSec = 40)

val SEED: List<SeedSession> = listOf(
    SeedSession(
        "A - Chest, Shoulders, Triceps", listOf(
            ex("Bench press - barbell", BodyGroup.CHEST, 70, 4, 6),
            ex("Pec fly - machine", BodyGroup.CHEST, 60, 3, 8),
            ex("Shoulder press - machine", BodyGroup.SHOULDERS, 30, 3, 10),
            ex("Dips", BodyGroup.TRICEPS, 0, 3, 10),
            ex("Triceps pushdown - cable", BodyGroup.TRICEPS, 15, 3, 12),
        )
    ),
    SeedSession(
        "B - Legs", listOf(
            ex("Squat - barbell", BodyGroup.LEGS, 60, 4, 6),
            ex("Romanian deadlift", BodyGroup.LEGS, 60, 3, 10),
            ex("Split squat / Lunges", BodyGroup.LEGS, 40, 3, 10),
            ex("Leg curl - machine", BodyGroup.LEGS, 35, 3, 12),
            ex("Calf raise", BodyGroup.LEGS, 90, 4, 15),
        )
    ),
    SeedSession(
        "C - Back, Rear delts, Biceps", listOf(
            ex("Weighted pull-up / Lat pulldown", BodyGroup.BACK, 80, 4, 6),
            ex("Chest-supported row", BodyGroup.BACK, 70, 4, 10),
            ex("Rear delt fly", BodyGroup.REAR_DELTS, 20, 3, 12),
            ex("Biceps curl", BodyGroup.BICEPS, 14, 3, 8),
            ex("Cable curl", BodyGroup.BICEPS, 30, 3, 10),
        )
    ),
)
