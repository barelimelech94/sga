package com.bar.sga.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bodyPart: String,
    val weightKg: Double,
    val sets: Int,
    val reps: Int,
    val restSec: Int,
    val workSec: Int,
    val notes: String = "",
    val archived: Boolean = false,
)

@Serializable
@Entity
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Serializable
@Entity(
    foreignKeys = [ForeignKey(Session::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val weightKg: Double,
    val sets: Int,
    val reps: Int,
    val restSec: Int,
    val workSec: Int,
)

/** Timestamps are epoch millis. No FK to Session: history outlives a deleted session. */
@Serializable
@Entity
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sessionName: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val restEndsAt: Long? = null,
)

@Serializable
@Entity(
    foreignKeys = [ForeignKey(Workout::class, ["id"], ["workoutId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class SetLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val completedAt: Long,
)
