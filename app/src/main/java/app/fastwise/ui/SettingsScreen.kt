@file:OptIn(ExperimentalMaterial3Api::class)

package app.fastwise.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fastwise.data.AppData
import app.fastwise.data.Exporter
import app.fastwise.data.FastRepository
import app.fastwise.data.Fmt
import app.fastwise.data.Goals
import app.fastwise.data.MINUTE_MS
import app.fastwise.data.Plans
import app.fastwise.data.SyncManager
import app.fastwise.data.ThemeMode
import app.fastwise.service.AppQuit
import app.fastwise.service.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Create-document picker that asks for long-term read/write access (needed for the sync file). */
class CreatePersistentDocument(mime: String) : ActivityResultContracts.CreateDocument(mime) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).addFlags(PERSIST_FLAGS)
}

class OpenPersistentDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(PERSIST_FLAGS)
}

private const val PERSIST_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

@Composable
fun SettingsScreen(data: AppData) {
    val ctx = LocalContext.current
    val is24 = DateFormat.is24HourFormat(ctx)
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val s = data.settings
    val sync by SyncManager.status.collectAsStateWithLifecycle()
    var showGoal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }
    var showPlan by remember { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(Notifier.canPost(ctx)) }
    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBattery(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = Notifier.canPost(ctx)
        batteryUnrestricted = isIgnoringBattery(ctx)
    }
    val stamp = LocalDate.now().toString()

    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            runIo(scope, snack, "Spreadsheet export saved") {
                Exporter.writeCsvZip(ctx, uri, FastRepository.data.value, System.currentTimeMillis())
            }
        }
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runIo(scope, snack, "Backup saved") { Exporter.writeBackup(ctx, uri, FastRepository.data.value) }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { Exporter.readBackup(ctx, uri) } }
                result.onSuccess { remote ->
                    val merged = FastRepository.importMerge(remote)
                    snack.show("Restored: ${merged.added} new and ${merged.updated} updated entries")
                }.onFailure {
                    snack.show("That file isn't a Fastwise backup.")
                }
            }
        }
    }
    val linkNew = rememberLauncherForActivityResult(CreatePersistentDocument("application/json")) { uri ->
        if (uri != null) SyncManager.link(uri) { snack.show(it) }
    }
    val linkExisting = rememberLauncherForActivityResult(OpenPersistentDocument()) { uri ->
        if (uri != null) SyncManager.link(uri) { snack.show(it) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
        )

        Section("Fasting")
        SettingRow(
            Icons.Outlined.Flag,
            "Fasting goal",
            "${Goals.name(s.goalMinutes)}, a ${Fmt.hm(s.goalMinutes * MINUTE_MS)} fast",
        ) { showGoal = true }
        SwitchRow(
            Icons.Outlined.Timer,
            "Timer in the notification bar",
            "Live fasting time with I just ate and End fast buttons",
            s.notificationOn,
        ) { v -> FastRepository.updateSettings { it.copy(notificationOn = v) } }
        SwitchRow(
            Icons.Outlined.NotificationsActive,
            "Goal reached alert",
            "Notify me the moment I hit my goal",
            s.goalAlert,
        ) { v -> FastRepository.updateSettings { it.copy(goalAlert = v) } }
        SwitchRow(
            Icons.Outlined.ThumbUp,
            "Encouragement nudges",
            "A note at halfway and when one hour is left",
            s.nudges,
        ) { v -> FastRepository.updateSettings { it.copy(nudges = v) } }
        SettingRow(
            Icons.Outlined.EventAvailable,
            "Weekly target",
            "Hit your goal on ${s.weeklyTargetDays} of 7 days",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        FastRepository.updateSettings { it.copy(weeklyTargetDays = (it.weeklyTargetDays - 1).coerceAtLeast(1)) }
                    }) { Icon(Icons.Outlined.Remove, contentDescription = "Fewer days") }
                    Text("${s.weeklyTargetDays}", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = {
                        FastRepository.updateSettings { it.copy(weeklyTargetDays = (it.weeklyTargetDays + 1).coerceAtMost(7)) }
                    }) { Icon(Icons.Outlined.Add, contentDescription = "More days") }
                }
            },
        )
        SettingRow(
            Icons.Outlined.Restaurant,
            "Meal plan",
            s.plan?.let { "${it.name}. ${Plans.describe(it, is24)}" } ?: "None. Try one meal a day, two meals and more",
        ) { showPlan = true }
        SwitchRow(
            Icons.Outlined.Alarm,
            "Meal reminders",
            "At planned meal times, and 30 minutes before your eating window closes",
            s.mealReminders,
        ) { v -> FastRepository.updateSettings { it.copy(mealReminders = v) } }
        SwitchRow(Icons.Outlined.DateRange, "Week starts on Monday", null, s.weekStartsMonday) { v ->
            FastRepository.updateSettings { it.copy(weekStartsMonday = v) }
        }

        Section("Appearance")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            val names = listOf("System", "Light", "Dark")
            ThemeMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = s.themeMode == mode,
                    onClick = { FastRepository.updateSettings { it.copy(themeMode = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = ThemeMode.entries.size),
                    label = { Text(names[i]) },
                )
            }
        }
        SwitchRow(Icons.Outlined.DarkMode, "Pure black", "Saves battery on your OLED screen in dark mode", s.pureBlack) { v ->
            FastRepository.updateSettings { it.copy(pureBlack = v) }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            SwitchRow(Icons.Outlined.Palette, "Wallpaper colors", "Use your One UI color palette instead of Fastwise colors", s.dynamicColor) { v ->
                FastRepository.updateSettings { it.copy(dynamicColor = v) }
            }
        }

        Section("Sync between devices")
        Text(
            "Link one file in Google Drive (or any cloud app). On another phone or tablet, install Fastwise " +
                "and link the same file. Every change merges both ways, and nothing is overwritten.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        if (!sync.linked) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Button(onClick = { linkNew.launch("fastwise-sync.json") }, modifier = Modifier.weight(1f)) {
                    Text("Create sync file")
                }
                OutlinedButton(onClick = { linkExisting.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                    Text("Use existing file")
                }
            }
        } else {
            val status = when {
                sync.busy -> "Syncing..."
                sync.error != null -> "Last sync failed: ${sync.error}"
                sync.lastSync > 0 -> "Last synced ${Fmt.dayTime(sync.lastSync, is24, Fmt.date(System.currentTimeMillis()))}"
                else -> "Not synced yet"
            }
            SettingRow(
                Icons.Outlined.Sync,
                sync.fileName ?: "Sync file",
                status,
                trailing = if (sync.busy) {
                    { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                Button(onClick = { SyncManager.syncNow { snack.show(it) } }, enabled = !sync.busy, modifier = Modifier.weight(1f)) {
                    Text("Sync now")
                }
                OutlinedButton(onClick = {
                    SyncManager.unlink()
                    snack.show("Sync file unlinked. Your data stays on this phone.")
                }, modifier = Modifier.weight(1f)) {
                    Text("Unlink")
                }
            }
        }

        Section("Export and backup")
        SettingRow(Icons.Outlined.FileDownload, "Export spreadsheet", "Zip with meals, fasts and a daily summary as CSV") {
            exportCsv.launch("fastwise-$stamp.zip")
        }
        SettingRow(Icons.Outlined.Backup, "Back up to a file", "Everything in one JSON file") {
            backup.launch("fastwise-backup-$stamp.json")
        }
        SettingRow(Icons.Outlined.Restore, "Restore from a backup", "Merges with what's on this phone") {
            restore.launch(arrayOf("*/*"))
        }

        Section("Keep the timer running")
        if (!notificationsAllowed) {
            SettingRow(Icons.Outlined.Notifications, "Allow notifications", "Needed for the live timer and goal alerts") {
                openNotificationSettings(ctx)
            }
        }
        SettingRow(
            Icons.Outlined.BatteryAlert,
            "Battery usage",
            if (batteryUnrestricted) "Unrestricted. Samsung won't stop the timer." else "Tap and allow, so One UI doesn't put Fastwise to sleep",
        ) { requestBatteryExemption(ctx) }

        Section("Data")
        SettingRow(
            Icons.Outlined.DeleteForever,
            "Delete all data",
            "Removes every meal and fast. Linked devices delete them on their next sync.",
        ) { confirmDelete = true }

        Section("App")
        SettingRow(
            Icons.Outlined.PowerSettingsNew,
            "Quit Fastwise",
            "Stops the timer, alerts, reminders and sync, and closes the app",
        ) { confirmQuit = true }

        Text(
            "Fastwise ${appVersion(ctx)}. Your data stays on this phone unless you export or sync it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
        )
    }

    if (showGoal) GoalSheet(s.goalMinutes) { showGoal = false }
    if (showPlan) PlanSheet(s.plan) { showPlan = false }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = { Text("Every meal and fast will be removed. Export a backup first if you might want them later.") },
            confirmButton = {
                TextButton(onClick = {
                    FastRepository.deleteAll()
                    confirmDelete = false
                    snack.show("All data deleted")
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmQuit) {
        val fasting = data.activeFast != null
        val message = buildString {
            append("The timer notification, goal alerts, meal reminders and sync stop, and Fastwise closes. ")
            append("Nothing starts again on its own, even after your phone restarts. Your data is kept. ")
            append("Open Fastwise to turn everything back on.")
            if (fasting) {
                append("\n\nYour current fast stays saved and keeps counting from when it started. ")
                append("To stop it as well, choose End fast and quit.")
            }
        }
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Quit Fastwise?") },
            text = { Text(message) },
            confirmButton = {
                if (fasting) {
                    TextButton(onClick = {
                        confirmQuit = false
                        FastRepository.endFast()
                        AppQuit.quit(ctx)
                    }) { Text("End fast and quit") }
                }
                TextButton(onClick = {
                    confirmQuit = false
                    AppQuit.quit(ctx)
                }) { Text("Quit") }
            },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp) else Modifier.padding(horizontal = 8.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        trailingContent = trailing,
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, title, subtitle, trailing = { Switch(checked = checked, onCheckedChange = onChange) }) {
        onChange(!checked)
    }
}

private fun runIo(scope: CoroutineScope, snack: SnackController, done: String, block: () -> Unit) {
    scope.launch {
        val result = runCatching { withContext(Dispatchers.IO) { block() } }
        snack.show(if (result.isSuccess) done else "Couldn't save: ${result.exceptionOrNull()?.message ?: "unknown error"}")
    }
}

private fun isIgnoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

@SuppressLint("BatteryLife")
private fun requestBatteryExemption(ctx: Context) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    val intent = if (isIgnoringBattery(ctx)) {
        details
    } else {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
    }
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        runCatching { ctx.startActivity(details) }
    }
}

private fun openNotificationSettings(ctx: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    runCatching { ctx.startActivity(intent) }
}

private fun appVersion(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
