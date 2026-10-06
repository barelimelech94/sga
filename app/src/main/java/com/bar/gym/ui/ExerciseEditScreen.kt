package com.bar.gym.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bar.gym.GymApp
import com.bar.gym.data.Exercise
import com.bar.gym.domain.formatKg
import kotlinx.coroutines.launch

class ExerciseEditViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as GymApp).db
    private val dao = db.dao()

    suspend fun load(id: Long): Exercise? = if (id == -1L) null else dao.getExercise(id)

    fun save(e: Exercise, done: () -> Unit) = viewModelScope.launch { dao.upsertExercise(e); done() }

    fun delete(id: Long, done: () -> Unit) = viewModelScope.launch {
        dao.deleteSessionExercisesForExercise(id)
        if (dao.countLogsForExercise(id) > 0) dao.archiveExercise(id) else dao.deleteExercise(id)
        done()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditScreen(id: Long, onDone: () -> Unit, vm: ExerciseEditViewModel = viewModel()) {
    var loaded by remember { mutableStateOf<Exercise?>(null) }
    var ready by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("0") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("10") }
    var rest by remember { mutableStateOf("120") }
    var work by remember { mutableStateOf("40") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(emptyList<String>()) } // field values as loaded (the defaults, for a new exercise)
    fun values() = listOf(name, body, weight, sets, reps, rest, work, notes)
    fun dirty() = ready && values() != saved

    LaunchedEffect(id) {
        vm.load(id)?.let { e ->
            loaded = e; name = e.name; body = e.bodyPart; weight = formatKg(e.weightKg)
            sets = "${e.sets}"; reps = "${e.reps}"; rest = "${e.restSec}"; work = "${e.workSec}"; notes = e.notes
        }
        saved = values()
        ready = true
    }
    BackHandler(enabled = dirty()) { confirmLeave = true }

    fun save(confirmed: Boolean = false) {
        val w = weight.trim().replace(',', '.').toDoubleOrNull()
        val s = sets.trim().toIntOrNull(); val r = reps.trim().toIntOrNull()
        val rs = rest.trim().toIntOrNull(); val wk = work.trim().toIntOrNull()
        error = when {
            name.isBlank() -> "Name is required"
            w == null || w < 0 || w.isNaN() || w.isInfinite() -> "Weight must be a number, 0 or more"
            s == null || s < 1 -> "Sets must be at least 1"
            r == null || r < 1 -> "Reps must be at least 1"
            rs == null || rs < 0 -> "Rest must be 0 or more seconds"
            wk == null || wk < 0 -> "Work must be 0 or more seconds"
            else -> null
        }
        if (error != null) return
        if (loaded != null && dirty() && !confirmed) { confirmSave = true; return }
        vm.save(
            Exercise(id = loaded?.id ?: 0, name = name.trim(), bodyPart = body.trim(), weightKg = w!!, sets = s!!, reps = r!!,
                restSec = rs!!, workSec = wk!!, notes = notes, archived = false),
            onDone,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == -1L) "New exercise" else "Edit exercise") },
                navigationIcon = {
                    IconButton(onClick = { if (dirty()) confirmLeave = true else onDone() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        if (!ready) return@Scaffold
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), label = { Text("Body part") }, singleLine = true)
            OutlinedTextField(weight, { weight = it }, Modifier.fillMaxWidth(), label = { Text("Weight (kg)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(sets, { sets = it }, Modifier.fillMaxWidth(), label = { Text("Sets") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(reps, { reps = it }, Modifier.fillMaxWidth(), label = { Text("Reps") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(rest, { rest = it }, Modifier.fillMaxWidth(), label = { Text("Rest (sec)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(work, { work = it }, Modifier.fillMaxWidth(), label = { Text("Work (sec)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { save() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
            if (loaded != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete exercise?",
            text = "It will be removed from all sessions. Logged history is kept.",
            confirm = "Delete",
            onConfirm = { confirmDelete = false; vm.delete(id, onDone) },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmSave) {
        ConfirmDialog(
            title = "Save changes?",
            text = "\"${name.trim()}\" will be updated.",
            confirm = "Save",
            onConfirm = { confirmSave = false; save(confirmed = true) },
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
