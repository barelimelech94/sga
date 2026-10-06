package com.bar.gym.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bar.gym.GymApp
import com.bar.gym.R
import com.bar.gym.domain.HistoryPoint
import com.bar.gym.domain.formatKg
import com.bar.gym.domain.historyPoints
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as GymApp).db.dao()
    suspend fun name(id: Long) = dao.getExercise(id)?.name ?: getApplication<Application>().getString(R.string.history)
    fun points(id: Long) = combine(dao.observeLogsForExercise(id), dao.observeWorkouts()) { logs, ws ->
        historyPoints(logs, ws.associate { it.id to it.startedAt })
    }
}

// built per call: the app language can change while the process is alive
private fun fmtDate(ms: Long) = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(exerciseId: Long, onBack: () -> Unit, vm: HistoryViewModel = viewModel()) {
    var title by remember { mutableStateOf("") }
    LaunchedEffect(exerciseId) { title = vm.name(exerciseId) }
    val points by remember(exerciseId) { vm.points(exerciseId) }.collectAsStateWithLifecycle(emptyList<HistoryPoint>())
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        if (points.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(16.dp)) { Text(stringResource(R.string.no_sets_yet)) }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(R.string.top_weight_title), style = MaterialTheme.typography.titleMedium)
                LineChart(points.map { it.topWeightKg }, points.map { fmtDate(it.date) }, Modifier.fillMaxWidth().height(220.dp))
                Text(stringResource(R.string.best_top_weight, formatKg(points.maxOf { it.topWeightKg }), pluralStringResource(R.plurals.workout_count, points.size, points.size)))
                Spacer(Modifier.height(8.dp))
            }
            items(points.reversed(), key = { it.workoutId }) { p ->
                Column {
                    Text(fmtDate(p.date), style = MaterialTheme.typography.labelLarge)
                    Text(p.sets.map { stringResource(R.string.weight_reps, formatKg(it.weightKg), it.reps) }.joinToString(", "))
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
