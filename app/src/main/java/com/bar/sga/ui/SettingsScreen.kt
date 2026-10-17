package com.bar.sga.ui

import android.app.Application
import android.app.LocaleManager
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bar.sga.SgApp
import com.bar.sga.R
import com.bar.sga.ThemeMode
import com.bar.sga.data.*
import com.bar.sga.timer.RestTimer
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val themes = listOf(ThemeMode.SYSTEM to R.string.system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)

// "he", not "iw": toLanguageTag() gives the modern code
private val langTags = listOf("", "en", "he")
private val langNames = listOf(R.string.system, R.string.lang_english, R.string.lang_hebrew)
private fun langIndex(l: LocaleList) = when {
    l.isEmpty -> 0
    l[0].toLanguageTag().startsWith("he") -> 2
    else -> 1
}

class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = (app as SgApp).db
    private fun counts(e: Int, s: Int, w: Int) = Triple(
        app.resources.getQuantityString(R.plurals.exercise_count, e, e),
        app.resources.getQuantityString(R.plurals.session_count, s, s),
        app.resources.getQuantityString(R.plurals.workout_count, w, w),
    )

    val message = MutableStateFlow("")
    val pending = MutableStateFlow<Backup?>(null)

    fun export(uri: Uri) = viewModelScope.launch {
        message.value = try {
            withContext(Dispatchers.IO) {
                val json = exportBackup(db.dao()).toJson()
                val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no stream")
                out.use { it.write(json.toByteArray()) }
            }
            app.getString(R.string.backup_saved)
        } catch (e: Exception) { app.getString(R.string.backup_save_failed, e.message ?: e.javaClass.simpleName) }
    }

    fun read(uri: Uri) = viewModelScope.launch {
        try {
            val b = withContext(Dispatchers.IO) {
                val text = app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: throw IOException("no stream")
                backupFromJson(text)
            }
            val problem = b.problem()
            if (problem == null) pending.value = b else message.value = app.getString(R.string.backup_invalid_reason, app.getString(R.string.backup_invalid), problem)
        } catch (e: Exception) { // SerializationException, IOException, IllegalArgumentException
            message.value = app.getString(R.string.backup_invalid)
        }
    }

    fun confirm() = viewModelScope.launch {
        val b = pending.value ?: return@launch
        pending.value = null
        message.value = try {
            val dao = db.dao()
            dao.getActiveWorkout()?.let { RestTimer.cancel(app, dao, it) }
            importBackup(db, b)
            val (e, s, w) = counts(b.exercises.size, b.sessions.size, b.workouts.size)
            app.getString(R.string.backup_restored, e, s, w)
        } catch (e: Exception) { app.getString(R.string.restore_failed, e.message ?: e.javaClass.simpleName) }
    }

    fun dismiss() { pending.value = null }

    fun restoreText(b: Backup): String {
        val (e, s, w) = counts(b.exercises.size, b.sessions.size, b.workouts.size)
        return app.getString(R.string.restore_text, e, s, w)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::read) }
    val app = LocalContext.current.applicationContext as SgApp
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                themes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = app.themeMode == mode,
                        onClick = { app.themeMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(i, themes.size),
                    ) { Text(stringResource(label)) }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                // Android stores the choice and recreates the activity itself
                val lm = LocalContext.current.getSystemService(LocaleManager::class.java)
                var lang by remember { mutableIntStateOf(langIndex(lm.applicationLocales)) }
                Text(stringResource(R.string.language), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    langNames.forEachIndexed { i, name ->
                        SegmentedButton(
                            selected = lang == i,
                            onClick = { lang = i; lm.applicationLocales = LocaleList.forLanguageTags(langTags[i]) },
                            shape = SegmentedButtonDefaults.itemShape(i, langNames.size),
                        ) { Text(stringResource(name)) }
                    }
                }
            }
            Text(stringResource(R.string.backup), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.backup_note))
            Button(onClick = { exportLauncher.launch("gym-backup-${LocalDate.now()}.json") }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_backup)) }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.import_backup)) }
            if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
    pending?.let { b ->
        ConfirmDialog(
            title = stringResource(R.string.restore_title),
            text = vm.restoreText(b),
            confirm = stringResource(R.string.replace),
            onConfirm = vm::confirm,
            onDismiss = vm::dismiss,
        )
    }
}
