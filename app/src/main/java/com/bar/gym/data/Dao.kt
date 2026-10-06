package com.bar.gym.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GymDao {
    // Exercises
    @Query("SELECT * FROM Exercise WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeExercises(): Flow<List<Exercise>>
    @Query("SELECT * FROM Exercise WHERE id = :id")
    suspend fun getExercise(id: Long): Exercise?
    @Upsert suspend fun upsertExercise(exercise: Exercise): Long
    @Query("DELETE FROM Exercise WHERE id = :id")
    suspend fun deleteExercise(id: Long)
    @Query("UPDATE Exercise SET archived = 1 WHERE id = :id")
    suspend fun archiveExercise(id: Long)
    @Query("SELECT COUNT(*) FROM SetLog WHERE exerciseId = :exerciseId")
    suspend fun countLogsForExercise(exerciseId: Long): Int

    @Query("DELETE FROM SessionExercise WHERE exerciseId = :exerciseId")
    suspend fun deleteSessionExercisesForExercise(exerciseId: Long)

    // Sessions
    @Query("SELECT * FROM Session ORDER BY id")
    fun observeSessions(): Flow<List<Session>>
    @Query("SELECT * FROM Session WHERE id = :id")
    suspend fun getSession(id: Long): Session?
    @Upsert suspend fun upsertSession(session: Session): Long
    @Query("DELETE FROM Session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    // SessionExercises
    @Query("SELECT * FROM SessionExercise WHERE sessionId = :sessionId ORDER BY position")
    fun observeSessionExercises(sessionId: Long): Flow<List<SessionExercise>>
    @Query("SELECT * FROM SessionExercise WHERE sessionId = :sessionId ORDER BY position")
    suspend fun getSessionExercises(sessionId: Long): List<SessionExercise>
    @Query("SELECT * FROM SessionExercise ORDER BY sessionId, position")
    fun observeAllSessionExercises(): Flow<List<SessionExercise>>
    @Upsert suspend fun upsertSessionExercise(item: SessionExercise): Long
    @Upsert suspend fun upsertSessionExercises(items: List<SessionExercise>)
    @Query("DELETE FROM SessionExercise WHERE id = :id")
    suspend fun deleteSessionExercise(id: Long)
    @Query("DELETE FROM SessionExercise WHERE sessionId = :sessionId")
    suspend fun deleteSessionExercisesFor(sessionId: Long)

    // Workouts
    @Query("SELECT * FROM Workout WHERE finishedAt IS NULL ORDER BY startedAt DESC, id DESC LIMIT 1")
    fun observeActiveWorkout(): Flow<Workout?>
    @Query("SELECT * FROM Workout WHERE finishedAt IS NULL ORDER BY startedAt DESC, id DESC LIMIT 1")
    suspend fun getActiveWorkout(): Workout?
    @Insert suspend fun insertWorkout(workout: Workout): Long
    @Update suspend fun updateWorkout(workout: Workout)
    @Query("SELECT * FROM Workout WHERE id = :id")
    suspend fun getWorkout(id: Long): Workout?

    // SetLogs
    @Query("SELECT * FROM SetLog WHERE workoutId = :workoutId ORDER BY completedAt")
    fun observeLogs(workoutId: Long): Flow<List<SetLog>>
    @Insert suspend fun insertLog(log: SetLog): Long
    @Query("DELETE FROM SetLog WHERE id = :id")
    suspend fun deleteLog(id: Long)
    @Query("SELECT * FROM Workout")
    fun observeWorkouts(): Flow<List<Workout>>
    @Query("SELECT * FROM SetLog WHERE exerciseId = :exerciseId ORDER BY completedAt")
    fun observeLogsForExercise(exerciseId: Long): Flow<List<SetLog>>
    @Query(
        "SELECT * FROM SetLog WHERE exerciseId = :exerciseId AND workoutId = " +
            "(SELECT MAX(workoutId) FROM SetLog WHERE exerciseId = :exerciseId AND workoutId != :currentWorkoutId) " +
            "ORDER BY setNumber"
    )
    suspend fun lastTimeLogs(exerciseId: Long, currentWorkoutId: Long): List<SetLog>

    // Backup helpers
    @Query("SELECT * FROM Exercise") suspend fun allExercises(): List<Exercise>
    @Query("SELECT * FROM Session") suspend fun allSessions(): List<Session>
    @Query("SELECT * FROM SessionExercise") suspend fun allSessionExercises(): List<SessionExercise>
    @Query("SELECT * FROM Workout") suspend fun allWorkouts(): List<Workout>
    @Query("SELECT * FROM SetLog") suspend fun allSetLogs(): List<SetLog>

    @Query("DELETE FROM Exercise") suspend fun deleteAllExercises()
    @Query("DELETE FROM Session") suspend fun deleteAllSessions()
    @Query("DELETE FROM SessionExercise") suspend fun deleteAllSessionExercises()
    @Query("DELETE FROM Workout") suspend fun deleteAllWorkouts()
    @Query("DELETE FROM SetLog") suspend fun deleteAllSetLogs()

    @Insert suspend fun insertExercises(items: List<Exercise>)
    @Insert suspend fun insertSessions(items: List<Session>)
    @Insert suspend fun insertSessionExercises(items: List<SessionExercise>)
    @Insert suspend fun insertWorkouts(items: List<Workout>)
    @Insert suspend fun insertSetLogs(items: List<SetLog>)
}
