package app.fieldwatch.ui.screen

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import android.content.res.Resources
import app.fieldwatch.R
import app.fieldwatch.ui.component.DecodeGlyph
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.domain.CodDecoder
import app.fieldwatch.domain.FamilyVerdict
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.DeviceExplain
import app.fieldwatch.domain.Palette
import app.fieldwatch.domain.RadioDb
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Rssi
import app.fieldwatch.domain.ServiceDataRecord
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureFamilyHint
import app.fieldwatch.domain.SignatureFieldDecoder
import app.fieldwatch.domain.hexSpaced
import app.fieldwatch.domain.label
import app.fieldwatch.radio.BleAdParser
import app.fieldwatch.ui.RadioKindMark
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.theme.Amber
import app.fieldwatch.ui.theme.Cyan
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf
import app.fieldwatch.ui.component.PresenceTrack
import app.fieldwatch.ui.component.Sparkline
import app.fieldwatch.ui.component.StickyHeight
import app.fieldwatch.ui.component.rssiColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: Sighting,
    vm: FieldwatchViewModel,
    watched: Boolean,
    onBack: () -> Unit,
    onCreateFleet: () -> Unit,
    onHunt: () -> Unit,
    demoMode: Boolean = false,
) {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    val context = LocalContext.current
    val res = context.resources
    val accent = (device.fleetIds.firstOrNull()
        ?.let { Color(Palette.color(vm.fleetColor(it))) }
        ?: rssiColor(device.rssi))
        .nightIf(LocalNightMode.current)
    val facts = device.facts
    val familyHint by vm.familyHint.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val custom = vm.watchLabelFor(device.key)
                    val title = custom?.takeIf { it.isNotBlank() }
                        ?: device.listTitle(res, device.fleetIds.map { vm.fleetName(it) })
                    Text(MacUtil.redactMacIn(title, device.mac, demoMode), maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { vm.toggleWatchDevice(device) }) {
                        Icon(if (watched) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, stringResource(R.string.watch))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(MacUtil.screenMac(device.mac, demoMode), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
            if (device.gone) {
                Text(
                    stringResource(R.string.not_on_air),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            var nameDraft by remember(device.key) {
                mutableStateOf(vm.watchLabelFor(device.key).orEmpty())
            }
            var lastSaved by remember(device.key) {
                mutableStateOf(vm.watchLabelFor(device.key).orEmpty())
            }
            var editingName by remember(device.key) { mutableStateOf(false) }
            val draftLabel = RadioBookmarks.clip(nameDraft)
            val nameIsSaved = lastSaved.isNotBlank() && draftLabel == lastSaved
            val canName = RadioBookmarks.canSetCustomName(device)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (lastSaved.isNotBlank()) {
                        Text(
                            stringResource(R.string.custom_name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(lastSaved, style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.advertised),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            device.name.ifBlank { stringResource(R.string.no_advertised_name) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (device.name.isNotBlank()) {
                        Text(
                            stringResource(R.string.advertised_name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(device.name, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            stringResource(R.string.name_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.no_advertised_name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (canName) {
                    IconButton(onClick = { editingName = !editingName }) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editingName) stringResource(R.string.hide_custom_name) else stringResource(R.string.custom_name),
                        )
                    }
                }
            }
            if (editingName && canName) {
                FieldwatchOutlinedField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(RadioBookmarks.MAX_NAME) },
                    label = stringResource(R.string.custom_name),
                    supportingText = RadioBookmarks.customNameHint(device),
                )
                FieldwatchActionButton(
                    onClick = {
                        vm.saveRadioName(device, nameDraft)
                        nameDraft = draftLabel
                        lastSaved = draftLabel
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.saved_as, draftLabel))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nameDraft.isNotBlank() && !nameIsSaved,
                ) {
                    if (nameIsSaved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text(stringResource(R.string.saved))
                    } else {
                        Text(stringResource(R.string.save_name))
                    }
                }
            }

            var notesDraft by remember(device.key) {
                mutableStateOf(vm.watchObserverNoteFor(device.key).orEmpty())
            }
            var lastSavedNotes by remember(device.key) {
                mutableStateOf(vm.watchObserverNoteFor(device.key).orEmpty())
            }
            var editingNotes by remember(device.key) { mutableStateOf(false) }
            val draftNotes = RadioBookmarks.clipNotes(notesDraft)
            val notesIsSaved = draftNotes == lastSavedNotes
            if (canName || lastSavedNotes.isNotBlank()) {
                ObserverNotesCard(
                    notes = lastSavedNotes,
                    canEdit = canName,
                    editing = editingNotes && canName,
                    draft = notesDraft,
                    onToggleEdit = { editingNotes = !editingNotes },
                    onDraftChange = { notesDraft = it.take(RadioBookmarks.MAX_NOTES) },
                    onSave = {
                        vm.saveRadioNotes(device, notesDraft)
                        notesDraft = draftNotes
                        lastSavedNotes = draftNotes
                        if (lastSaved.isBlank() && draftNotes.isNotBlank()) {
                            val suggest = RadioBookmarks.suggestLabel(
                                res,
                                device,
                                device.fleetIds.map { vm.fleetName(it) },
                            )
                            lastSaved = suggest
                            nameDraft = suggest
                        }
                        editingNotes = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (draftNotes.isBlank()) context.getString(R.string.observer_notes_cleared) else context.getString(R.string.observer_notes_saved),
                            )
                        }
                    },
                    saveEnabled = !notesIsSaved,
                    saved = notesIsSaved && lastSavedNotes.isNotBlank(),
                )
            }
            if (canName) {
                var mineOn by remember(device.key) { mutableStateOf(vm.isMine(device.key)) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.mine), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.mine_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FieldwatchSwitch(
                            checked = mineOn,
                            onCheckedChange = { on ->
                                vm.setRadioMine(device, on)
                                mineOn = on
                                if (on && lastSaved.isBlank()) {
                                    val suggest = RadioBookmarks.suggestLabel(
                                        res,
                                        device,
                                        device.fleetIds.map { vm.fleetName(it) },
                                    )
                                    lastSaved = suggest
                                    nameDraft = suggest
                                }
                                if (on) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.marked_as_yours))
                                    }
                                }
                            },
                        )
                    }
                }
            }

            val guess = DeviceExplain.guess(res, device, device.fleetIds.map { vm.fleetName(it) })
            StickyHeight(device.key to "guess") { GuessCard(guess) }
            val attention = vm.attentionNotesFor(device)
            if (attention.isNotEmpty()) {
                StickyHeight(device.key to "attention") { ExtraAttentionCard(attention) }
            }
            val notes = vm.signatureNotesFor(device)
            if (notes.isNotEmpty()) {
                StickyHeight(device.key to "notes") { SignatureNotesCard(notes) }
            }

            StickyHeight(device.key to "identity") {
                Section(stringResource(R.string.identity))
                Meta(
                    stringResource(R.string.radio),
                    if (device.kind == RadioKind.WIFI) {
                        stringResource(R.string.wifi_ap_beaconing)
                    } else {
                        stringResource(R.string.ble_advertiser)
                    },
                )
                Meta(stringResource(R.string.address), DeviceExplain.addressExplain(res, device))
                vendorLine(device)?.let { Meta(stringResource(R.string.who_made_it), it) }
                    ?: Meta(stringResource(R.string.oui_vendor_prefix), stringResource(R.string.oui_no_ieee_match, device.oui))
                if (device.hiddenSsid) {
                    Meta(stringResource(R.string.network_name_ssid), stringResource(R.string.hidden_ssid_value))
                }
            }

            StickyHeight(device.key to "signal") {
                Section(stringResource(R.string.signal))
                if (device.gone) {
                    Meta(stringResource(R.string.how_loud_rssi), stringResource(R.string.not_available))
                    Meta(
                        stringResource(R.string.last_heard),
                        buildString {
                            append(fmt.format(Date(device.lastSeen)))
                            Rssi.lastMeasured(device.rssi, device.rssiHistory)?.let {
                                append(stringResource(R.string.at_dbm, it))
                            }
                        },
                    )
                } else {
                    Meta(stringResource(R.string.how_loud_rssi), DeviceExplain.rssiExplain(res, device.rssi))
                    Text(
                        stringResource(R.string.rssi_distance_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Meta(
                    stringResource(R.string.heard_range_session),
                    Rssi.sessionRange(device.rssiMin, device.rssiMax, device.rssiHistory),
                )
                facts.txPowerDbm?.let {
                    Meta(stringResource(R.string.claimed_tx_power), stringResource(R.string.claimed_tx_power_format, it))
                }
                if (device.channel != 0 || device.frequencyMhz != 0) {
                    Meta(
                        stringResource(R.string.channel_frequency),
                        buildString {
                            if (device.channel != 0) append(stringResource(R.string.channel_number, device.channel))
                            if (device.frequencyMhz != 0) {
                                if (isNotEmpty()) append("  ·  ")
                                append(stringResource(R.string.mhz, device.frequencyMhz))
                            }
                            facts.channelWidth?.let { append("  ·  ").append(stringResource(R.string.wide_format, it)) }
                        },
                    )
                }
                facts.wifiStandard?.let { Meta(stringResource(R.string.wifi_generation), it) }
                if (facts.centerFreq0 != null || facts.centerFreq1 != null) {
                    Meta(
                        stringResource(R.string.center_frequencies),
                        listOfNotNull(
                            facts.centerFreq0?.let { stringResource(R.string.mhz, it) },
                            facts.centerFreq1?.let { stringResource(R.string.mhz, it) },
                        ).joinToString("  ·  "),
                    )
                }
            }

            if (device.kind == RadioKind.BLE) {
                StickyHeight(device.key to "ble") {
                Section(stringResource(R.string.bluetooth_advertisement))
                facts.primaryPhy?.let {
                    val phys = listOfNotNull(it, facts.secondaryPhy).distinct()
                    Meta(stringResource(R.string.radio_phy), phys.joinToString(" / ") { phy -> DeviceExplain.phyExplain(res, phy) })
                }
                facts.connectable?.let {
                    Meta(
                        stringResource(R.string.connectable),
                        if (it) stringResource(R.string.connectable_yes)
                        else stringResource(R.string.connectable_no),
                    )
                }
                facts.advertisingIntervalMs?.let {
                    Meta(
                        stringResource(R.string.how_often_advertises),
                        stringResource(R.string.advertise_interval_format, it),
                    )
                }
                facts.periodicIntervalMs?.let {
                    Meta(stringResource(R.string.periodic_advertising), stringResource(R.string.periodic_ms_format, it))
                }
                facts.advFlags?.let { flags ->
                    Meta(stringResource(R.string.discoverability), DeviceExplain.flagsExplain(res, flags))
                    Meta(stringResource(R.string.flags_raw), "0x%02X".format(flags), mono = true)
                }
                facts.appearance?.let { value ->
                    val name = RadioDb.appearance(value)
                    Meta(
                        stringResource(R.string.appearance),
                        name?.let { stringResource(R.string.appearance_with_description, it) }
                            ?: stringResource(R.string.unlisted_appearance_format, value),
                    )
                    Meta(stringResource(R.string.appearance_code), "0x%04X".format(value), mono = true)
                }
                CodDecoder.decodeOrNull(facts.deviceClass)?.let { cod ->
                    Meta(
                        stringResource(R.string.classic_bluetooth_class),
                        buildString {
                            append(cod.major)
                            if (cod.minor.isNotBlank()) append(" / ").append(cod.minor)
                            append(stringResource(R.string.class_of_device_description))
                            if (cod.services.isNotEmpty()) {
                                append(stringResource(R.string.also_offers))
                                append(cod.services.joinToString(", "))
                            }
                        },
                    )
                }
                }
            }

            if (device.kind == RadioKind.WIFI) {
                StickyHeight(device.key to "wifi") {
                    Section(stringResource(R.string.wifi_access_point_section))
                    facts.security?.let {
                        Meta(stringResource(R.string.encryption_login), DeviceExplain.wifiSecurityExplain(res, it))
                        if (it.isNotBlank()) Meta(stringResource(R.string.security_string), it, mono = true)
                    }
                    facts.supportedRates?.let {
                        Meta(stringResource(R.string.supported_rates), stringResource(R.string.supported_rates_format, it))
                    }
                    facts.capabilities?.takeIf { it.isNotBlank() && it != facts.security }?.let {
                        Meta(stringResource(R.string.capability_string), it, mono = true)
                    }
                }
            }

            if (device.serviceUuids.isNotEmpty() || facts.serviceData.isNotEmpty()) {
                StickyHeight(device.key to "services") {
                    if (device.serviceUuids.isNotEmpty()) {
                        Section(stringResource(R.string.services_it_offers))
                        Meta(
                            stringResource(R.string.service_ids),
                            device.serviceUuids.map { uuid ->
                                DeviceExplain.uuidGloss(res, uuid)?.let { stringResource(R.string.detail_uuid_gloss_line, uuid, it) } ?: uuid
                            }.joinToString("\n"),
                            mono = true,
                        )
                    }
                    facts.serviceData.forEach { sd ->
                        val decoded = app.fieldwatch.domain.AdvPayloadDecoder.decodeService(res, sd)
                        decoded.forEach { field -> Meta(field.label, field.value) }
                        Meta(
                            serviceDataHeading(sd),
                            sd.dataHex.hexSpaced().ifBlank { stringResource(R.string.empty) },
                            mono = true,
                        )
                    }
                }
            }

            if (device.kind == RadioKind.BLE || device.kind == RadioKind.WIFI) {
                val aircraft = device.payloadAircraft?.trim().orEmpty()
                if (aircraft.isNotEmpty()) Meta(stringResource(R.string.aircraft), aircraft)
                val fleets = vm.ui.value.fleets
                val decoded = remember(device.key, device.facts, device.fleetIds) {
                    SignatureFieldDecoder.decodeSighting(device, fleets)
                }
                val mapped = device.fleetIds.mapNotNull { id -> fleets.find { it.id == id && it.decode != null } }
                val hasPayload = device.facts.mfgRecords.isNotEmpty() ||
                    device.manufacturerDataHex.isNotBlank() ||
                    device.facts.serviceData.isNotEmpty()
                if (decoded.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DecodeGlyph(
                            tint = MaterialTheme.colorScheme.primary,
                            size = 16.dp,
                            contentDescription = stringResource(R.string.decode_glyph_cd),
                        )
                        Text(
                            stringResource(R.string.decoded_fields),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val multi = decoded.map { it.fleetId }.distinct().size > 1
                    decoded.forEach { row ->
                        Meta(
                            if (multi) stringResource(R.string.detail_fleet_row, row.fleetName, row.label) else row.label,
                            row.display,
                        )
                        if (row.note.isNotBlank()) {
                            Text(
                                row.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (mapped.isNotEmpty()) {
                    val govee = mapped.any { it.id == "fleet-govee" }
                    Text(
                        when {
                            hasPayload && govee ->
                                stringResource(R.string.decode_not_applied_govee)
                            hasPayload ->
                                stringResource(R.string.decode_not_applied)
                            govee ->
                                stringResource(R.string.decode_map_no_payload_govee)
                            else ->
                                stringResource(R.string.decode_map_no_payload)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val mfg = facts.mfgRecords.ifEmpty {
                device.manufacturerId?.let {
                    listOf(app.fieldwatch.domain.MfgRecord(it, device.manufacturerDataHex))
                } ?: emptyList()
            }
            if (mfg.isNotEmpty()) {
                StickyHeight(device.key to "mfg") {
                    Section(stringResource(R.string.maker_data_inside_the_ad))
                    mfg.forEach { rec ->
                        val company = RadioDb.company(rec.companyId) ?: stringResource(R.string.not_in_bt_company_list)
                        Meta(
                            stringResource(R.string.bt_company_format, rec.companyId),
                            "$company\n${stringResource(R.string.bt_company_description)}",
                        )
                        val decoded = BleAdParser.mfgDecodedFields(res, rec)
                        decoded.forEach { (k, v) -> Meta(k, v) }
                        if (rec.dataHex.isNotBlank()) {
                            Meta(stringResource(R.string.raw_payload_format, rec.dataHex.length / 2), rec.dataHex.hexSpaced(), mono = true)
                        }
                    }
                }
            }

            if (facts.vendorIes.isNotEmpty() || device.vendorIeOuis.isNotEmpty()) {
                StickyHeight(device.key to "ies") {
                    Section(stringResource(R.string.wifi_vendor_tags))
                    val rows = facts.vendorIes.ifEmpty {
                        device.vendorIeOuis.map { app.fieldwatch.domain.VendorIeRecord(it, -1, "") }
                    }
                    rows.forEach { ie ->
                        val org = RadioDb.vendorForOui24(ie.oui)
                        val type = if (ie.type >= 0) stringResource(R.string.type_format, ie.type) else ""
                        Meta(
                            stringResource(R.string.vendor_oui_format, ie.oui, type),
                            buildString {
                                append(org ?: stringResource(R.string.unknown_ieee_oui))
                                append(stringResource(R.string.extra_ap_information))
                                if (ie.dataHex.isNotBlank()) {
                                    append("\n")
                                    append(ie.dataHex.hexSpaced())
                                }
                            },
                        )
                    }
                }
            }

            StickyHeight(device.key to "session") {
                Section(stringResource(R.string.session))
                Meta(stringResource(R.string.first_seen), fmt.format(Date(device.firstSeen)))
                Meta(stringResource(R.string.last_seen), fmt.format(Date(device.lastSeen)))
                Meta(stringResource(R.string.hits), device.hitCount.toString())
                Geo.screenCoord(device.latitude, device.longitude, demoMode)?.let { Meta(stringResource(R.string.last_fix), it) }
                if (device.fleetIds.isNotEmpty()) {
                    Meta(
                        stringResource(R.string.matched_signatures),
                        device.fleetIds.map { id ->
                            val name = vm.fleetName(id)
                            if (vm.fleetHasDecode(id)) stringResource(R.string.detail_fleet_decode_marker, name) else name
                        }.joinToString("\n"),
                    )
                }
                if (device.rawHex.isNotBlank() && device.kind == RadioKind.BLE) {
                    Meta(stringResource(R.string.detail_raw_advertisement), device.rawHex.hexSpaced())
                }
            }

            Text(stringResource(R.string.detail_signal_trend), style = MaterialTheme.typography.titleSmall)
            Sparkline(device.rssiHistory, accent, modifier = Modifier.fillMaxWidth().height(56.dp))
            Text(stringResource(R.string.detail_presence_15min), style = MaterialTheme.typography.titleSmall)
            PresenceTrack(device, System.currentTimeMillis(), 15 * 60 * 1000L, accent)
            if (device.kind == RadioKind.BLE) {
                FieldwatchActionButton(
                    onClick = onHunt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.NearMe, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.detail_hunt))
                }
            } else {
                Text(
                    stringResource(R.string.detail_hunt_ble_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            familyHint?.let { hint ->
                StickyHeight(device.key to "family") { FamilyCard(hint) }
            }
            FieldwatchActionButton(
                onClick = onCreateFleet,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.GroupAdd, null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.detail_create_signature_from_device))
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailShare(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.detail_share_as_text))
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailAiExport(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.export_title_ai))
            }
            Text(
                stringResource(R.string.detail_ai_export_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FamilyCard(hint: SignatureFamilyHint) {
    val scheme = MaterialTheme.colorScheme
    val container = when (hint.verdict) {
        FamilyVerdict.STRONG -> scheme.primaryContainer
        FamilyVerdict.POSSIBLE -> Amber.nightIf(LocalNightMode.current).copy(alpha = 0.22f)
        FamilyVerdict.SINGLE, FamilyVerdict.TAGGED -> scheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val onContainer = when (hint.verdict) {
        FamilyVerdict.STRONG -> scheme.onPrimaryContainer
        FamilyVerdict.POSSIBLE, FamilyVerdict.SINGLE, FamilyVerdict.TAGGED -> scheme.onSurface
    }
    val muted = onContainer.copy(alpha = 0.78f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = container,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.detail_signature_family),
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                    Text(hint.title, style = MaterialTheme.typography.titleMedium, color = onContainer)
                }
                if (hint.displayCount > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            hint.displayCount.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = onContainer,
                        )
                        RadioKindMark(hint.radioKind, size = 13.dp)
                    }
                }
            }
            hint.ruleLabel?.let { rule ->
                Text(
                    rule,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(hint.body, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@Composable
private fun SignatureNotesCard(notes: List<Pair<String, String>>) {
    if (notes.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.detail_notes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            notes.forEach { (name, note) ->
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ObserverNotesCard(
    notes: String,
    canEdit: Boolean,
    editing: Boolean,
    draft: String,
    onToggleEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saved: Boolean,
) {
    val ink = Cyan.nightIf(LocalNightMode.current)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ink.copy(alpha = 0.18f),
        border = BorderStroke(1.5.dp, ink),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.observer_notes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    modifier = Modifier.weight(1f),
                )
                if (canEdit) {
                    IconButton(onClick = onToggleEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editing) stringResource(R.string.detail_hide_observer_notes) else stringResource(R.string.observer_notes),
                        )
                    }
                }
            }
            if (!editing) {
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        stringResource(R.string.detail_no_observer_notes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FieldwatchOutlinedField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = stringResource(R.string.observer_notes),
                    singleLine = false,
                    minLines = 3,
                    supportingText = stringResource(R.string.bookmarks_notes_counter, draft.trim().length, RadioBookmarks.MAX_NOTES) +
                        ". " + RadioBookmarks.observerNotesHint(LocalContext.current.resources),
                )
                FieldwatchActionButton(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = saveEnabled,
                ) {
                    if (saved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text(stringResource(R.string.saved))
                    } else {
                        Text(stringResource(R.string.detail_save_notes))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraAttentionCard(notes: List<Pair<String, String>>) {
    if (notes.isEmpty()) return
    val warn = Amber.nightIf(LocalNightMode.current)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = warn.copy(alpha = 0.28f),
        border = BorderStroke(1.5.dp, warn),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = warn,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    stringResource(R.string.field_extra_attention),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = warn,
                )
            }
            notes.forEach { (name, note) ->
                Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                stringResource(R.string.detail_extra_attention_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuessCard(guess: DeviceExplain.Guess) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.detail_what_this_looks_like),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(guess.headline, style = MaterialTheme.typography.titleMedium)
            Text(guess.because, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Meta(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun vendorLine(device: Sighting): String? {
    val parts = ArrayList<String>(3)
    device.vendor?.let {
        parts += stringResource(R.string.detail_vendor_line, it, device.oui)
    }
    val mfgId = device.facts.mfgRecords.firstOrNull()?.companyId ?: device.manufacturerId
    if (mfgId != null) {
        val company = RadioDb.company(mfgId) ?: stringResource(R.string.detail_company_unlisted)
        parts += stringResource(R.string.detail_bluetooth_company_line, company, mfgId)
    }
    return parts.joinToString("\n").ifBlank { null }
}

private fun uuidShort(uuid: String): String {
    val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
    return if (hex.length >= 8 && hex.startsWith("0000")) hex.substring(4, 8) else uuid.take(8)
}

@Composable
private fun serviceDataHeading(sd: ServiceDataRecord): String {
    val named = RadioDb.serviceUuid(sd.uuid)?.let { " ($it)" } ?: ""
    val frame = eddystoneFrameTag(sd)?.let { " · $it" } ?: ""
    return stringResource(R.string.detail_service_data_heading, uuidShort(sd.uuid), named, frame)
}

private fun eddystoneFrameTag(sd: ServiceDataRecord): String? {
    val hex = sd.uuid.filter { it.isLetterOrDigit() }.uppercase()
    val short = when {
        hex.length == 4 -> hex
        hex.length >= 8 && hex.startsWith("0000") -> hex.substring(4, 8)
        else -> return null
    }
    if (short != "FEAA") return null
    return when (sd.dataHex.filter { it.isLetterOrDigit() }.uppercase().take(2)) {
        "00" -> "UID"
        "10" -> "URL"
        "20" -> "TLM"
        "30" -> "EID"
        else -> null
    }
}
