package app.fieldwatch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchDropdownField
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fieldwatch.R
import app.fieldwatch.domain.DecodedFieldValue
import app.fieldwatch.domain.DecodeEndian
import app.fieldwatch.domain.DecodeField
import app.fieldwatch.domain.DecodeSource
import app.fieldwatch.domain.DecodeType
import app.fieldwatch.domain.DecodeWhen
import app.fieldwatch.domain.DecodeWhenOp
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.FleetDecode
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureFieldDecoder
import app.fieldwatch.domain.defaultLength
import app.fieldwatch.domain.hexSpaced
import app.fieldwatch.domain.normalized
import app.fieldwatch.domain.resolvedLength
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.spectreTileFill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecodeFieldsScreen(
    fleet: Fleet,
    previewDevice: Sighting?,
    onSave: (FleetDecode?) -> Unit,
    onBack: () -> Unit,
) {
    val initial = fleet.decode
    var source by remember { mutableStateOf(initial?.source ?: DecodeSource.MANUFACTURER_DATA) }
    var companyText by remember {
        mutableStateOf(initial?.companyId?.takeIf { it != 0 }?.let { "0x%04X".format(it) }.orEmpty())
    }
    var serviceUuid by remember { mutableStateOf(initial?.serviceUuid.orEmpty()) }
    var fields by remember { mutableStateOf(initial?.fields ?: emptyList()) }
    var confirmRemove by remember { mutableStateOf(false) }
    val includeCompanyId = initial?.includeCompanyId == true

    fun currentDecode(): FleetDecode? {
        val cleaned = fields.filter { it.label.isNotBlank() && it.id.isNotBlank() }.map { it.normalized() }
        if (cleaned.isEmpty()) return null
        return FleetDecode(
            source = source,
            serviceUuid = serviceUuid.trim().ifBlank { null },
            companyId = parseCompanyId(companyText),
            includeCompanyId = includeCompanyId,
            fields = cleaned,
        )
    }

    val previewDecode = currentDecode()
    val previewRows = remember(previewDecode, previewDevice) {
        if (previewDecode == null || previewDevice == null) emptyList()
        else SignatureFieldDecoder.decodeFleet(fleet.copy(decode = previewDecode), previewDecode, previewDevice)
    }
    val previewHex = remember(previewDecode, previewDevice) {
        if (previewDecode == null || previewDevice == null) null
        else SignatureFieldDecoder.payloadHex(previewDecode, previewDevice)
    }

    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = stringResource(R.string.decode_fields_title),
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
                actions = { TextButton(onClick = { onSave(currentDecode()) }) { Text(stringResource(R.string.save)) } },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(stringResource(R.string.decode_source)) {
            Text(
                stringResource(R.string.decode_source_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = source == DecodeSource.MANUFACTURER_DATA,
                    onClick = { source = DecodeSource.MANUFACTURER_DATA },
                    label = { Text(stringResource(R.string.decode_manufacturer)) },
                )
                FieldwatchFilterChip(
                    selected = source == DecodeSource.SERVICE_DATA,
                    onClick = { source = DecodeSource.SERVICE_DATA },
                    label = { Text(stringResource(R.string.decode_service_data)) },
                )
            }
            if (source == DecodeSource.MANUFACTURER_DATA) {
                CompactField(
                    companyText,
                    { companyText = it },
                    stringResource(R.string.decode_company_id),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.decode_company_id_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CompactField(
                    serviceUuid,
                    { serviceUuid = it },
                    stringResource(R.string.decode_service_uuid),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.decode_service_uuid_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }

            SectionCard(stringResource(R.string.decode_fields)) {
            fields.forEachIndexed { index, field ->
                FieldCard(
                    field = field,
                    onChange = { next ->
                        fields = fields.toMutableList().also { it[index] = next }
                    },
                    onDelete = {
                        fields = fields.filterIndexed { i, _ -> i != index }
                    },
                )
            }
            val context = LocalContext.current
            FieldwatchActionButton(
                onClick = {
                    val nextOffset = fields.lastOrNull()?.let { it.offset + it.resolvedLength() } ?: 0
                    val n = fields.size + 1
                    fields = fields + DecodeField(
                        id = "field_$n",
                        label = context.getString(R.string.decode_field_default, n),
                        offset = nextOffset,
                        type = DecodeType.U8,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.decode_add_field)) }
            if (initial != null || fields.isNotEmpty()) {
                FieldwatchActionButton(
                    onClick = { confirmRemove = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.decode_remove_map)) }
                Text(
                    stringResource(R.string.decode_remove_map_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }

            SectionCard(stringResource(R.string.decode_preview)) {
            PreviewBlock(
                previewDevice = previewDevice,
                previewHex = previewHex,
                previewRows = previewRows,
            )
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.decode_remove_confirm_title)) },
            text = {
                Text(stringResource(R.string.decode_remove_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onSave(null)
                    },
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PreviewBlock(
    previewDevice: Sighting?,
    previewHex: String?,
    previewRows: List<DecodedFieldValue>,
) {
    when {
        previewDevice == null -> {
            Text(
                stringResource(R.string.decode_preview_no_radio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        previewHex.isNullOrBlank() -> {
            Text(
                stringResource(R.string.decode_preview_no_bytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            Text(
                previewHex.hexSpaced(),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (previewRows.isEmpty()) {
                Text(
                    stringResource(R.string.decode_preview_nothing_parsed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                previewRows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(row.display, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldCard(
    field: DecodeField,
    onChange: (DecodeField) -> Unit,
    onDelete: () -> Unit,
) {
    val needsEndian = field.type !in setOf(
        DecodeType.U8, DecodeType.I8, DecodeType.UTF8, DecodeType.HEX, DecodeType.BOOL,
    )
    val numeric = field.type in setOf(
        DecodeType.U8, DecodeType.I8, DecodeType.U16, DecodeType.I16,
        DecodeType.U24, DecodeType.U32, DecodeType.I32, DecodeType.F32, DecodeType.BITS,
    )
    val idIsCustom = !looksGeneratedId(field.id, field.label)
    var more by remember(field.id) {
        mutableStateOf(idIsCustom || field.modulo != null)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = spectreTileFill(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactField(
                    field.label,
                    { next ->
                        val id = if (looksGeneratedId(field.id, field.label)) slugId(next) else field.id
                        onChange(field.copy(label = next, id = id))
                    },
                    stringResource(R.string.decode_label),
                    modifier = Modifier.weight(1f),
                )
                TypeMenu(field.type, Modifier.width(112.dp)) { onChange(field.copy(type = it)) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.decode_delete_field)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactField(
                    field.offset.toString(),
                    { onChange(field.copy(offset = it.toIntOrNull() ?: 0)) },
                    stringResource(R.string.decode_offset),
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                CompactField(
                    (field.length ?: field.type.defaultLength()).toString(),
                    { onChange(field.copy(length = it.toIntOrNull()?.coerceAtLeast(1))) },
                    stringResource(R.string.decode_length),
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                CompactField(
                    field.unit.orEmpty(),
                    { onChange(field.copy(unit = it.ifBlank { null })) },
                    stringResource(R.string.decode_unit),
                    modifier = Modifier.width(72.dp),
                )
                if (needsEndian) {
                    EndianMenu(field.endian, Modifier.weight(1f)) { onChange(field.copy(endian = it)) }
                }
            }
            if (field.type == DecodeType.BITS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactField(
                        (field.bitOffset ?: 0).toString(),
                        { onChange(field.copy(bitOffset = it.toIntOrNull() ?: 0)) },
                        stringResource(R.string.decode_bit_offset),
                        keyboard = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    CompactField(
                        (field.bitWidth ?: 1).toString(),
                        { onChange(field.copy(bitWidth = it.toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
                        stringResource(R.string.decode_bit_width),
                        keyboard = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (numeric) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactField(
                        field.scale?.toString().orEmpty(),
                        { onChange(field.copy(scale = it.toDoubleOrNull())) },
                        stringResource(R.string.decode_scale),
                        keyboard = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    CompactField(
                        field.offsetAdd?.toString().orEmpty(),
                        { onChange(field.copy(offsetAdd = it.toDoubleOrNull())) },
                        stringResource(R.string.decode_add),
                        keyboard = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            OnlyIfBlock(field.gate, onChange = { onChange(field.copy(gate = it)) })
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.decode_live_row), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.decode_live_row_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = field.live,
                    onCheckedChange = { onChange(field.copy(live = it)) },
                )
            }
            NamedValuesBlock(field, onChange)
            if (!more) {
                TextButton(onClick = { more = true }) { Text(stringResource(R.string.decode_more)) }
            } else {
                CompactField(
                    field.id,
                    { onChange(field.copy(id = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' })) },
                    stringResource(R.string.decode_field_id_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (numeric) {
                    CompactField(
                        field.modulo?.toString().orEmpty(),
                        { onChange(field.copy(modulo = it.toDoubleOrNull())) },
                        stringResource(R.string.decode_modulo_label),
                        keyboard = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { more = false }) { Text(stringResource(R.string.decode_hide_extra)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlyIfBlock(gate: DecodeWhen?, onChange: (DecodeWhen?) -> Unit) {
    if (gate == null) {
        TextButton(onClick = {
            onChange(DecodeWhen(offset = 0, length = 1, op = DecodeWhenOp.EQ, valueHex = ""))
        }) { Text(stringResource(R.string.decode_only_if_short)) }
        return
    }
    Text(stringResource(R.string.decode_only_if), style = MaterialTheme.typography.titleSmall)
    if (gate.op == DecodeWhenOp.LEN) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WhenOpMenu(gate.op, Modifier.weight(1f)) { onChange(gate.copy(op = it)) }
            CompactField(
                gate.length.toString(),
                { onChange(gate.copy(length = it.toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
                stringResource(R.string.decode_bytes),
                keyboard = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactField(
                gate.offset.toString(),
                { onChange(gate.copy(offset = it.toIntOrNull() ?: 0)) },
                stringResource(R.string.decode_offset),
                keyboard = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            CompactField(
                gate.length.toString(),
                { onChange(gate.copy(length = it.toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
                stringResource(R.string.decode_length),
                keyboard = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WhenOpMenu(gate.op, Modifier.weight(1f)) { onChange(gate.copy(op = it)) }
            CompactField(
                gate.valueHex,
                { onChange(gate.copy(valueHex = it)) },
                stringResource(R.string.decode_hex),
                modifier = Modifier.width(96.dp),
            )
        }
    }
    TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.remove)) }
}

@Composable
private fun NamedValuesBlock(
    field: DecodeField,
    onChange: (DecodeField) -> Unit,
) {
    val fieldId = field.id
    val labels = field.enumLabels
    var rows by remember(fieldId) {
        mutableStateOf(labels?.toList() ?: emptyList())
    }
    var open by remember(fieldId) { mutableStateOf(rows.isNotEmpty()) }
    fun publish(
        nextRows: List<Pair<String, String>>,
        emphasis: List<String> = field.liveEmphasis,
        notes: Map<String, String>? = field.enumNotes,
    ) {
        onChange(
            field.copy(
                enumLabels = nextRows.toEnumMap(),
                liveEmphasis = emphasis,
                enumNotes = notes?.filterValues { it.isNotBlank() }?.ifEmpty { null },
            ),
        )
    }
    if (!open) {
        TextButton(onClick = {
            open = true
            rows = listOf("" to "")
        }) { Text(stringResource(R.string.decode_named_values_short)) }
        return
    }
    Text(stringResource(R.string.decode_named_values), style = MaterialTheme.typography.titleSmall)
    rows.forEachIndexed { index, (raw, shown) ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactField(
                    raw,
                    { next ->
                        val nextRows = rows.toMutableList().also { it[index] = next to shown }
                        rows = nextRows
                        val emphasis = field.liveEmphasis.map { if (it == raw) next else it }
                        val notes = field.enumNotes?.mapKeys { (key, _) -> if (key == raw) next else key }
                        publish(nextRows, emphasis, notes)
                    },
                    stringResource(R.string.decode_raw),
                    modifier = Modifier.width(88.dp),
                )
                CompactField(
                    shown,
                    { next ->
                        val nextRows = rows.toMutableList().also { it[index] = raw to next }
                        rows = nextRows
                        publish(nextRows)
                    },
                    stringResource(R.string.decode_show_as),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val nextRows = rows.filterIndexed { i, _ -> i != index }
                        rows = nextRows
                        val emphasis = field.liveEmphasis.filter { it != raw }
                        val notes = field.enumNotes?.filterKeys { it != raw }
                        if (nextRows.isEmpty()) {
                            open = false
                            onChange(field.copy(enumLabels = null, liveEmphasis = emphasis, enumNotes = notes?.ifEmpty { null }))
                        } else {
                            publish(nextRows, emphasis, notes)
                        }
                    },
                ) { Icon(Icons.Outlined.Delete, stringResource(R.string.decode_delete_value_cd)) }
            }
            if (field.live && raw.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val strong = field.liveEmphasis.any { it == raw }
                    FieldwatchFilterChip(
                        selected = strong,
                        onClick = {
                            val emphasis = if (strong) {
                                field.liveEmphasis.filter { it != raw }
                            } else {
                                field.liveEmphasis + raw
                            }
                            publish(rows, emphasis)
                        },
                        label = { Text(stringResource(R.string.decode_value_strong)) },
                    )
                    CompactField(
                        field.enumNotes?.get(raw).orEmpty(),
                        { note ->
                            val notes = (field.enumNotes ?: emptyMap()).toMutableMap()
                            if (note.isBlank()) notes.remove(raw) else notes[raw] = note
                            publish(rows, notes = notes)
                        },
                        stringResource(R.string.decode_note),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    Row {
        TextButton(onClick = { rows = rows + ("" to "") }) { Text(stringResource(R.string.decode_add_value)) }
        TextButton(
            onClick = {
                open = false
                rows = emptyList()
                onChange(field.copy(enumLabels = null, liveEmphasis = emptyList(), enumNotes = null))
            },
        ) { Text(stringResource(R.string.remove)) }
    }
}

@Composable
private fun CompactField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    FieldwatchOutlinedField(
        value,
        onChange,
        label,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenOpMenu(op: DecodeWhenOp, modifier: Modifier, onChange: (DecodeWhenOp) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = when (op) {
        DecodeWhenOp.EQ -> stringResource(R.string.decode_when_equals)
        DecodeWhenOp.NEQ -> stringResource(R.string.decode_when_not_equals)
        DecodeWhenOp.MASK -> stringResource(R.string.decode_when_mask)
        DecodeWhenOp.NMASK -> stringResource(R.string.decode_when_nmask)
        DecodeWhenOp.LEN -> stringResource(R.string.decode_when_length)
    }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        FieldwatchDropdownField(stringResource(R.string.decode_when_label), label, open)
        ExposedDropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.decode_when_equals)) }, onClick = { onChange(DecodeWhenOp.EQ); open = false })
            DropdownMenuItem(text = { Text(stringResource(R.string.decode_when_not_equals)) }, onClick = { onChange(DecodeWhenOp.NEQ); open = false })
            DropdownMenuItem(text = { Text(stringResource(R.string.decode_when_mask)) }, onClick = { onChange(DecodeWhenOp.MASK); open = false })
            DropdownMenuItem(text = { Text(stringResource(R.string.decode_when_nmask)) }, onClick = { onChange(DecodeWhenOp.NMASK); open = false })
            DropdownMenuItem(text = { Text(stringResource(R.string.decode_when_payload_length)) }, onClick = { onChange(DecodeWhenOp.LEN); open = false })
        }
    }
}

private fun List<Pair<String, String>>.toEnumMap(): Map<String, String>? {
    val out = linkedMapOf<String, String>()
    for ((k, v) in this) {
        val key = k.trim()
        val label = v.trim()
        if (key.isEmpty() || label.isEmpty()) continue
        out[key] = label
    }
    return out.ifEmpty { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeMenu(type: DecodeType, modifier: Modifier, onChange: (DecodeType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        FieldwatchDropdownField(stringResource(R.string.decode_type_label), type.name.lowercase(), open)
        ExposedDropdownMenu(open, { open = false }) {
            DecodeType.entries.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.name.lowercase()) },
                    onClick = { onChange(t); open = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndianMenu(endian: DecodeEndian, modifier: Modifier, onChange: (DecodeEndian) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        FieldwatchDropdownField(stringResource(R.string.decode_endian_label), if (endian == DecodeEndian.BE) "BE" else "LE", open)
        ExposedDropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.decode_endian_little)) }, onClick = { onChange(DecodeEndian.LE); open = false })
            DropdownMenuItem(text = { Text(stringResource(R.string.decode_endian_big)) }, onClick = { onChange(DecodeEndian.BE); open = false })
        }
    }
}

private fun parseCompanyId(text: String): Int? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val hex = t.removePrefix("0x").removePrefix("0X")
    return hex.toIntOrNull(16) ?: t.toIntOrNull()
}

private fun slugId(label: String): String {
    val slug = label.lowercase().map { ch ->
        if (ch.isLetterOrDigit()) ch else '_'
    }.joinToString("").trim('_')
    val clipped = slug.take(32).ifBlank { "field" }
    return if (clipped.first().isLetter()) clipped else "f_$clipped"
}

private fun looksGeneratedId(id: String, label: String): Boolean =
    id == slugId(label) || id.matches(Regex("field_\\d+"))
