package app.fieldwatch.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.R
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchSlider
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.FilterLogic
import app.fieldwatch.domain.FilterPreset
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.ui.ClassGlyphs
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.spectreSectionFill
import app.fieldwatch.ui.component.spectreTileEdge
import app.fieldwatch.ui.component.spectreTileFill
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.PhosphorActive
import app.fieldwatch.ui.theme.nightIf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen(state: FieldwatchUi, vm: FieldwatchViewModel) {
    var presetName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<FilterPreset?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val filter = state.filter
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar(stringResource(R.string.filter_title)) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(stringResource(R.string.filter_section_presets)) {
            Text(
                stringResource(R.string.filter_presets_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.presets.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            row.forEach { preset ->
                                PresetChip(
                                    name = preset.name,
                                    selected = preset.filter == filter,
                                    onApply = { vm.applyPreset(preset) },
                                    onLongPress = { pendingDelete = preset },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldwatchOutlinedField(
                    presetName,
                    { presetName = it },
                    stringResource(R.string.filter_save_current_as),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    if (presetName.isNotBlank()) {
                        vm.savePreset(presetName.trim())
                        presetName = ""
                    }
                }) { Text(stringResource(R.string.filter_save)) }
            }
            }

            SectionCard(stringResource(R.string.filter_section_radios)) {
            Text(
                if (filter.movingWithYou) {
                    stringResource(R.string.filter_radios_help_moving)
                } else {
                    stringResource(R.string.filter_radios_help_normal)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = !filter.movingWithYou && filter.showWifi && filter.showBle,
                    onClick = { vm.updateFilter { it.copy(showWifi = true, showBle = true) } },
                    enabled = !filter.movingWithYou,
                    label = { Text(stringResource(R.string.filter_radio_both)) },
                )
                FieldwatchFilterChip(
                    selected = !filter.movingWithYou && filter.showWifi && !filter.showBle,
                    onClick = { vm.updateFilter { it.copy(showWifi = true, showBle = false) } },
                    enabled = !filter.movingWithYou,
                    label = { Text(stringResource(R.string.filter_radio_wifi_only)) },
                )
                FieldwatchFilterChip(
                    selected = filter.movingWithYou || (filter.showBle && !filter.showWifi),
                    onClick = { vm.updateFilter { it.copy(showWifi = false, showBle = true) } },
                    label = { Text(stringResource(R.string.filter_radio_ble_only)) },
                )
            }
            }

            SectionCard(stringResource(R.string.filter_section_moving_with_you)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_moving_label), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.movingWithYou,
                    { on ->
                        vm.updateFilter { current ->
                            if (!on) current.copy(movingWithYou = false)
                            else {
                                // Follow test is BLE. Leftover Trackers / Show only hides
                                // unmatched rows; AirTags rotate, so Live looks empty.
                                val hiding = current.useClassFilter && current.excludeClasses
                                current.copy(
                                    movingWithYou = true,
                                    showWifi = false,
                                    showBle = true,
                                    namedOnly = false,
                                    customNamesOnly = false,
                                    watchedOnly = false,
                                    useClassFilter = hiding,
                                    excludeClasses = hiding,
                                    classes = if (hiding) current.classes else emptySet(),
                                    includeSignatures = false,
                                )
                            }
                        }
                    },
                )
            }
            Text(
                when {
                    !state.settings.tagLocation ->
                        stringResource(R.string.filter_moving_help_no_tag_location)
                    state.operatorSpanM < 45.0 ->
                        stringResource(
                            R.string.filter_moving_help_short_span,
                            state.operatorSpanM.toInt(),
                            when {
                                filter.customNamesOnly ->
                                    stringResource(R.string.filter_moving_help_custom_names_only)
                                filter.watchedOnly ->
                                    stringResource(R.string.filter_moving_help_watched_only)
                                filter.namedOnly || filter.namedOnlyImplied() ->
                                    stringResource(R.string.filter_moving_help_named_only)
                                else -> ""
                            },
                            if (filter.hideMine) stringResource(R.string.filter_moving_help_hide_mine) else "",
                        )
                    filter.customNamesOnly || filter.watchedOnly || filter.namedOnly || filter.namedOnlyImplied() ->
                        stringResource(
                            R.string.filter_moving_help_other_filter,
                            state.operatorSpanM.toInt(),
                            if (filter.hideMine) stringResource(R.string.filter_moving_help_hide_mine) else "",
                        )
                    else ->
                        stringResource(
                            R.string.filter_moving_help_else,
                            state.operatorSpanM.toInt(),
                            if (filter.hideMine) {
                                stringResource(R.string.filter_moving_help_hide_mine_phrase)
                            } else {
                                stringResource(R.string.filter_moving_help_tag_phrase)
                            },
                        )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(stringResource(R.string.filter_section_new_detections)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.arrivalsLearning) {
                        stringResource(R.string.filter_new_detections_learning)
                    } else {
                        stringResource(R.string.filter_new_detections_only)
                    },
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(
                    filter.arrivalsOnly,
                    { on -> vm.updateFilter { it.copy(arrivalsOnly = on) } },
                )
            }
            Text(
                if (filter.arrivalsOnly) {
                    stringResource(R.string.filter_new_detections_help_on) +
                        when {
                            state.arrivalsLearning ->
                                stringResource(R.string.filter_new_detections_help_learning)
                            state.hiddenKnown > 0 ->
                                stringResource(R.string.filter_new_detections_help_hidden_count, state.hiddenKnown)
                            else ->
                                stringResource(R.string.filter_new_detections_help_nothing)
                        }
                } else {
                    stringResource(R.string.filter_new_detections_help_off)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(stringResource(R.string.filter_section_who_stays)) {
            val namedImplied = filter.namedOnlyImplied()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.filter_signatures_only_hide_unmatched),
                    Modifier.weight(1f),
                    color = if (namedImplied) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                FieldwatchSwitch(
                    checked = filter.namedOnly || namedImplied,
                    onCheckedChange = { on ->
                        if (!namedImplied) vm.updateFilter { it.copy(namedOnly = on) }
                    },
                    enabled = !namedImplied,
                )
            }
            if (namedImplied) {
                Text(
                    stringResource(R.string.filter_show_only_hides_unmatched),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_watched_only), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.watchedOnly,
                    { on -> vm.updateFilter { it.copy(watchedOnly = on) } },
                )
            }
            Text(
                stringResource(R.string.filter_watched_only_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_named_radios_only), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.customNamesOnly,
                    { on -> vm.updateFilter { it.copy(customNamesOnly = on) } },
                )
            }
            Text(
                stringResource(R.string.filter_named_radios_only_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_hide_my_radios), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.hideMine,
                    { on -> vm.updateFilter { it.copy(hideMine = on) } },
                )
            }
            Text(
                stringResource(R.string.filter_hide_my_radios_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_hide_fast_pair), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.hideFastPairAccountKey,
                    { on -> vm.updateFilter { it.copy(hideFastPairAccountKey = on) } },
                )
            }
            Text(
                stringResource(R.string.filter_hide_fast_pair_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(stringResource(R.string.filter_section_signature_classes)) {
            Text(
                stringResource(R.string.filter_signature_classes_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = filter.useClassFilter && !filter.excludeClasses,
                    onClick = {
                        vm.updateFilter {
                            val on = !(it.useClassFilter && !it.excludeClasses)
                            it.copy(useClassFilter = on, excludeClasses = false)
                        }
                    },
                    label = { Text(stringResource(R.string.filter_show_only)) },
                )
                FieldwatchFilterChip(
                    selected = filter.useClassFilter && filter.excludeClasses,
                    onClick = {
                        vm.updateFilter {
                            val on = !(it.useClassFilter && it.excludeClasses)
                            it.copy(useClassFilter = on, excludeClasses = on)
                        }
                    },
                    label = { Text(stringResource(R.string.filter_hide_these)) },
                )
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SignatureClass.visible.sortedBy { it.label().lowercase() }.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            row.forEach { kind ->
                                val on = kind in filter.classes
                                FieldwatchFilterChip(
                                    selected = on,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(max = 32.dp),
                                    onClick = {
                                        vm.updateFilter { current ->
                                            val next = current.classes.toMutableSet()
                                            if (on) next.remove(kind) else next.add(kind)
                                            current.copy(classes = next)
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            ClassGlyphs.of(kind),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            stringResource(kind.labelRes),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            }

            SectionCard(stringResource(R.string.filter_section_selected_signatures)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_show_only_selected), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.includeSignatures,
                    { on -> vm.updateFilter { it.copy(includeSignatures = on) } },
                )
            }
            if (filter.includeSignatures) {
                SignaturePickList(
                    fleets = state.fleets,
                    selected = filter.includeFleetIds,
                    help = stringResource(R.string.filter_show_only_selected_help),
                    onToggle = { id, checked ->
                        vm.updateFilter { current ->
                            val next = current.includeFleetIds.toMutableSet()
                            if (checked) next.add(id) else next.remove(id)
                            current.copy(includeFleetIds = next)
                        }
                    },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_hide_selected), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.excludeSignatures,
                    { on -> vm.updateFilter { it.copy(excludeSignatures = on) } },
                )
            }
            if (filter.excludeSignatures) {
                SignaturePickList(
                    fleets = state.fleets,
                    selected = filter.fleetIds,
                    help = stringResource(R.string.filter_hide_selected_help),
                    onToggle = { id, checked ->
                        vm.updateFilter { current ->
                            val next = current.fleetIds.toMutableSet()
                            if (checked) next.add(id) else next.remove(id)
                            current.copy(fleetIds = next)
                        }
                    },
                )
            }
            }

            SectionCard(stringResource(R.string.filter_section_fine_filter)) {
            var rssiDrag by remember { mutableIntStateOf(filter.rssiMin) }
            var rssiDragging by remember { mutableStateOf(false) }
            LaunchedEffect(filter.rssiMin) {
                if (!rssiDragging) rssiDrag = filter.rssiMin
            }
            Text(
                stringResource(R.string.filter_minimum_rssi, rssiDrag),
                style = MaterialTheme.typography.labelLarge,
            )
            FieldwatchSlider(
                value = rssiDrag.toFloat(),
                onValueChange = { v ->
                    rssiDragging = true
                    rssiDrag = v.toInt()
                },
                onValueChangeFinished = {
                    vm.updateFilter { it.copy(rssiMin = rssiDrag) }
                    rssiDragging = false
                },
                valueRange = -100f..-30f,
            )

            FieldwatchOutlinedField(
                filter.nameQuery,
                { value -> vm.updateFilter { it.copy(nameQuery = value) } },
                stringResource(R.string.filter_name_mac_contains),
            )
            FieldwatchOutlinedField(
                filter.ouiQuery,
                { value -> vm.updateFilter { it.copy(ouiQuery = value) } },
                stringResource(R.string.filter_vendor_contains),
            )

            Text(
                stringResource(R.string.filter_name_vendor_signal_class),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                stringResource(R.string.filter_and_or_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = filter.logic == FilterLogic.AND,
                    onClick = { vm.updateFilter { it.copy(logic = FilterLogic.AND) } },
                    label = { Text(stringResource(R.string.filter_and)) },
                )
                FieldwatchFilterChip(
                    selected = filter.logic == FilterLogic.OR,
                    onClick = { vm.updateFilter { it.copy(logic = FilterLogic.OR) } },
                    label = { Text(stringResource(R.string.filter_or)) },
                )
            }

            FieldwatchActionButton(onClick = { confirmReset = true }) {
                Text(stringResource(R.string.filter_reset))
            }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.filter_reset_title)) },
            text = {
                Text(stringResource(R.string.filter_reset_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        vm.updateFilter { app.fieldwatch.domain.FilterState() }
                    },
                ) { Text(stringResource(R.string.filter_reset_button)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.filter_cancel))
                }
            },
        )
    }
    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.filter_delete_preset_title)) },
            text = {
                Text(
                    if (preset.isBuiltIn()) {
                        stringResource(R.string.filter_delete_builtin_preset, preset.name)
                    } else {
                        stringResource(R.string.filter_delete_custom_preset, preset.name)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePreset(preset.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.filter_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.filter_cancel))
                }
            },
        )
    }
}

