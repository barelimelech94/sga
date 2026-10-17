package com.bar.sga.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Backup(
    val version: Int = 1,
    val exercises: List<Exercise>,
    val sessions: List<Session>,
    val sessionExercises: List<SessionExercise>,
    val workouts: List<Workout>,
    val setLogs: List<SetLog>,
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun Backup.toJson(): String = json.encodeToString(Backup.serializer(), this)

fun backupFromJson(json: String): Backup = Json { ignoreUnknownKeys = true }.decodeFromString(Backup.serializer(), json)

suspend fun exportBackup(dao: GymDao) = Backup(
    exercises = dao.allExercises(),
    sessions = dao.allSessions(),
    sessionExercises = dao.allSessionExercises(),
    workouts = dao.allWorkouts(),
    setLogs = dao.allSetLogs(),
)

suspend fun importBackup(db: GymDb, backup: Backup) {
    val dao = db.dao()
    db.withTransaction {
        dao.deleteAllSetLogs(); dao.deleteAllWorkouts(); dao.deleteAllSessionExercises()
        dao.deleteAllSessions(); dao.deleteAllExercises()
        dao.insertExercises(backup.exercises); dao.insertSessions(backup.sessions)
        dao.insertSessionExercises(backup.sessionExercises)
        dao.insertWorkouts(backup.workouts); dao.insertSetLogs(backup.setLogs)
    }
}

// ponytail: reasons stay English (rare diagnostic); move to resources if they need translating
/** Null when the backup is safe to import, else a short reason. */
fun Backup.problem(): String? {
    if (version != 1) return "unsupported version $version"
    val sessionIds = sessions.map { it.id }.toSet()
    val workoutIds = workouts.map { it.id }.toSet()
    if (sessionExercises.any { it.sessionId !in sessionIds }) return "an exercise entry points at a missing session"
    if (setLogs.any { it.workoutId !in workoutIds }) return "a logged set points at a missing workout"
    return null
}
