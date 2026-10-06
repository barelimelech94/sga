package com.bar.gym.ui

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bar.gym.GymApp
import com.bar.gym.data.SessionExercise
import com.bar.gym.data.SetLog
import com.bar.gym.data.Workout
import com.bar.gym.domain.*
import com.bar.gym.timer.RestTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Wrapper so "not loaded yet" (null) differs from "no active workout" (Loaded(null)). */
class Loaded(val workout: Workout?)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(app: Application) : AndroidViewModel(app) {
    val dao = (app as GymApp).db.dao()
    private val share = SharingStarted.WhileSubscribed(5000)
    private val active = dao.observeActiveWorkout()
    val workout = active.map { Loaded(it) }.stateIn(viewModelScope, share, null)
    val items = active.flatMapLatest { w -> w?.let { dao.observeSessionExercises(it.sessionId) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, share, emptyList())
    val logs = active.flatMapLatest { w -> w?.let { dao.observeLogs(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, share, emptyList())
    val names = dao.observeExercises().map { l -> l.associate { it.id to it.name } }
        .stateIn(viewModelScope, share, emptyMap())

    /** Explicit choice (SessionExercise.id); null = use [defaultSelection]. Not persisted. */
    val selectedId = MutableStateFlow<Long?>(null)

    fun setDone(item: SessionExercise, weight: Double, reps: Int) = viewModelScope.launch {
        val w = dao.getActiveWorkout() ?: return@launch
        val now = System.currentTimeMillis()
        val done = logs.value.count { it.exerciseId == item.exerciseId }
        val log = SetLog(workoutId = w.id, exerciseId = item.exerciseId, setNumber = done + 1,
            weightKg = weight, reps = reps, completedAt = now)
        dao.insertLog(log)
        if (item.restSec > 0) RestTimer.start(getApplication(), dao, w, now + item.restSec * 1000L)
        else if (w.restEndsAt != null) RestTimer.cancel(getApplication(), dao, w)
        if (done + 1 == item.sets) nextUp(items.value, logs.value + log)?.let { selectedId.value = it.id }
    }

    fun undo(item: SessionExercise) = viewModelScope.launch {
        val w = dao.getActiveWorkout() ?: return@launch
        val last = logs.value.filter { it.exerciseId == item.exerciseId }
            .maxWithOrNull(compareBy({ it.completedAt }, { it.id })) ?: return@launch
        dao.deleteLog(last.id)
        RestTimer.cancel(getApplication(), dao, w)
    }

    fun addRest(w: Workout, endsAt: Long) = viewModelScope.launch { RestTimer.start(getApplication(), dao, w, endsAt + 30_000) }
    fun skipRest(w: Workout) = viewModelScope.launch { RestTimer.cancel(getApplication(), dao, w) }

    fun finish() = viewModelScope.launch {
        val w = dao.getActiveWorkout() ?: return@launch
        RestTimer.cancel(getApplication(), dao, w)
        dao.getWorkout(w.id)?.let { dao.updateWorkout(it.copy(finishedAt = System.currentTimeMillis(), restEndsAt = null)) }
    }

    /** Deletes the workout and, by cascade, its logs. The screen leaves on its own once there is no active workout. */
    fun abort() = viewModelScope.launch {
        val w = dao.getActiveWorkout() ?: return@launch
        RestTimer.cancel(getApplication(), dao, w)
        dao.deleteWorkout(w.id)
    }
}

@Composable
private fun rememberNow(): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.longValue = System.currentTimeMillis()
            delay(250)
        }
    }
    return now
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(onGone: () -> Unit, vm: WorkoutViewModel = viewModel()) {
    val loaded by vm.workout.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val explicit by vm.selectedId.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val ctx = LocalContext.current
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { // once per screen visit; denial just means in-app countdown only
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val l = loaded ?: return
    val workout = l.workout
    if (workout == null) {
        LaunchedEffect(Unit) { onGone() }
        return
    }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmAbort by remember { mutableStateOf(false) }
    val sorted = items.sortedBy { it.position }
    val next = nextUp(items, logs)
    val selected = sorted.firstOrNull { it.id == explicit } ?: defaultSelection(items, logs) ?: sorted.firstOrNull()
    val total = items.sumOf { it.sets }
    val done = items.sumOf { doneSets(it, logs) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(workout.sessionName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val now by rememberNow()
                        Text(formatClock((now - workout.startedAt) / 1000), style = MaterialTheme.typography.bodyMedium)
                    }
                },
                actions = {
                    TextButton(onClick = { confirmAbort = true }) { Text("Abort", color = MaterialTheme.colorScheme.error, fontSize = 18.sp) }
                    TextButton(onClick = { confirmFinish = true }) { Text("Finish", fontSize = 18.sp) }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).imePadding().fillMaxSize()) {
            Text(
                "$done / $total sets done · about ${formatMinutes(remainingSeconds(items, logs))} left",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            RestBanner(workout, vm)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sorted, key = { it.id }) { it ->
                    val d = doneSets(it, logs)
                    val complete = d >= it.sets
                    val isNext = it.id == next?.id
                    Card(
                        Modifier.fillMaxWidth().alpha(if (complete) 0.55f else 1f).clickable { vm.selectedId.value = it.id },
                        colors = CardDefaults.cardColors(
                            containerColor = if (it.id == selected?.id) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        border = if (isNext) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Row(Modifier.padding(12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if (isNext) Text("NEXT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                Text(names[it.exerciseId] ?: "(removed)", style = MaterialTheme.typography.titleMedium)
                                Text("${formatKg(it.weightKg)} kg × ${it.reps}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (complete) Icon(Icons.Default.Check, "Done", Modifier.padding(end = 8.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("$d/${it.sets}", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
            if (selected != null) {
                ExercisePanel(workout, selected, names[selected.exerciseId] ?: "(removed)", logs, vm)
            }
        }
    }

    if (confirmFinish) {
        ConfirmDialog(
            title = "Finish workout?",
            text = if (done < total) "${total - done} planned sets are not done yet." else "All planned sets are done.",
            confirm = "Finish",
            onConfirm = { confirmFinish = false; vm.finish() },
            onDismiss = { confirmFinish = false },
            dismiss = "Keep going",
        )
    }
    if (confirmAbort) {
        ConfirmDialog(
            title = "Abort workout?",
            text = if (logs.isEmpty()) "Nothing has been logged yet."
            else "The workout and its ${logs.size} logged sets will be deleted. This cannot be undone.",
            confirm = "Abort",
            onConfirm = { confirmAbort = false; vm.abort() },
            onDismiss = { confirmAbort = false },
            dismiss = "Keep going",
        )
    }
}

@Composable
private fun RestBanner(workout: Workout, vm: WorkoutViewModel) {
    val ends = workout.restEndsAt ?: return
    val now by rememberNow()
    val context = LocalContext.current
    // Vibrate only if the countdown was still running when this effect started (not on resume after it ended).
    LaunchedEffect(ends) {
        val left = ends - System.currentTimeMillis()
        if (left > 0) {
            delay(left)
            // the "Rest over" notification channel vibrates and rings; only do it here if it can't be shown
            if (context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true) return@LaunchedEffect
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
            RestTimer.playOverSound(context)
        }
    }
    val leftMs = ends - now
    if (leftMs < -60_000) return // stale "Rest over" is hidden after a minute
    val over = leftMs <= 0
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (over) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (over) "Rest over" else formatClock((leftMs + 999) / 1000),
                Modifier.weight(1f),
                fontSize = if (over) 28.sp else 44.sp,
                style = MaterialTheme.typography.displaySmall,
            )
            if (!over) {
                FilledTonalButton(onClick = { vm.addRest(workout, ends) }, Modifier.height(56.dp)) { Text("+30s") }
                Spacer(Modifier.width(8.dp))
            }
            Button(onClick = { vm.skipRest(workout) }, Modifier.height(56.dp)) { Text(if (over) "Dismiss" else "Skip") }
        }
    }
}

@Composable
private fun ExercisePanel(workout: Workout, item: SessionExercise, name: String, logs: List<SetLog>, vm: WorkoutViewModel) {
    val mine = logs.filter { it.exerciseId == item.exerciseId }
    val done = mine.size
    val prev = mine.maxWithOrNull(compareBy({ it.completedAt }, { it.id }))
    var weight by remember(item.id, done) { mutableStateOf(formatKg(prev?.weightKg ?: item.weightKg)) }
    var reps by remember(item.id, done) { mutableStateOf((prev?.reps ?: item.reps).toString()) }
    var last by remember(item.exerciseId) { mutableStateOf(emptyList<SetLog>()) }
    var confirmUndo by remember(item.id) { mutableStateOf(false) }
    val now = rememberNow()
    val resting by remember(workout.restEndsAt) { derivedStateOf { (workout.restEndsAt ?: 0L) > now.value } }
    LaunchedEffect(item.exerciseId, workout.id) { last = vm.dao.lastTimeLogs(item.exerciseId, workout.id) }
    val w = weight.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val r = reps.toIntOrNull()?.coerceAtLeast(1) ?: 1

    Card(
        Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    if (done >= item.sets) "Done · $done logged" else "Set ${done + 1} of ${item.sets}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (last.isNotEmpty()) {
                Text("Last time: " + last.joinToString(", ") { "${formatKg(it.weightKg)} × ${it.reps}" }, style = MaterialTheme.typography.bodyMedium)
            }
            Stepper("kg", weight, KeyboardType.Decimal, { weight = it },
                { weight = formatKg((w - 2.5).coerceAtLeast(0.0)) }, { weight = formatKg(w + 2.5) })
            Stepper("reps", reps, KeyboardType.Number, { reps = it.filter(Char::isDigit) },
                { reps = (r - 1).coerceAtLeast(1).toString() }, { reps = (r + 1).toString() })
            if (!resting) Button(onClick = { vm.setDone(item, w, r) }, Modifier.fillMaxWidth().height(64.dp)) {
                Text(if (done >= item.sets) "Extra set" else "Set done", fontSize = 22.sp)
            }
            if (done > 0) TextButton(onClick = { confirmUndo = true }, Modifier.align(Alignment.CenterHorizontally)) { Text("Undo last set") }
        }
    }
    if (confirmUndo && prev != null) {
        ConfirmDialog(
            title = "Undo last set?",
            text = "Set ${prev.setNumber} (${formatKg(prev.weightKg)} kg × ${prev.reps}) will be deleted.",
            confirm = "Undo",
            onConfirm = { confirmUndo = false; vm.undo(item) },
            onDismiss = { confirmUndo = false },
        )
    }
}

@Composable
private fun Stepper(unit: String, value: String, type: KeyboardType, onValue: (String) -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onMinus, Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.Remove, "Minus $unit") }
        OutlinedTextField(
            value, onValue, Modifier.weight(1f),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
            suffix = { Text(unit) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type),
        )
        FilledTonalButton(onClick = onPlus, Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.Add, "Plus $unit") }
    }
}
