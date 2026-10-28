package com.priorityring.app.ui.editor

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.priorityring.app.ui.theme.LocalPalette
import com.priorityring.app.ui.theme.Spacing
import com.priorityring.engine.block.ConfigField
import com.priorityring.engine.block.TimeRange
import com.priorityring.engine.notification.KnownApps
import com.priorityring.engine.variables.VariableSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.DayOfWeek
import java.time.format.TextStyle

/**
 * Renders a block's config form from its [ConfigField] schema. This is what lets new blocks ship
 * without any UI code: every field type has exactly one editor here.
 */
@Composable
fun ConfigForm(
    fields: List<ConfigField>,
    values: JsonObject,
    variables: List<VariableSpec>,
    onChange: (JsonObject) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        for (field in fields) {
            val current = values[field.key] ?: field.default
            val set: (JsonElement) -> Unit = { onChange(JsonObject(values + (field.key to it))) }
            Column {
                FieldEditor(field, current, variables, set)
                field.validate(current)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.redText,
                        modifier = Modifier.padding(top = Spacing.xs))
                }
                field.help?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }
    }
}

@Composable
private fun FieldEditor(field: ConfigField, value: JsonElement, variables: List<VariableSpec>, set: (JsonElement) -> Unit) {
    when (field) {
        is ConfigField.Text -> TextEditor(field, value.str(), variables) { set(JsonPrimitive(it)) }
        is ConfigField.Number -> NumberEditor(field.label, value.str().toLongOrNull() ?: field.defaultValue, field.unit) { set(JsonPrimitive(it)) }
        is ConfigField.Duration -> DurationEditor(field.label, value.str().toLongOrNull() ?: field.defaultMs) { set(JsonPrimitive(it)) }
        is ConfigField.TimeRangeField -> TimeRangeEditor(field.label, TimeRange.fromJson(value) ?: field.defaultValue) { set(it.toJson()) }
        is ConfigField.Days -> DaysEditor(field.label, value.ints()) { set(JsonArray(it.sorted().map(::JsonPrimitive))) }
        is ConfigField.AppPicker -> AppPickerEditor(field.label, value.strings()) { set(JsonArray(it.map(::JsonPrimitive))) }
        is ConfigField.ContactPicker -> NameListEditor(field.label, value.strings()) { set(JsonArray(it.map(::JsonPrimitive))) }
        is ConfigField.SoundPicker -> SoundEditor(field.label, value.str()) { set(JsonPrimitive(it)) }
        is ConfigField.Dropdown -> ChoiceButton(
            field.label, field.options.map { it.value to it.label }, value.str().ifEmpty { field.defaultValue },
        ) { set(JsonPrimitive(it)) }
        is ConfigField.Toggle -> ToggleEditor(field.label, (value as? JsonPrimitive)?.booleanOrNull ?: field.defaultValue) { set(JsonPrimitive(it)) }
        is ConfigField.Slider -> SliderEditor(field, value.str().toIntOrNull() ?: field.defaultValue) { set(JsonPrimitive(it)) }
        is ConfigField.VariableRef -> {
            val current = value.str().ifEmpty { field.defaultValue }
            val options = (variables.map { it.name to "{${it.name}} · ${it.label}" } +
                listOf(current to "{$current}")).distinctBy { it.first }
            ChoiceButton(field.label, options, current) { set(JsonPrimitive(it)) }
        }
    }
}

// ---- individual editors -----------------------------------------------------------------------

