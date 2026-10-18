package com.bar.sga.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.bar.sga.SgApp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private val longArg = listOf(navArgument("id") { type = NavType.LongType })

@Composable
fun GymNav() {
    val nav = rememberNavController()
    val dao = (LocalContext.current.applicationContext as SgApp).db.dao()
    // Resume: an unfinished workout opens straight away, with "sessions" underneath. Once per launch.
    var checked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!checked) {
            checked = true
            if (dao.getActiveWorkout() != null) nav.navigate("workout")
        }
    }
    NavHost(nav, startDestination = "sessions") {
        composable("sessions") {
            SessionsScreen(
                onStart = { nav.navigate("workout") },
                onResume = { nav.navigate("workout") },
                onEdit = { nav.navigate("session/$it") },
                onExercises = { nav.navigate("exercises") },
                onSettings = { nav.navigate("settings") },
                onHistory = { nav.navigate("workouts") },
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
        composable("workout") { WorkoutScreen(onGone = { nav.popBackStack("sessions", false) }) }
        composable("history/{exerciseId}", listOf(navArgument("exerciseId") { type = NavType.LongType })) { e ->
            HistoryScreen(e.arguments!!.getLong("exerciseId"), onBack = { nav.popBackStack() })
        }
        composable("workouts") { WorkoutLogScreen(onBack = { nav.popBackStack() }, onExercise = { nav.navigate("history/$it") }) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
