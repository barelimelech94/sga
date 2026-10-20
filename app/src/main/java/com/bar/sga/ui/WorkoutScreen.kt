package com.bar.sga.ui

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.semantics.semantics
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
import com.bar.sga.SgApp
import com.bar.sga.R
import com.bar.sga.data.SessionExercise
import com.bar.sga.data.SetLog
import com.bar.sga.data.Workout
import com.bar.sga.data.bodyGroup
import com.bar.sga.domain.*
import com.bar.sga.timer.RestTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Wrapper so "not loaded yet" (null) differs from "no active workout" (Loaded(null)). */
class Loaded(val workout: Workout?)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(app: Application) : AndroidViewModel(app) {
    val dao = (app as SgApp).db.dao()
    private val share = SharingStarted.WhileSubscribed(5000)
    private val active = dao.observeActiveWorkout()
    val workout = active.map { Loaded(it) }.stateIn(viewModelScope, share, null)
    val items = active.flatMapLatest { w -> w?.let { dao.observeSessionExercises(it.sessionId) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, share, emptyList())
    val logs = active.flatMapLatest { w -> w?.let { dao.observeLogs(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, share, emptyList())
    val names = dao.observeExercises().map { l -> l.associate { it.id to it.name } }
        .stateIn(viewModelScope, share, emptyMap())
    val groups = dao.observeExercises().map { l -> l.associate { it.id to it.bodyGroup } }
        .stateIn(viewModelScope, share, emptyMap())

    /** Explicit choice (SessionExercise.id); null = no exercise selected, panel hidden. Not persisted. */
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

    /** From the rest slider: [seconds] of rest left from now; 0 ends the rest. */
    fun setRest(w: Workout, seconds: Int) = viewModelScope.launch {
        if (seconds <= 0) RestTimer.cancel(getApplication(), dao, w)
        else RestTimer.start(getApplication(), dao, w, System.currentTimeMillis() + seconds * 1000L)
    }

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
    val groups by vm.groups.collectAsStateWithLifecycle()
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
    val selected = sorted.firstOrNull { it.id == explicit }
    val clock = rememberNow()
    val resting by remember(workout.restEndsAt) { derivedStateOf { (workout.restEndsAt ?: 0L) > clock.value } }
    val total = items.sumOf { it.sets }
    val done = items.sumOf { doneSets(it, logs) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(workout.sessionName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val now by rememberNow()
                        val groupIcons = sessionBodyGroups(items, groups)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatClock((now - workout.startedAt) / 1000), style = MaterialTheme.typography.bodyMedium)
                            if (groupIcons.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                BodyGroupIcons(groupIcons, 20.dp)
                            }
                        }
                    }
                },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.abort)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.finish), fontSize = 18.sp) },
                            onClick = { menu = false; confirmFinish = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.abort), color = MaterialTheme.colorScheme.error, fontSize = 18.sp) },
                            onClick = { menu = false; confirmAbort = true },
                        )
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).imePadding().fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.workout_progress, done, total, stringResource(R.string.minutes_format, roundMinutes(remainingSeconds(items, logs)))),
                    style = MaterialTheme.typography.titleMedium,
                )
                LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, Modifier.fillMaxWidth().height(6.dp))
            }
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
                    // The border marks the exercise in the panel; with none open, it points at what's next.
                    val outlined = it.id == (selected ?: next)?.id
                    Card(
                        Modifier.fillMaxWidth().alpha(if (complete) 0.55f else 1f).clickable { vm.selectedId.value = it.id },
                        colors = selectableCardColors(it.id == selected?.id),
                        border = if (outlined) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Row(Modifier.padding(12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if (isNext) Text(stringResource(R.string.next_badge), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                Text(names[it.exerciseId] ?: stringResource(R.string.removed_short), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.weight_kg_reps, formatKg(it.weightKg), it.reps), style = MaterialTheme.typography.bodyMedium)
                            }
                            if (complete) Icon(Icons.Default.Check, stringResource(R.string.done), Modifier.padding(end = 8.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("$d/${it.sets}", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                item {
                    Button({ confirmFinish = true }, Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.finish), fontSize = 20.sp) }
                }
            }
            // Collapsed/expanded is the user's choice and stays as is across exercises and rests.
            var panelExpanded by rememberSaveable { mutableStateOf(true) }
            val panelItem = if (!resting) selected else null
            // keeps the last exercise so the panel can animate out after it is cleared
            val shownItem = remember { arrayOfNulls<SessionExercise>(1) }
            if (panelItem != null) shownItem[0] = panelItem
            AnimatedVisibility(
                visible = panelItem != null,
                enter = Motion.reveal,
                exit = Motion.hide,
            ) {
                shownItem[0]?.let {
                    ExercisePanel(workout, it, names[it.exerciseId] ?: stringResource(R.string.removed_short), logs, vm, panelExpanded) { e -> panelExpanded = e }
                }
            }
        }
    }

    if (confirmFinish) {
        ConfirmDialog(
            title = stringResource(R.string.finish_workout_title),
            text = if (done < total) pluralStringResource(R.plurals.sets_not_done, total - done, total - done) else stringResource(R.string.all_sets_done),
            confirm = stringResource(R.string.finish),
            onConfirm = { confirmFinish = false; vm.finish() },
            onDismiss = { confirmFinish = false },
            dismiss = stringResource(R.string.keep_going),
        )
    }
    if (confirmAbort) {
        ConfirmDialog(
            title = stringResource(R.string.abort_workout_title),
            text = if (logs.isEmpty()) stringResource(R.string.nothing_logged)
            else pluralStringResource(R.plurals.abort_logged_text, logs.size, logs.size),
            confirm = stringResource(R.string.abort),
            onConfirm = { confirmAbort = false; vm.abort() },
            onDismiss = { confirmAbort = false },
            dismiss = stringResource(R.string.keep_going),
        )
    }
}

