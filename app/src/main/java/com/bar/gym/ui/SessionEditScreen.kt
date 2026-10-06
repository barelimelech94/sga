package com.bar.gym.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    private var savedName = ""
    private var savedRows = emptyList<Row>()
    val dirty get() = name != savedName || rows.toList() != savedRows

    private fun SessionExercise.toRow() = Row(nextKey++, exerciseId, formatKg(weightKg), "$sets", "$reps", "$restSec", "$workSec")

    suspend fun load(id: Long) {
        if (loadedId == id) return // survive recomposition/rotation: keep edits
        loadedId = id
        if (id == -1L) return
        name = dao.getSession(id)?.name ?: ""
        rows.clear()
        // ponytail: one-shot read of current items; flow isn't needed while editing
        rows.addAll(dao.getSessionExercises(id).map { it.toRow() })
        savedName = name
        savedRows = rows.toList()
    }

    fun add(e: Exercise) {
        rows.add(Row(nextKey++, e.id, formatKg(e.weightKg), "${e.sets}", "${e.reps}", "${e.restSec}", "${e.workSec}"))
    }

    /** Keys, not list indices: the list also holds the name field, the estimate and the add button. */
    fun move(fromKey: Any, toKey: Any) {
        val i = rows.indexOfFirst { it.key == fromKey }; val j = rows.indexOfFirst { it.key == toKey }
        if (i >= 0 && j >= 0) rows.add(j, rows.removeAt(i))
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
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var removing by remember { mutableStateOf<Row?>(null) }
    var confirmSave by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(id) { vm.load(id); ready = true }
    val names = bank.associate { it.id to it.name }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to -> vm.move(from.key, to.key) }
    BackHandler(enabled = vm.dirty) { confirmLeave = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == -1L) "New session" else "Edit session") },
                navigationIcon = {
                    IconButton(onClick = { if (vm.dirty) confirmLeave = true else onDone() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(
                        onClick = { if (id != -1L && vm.dirty) confirmSave = true else vm.save(id, onDone) },
                        enabled = vm.name.isNotBlank(),
                    ) { Text("Save") }
                },
            )
        },
    ) { pad ->
        if (!ready) return@Scaffold
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "name") {
                OutlinedTextField(vm.name, { vm.name = it }, Modifier.fillMaxWidth(), label = { Text("Session name") }, singleLine = true)
            }
            item(key = "estimate") {
                Text("Estimated duration: ${formatMinutes(vm.estimate())}", style = MaterialTheme.typography.titleMedium)
            }
            items(vm.rows, key = { it.key }) { r ->
                ReorderableItem(reorder, key = r.key) { isDragging ->
                    val sel = r.key in selected
                    fun upd(n: Row) { val i = vm.rows.indexOfFirst { it.key == n.key }; if (i >= 0) vm.rows[i] = n }
                    Card(
                        onClick = { selected = if (sel) selected - r.key else selected + r.key },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (sel) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 0.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {}, Modifier.size(48.dp).draggableHandle()) { Icon(Icons.Default.DragHandle, "Reorder") }
                                Text(names[r.exerciseId] ?: "(removed exercise)", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                if (sel) IconButton(onClick = { removing = r }, Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Remove") }
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
            }
            item(key = "add") {
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
    removing?.let { r ->
        ConfirmDialog(
            title = "Remove exercise?",
            text = "Remove \"${names[r.exerciseId] ?: "(removed exercise)"}\" from this session?",
            confirm = "Remove",
            onConfirm = { vm.rows.removeAll { it.key == r.key }; selected = selected - r.key; removing = null },
            onDismiss = { removing = null },
        )
    }
    if (confirmSave) {
        ConfirmDialog(
            title = "Save changes?",
            text = "\"${vm.name.trim()}\" will be updated.",
            confirm = "Save",
            onConfirm = { confirmSave = false; vm.save(id, onDone) },
            onDismiss = { confirmSave = false },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "You have unsaved changes.",
            confirm = "Discard",
            onConfirm = { confirmLeave = false; onDone() },
            onDismiss = { confirmLeave = false },
            dismiss = "Keep editing",
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
