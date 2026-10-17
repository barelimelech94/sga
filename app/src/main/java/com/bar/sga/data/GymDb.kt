package com.bar.sga.data

import android.content.ContentValues
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Exercise::class, Session::class, SessionExercise::class, Workout::class, SetLog::class],
    version = 1,
    exportSchema = false,
)
abstract class GymDb : RoomDatabase() {
    abstract fun dao(): GymDao

    companion object {
        fun create(context: Context): GymDb =
            Room.databaseBuilder(context.applicationContext, GymDb::class.java, "gym.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = seed(db)
                })
                .build()

        private fun seed(db: SupportSQLiteDatabase) {
            for (s in SEED) {
                val sessionId = db.insert("Session", 0, ContentValues().apply { put("name", s.name) })
                s.exercises.forEachIndexed { i, e ->
                    val exerciseId = db.insert("Exercise", 0, ContentValues().apply {
                        put("name", e.name); put("bodyPart", e.bodyPart); put("weightKg", e.weightKg)
                        put("sets", e.sets); put("reps", e.reps); put("restSec", e.restSec)
                        put("workSec", e.workSec); put("notes", e.notes); put("archived", 0)
                    })
                    db.insert("SessionExercise", 0, ContentValues().apply {
                        put("sessionId", sessionId); put("exerciseId", exerciseId); put("position", i)
                        put("weightKg", e.weightKg); put("sets", e.sets); put("reps", e.reps)
                        put("restSec", e.restSec); put("workSec", e.workSec)
                    })
                }
            }
        }
    }
}