@Composable
private fun SignaturePickList(
    fleets: List<Fleet>,
    selected: Set<String>,
    help: String,
    onToggle: (id: String, checked: Boolean) -> Unit,
) {
    val groups = remember(fleets) {
        fleets.groupBy { it.kind.folded() }
            .toList()
            .sortedBy { it.first.label().lowercase() }
            .map { (kind, rows) -> kind to rows.sortedBy { it.name.lowercase() } }
    }
    var open by remember {
        mutableStateOf(
            groups.filter { (_, rows) -> rows.any { it.id in selected } }
                .map { it.first.name }
                .toSet(),
        )
    }
    Column(
        modifier = Modifier.padding(start = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        groups.forEach { (kind, rows) ->
            val classId = kind.name
            val expanded = classId in open
            val picked = rows.count { it.id in selected }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        open = if (expanded) open - classId else open + classId
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    ClassGlyphs.of(kind),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(kind.labelRes),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(if (expanded) "▾  " else "▸  ")
                        if (picked > 0) append("$picked/")
                        append(rows.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (picked > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (expanded) {
                rows.forEach { fleet ->
                    val on = fleet.id in selected
                    Row(
                        modifier = Modifier.padding(start = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(fleet.name, Modifier.weight(1f))
                        FieldwatchSwitch(on, { checked -> onToggle(fleet.id, checked) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(
    name: String,
    selected: Boolean = false,
    onApply: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = FilterChipDefaults.shape,
        color = if (selected) spectreSectionFill() else spectreTileFill(),
        border = BorderStroke(
            1.dp,
            if (selected) PhosphorActive.nightIf(LocalNightMode.current) else spectreTileEdge(),
        ),
        modifier = modifier
            .heightIn(max = 32.dp)
            .combinedClickable(
                onClick = onApply,
                onLongClick = onLongPress,
            ),
    ) {
        Text(
            name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
