package com.bar.gym.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private val longArg = listOf(navArgument("id") { type = NavType.LongType })

@Composable
fun GymNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "sessions") {
        composable("sessions") {
            SessionsScreen(
                onStart = { _ -> /* step 4 creates the Workout row here */ nav.navigate("workout") },
                onResume = { nav.navigate("workout") },
                onEdit = { nav.navigate("session/$it") },
                onExercises = { nav.navigate("exercises") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("session/{id}", longArg) { e ->
            SessionEditScreen(e.arguments!!.getLong("id"), onDone = { nav.popBackStack() })
        }
        composable("exercises") {
            ExercisesScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("exercise/$it") },
                onHistory = { nav.navigate("history/$it") },
            )
        }
        composable("exercise/{id}", longArg) { e ->
            ExerciseEditScreen(e.arguments!!.getLong("id"), onDone = { nav.popBackStack() })
        }
        composable("workout") { Text("Workout") }
        composable("history/{exerciseId}", listOf(navArgument("exerciseId") { type = NavType.LongType })) { Text("History") }
        composable("settings") { Text("Settings") }
    }
}
