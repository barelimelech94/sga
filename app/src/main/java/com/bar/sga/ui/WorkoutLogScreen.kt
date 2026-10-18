package com.bar.sga.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bar.sga.R
import com.bar.sga.SgApp
import com.bar.sga.domain.WorkoutLog
import com.bar.sga.domain.formatKg
import com.bar.sga.domain.monthGrid
import com.bar.sga.domain.roundMinutes
import com.bar.sga.domain.workoutDates
import com.bar.sga.domain.workoutLogs
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class WorkoutLogViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as SgApp).db.dao()
    val logs = combine(dao.observeFinishedWorkouts(), dao.observeAllLogs()) { ws, ls -> workoutLogs(ws, ls) }
    val names = dao.observeAllExercisesIncludingArchived()

    /** Deletes a finished workout and, by cascade, its logs. */
    fun delete(id: Long) = viewModelScope.launch { dao.deleteWorkout(id) }
}

// built per call: the app language can change while the process is alive
private fun fmtDateTime(ms: Long) =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
private fun fmtDay(d: LocalDate) = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()).format(d)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLogScreen(onBack: () -> Unit, onExercise: (Long) -> Unit, vm: WorkoutLogViewModel = viewModel()) {
    val logs by vm.logs.collectAsStateWithLifecycle<List<WorkoutLog>?>(null)
    val exercises by vm.names.collectAsStateWithLifecycle(emptyList())
    val nameOf = exercises.associate { it.id to it.name }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    // saved as plain numbers so they survive rotation and process death
    var monthIndex by rememberSaveable { mutableIntStateOf(YearMonth.now().let { it.year * 12 + it.monthValue - 1 }) }
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var toDelete by remember { mutableStateOf<WorkoutLog?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.workout_history)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        val list = logs ?: return@Scaffold
        val dates = workoutDates(list, ZoneId.systemDefault())
        val perDay = dates.values.groupingBy { it }.eachCount()
        val selected = selectedDay?.let(LocalDate::ofEpochDay)
        // the day's last workout was deleted: drop the filter rather than show an empty list
        LaunchedEffect(selected, perDay) { if (selected != null && selected !in perDay) selectedDay = null }
        val shown = if (selected == null) list else list.filter { dates[it.workout.id] == selected }
        val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)

        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                MonthCalendar(
                    month, perDay, selected,
                    onMonth = { monthIndex = it.year * 12 + it.monthValue - 1 },
                    onDay = { selectedDay = if (it == selected) null else it.toEpochDay() },
                )
            }
            if (list.isEmpty()) {
                item { Text(stringResource(R.string.no_workouts_yet)) }
            }
            if (selected != null) {
                item {
                    InputChip(
                        selected = true,
                        onClick = { selectedDay = null },
                        label = { Text(fmtDay(selected)) },
                        trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.show_all), Modifier.size(18.dp)) },
                    )
                }
            }
            items(shown, key = { it.workout.id }) { log ->
                val expanded = log.workout.id == expandedId
                WorkoutCard(
                    log, expanded, nameOf,
                    onToggle = { expandedId = if (expanded) null else log.workout.id },
                    onExercise = onExercise,
                    onDelete = { toDelete = log },
                )
            }
        }
    }
    toDelete?.let { log ->
        val w = log.workout
        ConfirmDialog(
            title = stringResource(R.string.delete_workout_title),
            text = pluralStringResource(R.plurals.delete_workout_text, log.setCount, log.setCount, w.sessionName, fmtDateTime(w.startedAt)),
            confirm = stringResource(R.string.delete),
            onConfirm = { vm.delete(w.id); if (expandedId == w.id) expandedId = null; toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    perDay: Map<LocalDate, Int>,
    selected: LocalDate?,
    onMonth: (YearMonth) -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    val locale = Locale.getDefault()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val today = LocalDate.now()
    val inMonth = perDay.filterKeys { YearMonth.from(it) == month }.values.sum()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_month))
                }
                Text(
                    DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(month),
                    Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                repeat(7) { i ->
                    Text(
                        firstDay.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale),
                        Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            monthGrid(month, firstDay).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (day != null) DayCell(day, perDay[day] ?: 0, day == selected, day == today, onDay)
                        }
                    }
                }
            }
            Text(
                pluralStringResource(R.plurals.workout_count, inMonth, inMonth),
                Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, count: Int, selected: Boolean, today: Boolean, onDay: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when {
        selected -> cs.primary to cs.onPrimary
        count > 0 -> cs.primaryContainer to cs.onPrimaryContainer
        else -> Color.Transparent to cs.onSurface
    }
    var m = Modifier.fillMaxSize().clip(CircleShape).background(bg)
    if (today && !selected) m = m.border(1.5.dp, cs.primary, CircleShape)
    if (count > 0) m = m.clickable { onDay(day) }
    Box(m, contentAlignment = Alignment.Center) {
        Text(
            day.dayOfMonth.toString(), color = fg,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (count > 0) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun WorkoutCard(
    log: WorkoutLog,
    expanded: Boolean,
    nameOf: Map<Long, String>,
    onToggle: () -> Unit,
    onExercise: (Long) -> Unit,
    onDelete: () -> Unit,
) {
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
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}
