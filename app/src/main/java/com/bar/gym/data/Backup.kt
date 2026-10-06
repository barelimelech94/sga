package com.bar.gym.data

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
