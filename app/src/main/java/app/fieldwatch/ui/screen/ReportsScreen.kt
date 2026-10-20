package app.fieldwatch.ui.screen

import android.content.Context
import app.fieldwatch.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.ui.RadioClassBadge
import app.fieldwatch.ui.RadioKindMark
import app.fieldwatch.domain.LogExportKind
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.Sit
import app.fieldwatch.domain.SitDiff
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.ui.component.AircraftAmber
import app.fieldwatch.ui.component.FieldwatchDropdownField
import app.fieldwatch.ui.component.SitPathCanvas
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.FieldwatchSwitch
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.theme.Cyan
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    exporting: Boolean,
    onSaveToStorage: () -> Unit,
    onSaveSitToStorage: () -> Unit,
    onSignatureCandidates: () -> Unit,
    onOpenPathRadio: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val settings = state.settings
    var confirmClear by remember { mutableStateOf(false) }
    var startSit by remember { mutableStateOf(false) }
    var sitNameDraft by remember { mutableStateOf("") }
    var renameSitId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteSitId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar(stringResource(R.string.report_title)) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (settings.demoMode) {
                Text(
                    stringResource(R.string.report_privacy_mode_on),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            SectionCard(stringResource(R.string.report_sits)) {
                Text(
                    stringResource(R.string.report_sit_definition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val open = state.sit.open
                if (open != null) {
                    val dur = Sit.fmtDuration(open.durationMs())
                    Text(
                        stringResource(R.string.report_this_sit, open.name, dur, state.sit.radioCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FieldwatchActionButton(
                        onClick = vm::endSit,
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.report_end_sit)) }
                } else {
                    FieldwatchActionButton(
                        onClick = {
                            sitNameDraft = vm.defaultSitName()
                            startSit = true
                        },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.report_start_sit)) }
                    Text(
                        if (state.sit.closed.isEmpty()) {
                            stringResource(R.string.report_no_sit_running_15)
                        } else {
                            stringResource(R.string.report_no_sit_running_pick)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.sit.closed.isEmpty() && open == null) {
                    Text(
                        stringResource(R.string.report_no_saved_sits),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.sit.closed.isNotEmpty()) {
                    val pickEnabled = open == null && !exporting
                    SitChoiceRow(
                        selected = state.sit.selectedId == null,
                        enabled = pickEnabled,
                        title = stringResource(R.string.report_last_15_minutes),
                        subtitle = stringResource(R.string.report_last_15_minutes_sub),
                        onSelect = { vm.selectSit(null) },
                    )
                    state.sit.closed.forEach { row ->
                        val dur = Sit.fmtDuration(row.durationMs())
                        val extra = if (row.extraAttentionCount > 0) {
                            stringResource(R.string.report_extra_attention, row.extraAttentionCount)
                        } else {
                            ""
                        }
                        SitChoiceRow(
                            selected = state.sit.selectedId == row.id,
                            enabled = pickEnabled,
                            title = row.name,
                            subtitle = stringResource(R.string.report_sit_choice_subtitle, Sit.defaultName(row.startAt), dur, row.radioCount, extra),
                            onSelect = { vm.selectSit(row.id) },
                        )
                    }
                    if (open != null) {
                        Text(
                            stringResource(R.string.report_end_sit_to_pick),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val picked = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FieldwatchActionButton(
                            onClick = {
                                if (picked != null) {
                                    renameSitId = picked.id
                                    renameDraft = picked.name
                                }
                            },
                            enabled = !exporting && picked != null,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.report_rename)) }
                        FieldwatchActionButton(
                            onClick = { if (picked != null) deleteSitId = picked.id },
                            enabled = !exporting && picked != null,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.report_delete)) }
                    }
                    FieldwatchActionButton(
                        onClick = { confirmDeleteAll = true },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.report_delete_all_sits)) }
                }
            }

            val pathModel by vm.sitPath.collectAsStateWithLifecycle()
            LaunchedEffect(state.sit.selectedId, state.sit.open?.id) {
                while (true) {
                    vm.refreshSitPath()
                    kotlinx.coroutines.delay(3_000L)
                }
            }
            SectionCard(stringResource(R.string.report_path)) {
                Text(
                    stringResource(R.string.report_path_legend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val model = pathModel
                val showWalk = model != null && model.emptyHint == null
                val showAircraft = model != null && model.aircraftCards.isNotEmpty()
                if (model == null || (!showWalk && !showAircraft)) {
                    Text(
                        model?.emptyHint ?: stringResource(R.string.report_turn_on_tag),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val pathTiles by vm.pathTiles.collectAsStateWithLifecycle()
                    val aircraftTiles by vm.pathAircraftTiles.collectAsStateWithLifecycle()
                    if (!showWalk) {
                        Text(
                            model.emptyHint ?: stringResource(R.string.report_turn_on_tag),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showWalk) {
                    val stopN = model.dots.size
                    val gnssN = model.gnss.size
                    val pathSummary = stringResource(R.string.report_path_summary, model.title, model.lengthM.toInt(), model.spanM.toInt())
                    val alertTxt = if (stopN > 0) " · " + pluralStringResource(R.plurals.report_alert_count, stopN, stopN) else ""
                    val gnssSuffix = if (gnssN > 0) stringResource(R.string.report_gnss_suffix, gnssN) else ""
                    Text(
                        buildString {
                            append(pathSummary)
                            append(alertTxt)
                            append(gnssSuffix)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SitPathCanvas(model, tiles = pathTiles, onOpenRadio = onOpenPathRadio)
                    Text(
                        stringResource(R.string.report_tap_instructions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (model.craft.isNotEmpty() || model.pilots.isNotEmpty() || model.gnss.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (model.craft.isNotEmpty()) {
                                val multi = model.craft.any { it.samples.size >= 2 }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (multi) AdvertisedTrackSwatch() else AdvertisedRingSwatch()
                                    Text(
                                        if (multi) {
                                            stringResource(R.string.report_advertised_track_legend)
                                        } else {
                                            stringResource(R.string.report_advertised_position_legend)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            if (model.pilots.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    PilotSwatch()
                                    Text(
                                        stringResource(R.string.report_operator_legend),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (model.gnss.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    GnssSwatch()
                                    Text(
                                        stringResource(R.string.report_gnss_detection_legend),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                    val alertsOnACard = model.aircraftCards.any { it.dots.isNotEmpty() }
                    if (model.dots.isEmpty() && model.gnss.isEmpty() && !alertsOnACard) {
                        Text(
                            stringResource(R.string.report_no_alerts_gps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (model.dots.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            model.dots.forEachIndexed { i, dot ->
                                PathRadioRow(
                                    index = i + 1,
                                    dot = dot,
                                    demoMode = settings.demoMode,
                                    onOpen = { onOpenPathRadio(dot.key) },
                                )
                            }
                        }
                    }
                    if (model.gnss.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            model.gnss.forEach { pin ->
                                GnssPathNote(pin)
                            }
                        }
                    }
                    }
                    model.aircraftCards.forEachIndexed { index, card ->
                        val fixes = card.craft.sumOf { it.samples.size }
                        Text(
                            privacyAircraftHeading(context, card.title, settings.demoMode),
                            style = MaterialTheme.typography.titleSmall,
                            color = AircraftAmber,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        val fixesTxt = pluralStringResource(R.plurals.report_advertised_fix, fixes, fixes)
                        Text(
                            buildString {
                                append(fixesTxt)
                                if (card.lengthM >= 1.0) {
                                    append(stringResource(R.string.report_path_length_suffix, card.lengthM.toInt()))
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (card.dots.isNotEmpty()) {
                            Text(
                                pluralStringResource(R.plurals.report_alert_count, card.dots.size, card.dots.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SitPathCanvas(
                            card,
                            tiles = aircraftTiles.getOrElse(index) { emptyList() },
                            onOpenRadio = onOpenPathRadio,
                        )
                        if (card.dots.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                card.dots.forEachIndexed { i, dot ->
                                    PathRadioRow(
                                        index = i + 1,
                                        dot = dot,
                                        demoMode = settings.demoMode,
                                        onOpen = { onOpenPathRadio(dot.key) },
                                    )
                                }
                            }
                        }
                        if (card.caption.isNotBlank()) {
                            Text(
                                privacyAircraftCaption(card.caption, card.title, settings.demoMode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (model.looseAdvertised > 0) {
                        Text(
                            pluralStringResource(R.plurals.report_loose_advertised, model.looseAdvertised, model.looseAdvertised),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionCard(stringResource(R.string.report_sit_report)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldwatchActionButton(
                    onClick = vm::startFieldDebrief,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.report_debrief_text)) }
                FieldwatchActionButton(
                    onClick = vm::startFieldDebriefPdf,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.report_debrief_pdf)) }
            }
            Text(
                sitReportCaption(context, state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.report_show_unmatched_ble),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FieldwatchSwitch(
                    settings.debriefShowUnmatchedRandomBle,
                    { on -> vm.updateSettings { it.copy(debriefShowUnmatchedRandomBle = on) } },
                )
            }
            Text(
                stringResource(R.string.report_unmatched_ble_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.report_show_all_radios),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FieldwatchSwitch(
                    settings.debriefShowAllRadios,
                    { on -> vm.updateSettings { it.copy(debriefShowAllRadios = on) } },
                )
            }
            Text(
                stringResource(R.string.report_all_radios_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startAiExport,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.report_ai_export)) }
            Text(
                stringResource(R.string.report_ai_export_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(stringResource(R.string.report_sit_export)) {
            val sitKind by vm.sitExportKind.collectAsStateWithLifecycle()
            val sitRadios by vm.sitExportRadios.collectAsStateWithLifecycle()
            ExportFormatBlock(
                kind = sitKind,
                radios = sitRadios,
                exporting = exporting,
                onKind = vm::setSitExportKind,
                onRadios = vm::setSitExportRadios,
                onShare = vm::startSitExport,
                onSave = onSaveSitToStorage,
                hint = stringResource(R.string.report_sit_export_hint),
            )
            }

            SectionCard(stringResource(R.string.report_compare_sits)) {
                Text(
                    compareThisCaption(context, state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val thisSaved = SitDiff.thisSavedId(state.sit.open, state.sit.selectedId)
                val choices = SitDiff.secondSitChoices(state.sit.closed, thisSaved)
                if (choices.isEmpty()) {
                    Text(
                        stringResource(R.string.report_compare_needs_second),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.report_second_sit),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    choices.forEach { row ->
                        val dur = Sit.fmtDuration(row.durationMs())
                        SitChoiceRow(
                            selected = state.sit.compareId == row.id,
                            enabled = !exporting,
                            title = row.name,
                            subtitle = stringResource(R.string.report_sit_choice_subtitle, Sit.defaultName(row.startAt), dur, row.radioCount, ""),
                            onSelect = { vm.selectCompareSit(row.id) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldwatchActionButton(
                        onClick = vm::startSitCompare,
                        enabled = !exporting && state.sit.compareId != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.report_compare_text)) }
                    FieldwatchActionButton(
                        onClick = vm::startSitComparePdf,
                        enabled = !exporting && state.sit.compareId != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.report_compare_pdf)) }
                }
                Text(
                    stringResource(R.string.report_compare_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FieldwatchActionButton(
                    onClick = vm::startSitCompareAiExport,
                    enabled = !exporting && state.sit.compareId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.report_ai_export)) }
                Text(
                    stringResource(R.string.report_compare_ai_export_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(stringResource(R.string.report_catalog)) {
            FieldwatchActionButton(
                onClick = onSignatureCandidates,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.report_signature_candidates)) }
            Text(
                stringResource(R.string.report_signature_candidates_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(stringResource(R.string.report_log_export)) {
            Text(
                stringResource(R.string.report_log_lines, state.logLines, vm.logBytes() / 1024) +
                    if (settings.loggingEnabled) "" else stringResource(R.string.report_logging_off),
                style = MaterialTheme.typography.bodySmall,
            )
            val logKind by vm.logExportKind.collectAsStateWithLifecycle()
            val logRadios by vm.logExportRadios.collectAsStateWithLifecycle()
            ExportFormatBlock(
                kind = logKind,
                radios = logRadios,
                exporting = exporting,
                onKind = vm::setLogExportKind,
                onRadios = vm::setLogExportRadios,
                onShare = vm::startExport,
                onSave = onSaveToStorage,
                hint = stringResource(R.string.report_log_export_hint),
            )
            FieldwatchActionButton(
                onClick = { confirmClear = true },
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.report_reset_clear_log))
            }
            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text(stringResource(R.string.report_clear_log_question)) },
                    text = {
                        Text(stringResource(R.string.report_clear_log_warning))
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmClear = false
                            vm.clearLogs()
                        }) { Text(stringResource(R.string.report_clear_log)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.report_cancel)) }
                    },
                )
            }
            }
        }
    }
    if (startSit) {
        AlertDialog(
            onDismissRequest = { startSit = false },
            title = { Text(stringResource(R.string.report_start_sit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldwatchOutlinedField(
                        value = sitNameDraft,
                        onValueChange = { sitNameDraft = it.take(Sit.NAME_MAX) },
                        label = stringResource(R.string.report_name),
                    )
                    Text(
                        stringResource(R.string.report_start_sit_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Sit.dropWarning(state.sit.closed)?.let { warn ->
                        Text(
                            warn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    startSit = false
                    vm.startSit(sitNameDraft)
                }) { Text(stringResource(R.string.report_start)) }
            },
            dismissButton = {
                TextButton(onClick = { startSit = false }) { Text(stringResource(R.string.report_cancel)) }
            },
        )
    }
    val renaming = renameSitId
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { renameSitId = null },
            title = { Text(stringResource(R.string.report_rename_sit)) },
            text = {
                FieldwatchOutlinedField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(Sit.NAME_MAX) },
                    label = stringResource(R.string.report_name),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameSitId = null
                    vm.renameSit(renaming, renameDraft)
                }) { Text(stringResource(R.string.report_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameSitId = null }) { Text(stringResource(R.string.report_cancel)) }
            },
        )
    }
    val deleting = deleteSitId
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteSitId = null },
            title = { Text(stringResource(R.string.report_delete_sit_question)) },
            text = { Text(stringResource(R.string.report_delete_sit_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteSitId = null
                    vm.deleteSit(deleting)
                }) { Text(stringResource(R.string.report_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteSitId = null }) { Text(stringResource(R.string.report_cancel)) }
            },
        )
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.report_delete_all_sits_question)) },
            text = { Text(stringResource(R.string.report_delete_all_sits_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    vm.deleteAllSits()
                }) { Text(stringResource(R.string.report_delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.report_cancel)) }
            },
        )
    }
}

@Composable
private fun SitChoiceRow(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Column(Modifier.padding(start = 8.dp).fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected && enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdvertisedTrackSwatch() {
    Canvas(Modifier.width(28.dp).height(10.dp)) {
        val dash = 3.dp.toPx()
        val gap = 4.5.dp.toPx()
        drawLine(
            Color.White,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), 0f),
        )
    }
}

@Composable
private fun AdvertisedRingSwatch() {
    Canvas(Modifier.size(12.dp)) {
        drawCircle(
            Color.White,
            radius = size.minDimension / 2f - 1.dp.toPx(),
            style = Stroke(width = 1.6.dp.toPx()),
        )
    }
}

@Composable
private fun GnssSwatch() {
    val ink = Color(0xFFB91C1C).nightIf(LocalNightMode.current)
    Canvas(Modifier.size(14.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        fun diamond(rad: Float) = Path().apply {
            moveTo(c.x, c.y - rad)
            lineTo(c.x + rad * 0.86f, c.y)
            lineTo(c.x, c.y + rad)
            lineTo(c.x - rad * 0.86f, c.y)
            close()
        }
        drawPath(diamond(r), Color.White)
        drawPath(diamond(r * 0.72f), ink)
    }
}

@Composable
private fun GnssPathNote(pin: SitPathPlot.GnssPin) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.report_gnss_pin_line, pin.title(), pin.confidence),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            pin.whenLine(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            pin.detail.trim().ifBlank { stringResource(R.string.report_signal_changed) },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PilotSwatch() {
    val painter = rememberVectorPainter(Icons.Outlined.Person)
    Canvas(Modifier.size(18.dp)) {
        val radius = size.minDimension / 2f
        val disc = radius * 0.86f
        drawCircle(Color.White, radius = radius)
        drawCircle(Color(0xFFF4F7FB), radius = disc)
        drawCircle(Color(0xFF3D4A55), radius = disc, style = Stroke(width = 1.2.dp.toPx()))
        val icon = disc * 1.35f
        translate((size.width - icon) / 2f, (size.height - icon) / 2f) {
            with(painter) {
                draw(Size(icon, icon), colorFilter = ColorFilter.tint(Color(0xFF3D4A55)))
            }
        }
    }
}

/** A one-token serial is the UAS id. A name with a space stays. A MAC keeps its prefix. */
private fun privacyAircraftHeading(context: Context, title: String, demo: Boolean): String {
    if (!demo || title.isBlank()) return title
    val serial = !title.contains(' ') && !title.contains(':') &&
        title.length >= 6 && title.any { it.isDigit() } && title.any { it.isLetter() }
    if (serial) return context.getString(R.string.report_aircraft)
    return MacUtil.redactMacIn(title, title, true)
}

@Composable
private fun privacyAircraftCaption(caption: String, title: String, demo: Boolean): String {
    if (!demo || caption.isBlank()) return caption
    val masked = stringResource(R.string.report_masked_token)
    val stripped = if (title.isBlank()) caption else caption.replace(title, masked, ignoreCase = true)
    return Geo.redactCoordsIn(MacUtil.redactMacIn(stripped, title, true), true)
}

@Composable
private fun PathRadioRow(
    index: Int,
    dot: SitPathPlot.Dot,
    demoMode: Boolean,
    onOpen: () -> Unit,
) {
    val mac = MacUtil.screenMac(dot.mac, demoMode)
    val named = dot.label.isNotBlank() && !dot.label.equals(dot.mac, ignoreCase = true)
    val label = MacUtil.redactMacIn(dot.label, dot.mac, demoMode)
    val fleets = dot.fleetNames.joinToString(" · ")
    val note = dot.observerNotes.trim()
    val accent = (if (dot.accentArgb != 0) Color(dot.accentArgb) else MaterialTheme.colorScheme.onSurfaceVariant)
        .nightIf(LocalNightMode.current)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$index",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        RadioClassBadge(dot.classKind, accent, compact = true)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            if (named) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (mac.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioKindMark(dot.kind, size = 13.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        mac,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (fleets.isNotEmpty()) {
                Text(
                    fleets,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.isNotEmpty()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Cyan.nightIf(LocalNightMode.current),
                )
            }
        }
    }
}

private fun compareThisCaption(context: Context, state: FieldwatchUi): String {
    val open = state.sit.open
    if (open != null) return context.getString(R.string.report_compare_this_sit_named, open.name)
    val selected = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
    if (selected != null) return context.getString(R.string.report_compare_this_sit_named, selected.name)
    return context.getString(R.string.report_compare_this_sit_default)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatBlock(
    kind: LogExportKind,
    radios: LogExportRadios,
    exporting: Boolean,
    onKind: (LogExportKind) -> Unit,
    onRadios: (LogExportRadios) -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    hint: String,
) {
    var openFormat by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = openFormat,
        onExpandedChange = { openFormat = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        FieldwatchDropdownField(stringResource(R.string.report_format), stringResource(kind.labelRes), openFormat)
        ExposedDropdownMenu(openFormat, { openFormat = false }) {
            LogExportKind.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(stringResource(item.labelRes)) },
                    onClick = {
                        onKind(item)
                        openFormat = false
                    },
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LogExportRadios.entries.forEach { item ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = radios == item,
                        onClick = { onRadios(item) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = radios == item,
                    onClick = { onRadios(item) },
                    enabled = !exporting,
                )
                Text(stringResource(item.labelRes), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    FieldwatchActionButton(
        onClick = onShare,
        enabled = !exporting,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.report_share)) }
    FieldwatchActionButton(
        onClick = onSave,
        enabled = !exporting,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.report_save_to_storage)) }
    Text(
        hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun sitReportCaption(context: Context, state: FieldwatchUi): String {
    val open = state.sit.open
    val selected = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
    val window = when {
        open != null -> context.getString(R.string.report_sit_report_window_this, open.name)
        selected != null -> context.getString(R.string.report_sit_report_window_selected, selected.name)
        else -> context.getString(R.string.report_sit_report_window_default)
    }
    return context.getString(R.string.report_sit_report_caption, window)
}