@Composable
private fun TextEditor(field: ConfigField.Text, text: String, variables: List<VariableSpec>, onChange: (String) -> Unit) {
    // TextFieldValue (not String) so we know the cursor position for inserting variables.
    var tfv by remember(field.key) { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    var menu by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = tfv,
            onValueChange = { tfv = it; onChange(it.text) },
            label = { Text(field.label) },
            placeholder = field.placeholder?.let { { Text(it) } },
            singleLine = !field.multiline,
            minLines = if (field.multiline) 2 else 1,
            modifier = Modifier.fillMaxWidth(),
        )
        if (field.variables && variables.isNotEmpty()) {
            Box {
                TextButton(onClick = { menu = true }) { Text("Insert variable") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    variables.forEach { v ->
                        DropdownMenuItem(
                            text = { Text("{${v.name}}  ${v.label}") },
                            onClick = {
                                menu = false
                                val token = "{${v.name}}"
                                val sel = tfv.selection
                                val newText = tfv.text.replaceRange(sel.min, sel.max, token)
                                tfv = TextFieldValue(newText, TextRange(sel.min + token.length))
                                onChange(newText)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberEditor(label: String, value: Long, unit: String?, onChange: (Long) -> Unit) {
    var text by remember(label) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            text = s.filter { it.isDigit() || it == '-' }
            text.toLongOrNull()?.let(onChange)
        },
        label = { Text(label) },
        suffix = unit?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val durationUnits = listOf("s" to 1_000L, "min" to 60_000L, "h" to 3_600_000L)

@Composable
private fun DurationEditor(label: String, ms: Long, onChange: (Long) -> Unit) {
    val initialUnit = durationUnits.last { (_, u) -> ms >= u && ms % u == 0L || u == 1_000L }
    var unit by rememberSaveable(label) { mutableStateOf(initialUnit.first) }
    val factor = durationUnits.first { it.first == unit }.second
    var text by remember(label, unit) { mutableStateOf((ms / factor).toString()) }
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { s ->
                    text = s.filter(Char::isDigit)
                    text.toLongOrNull()?.let { onChange(it * factor) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp).semantics { contentDescription = "$label in $unit" },
            )
            Spacer(Modifier.width(Spacing.s))
            SingleChoiceSegmentedButtonRow {
                durationUnits.forEachIndexed { i, (name, f) ->
                    SegmentedButton(
                        selected = unit == name,
                        onClick = {
                            unit = name
                            val n = (text.toLongOrNull() ?: 0).coerceAtLeast(1)
                            onChange(n * f)
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, durationUnits.size),
                    ) { Text(name) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRangeEditor(label: String, range: TimeRange, onChange: (TimeRange) -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) } // "start" | "end"
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { editing = "start" }) { Text("From ${TimeRange.hhmm(range.startMinute)}") }
            OutlinedButton(onClick = { editing = "end" }) { Text("To ${TimeRange.hhmm(range.endMinute)}") }
        }
        val note = when {
            range.isAllDay -> "Same start and end = all day"
            range.crossesMidnight -> "Crosses midnight"
            else -> null
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    editing?.let { which ->
        val minute = if (which == "start") range.startMinute else range.endMinute
        val state = rememberTimePickerState(minute / 60, minute % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    onChange(if (which == "start") range.copy(startMinute = m) else range.copy(endMinute = m))
                    editing = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaysEditor(label: String, days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    // From the configuration (not Locale.getDefault()) so day names update if the language changes.
    val locale = LocalConfiguration.current.locales[0]
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            for (d in 1..7) {
                val name = DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, locale)
                FilterChip(
                    selected = d in days,
                    onClick = { onChange(if (d in days) days - d else days + d) },
                    label = { Text(name) },
                )
            }
        }
        if (days.isEmpty()) {
            Text("No days selected means every day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class AppEntry(val packageName: String, val label: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppPickerEditor(label: String, selected: List<String>, onChange: (List<String>) -> Unit) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(false) }
    fun labelOf(pkg: String) = KnownApps.label(pkg) ?: runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (selected.isEmpty()) {
            Text("Any app", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            selected.forEach { pkg ->
                InputChip(
                    selected = true,
                    onClick = { onChange(selected - pkg) },
                    label = { Text(labelOf(pkg)) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove ${labelOf(pkg)}") },
                )
            }
            // Presets: one tap for the apps with special parsers.
            listOf(KnownApps.GMAIL, KnownApps.WHATSAPP).filterNot { it in selected }.forEach { pkg ->
                AssistChip(onClick = { onChange(selected + pkg) }, label = { Text("+ ${KnownApps.display(pkg)}") })
            }
            AssistChip(onClick = { dialog = true }, label = { Text("Choose apps…") })
        }
    }
    if (dialog) {
        AppPickerDialog(selected, onDismiss = { dialog = false }) { onChange(it); dialog = false }
    }
}

@Composable
private fun AppPickerDialog(selected: List<String>, onDismiss: () -> Unit, onDone: (List<String>) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
    var picked by remember { mutableStateOf(selected.toSet()) }
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose apps") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val list = apps
                if (list == null) {
                    Text("Loading…", Modifier.padding(Spacing.m))
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(list.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }, key = { it.packageName }) { app ->
                            val checked = app.packageName in picked
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox) {
                                    picked = if (it) picked + app.packageName else picked - app.packageName
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Spacer(Modifier.width(Spacing.s))
                                Column {
                                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(selected.filter { it in picked } + (picked - selected.toSet())) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameListEditor(label: String, names: List<String>, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (input.isNotBlank()) { onChange(names + input.trim()); input = "" } }) { Text("Add") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            names.forEach { n ->
                InputChip(selected = true, onClick = { onChange(names - n) }, label = { Text(n) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $n") })
            }
        }
    }
}

@Composable
private fun SoundEditor(label: String, uri: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val title = remember(uri) {
        if (uri.isBlank()) "Default alarm sound"
        else runCatching { RingtoneManager.getRingtone(context, uri.toUri())?.getTitle(context) }.getOrNull() ?: "Chosen sound"
    }
    // The system ringtone picker is an Activity that returns the chosen sound's URI.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            onChange(picked?.toString().orEmpty())
        }
    }
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OutlinedButton(onClick = {
                picker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri.takeIf { it.isNotBlank() }?.let { it.toUri() }),
                )
            }) { Text(title) }
            if (uri.isNotBlank()) TextButton(onClick = { onChange("") }) { Text("Use default") }
        }
    }
}

@Composable
private fun ToggleEditor(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SliderEditor(field: ConfigField.Slider, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text("${field.label}: $value${field.unit.orEmpty()}", style = MaterialTheme.typography.titleSmall)
        val steps = ((field.max - field.min) / field.step - 1).coerceAtLeast(0)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = field.min.toFloat()..field.max.toFloat(),
            steps = steps,
        )
    }
}

/** A labelled button that opens a menu of options. Used for dropdowns and variable pickers. */
@Composable
fun ChoiceButton(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(options.firstOrNull { it.first == selected }?.second ?: selected, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
                }
            }
        }
    }
}

// ---- JSON helpers ------------------------------------------------------------------------------

private fun JsonElement.str(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonElement.strings(): List<String> = (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
private fun JsonElement.ints(): Set<Int> = (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }?.toSet().orEmpty()
