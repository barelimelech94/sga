package com.bar.gym.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bar.gym.GymApp
import com.bar.gym.data.Session
import com.bar.gym.domain.estimateSeconds
import com.bar.gym.domain.formatMinutes
import kotlinx.coroutines.launch

class SessionsViewModel(app: Application) : AndroidViewModel(app) {
    val dao = (app as GymApp).db.dao()
    val sessions = dao.observeSessions()
    val items = dao.observeAllSessionExercises()
    val active = dao.observeActiveWorkout()
    fun delete(id: Long) = viewModelScope.launch { dao.deleteSession(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onStart: (Long) -> Unit,
    onResume: () -> Unit,
    onEdit: (Long) -> Unit,
    onExercises: () -> Unit,
    onSettings: () -> Unit,
    vm: SessionsViewModel = viewModel(),
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle(emptyList())
    val all by vm.items.collectAsStateWithLifecycle(emptyList())
    val active by vm.active.collectAsStateWithLifecycle(null)
    var toDelete by remember { mutableStateOf<Session?>(null) }
    val bySession = all.groupBy { it.sessionId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sessions") },
                actions = {
                    IconButton(onClick = onExercises) { Icon(Icons.Default.FitnessCenter, "Exercise bank") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(-1) }) { Icon(Icons.Default.Add, "Add session") }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            active?.let { w ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Workout in progress: ${w.sessionName}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = onResume) { Text("Resume") }
                        }
                    }
                }
            }
            items(sessions, key = { it.id }) { s ->
                val list = bySession[s.id].orEmpty()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleLarge)
                        Text("${list.size} exercises · ${formatMinutes(estimateSeconds(list))}", style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { onStart(s.id) }, Modifier.height(48.dp)) { Text("Start") }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { onEdit(s.id) }, Modifier.size(48.dp)) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton(onClick = { toDelete = s }, Modifier.size(48.dp)) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete session?") },
            text = { Text("Delete \"${s.name}\"? Past workouts are kept.") },
            confirmButton = { TextButton(onClick = { vm.delete(s.id); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}
