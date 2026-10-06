package com.bar.gym.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bar.gym.GymApp
import com.bar.gym.data.Exercise
import com.bar.gym.domain.formatKg

class ExercisesViewModel(app: Application) : AndroidViewModel(app) {
    val exercises = (app as GymApp).db.dao().observeExercises()
}

fun Exercise.defaultsText() = "${formatKg(weightKg)} kg · $sets × $reps · rest ${restSec}s"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onHistory: (Long) -> Unit,
    vm: ExercisesViewModel = viewModel(),
) {
    val list by vm.exercises.collectAsStateWithLifecycle(emptyList())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercise bank") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(-1) }) { Icon(Icons.Default.Add, "Add exercise") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            list.groupBy { it.bodyPart }.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (part, exs) ->
                item(key = "h$part") {
                    Text(
                        part.ifBlank { "Other" },
                        Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(exs.size, key = { exs[it].id }) { i ->
                    val e = exs[i]
                    ListItem(
                        headlineContent = { Text(e.name, style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text(e.defaultsText(), style = MaterialTheme.typography.bodyLarge) },
                        trailingContent = {
                            IconButton(onClick = { onHistory(e.id) }, Modifier.size(48.dp)) {
                                Icon(Icons.Default.ShowChart, "History")
                            }
                        },
                        modifier = Modifier.clickable { onEdit(e.id) }.heightIn(min = 64.dp),
                    )
                }
            }
        }
    }
}
