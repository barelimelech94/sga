package com.bar.sga.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bar.sga.R
import com.bar.sga.SgApp
import com.bar.sga.domain.WorkoutLog
import com.bar.sga.domain.formatKg
import com.bar.sga.domain.roundMinutes
import com.bar.sga.domain.workoutLogs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.combine

class WorkoutLogViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as SgApp).db.dao()
    val logs = combine(dao.observeFinishedWorkouts(), dao.observeAllLogs()) { ws, ls -> workoutLogs(ws, ls) }
    val names = dao.observeAllExercisesIncludingArchived()
}

// built per call: the app language can change while the process is alive
private fun fmtDateTime(ms: Long) =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLogScreen(onBack: () -> Unit, onExercise: (Long) -> Unit, vm: WorkoutLogViewModel = viewModel()) {
    val logs by vm.logs.collectAsStateWithLifecycle<List<WorkoutLog>?>(null)
    val exercises by vm.names.collectAsStateWithLifecycle(emptyList())
    val nameOf = exercises.associate { it.id to it.name }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.workout_history)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        val list = logs ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(16.dp)) { Text(stringResource(R.string.no_workouts_yet)) }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.workout.id }) { log ->
                val expanded = log.workout.id == expandedId
                WorkoutCard(log, expanded, nameOf, onToggle = { expandedId = if (expanded) null else log.workout.id }, onExercise)
            }
        }
    }
}

@Composable
private fun WorkoutCard(log: WorkoutLog, expanded: Boolean, nameOf: Map<Long, String>, onToggle: () -> Unit, onExercise: (Long) -> Unit) {
    val w = log.workout
    val minutes = roundMinutes((((w.finishedAt ?: w.startedAt) - w.startedAt) / 1000).toInt())
    Card(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(Modifier.padding(16.dp)) {
            Text(w.sessionName, style = MaterialTheme.typography.titleLarge)
            Text(fmtDateTime(w.startedAt), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(
                    R.string.workout_log_summary,
                    stringResource(R.string.minutes_format, minutes),
                    pluralStringResource(R.plurals.set_count, log.setCount, log.setCount),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (expanded) {
                log.exercises.forEach { (exerciseId, sets) ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Column(Modifier.fillMaxWidth().clickable { onExercise(exerciseId) }) {
                        Text(nameOf[exerciseId] ?: stringResource(R.string.deleted_exercise), style = MaterialTheme.typography.labelLarge)
                        Text(sets.map { stringResource(R.string.weight_reps, formatKg(it.weightKg), it.reps) }.joinToString(", "))
                    }
                }
            }
        }
    }
}