@Composable
private fun RestBanner(workout: Workout, vm: WorkoutViewModel) {
    val ends = workout.restEndsAt
    val now by rememberNow()
    val context = LocalContext.current
    // Vibrate only if the countdown was still running when this effect started (not on resume after it ended).
    LaunchedEffect(ends) {
        val left = (ends ?: return@LaunchedEffect) - System.currentTimeMillis()
        if (left > 0) {
            delay(left)
            // the alarm receiver plays the chime; the "Rest over" notification vibrates, so only vibrate here if it can't be shown
            if (context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true) return@LaunchedEffect
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    // keeps the last end time so the banner can animate out after the rest is skipped
    val shownEnds = remember { LongArray(1) }
    if (ends != null) shownEnds[0] = ends
    AnimatedVisibility(
        visible = ends != null && ends - now >= -60_000, // stale "Rest over" is hidden after a minute
        enter = Motion.reveal,
        exit = Motion.hide,
    ) {
        RestCard(workout, shownEnds[0], now, vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestCard(workout: Workout, ends: Long, now: Long, vm: WorkoutViewModel) {
    val leftMs = ends - now
    val over = leftMs <= 0
    val leftSec = ((leftMs + 999) / 1000).toInt().coerceAtLeast(0)
    // Slider position while the finger is on it; kept until the new end time arrives so the thumb doesn't jump back.
    var dragSec by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(ends) { dragSec = null }
    // Slider range only grows (3 min, or the rest rounded up to a minute), so the thumb moves steadily as time runs down.
    var maxSec by remember(workout.id) { mutableIntStateOf(180) }
    val range = maxOf(maxSec, (leftSec + 59) / 60 * 60)
    SideEffect { if (dragSec == null && range > maxSec) maxSec = range }
    val container by animateColorAsState(
        if (over) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer, label = "restColor",
    )
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        val rowBottom by animateDpAsState(if (over) 12.dp else 0.dp, Motion.dpSpring, label = "restRowBottom")
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = rowBottom), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (over) stringResource(R.string.rest_over) else formatClock((dragSec ?: leftSec).toLong()),
                Modifier.weight(1f),
                fontSize = if (over) 28.sp else 44.sp,
                style = MaterialTheme.typography.displaySmall,
            )
            if (!over) {
                FilledTonalButton(onClick = { vm.addRest(workout, ends) }, Modifier.height(56.dp)) { Text(stringResource(R.string.add_30s)) }
                Spacer(Modifier.width(8.dp))
            }
            Button(onClick = { vm.skipRest(workout) }, Modifier.height(56.dp)) { Text(stringResource(if (over) R.string.dismiss else R.string.skip)) }
        }
        AnimatedVisibility(!over, enter = Motion.reveal, exit = Motion.hide) {
            val label = stringResource(R.string.rest_time)
            val interaction = remember { MutableInteractionSource() }
            Slider(
                value = (dragSec ?: leftSec).toFloat().coerceAtMost(range.toFloat()),
                onValueChange = { dragSec = ((it / 5).roundToInt() * 5) },
                onValueChangeFinished = { dragSec?.let { vm.setRest(workout, it) } },
                valueRange = 0f..range.toFloat(),
                interactionSource = interaction,
                // default track; the default handle (4 × 44 dp) towers over it, so it is cut to just past the track
                thumb = { SliderDefaults.Thumb(interaction, thumbSize = DpSize(4.dp, 24.dp)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun ExercisePanel(
    workout: Workout, item: SessionExercise, name: String, logs: List<SetLog>, vm: WorkoutViewModel,
    expanded: Boolean, onExpanded: (Boolean) -> Unit,
) {
    val mine = logs.filter { it.exerciseId == item.exerciseId }
    val done = mine.size
    val prev = mine.maxWithOrNull(compareBy({ it.completedAt }, { it.id }))
    var weight by remember(item.id, done) { mutableStateOf(formatKg(prev?.weightKg ?: item.weightKg)) }
    var reps by remember(item.id, done) { mutableStateOf((prev?.reps ?: item.reps).toString()) }
    var last by remember(item.exerciseId) { mutableStateOf(emptyList<SetLog>()) }
    var confirmUndo by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.exerciseId, workout.id) { last = vm.dao.lastTimeLogs(item.exerciseId, workout.id) }
    val w = weight.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val r = reps.toIntOrNull()?.coerceAtLeast(1) ?: 1
    // Drag down to collapse, up to expand (dragging only: taps on the panel never toggle it).
    var drag by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    // Visual feedback: the panel follows the finger a little in the direction it can go, then springs back.
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val isExpanded by rememberUpdatedState(expanded)
    val dragState = rememberDraggableState { d ->
        drag += d
        val target = (if (isExpanded) drag.coerceAtLeast(0f) else drag.coerceAtMost(0f)) * 0.3f
        scope.launch { nudge.snapTo(target) }
    }

    Card(
        Modifier.fillMaxWidth().padding(8.dp)
            .draggable(
                dragState, Orientation.Vertical,
                onDragStarted = { drag = 0f },
                onDragStopped = { velocity ->
                    if (drag > threshold || velocity > 1500f) onExpanded(false)
                    else if (drag < -threshold || velocity < -1500f) onExpanded(true)
                    drag = 0f
                    nudge.animateTo(0f, Motion.floatSpring)
                },
            )
            // follows the finger a little in the direction it can go, as feedback (after draggable, so it doesn't skew the drag)
            .graphicsLayer { translationY = nudge.value },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        // No spacedBy here: a gap next to the animated body would pop in/out at the end of the animation.
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            val toggleLabel = stringResource(if (expanded) R.string.collapse else R.string.expand)
            // Drag handle. Not tappable; screen readers get the toggle as an action instead.
            Box(
                Modifier.fillMaxWidth().height(20.dp).semantics {
                    customActions = listOf(CustomAccessibilityAction(toggleLabel) { onExpanded(!expanded); true })
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    if (done >= item.sets) stringResource(R.string.done_logged, done) else stringResource(R.string.set_n_of, done + 1, item.sets),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (done > 0) IconButton(onClick = { confirmUndo = true }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo_last_set))
                }
            }
            AnimatedVisibility(expanded, enter = Motion.reveal, exit = Motion.hide) {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (last.isNotEmpty()) {
                        Text(stringResource(R.string.last_time, last.map { stringResource(R.string.weight_reps, formatKg(it.weightKg), it.reps) }.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
                    }
                    Stepper(stringResource(R.string.unit_kg), weight, KeyboardType.Decimal, { weight = it },
                        { weight = formatKg((w - 2.5).coerceAtLeast(0.0)) }, { weight = formatKg(w + 2.5) })
                    Stepper(stringResource(R.string.unit_reps), reps, KeyboardType.Number, { reps = it.filter(Char::isDigit) },
                        { reps = (r - 1).coerceAtLeast(1).toString() }, { reps = (r + 1).toString() })
                    Button(onClick = { vm.setDone(item, w, r) }, Modifier.fillMaxWidth().height(64.dp)) {
                        Text(stringResource(if (done >= item.sets) R.string.extra_set else R.string.set_done), fontSize = 22.sp)
                    }
                }
            }
        }
    }
    if (confirmUndo && prev != null) {
        ConfirmDialog(
            title = stringResource(R.string.undo_set_title),
            text = stringResource(R.string.undo_set_text, prev.setNumber, formatKg(prev.weightKg), prev.reps),
            confirm = stringResource(R.string.undo_set_confirm),
            onConfirm = { confirmUndo = false; vm.undo(item) },
            onDismiss = { confirmUndo = false },
        )
    }
}

@Composable
private fun Stepper(unit: String, value: String, type: KeyboardType, onValue: (String) -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onMinus, Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.Remove, stringResource(R.string.minus_unit, unit)) }
        OutlinedTextField(
            value, onValue, Modifier.weight(1f),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
            suffix = { Text(unit) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type),
        )
        FilledTonalButton(onClick = onPlus, Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.Add, stringResource(R.string.plus_unit, unit)) }
    }
}
