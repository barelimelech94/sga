package com.bar.gym.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import com.bar.gym.GymApp
import com.bar.gym.data.Exercise
import com.bar.gym.data.Session
import com.bar.gym.data.SessionExercise
import com.bar.gym.domain.estimateSeconds
import com.bar.gym.domain.formatKg
import com.bar.gym.domain.formatMinutes
import kotlinx.coroutines.launch

/** Editable row: raw strings while typing, parsed when estimating/saving. */
data class Row(val key: Long, val exerciseId: Long, val weight: String, val sets: String, val reps: String, val rest: String, val work: String) {
    fun toItem(sessionId: Long, position: Int) = SessionExercise(
        sessionId = sessionId, exerciseId = exerciseId, position = position,
        weightKg = weight.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0,
        sets = sets.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1,
        reps = reps.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1,
        restSec = rest.trim().toIntOrNull() ?: 0,
        workSec = work.trim().toIntOrNull() ?: 0,
    )
}

class SessionEditViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as GymApp).db
    private val dao = db.dao()
    val bank = dao.observeExercises()
    var loadedId: Long? = null
    var name by mutableStateOf("")
    val rows = mutableStateListOf<Row>()
    private var nextKey = 1L

    private fun SessionExercise.toRow() = Row(nextKey++, exerciseId, formatKg(weightKg), "$sets", "$reps", "$restSec", "$workSec")

    suspend fun load(id: Long) {
        if (loadedId == id) return // survive recomposition/rotation: keep edits
        loadedId = id
        if (id == -1L) return
        name = dao.getSession(id)?.name ?: ""
        rows.clear()
        // ponytail: one-shot read of current items; flow isn't needed while editing
        rows.addAll(dao.getSessionExercises(id).map { it.toRow() })
    }

    fun add(e: Exercise) {
        rows.add(Row(nextKey++, e.id, formatKg(e.weightKg), "${e.sets}", "${e.reps}", "${e.restSec}", "${e.workSec}"))
    }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in rows.indices) return
        val r = rows.removeAt(i); rows.add(j, r)
    }

    fun estimate() = estimateSeconds(rows.mapIndexed { i, r -> r.toItem(0, i) })

    fun save(id: Long, done: () -> Unit) = viewModelScope.launch {
        db.withTransaction {
            val returned = dao.upsertSession(Session(if (id == -1L) 0 else id, name.trim()))
            val sid = if (id == -1L) returned else id
            dao.deleteSessionExercisesFor(sid)
            dao.upsertSessionExercises(rows.mapIndexed { i, r -> r.toItem(sid, i) })
        }
        done()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditScreen(id: Long, onDone: () -> Unit, vm: SessionEditViewModel = viewModel()) {
    val bank by vm.bank.collectAsStateWithLifecycle(emptyList())
    var showPicker by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(id) { vm.load(id); ready = true }
    val names = bank.associate { it.id to it.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == -1L) "New session" else "Edit session") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { vm.save(id, onDone) }, enabled = vm.name.isNotBlank()) { Text("Save") } },
            )
        },
    ) { pad ->
        if (!ready) return@Scaffold
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(vm.name, { vm.name = it }, Modifier.fillMaxWidth(), label = { Text("Session name") }, singleLine = true)
            }
            item {
                Text("Estimated duration: ${formatMinutes(vm.estimate())}", style = MaterialTheme.typography.titleMedium)
            }
            itemsIndexed(vm.rows, key = { _, r -> r.key }) { i, r ->
                fun upd(n: Row) { vm.rows[i] = n }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(names[r.exerciseId] ?: "(removed exercise)", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { vm.move(i, -1) }, Modifier.size(48.dp), enabled = i > 0) { Icon(Icons.Default.ArrowUpward, "Move up") }
                            IconButton(onClick = { vm.move(i, 1) }, Modifier.size(48.dp), enabled = i < vm.rows.lastIndex) { Icon(Icons.Default.ArrowDownward, "Move down") }
                            IconButton(onClick = { vm.rows.removeAt(i) }, Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Remove") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Field("kg", r.weight, KeyboardType.Decimal, Modifier.weight(1.2f)) { upd(r.copy(weight = it)) }
                            Field("sets", r.sets, KeyboardType.Number, Modifier.weight(1f)) { upd(r.copy(sets = it)) }
                            Field("reps", r.reps, KeyboardType.Number, Modifier.weight(1f)) { upd(r.copy(reps = it)) }
                            Field("rest s", r.rest, KeyboardType.Number, Modifier.weight(1f)) { upd(r.copy(rest = it)) }
                            Field("work s", r.work, KeyboardType.Number, Modifier.weight(1f)) { upd(r.copy(work = it)) }
                        }
                    }
                }
            }
            item {
                Button(onClick = { showPicker = true }, Modifier.fillMaxWidth().height(52.dp)) { Text("Add exercise") }
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Add exercise") },
            text = {
                LazyColumn {
                    bank.groupBy { it.bodyPart }.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (part, exs) ->
                        item(key = "h$part") {
                            Text(part.ifBlank { "Other" }, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                        }
                        items(exs.size, key = { exs[it].id }) { k ->
                            TextButton(onClick = { vm.add(exs[k]); showPicker = false }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(exs[k].name, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, modifier, label = { Text(label, maxLines = 1) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
    )
}
