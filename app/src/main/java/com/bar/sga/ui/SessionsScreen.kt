package com.bar.sga.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bar.sga.SgApp
import com.bar.sga.R
import com.bar.sga.data.Session
import com.bar.sga.data.bodyGroup
import com.bar.sga.data.Workout
import com.bar.sga.timer.RestTimer
import com.bar.sga.domain.estimateSeconds
import com.bar.sga.domain.roundMinutes
import com.bar.sga.domain.sessionBodyGroups
import kotlinx.coroutines.launch

class SessionsViewModel(app: Application) : AndroidViewModel(app) {
    val dao = (app as SgApp).db.dao()
    val sessions = dao.observeSessions()
    val items = dao.observeAllSessionExercises()
    val exercises = dao.observeExercises()
    val active = dao.observeActiveWorkout()
    fun delete(id: Long) = viewModelScope.launch { dao.deleteSession(id) }

    /** Never creates a second active workout: resumes it, or asks via [onConflict] if it is another session's. */
    fun start(s: Session, onStarted: () -> Unit, onConflict: (Workout) -> Unit) = viewModelScope.launch {
        val cur = dao.getActiveWorkout()
        when {
            cur == null -> { insertNew(s); onStarted() }
            cur.sessionId == s.id -> onStarted()
            else -> onConflict(cur)
        }
    }

    fun finishAndStart(old: Workout, s: Session, onStarted: () -> Unit) = viewModelScope.launch {
        RestTimer.cancel(getApplication(), dao, old)
        dao.getWorkout(old.id)?.let { dao.updateWorkout(it.copy(finishedAt = System.currentTimeMillis(), restEndsAt = null)) }
        insertNew(s)
        onStarted()
    }

    private suspend fun insertNew(s: Session) {
        dao.insertWorkout(Workout(sessionId = s.id, sessionName = s.name, startedAt = System.currentTimeMillis()))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onStart: () -> Unit,
    onResume: () -> Unit,
    onEdit: (Long) -> Unit,
    onExercises: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    vm: SessionsViewModel = viewModel(),
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle(emptyList())
    val all by vm.items.collectAsStateWithLifecycle(emptyList())
    val exercises by vm.exercises.collectAsStateWithLifecycle(emptyList())
    val active by vm.active.collectAsStateWithLifecycle(null)
    var toDelete by remember { mutableStateOf<Session?>(null) }
    var conflict by remember { mutableStateOf<Pair<Workout, Session>?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val bySession = all.groupBy { it.sessionId }
    val groupOf = exercises.associate { it.id to it.bodyGroup }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sessions_title)) },
                actions = {
                    IconButton(onClick = onHistory) { Icon(Icons.Default.History, stringResource(R.string.workout_history)) }
                    IconButton(onClick = onExercises) { Icon(Icons.Default.FitnessCenter, stringResource(R.string.exercise_bank)) }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(-1) }) { Icon(Icons.Default.Add, stringResource(R.string.add_session)) }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            active?.let { w ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.workout_in_progress_named, w.sessionName), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = onResume) { Text(stringResource(R.string.resume)) }
                        }
                    }
                }
            }
            items(sessions, key = { it.id }) { s ->
                val list = bySession[s.id].orEmpty()
                val selected = s.id == selectedId
                Card(
                    Modifier.fillMaxWidth().clickable { selectedId = if (selected) null else s.id },
                    colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    else CardDefaults.cardColors(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(
                                R.string.session_summary,
                                pluralStringResource(R.plurals.exercise_count, list.size, list.size),
                                stringResource(R.string.minutes_format, roundMinutes(estimateSeconds(list))),
                            ), style = MaterialTheme.typography.bodyLarge)
                        val groupIcons = sessionBodyGroups(list, groupOf)
                        if (groupIcons.isNotEmpty()) BodyGroupIcons(groupIcons, 28.dp, Modifier.padding(top = 8.dp, bottom = if (selected) 8.dp else 0.dp))
                        if (selected) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { vm.start(s, onStart) { conflict = it to s } }, Modifier.height(48.dp)) { Text(stringResource(R.string.start)) }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { onEdit(s.id) }, Modifier.size(48.dp)) { Icon(Icons.Default.Edit, stringResource(R.string.edit)) }
                                IconButton(onClick = { toDelete = s }, Modifier.size(48.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                            }
                        }
                    }
                }
            }
        }
    }
    conflict?.let { (old, s) ->
        ConfirmDialog(
            title = stringResource(R.string.workout_in_progress),
            text = stringResource(R.string.finish_and_start_text, old.sessionName),
            confirm = stringResource(R.string.finish_and_start),
            onConfirm = { vm.finishAndStart(old, s, onStart); conflict = null },
            onDismiss = { conflict = null },
        )
    }
    toDelete?.let { s ->
        ConfirmDialog(
            title = stringResource(R.string.delete_session_title),
            text = stringResource(R.string.delete_session_text, s.name),
            confirm = stringResource(R.string.delete),
            onConfirm = { vm.delete(s.id); if (selectedId == s.id) selectedId = null; toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}
