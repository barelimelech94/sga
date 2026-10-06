package com.bar.gym.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bar.gym.GymApp
import com.bar.gym.data.*
import com.bar.gym.timer.RestTimer
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val INVALID = "That file is not a valid gym backup — nothing was changed."

class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = (app as GymApp).db
    val message = MutableStateFlow("")
    val pending = MutableStateFlow<Backup?>(null)

    fun export(uri: Uri) = viewModelScope.launch {
        message.value = try {
            withContext(Dispatchers.IO) {
                val json = exportBackup(db.dao()).toJson()
                val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no stream")
                out.use { it.write(json.toByteArray()) }
            }
            "Backup saved"
        } catch (e: Exception) { "Could not save the backup: ${e.message ?: e.javaClass.simpleName}" }
    }

    fun read(uri: Uri) = viewModelScope.launch {
        try {
            val b = withContext(Dispatchers.IO) {
                val text = app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: throw IOException("no stream")
                backupFromJson(text)
            }
            val problem = b.problem()
            if (problem == null) pending.value = b else message.value = "$INVALID ($problem)"
        } catch (e: Exception) { // SerializationException, IOException, IllegalArgumentException
            message.value = INVALID
        }
    }

    fun confirm() = viewModelScope.launch {
        val b = pending.value ?: return@launch
        pending.value = null
        message.value = try {
            val dao = db.dao()
            dao.getActiveWorkout()?.let { RestTimer.cancel(app, dao, it) }
            importBackup(db, b)
            "Backup restored: ${b.exercises.size} exercises, ${b.sessions.size} sessions, ${b.workouts.size} workouts"
        } catch (e: Exception) { "Restore failed, existing data was kept: ${e.message ?: e.javaClass.simpleName}" }
    }

    fun dismiss() { pending.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::read) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Backup") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your data is stored only on this phone. Export a backup before uninstalling or changing phones.")
            Button(onClick = { exportLauncher.launch("gym-backup-${LocalDate.now()}.json") }, Modifier.fillMaxWidth()) { Text("Export backup") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                Modifier.fillMaxWidth(),
            ) { Text("Import backup") }
            if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
    pending?.let { b ->
        ConfirmDialog(
            title = "Restore backup?",
            text = "Replace ALL current data with this backup? It contains ${b.exercises.size} exercises, " +
                "${b.sessions.size} sessions, ${b.workouts.size} workouts. This cannot be undone.",
            confirm = "Replace",
            onConfirm = vm::confirm,
            onDismiss = vm::dismiss,
        )
    }
}
