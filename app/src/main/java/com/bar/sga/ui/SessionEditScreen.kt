package com.bar.sga.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import com.bar.sga.SgApp
import com.bar.sga.R
import com.bar.sga.data.Exercise
import com.bar.sga.data.Session
import com.bar.sga.data.SessionExercise
import com.bar.sga.data.bodyGroup
import com.bar.sga.domain.estimateSeconds
import com.bar.sga.domain.formatKg
import com.bar.sga.domain.roundMinutes
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
    private val db = (app as SgApp).db
    private val dao = db.dao()
    val bank = dao.observeExercises()
    var loadedId: Long? = null
    var name by mutableStateOf("")
    val rows = mutableStateListOf<Row>()
    private var nextKey = 1L
    private var savedName by mutableStateOf("")
    private var savedRows by mutableStateOf(emptyList<Row>())

    /** Read in composition (BackHandler), so every input must be snapshot state; `load` sets savedName together with name. */
    val dirty get() = name != savedName || rows.toList() != savedRows

    private fun SessionExercise.toRow() = Row(nextKey++, exerciseId, formatKg(weightKg), "$sets", "$reps", "$restSec", "$workSec")

    suspend fun load(id: Long) {
        if (loadedId == id) return // survive recomposition/rotation: keep edits
        loadedId = id
        if (id == -1L) return
        name = dao.getSession(id)?.name ?: ""
        savedName = name
        rows.clear()
        // ponytail: one-shot read of current items; flow isn't needed while editing
        rows.addAll(dao.getSessionExercises(id).map { it.toRow() })
        savedRows = rows.toList()
    }

    /** Returns the new row's key. */
    fun add(e: Exercise): Long {
        val key = nextKey++
        rows.add(Row(key, e.id, formatKg(e.weightKg), "${e.sets}", "${e.reps}", "${e.restSec}", "${e.workSec}"))
        return key
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
    // One card open at a time: closed cards show a summary line, the open one shows its fields.
    var open by remember { mutableStateOf<Long?>(null) }
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
                title = { Text(stringResource(if (id == -1L) R.string.new_session else R.string.edit_session)) },
                navigationIcon = {
                    IconButton(onClick = { if (vm.dirty) confirmLeave = true else onDone() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(
                        onClick = { if (id != -1L && vm.dirty) confirmSave = true else vm.save(id, onDone) },
                        enabled = vm.name.isNotBlank(),
                    ) { Text(stringResource(R.string.save)) }
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
                OutlinedTextField(vm.name, { vm.name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.session_name)) }, singleLine = true)
            }
            item(key = "estimate") {
                Text(stringResource(R.string.estimated_duration, stringResource(R.string.minutes_format, roundMinutes(vm.estimate()))), style = MaterialTheme.typography.titleMedium)
            }
            items(vm.rows, key = { it.key }) { r ->
                ReorderableItem(reorder, key = r.key) { isDragging ->
                    val sel = r.key == open
                    fun upd(n: Row) { val i = vm.rows.indexOfFirst { it.key == n.key }; if (i >= 0) vm.rows[i] = n }
                    Card(
                        onClick = { open = if (sel) null else r.key },
                        modifier = Modifier.fillMaxWidth(),
                        colors = selectableCardColors(sel),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 0.dp),
                    ) {
                        Column(Modifier.padding(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {}, Modifier.size(48.dp).draggableHandle()) { Icon(Icons.Default.DragHandle, stringResource(R.string.reorder)) }
                                Column(Modifier.weight(1f)) {
                                    Text(names[r.exerciseId] ?: stringResource(R.string.removed_exercise), style = MaterialTheme.typography.titleMedium)
                                    if (!sel) Text(rowSummary(r), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (sel) IconButton(onClick = { removing = r }, Modifier.size(48.dp)) { Icon(Icons.Default.Close, stringResource(R.string.remove)) }
                            }
                            AnimatedVisibility(sel, enter = Motion.reveal, exit = Motion.hide) {
                                // extra bottom space lives inside the animated part, so it grows with it instead of jumping
                                Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Field(stringResource(R.string.unit_kg), r.weight, KeyboardType.Decimal, Modifier.weight(1f)) { upd(r.copy(weight = it)) }
                                        Field(stringResource(R.string.field_sets), r.sets, KeyboardType.Number, Modifier.weight(1f)) { upd(r.copy(sets = it)) }
                                        Field(stringResource(R.string.unit_reps), r.reps, KeyboardType.Number, Modifier.weight(1f)) { upd(r.copy(reps = it)) }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Field(stringResource(R.string.field_rest), r.rest, KeyboardType.Number, Modifier.weight(1f)) { upd(r.copy(rest = it)) }
                                        Field(stringResource(R.string.field_work), r.work, KeyboardType.Number, Modifier.weight(1f)) { upd(r.copy(work = it)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "add") {
                Button(onClick = { showPicker = true }, Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.add_exercise)) }
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Text(
                stringResource(R.string.add_exercise),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 24.dp)) {
                bank.groupBy { it.bodyGroup }.toSortedMap().forEach { (group, exs) ->
                    item(key = "h$group") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BodyGroupIcon(group, tint = MaterialTheme.colorScheme.onSecondaryContainer, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(group.labelRes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    items(exs.size, key = { exs[it].id }) { k ->
                        val e = exs[k]
                        Column(Modifier.fillMaxWidth().clickable { open = vm.add(e); showPicker = false }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(e.name, style = MaterialTheme.typography.titleMedium)
                            Text(e.defaultsText(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    removing?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.remove_exercise_title),
            text = stringResource(R.string.remove_exercise_text, names[r.exerciseId] ?: stringResource(R.string.removed_exercise)),
            confirm = stringResource(R.string.remove),
            onConfirm = { vm.rows.removeAll { it.key == r.key }; if (open == r.key) open = null; removing = null },
            onDismiss = { removing = null },
        )
    }
    if (confirmSave) {
        ConfirmDialog(
            title = stringResource(R.string.save_changes_title),
            text = stringResource(R.string.update_named, vm.name.trim()),
            confirm = stringResource(R.string.save),
            onConfirm = { confirmSave = false; vm.save(id, onDone) },
            onDismiss = { confirmSave = false },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(R.string.discard_changes_title),
            text = stringResource(R.string.unsaved_changes),
            confirm = stringResource(R.string.discard),
            onConfirm = { confirmLeave = false; onDone() },
            onDismiss = { confirmLeave = false },
            dismiss = stringResource(R.string.keep_editing),
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

/** One line for a closed card, e.g. "60 kg · 3 × 10 · rest 90s"; zero weight, rest and work are left out. */
@Composable
private fun rowSummary(r: Row): String {
    val x = r.toItem(0, 0)
    return listOfNotNull(
        if (x.weightKg > 0) stringResource(R.string.sum_kg, formatKg(x.weightKg)) else null,
        stringResource(R.string.sum_sets_reps, x.sets, x.reps),
        if (x.workSec > 0) stringResource(R.string.sum_work, x.workSec) else null,
        if (x.restSec > 0) stringResource(R.string.sum_rest, x.restSec) else null,
    ).joinToString(" · ")
}
