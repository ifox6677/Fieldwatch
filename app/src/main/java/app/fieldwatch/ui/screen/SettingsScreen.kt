package app.fieldwatch.ui.screen

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchSlider
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fieldwatch.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fieldwatch.domain.AlertVoiceWhat
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.GnssAlertFloor
import app.fieldwatch.domain.GnssSensitivity
import app.fieldwatch.domain.ScanIntensity
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakFeedStatus
import app.fieldwatch.domain.TakFloodSend
import app.fieldwatch.domain.TakGnssHold
import app.fieldwatch.domain.TakGnssSend
import app.fieldwatch.domain.TakPublish
import app.fieldwatch.domain.TakUdpPreset
import app.fieldwatch.radio.WifiRadio
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.StableCaption
import app.fieldwatch.ui.component.StickyHeight
import java.net.Inet4Address
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onRadioBookmarks: () -> Unit,
    onScanStatus: () -> Unit,
    onShowLiveTour: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = state.settings
    val saveSignatures = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::saveSignaturesToUri) }
    val importSignatures = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importSignaturesFromUri) }
    val saveSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::saveSettingsToUri) }
    val importSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importSettingsFromUri) }
    var confirmRestore by remember { mutableStateOf(false) }
    val pendingSettingsImport by vm.pendingSettingsImport.collectAsStateWithLifecycle()
    val gnssProfile by vm.gnssProfile.collectAsStateWithLifecycle()
    val gnssCal by vm.gnssCalibration.collectAsStateWithLifecycle()
    val gnssReady = gnssProfile != null
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar(stringResource(R.string.settings)) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(stringResource(R.string.settings_appearance)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_night_mode), Modifier.weight(1f))
                FieldwatchSwitch(settings.nightMode, { on -> vm.updateSettings { it.copy(nightMode = on) } })
            }
            Text(
                stringResource(R.string.settings_night_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_keep_screen_on), Modifier.weight(1f))
                FieldwatchSwitch(settings.keepScreenOn, { on -> vm.updateSettings { it.copy(keepScreenOn = on) } })
            }
            Text(
                stringResource(R.string.settings_keep_screen_on_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_privacy_mode), Modifier.weight(1f))
                FieldwatchSwitch(settings.demoMode, { on -> vm.updateSettings { it.copy(demoMode = on) } })
            }
            Text(
                stringResource(R.string.settings_privacy_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(stringResource(R.string.settings_scanning)) {
            val label = when (settings.intensity) {
                ScanIntensity.SAVER -> stringResource(R.string.settings_scan_intensity_saver)
                ScanIntensity.BALANCED -> stringResource(R.string.settings_scan_intensity_balanced)
                ScanIntensity.PERFORMANCE -> stringResource(R.string.settings_scan_intensity_performance)
            }
            Text(stringResource(R.string.settings_scan_intensity_format, label))
            FieldwatchSlider(
                value = settings.intensity.ordinal.toFloat(),
                onValueChange = { v ->
                    val next = ScanIntensity.entries[v.toInt().coerceIn(0, 2)]
                    vm.updateSettings { it.copy(intensity = next) }
                },
                valueRange = 0f..2f,
                steps = 1,
            )
            Text(
                stringResource(R.string.settings_scan_intensity_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StableCaption(
                state.throttleHint.ifBlank { " " },
                stringResource(R.string.settings_scan_wifi_waiting_os),
                stringResource(R.string.settings_scan_wifi_scanning),
                stringResource(R.string.settings_scan_wifi_next_99s),
                " ",
            )

            val lifecycleOwner = LocalLifecycleOwner.current
            var osThrottled by remember { mutableStateOf(WifiRadio.osScanThrottled(context)) }
            var backgroundAllowed by remember { mutableStateOf(isBackgroundUsageAllowed(context)) }
            var unrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
            var needDevOptions by remember { mutableStateOf(false) }
            var batteryGate by remember { mutableStateOf<BatteryAndroidGate?>(null) }
            DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        osThrottled = WifiRadio.osScanThrottled(context)
                        backgroundAllowed = isBackgroundUsageAllowed(context)
                        unrestricted = isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_faster_wifi_scans), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = !osThrottled,
                    onCheckedChange = { needDevOptions = true },
                )
            }
            StableCaption(
                when {
                    Build.VERSION.SDK_INT < 30 ->
                        stringResource(R.string.settings_wifi_throttle_pre_11)
                    !osThrottled ->
                        stringResource(R.string.settings_wifi_throttle_on)
                    else ->
                        stringResource(R.string.settings_wifi_throttle_off)
                },
                stringResource(R.string.settings_wifi_throttle_pre_11),
                stringResource(R.string.settings_wifi_throttle_on),
                stringResource(R.string.settings_wifi_throttle_off),
            )
            if (needDevOptions) {
                AlertDialog(
                    onDismissRequest = { needDevOptions = false },
                    title = { Text(stringResource(R.string.settings_faster_wifi_scans)) },
                    text = {
                        Text(
                            if (Build.VERSION.SDK_INT < 30) {
                                stringResource(R.string.settings_wifi_throttle_dialog_pre_11)
                            } else if (osThrottled) {
                                stringResource(R.string.settings_wifi_throttle_dialog_open_off)
                            } else {
                                stringResource(R.string.settings_wifi_throttle_dialog_open_on)
                            },
                        )
                    },
                    confirmButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(
                                onClick = {
                                    needDevOptions = false
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                    }
                                },
                            ) { Text(stringResource(R.string.settings_open_dev_options)) }
                        } else {
                            TextButton(onClick = { needDevOptions = false }) { Text(stringResource(R.string.settings_ok)) }
                        }
                    },
                    dismissButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(onClick = { needDevOptions = false }) { Text(stringResource(R.string.settings_not_now)) }
                        }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_allow_background), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = backgroundAllowed,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.BACKGROUND },
                )
            }
            Text(
                stringResource(R.string.settings_allow_background_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_unrestricted_battery), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = unrestricted,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.UNRESTRICTED },
                )
            }
            Text(
                stringResource(R.string.settings_unrestricted_battery_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (batteryGate != null) {
                val background = batteryGate == BatteryAndroidGate.BACKGROUND
                AlertDialog(
                    onDismissRequest = { batteryGate = null },
                    title = {
                        Text(if (background) stringResource(R.string.settings_allow_background) else stringResource(R.string.settings_unrestricted_battery))
                    },
                    text = {
                        Text(
                            if (background) {
                                stringResource(R.string.settings_battery_dialog_background)
                            } else {
                                stringResource(R.string.settings_battery_dialog_unrestricted)
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val gate = batteryGate
                                batteryGate = null
                                openAppBatteryPage(
                                    context,
                                    highlightBackground = gate == BatteryAndroidGate.BACKGROUND,
                                )
                            },
                        ) { Text(stringResource(R.string.settings_open_android_settings)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { batteryGate = null }) { Text(stringResource(R.string.settings_not_now)) }
                    },
                )
            }
            }

            SectionCard(stringResource(R.string.settings_watchlist)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_watchlist_alerts), Modifier.weight(1f))
                FieldwatchSwitch(settings.alertsEnabled, { on -> vm.updateSettings { it.copy(alertsEnabled = on) } })
            }
            Text(
                stringResource(R.string.settings_watchlist_alerts_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val radioWatchN = state.watchlist.count { it.deviceKey != null }
            FieldwatchActionButton(
                onClick = onRadioBookmarks,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_named_radios_format, radioWatchN)) }
            Text(
                stringResource(R.string.settings_named_radios_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_beep_watched), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertBeep,
                    { on -> vm.updateSettings { it.copy(alertBeep = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                stringResource(R.string.settings_beep_watched_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_voice_watched), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertVoice,
                    { on -> vm.updateSettings { it.copy(alertVoice = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                stringResource(R.string.settings_voice_watched_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.settings_what_to_say), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertVoiceWhat.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.alertVoiceWhat == item,
                        onClick = { vm.updateSettings { it.copy(alertVoiceWhat = item) } },
                        enabled = settings.alertsEnabled && settings.alertVoice,
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_what_to_say_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::testWatchBeep,
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
            ) { Text(stringResource(R.string.settings_test_alert)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_jump_watched), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.snapToBeep,
                    { on -> vm.updateSettings { it.copy(snapToBeep = on) } },
                    enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
                )
            }
            Text(
                stringResource(R.string.settings_jump_watched_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_system_notification), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertShade,
                    { on -> vm.updateSettings { it.copy(alertShade = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                stringResource(R.string.settings_system_notification_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(stringResource(R.string.settings_location)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_tag_gps), Modifier.weight(1f))
                FieldwatchSwitch(settings.tagLocation, { on -> vm.updateSettings { it.copy(tagLocation = on) } })
            }
            Text(
                stringResource(R.string.settings_tag_gps_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_online_place_names), Modifier.weight(1f))
                FieldwatchSwitch(settings.onlineLookup, { on -> vm.updateSettings { it.copy(onlineLookup = on) } })
            }
            Text(
                stringResource(R.string.settings_online_place_names_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(stringResource(R.string.settings_gnss_interference)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_gnss_check), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = settings.gnssMonitor && gnssReady,
                    onCheckedChange = { on ->
                        if (gnssReady) vm.updateSettings { it.copy(gnssMonitor = on) }
                    },
                    enabled = gnssReady && !gnssCal.running,
                )
            }
            Text(
                stringResource(R.string.settings_gnss_check_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!gnssReady) {
                Text(
                    stringResource(R.string.settings_gnss_calibrate_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            FieldwatchActionButton(
                onClick = { if (gnssCal.running) vm.stopGnssCalibration() else vm.startGnssCalibration() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (gnssCal.running) stringResource(R.string.settings_gnss_stop_calibration) else stringResource(R.string.settings_gnss_calibrate)) }
            Text(
                stringResource(R.string.settings_gnss_calibrate_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (gnssCal.running) {
                Text(
                    stringResource(R.string.settings_gnss_calibrating_format, gnssCal.leftSec),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (gnssCal.rejection.isNotBlank()) {
                Text(
                    gnssCal.rejection,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            gnssProfile?.let { profile ->
                Text(
                    profile.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.settings_gnss_calibrated_format, profile.calibratedLabel()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.settings_gnss_sensitivity), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GnssSensitivity.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.gnssSensitivity == item,
                        onClick = { vm.updateSettings { it.copy(gnssSensitivity = item) } },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_gnss_sensitivity_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.settings_gnss_alert_from), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GnssAlertFloor.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.gnssAlertFloor == item,
                        onClick = { vm.updateSettings { it.copy(gnssAlertFloor = item) } },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_gnss_alert_from_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_gnss_spoofing_checks), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssSpoofChecks, { on -> vm.updateSettings { it.copy(gnssSpoofChecks = on) } })
            }
            Text(
                stringResource(R.string.settings_gnss_spoofing_checks_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_gnss_beep), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssBeep, { on -> vm.updateSettings { it.copy(gnssBeep = on) } })
            }
            Text(
                stringResource(R.string.settings_gnss_beep_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_gnss_voice), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssVoice, { on -> vm.updateSettings { it.copy(gnssVoice = on) } })
            }
            Text(
                stringResource(R.string.settings_gnss_voice_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_system_notification), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssShade, { on -> vm.updateSettings { it.copy(gnssShade = on) } })
            }
            Text(
                stringResource(R.string.settings_gnss_notification_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_gnss_full_tracking), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssFullTracking, { on -> vm.updateSettings { it.copy(gnssFullTracking = on) } })
            }
            Text(
                stringResource(R.string.settings_gnss_full_tracking_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_gnss_fading_check), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssCorrelation, { on -> vm.updateSettings { it.copy(gnssCorrelation = on) } })
            }
            Text(
                stringResource(R.string.settings_gnss_fading_check_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_gnss_invert_gain), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssInvertAgc, { on -> vm.updateSettings { it.copy(gnssInvertAgc = on) } })
            }
            Text(
                stringResource(R.string.settings_gnss_invert_gain_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(stringResource(R.string.settings_tak_cot)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_tak_feed), Modifier.weight(1f))
                FieldwatchSwitch(settings.takEnabled, { on -> vm.updateSettings { it.copy(takEnabled = on) } })
            }
            Text(
                stringResource(R.string.settings_tak_feed_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.takEnabled && settings.demoMode) {
                Text(
                    stringResource(R.string.settings_tak_privacy_paused),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (settings.takEnabled) {
                TakFeedSettings(settings, vm, state.takStatus)
            }
            }

            SectionCard(stringResource(R.string.settings_logging)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_write_detections), Modifier.weight(1f))
                FieldwatchSwitch(settings.loggingEnabled, { on -> vm.updateSettings { it.copy(loggingEnabled = on) } })
            }
            StableCaption(
                if (settings.loggingEnabled) {
                    stringResource(R.string.settings_logging_on)
                } else {
                    stringResource(R.string.settings_logging_off)
                },
                stringResource(R.string.settings_logging_on),
                stringResource(R.string.settings_logging_off),
            )
            Text(
                stringResource(R.string.settings_logging_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var rotateDrag by remember { mutableIntStateOf(settings.logRotateKb) }
            var rotateDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.logRotateKb) {
                if (!rotateDragging) rotateDrag = settings.logRotateKb
            }
            Text(stringResource(R.string.settings_rotate_at_format, rotateDrag))
            FieldwatchSlider(
                value = rotateDrag.toFloat(),
                onValueChange = {
                    rotateDragging = true
                    rotateDrag = it.toInt().coerceIn(128, 4096)
                },
                onValueChangeFinished = {
                    vm.updateSettings { s -> s.copy(logRotateKb = rotateDrag) }
                    rotateDragging = false
                },
                valueRange = 128f..4096f,
            )
            var staleDrag by remember { mutableIntStateOf(settings.staleSec) }
            var staleDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.staleSec) {
                if (!staleDragging) staleDrag = settings.staleSec
            }
            Text(stringResource(R.string.settings_stale_after_format, staleDrag))
            FieldwatchSlider(
                value = staleDrag.toFloat(),
                onValueChange = {
                    staleDragging = true
                    staleDrag = it.toInt().coerceIn(15, 180)
                },
                onValueChangeFinished = {
                    vm.updateSettings { s -> s.copy(staleSec = staleDrag) }
                    staleDragging = false
                },
                valueRange = 15f..180f,
            )
            StickyHeight("log-stats") {
                Text(
                    stringResource(R.string.settings_log_lines_format, state.logLines, vm.logBytes() / 1024),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            }

            SectionCard(stringResource(R.string.settings_signatures)) {
            Text(
                stringResource(R.string.settings_signatures_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSignatureShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_export_signatures)) }
            FieldwatchActionButton(
                onClick = { saveSignatures.launch(vm.suggestedSignaturesName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_save_signatures)) }
            FieldwatchActionButton(
                onClick = {
                    importSignatures.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_import_signatures)) }
            FieldwatchActionButton(
                onClick = vm::updateStockCatalogFromGitHub,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_update_catalog)) }

            FieldwatchActionButton(
                onClick = { confirmRestore = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_restore_signatures))
            }
            }

            SectionCard(stringResource(R.string.settings_backup)) {
            Text(
                stringResource(R.string.settings_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSettingsShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_export_settings)) }
            FieldwatchActionButton(
                onClick = { saveSettings.launch(vm.suggestedSettingsName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_save_settings)) }
            FieldwatchActionButton(
                onClick = {
                    importSettings.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_import_settings)) }
            }

            FieldwatchActionButton(
                onClick = onShowLiveTour,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_show_live_tour)) }
            Text(
                stringResource(R.string.settings_show_live_tour_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = onScanStatus,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_diagnostics)) }
            Text(
                stringResource(R.string.settings_diagnostics_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                stringResource(R.string.settings_version_catalog_format, app.fieldwatch.BuildConfig.VERSION_NAME, state.catalogVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.settings_wifi_bluetooth_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val footerLifecycle = LocalLifecycleOwner.current
            var ipv4 by remember { mutableStateOf(localIpv4Addresses()) }
            DisposableEffect(footerLifecycle) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) ipv4 = localIpv4Addresses()
                }
                footerLifecycle.lifecycle.addObserver(obs)
                onDispose { footerLifecycle.lifecycle.removeObserver(obs) }
            }
            Text(
                if (ipv4.isEmpty()) {
                    stringResource(R.string.settings_ipv4_none)
                } else {
                    stringResource(R.string.settings_ipv4_format, ipv4.joinToString("  ·  "))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            CreditFooter()
        }
    }
    pendingSettingsImport?.let { pending ->
        AlertDialog(
            onDismissRequest = vm::dismissPendingSettingsImport,
            title = { Text(stringResource(R.string.settings_import_dialog_title)) },
            text = { Text(pending.message) },
            confirmButton = {
                TextButton(onClick = vm::confirmPendingSettingsImport) { Text(stringResource(R.string.settings_import)) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissPendingSettingsImport) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.settings_restore_dialog_title)) },
            text = {
                Text(
                    stringResource(R.string.settings_restore_dialog_body),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        vm.restoreDefaults()
                    },
                ) { Text(stringResource(R.string.settings_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

@Composable
private fun CreditFooter() {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.settings_copyright),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialChip(
                icon = R.drawable.ic_instagram,
                label = "@OffGridPete",
                tint = muted,
                onClick = { openUrl(context, "https://instagram.com/OffGridPete") },
            )
            SocialChip(
                icon = R.drawable.ic_x,
                label = "@OGridPete",
                tint = muted,
                onClick = { openUrl(context, "https://x.com/OGridPete") },
            )
        }
    }
}

@Composable
private fun SocialChip(
    icon: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TakFeedSettings(settings: AppSettings, vm: FieldwatchViewModel, status: TakFeedStatus) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var hostText by remember { mutableStateOf(settings.takHost) }
    var portText by remember { mutableStateOf(settings.takPort.toString()) }
    LaunchedEffect(settings.takHost) { hostText = settings.takHost }
    LaunchedEffect(settings.takPort) { portText = settings.takPort.toString() }
    val preset = TakPublish.udpPreset(settings.takHost, settings.takPort)
    Text(stringResource(R.string.settings_tak_destination), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.THIS_PHONE,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.THIS_PHONE)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text(stringResource(R.string.settings_tak_this_phone)) },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.LAN_MULTICAST,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.LAN_MULTICAST)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text(stringResource(R.string.settings_tak_lan_multicast)) },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.CUSTOM,
            onClick = {
                if (preset != TakUdpPreset.CUSTOM) {
                    val (host, port) = TakPublish.applyPreset(TakUdpPreset.CUSTOM)
                    vm.updateSettings { it.copy(takHost = host, takPort = port) }
                }
            },
            enabled = !settings.demoMode,
            label = { Text(stringResource(R.string.settings_tak_custom)) },
        )
    }
    Text(
        stringResource(
            R.string.settings_tak_destination_desc,
            TakDefaults.LOOPBACK,
            TakDefaults.PORT,
            TakDefaults.SA_HOST,
            TakDefaults.SA_PORT,
            TakDefaults.PORT,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    FieldwatchOutlinedField(
        value = hostText,
        onValueChange = { value ->
            hostText = value
            val trimmed = value.trim()
            if (trimmed.isNotEmpty()) {
                vm.updateSettings { it.copy(takHost = trimmed) }
            }
        },
        label = stringResource(R.string.settings_tak_host),
        placeholder = TakDefaults.HOST,
        enabled = !settings.demoMode,
    )
    FieldwatchOutlinedField(
        value = portText,
        onValueChange = { value ->
            val filtered = value.filter { it.isDigit() }.take(5)
            portText = filtered
            filtered.toIntOrNull()?.let { n ->
                if (n in 1..65_535) {
                    vm.updateSettings { it.copy(takPort = n) }
                }
            }
        },
        label = stringResource(R.string.settings_tak_port),
        placeholder = TakDefaults.PORT.toString(),
        supportingText = stringResource(
            R.string.settings_tak_port_supporting,
            TakDefaults.PORT,
            TakDefaults.SA_PORT,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !settings.demoMode,
    )
    Text(takStatusLine(status), style = MaterialTheme.typography.bodySmall, color = muted)
    Text(stringResource(R.string.settings_tak_what_to_send), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = settings.takAttention,
            onClick = { vm.updateSettings { it.copy(takAttention = !it.takAttention) } },
            enabled = !settings.demoMode,
            label = { Text(stringResource(R.string.field_extra_attention)) },
        )
        FieldwatchFilterChip(
            selected = settings.takPayloadFix,
            onClick = { vm.updateSettings { it.copy(takPayloadFix = !it.takPayloadFix) } },
            enabled = !settings.demoMode,
            label = { Text(stringResource(R.string.settings_tak_payload_location)) },
        )
        FieldwatchFilterChip(
            selected = settings.takWatchlist,
            onClick = { vm.updateSettings { it.copy(takWatchlist = !it.takWatchlist) } },
            enabled = !settings.demoMode,
            label = { Text(stringResource(R.string.settings_watchlist)) },
        )
        FieldwatchFilterChip(
            selected = settings.takAllSignatures,
            onClick = { vm.updateSettings { it.copy(takAllSignatures = !it.takAllSignatures) } },
            enabled = !settings.demoMode,
            label = { Text(stringResource(R.string.settings_tak_all_signatures)) },
        )
    }
    Text(
        stringResource(R.string.settings_tak_what_to_send_desc),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    Text(stringResource(R.string.settings_tak_spot_hold_label), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakGnssHold.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.takRadioHold == item,
                onClick = { vm.updateSettings { it.copy(takRadioHold = item) } },
                enabled = !settings.demoMode,
                label = { Text(takGnssHoldLabel(item)) },
            )
        }
    }
    Text(
        stringResource(R.string.settings_tak_spot_hold_desc),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    Text(stringResource(R.string.settings_tak_gnss_label), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakGnssSend.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.takGnss == item,
                onClick = { vm.updateSettings { it.copy(takGnss = item) } },
                enabled = !settings.demoMode,
                label = { Text(takGnssSendLabel(item)) },
            )
        }
    }
    Text(
        stringResource(R.string.settings_tak_gnss_desc),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    Text(stringResource(R.string.settings_tak_gnss_hold_label), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakGnssHold.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.takGnssHold == item,
                onClick = { vm.updateSettings { it.copy(takGnssHold = item) } },
                enabled = !settings.demoMode && settings.takGnss != TakGnssSend.OFF,
                label = { Text(takGnssHoldLabel(item)) },
            )
        }
    }
    Text(
        stringResource(R.string.settings_tak_gnss_hold_desc),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    Text(stringResource(R.string.settings_tak_flood_label), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakFloodSend.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.takFlood == item,
                onClick = { vm.updateSettings { it.copy(takFlood = item) } },
                enabled = !settings.demoMode,
                label = { Text(takFloodSendLabel(item)) },
            )
        }
    }
    Text(
        stringResource(R.string.settings_tak_flood_desc),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
}

@Composable
private fun takStatusLine(status: TakFeedStatus): String {
    if (status.paused) return stringResource(R.string.settings_tak_status_paused)
    val error = status.error
    if (error != null) {
        val whenAt = takStatusWhen(status.at)
        val head = stringResource(R.string.settings_tak_status_error, error)
        return if (whenAt.isNotEmpty()) {
            stringResource(R.string.settings_tak_status_detail, head, whenAt)
        } else {
            head
        }
    }
    if (status.at <= 0L) {
        return stringResource(R.string.settings_tak_status_no_send)
    }
    val bits = ArrayList<String>(5)
    bits += stringResource(R.string.settings_tak_status_on_feed, status.onFeed)
    bits += stringResource(R.string.settings_tak_status_sent, status.sent)
    if (status.gone > 0) {
        bits += if (status.gone == 1) {
            stringResource(R.string.settings_tak_status_gone_one)
        } else {
            stringResource(R.string.settings_tak_status_gone_many, status.gone)
        }
    }
    if (status.dest.isNotBlank()) bits += status.dest
    val whenAt = takStatusWhen(status.at)
    if (whenAt.isNotEmpty()) bits += whenAt
    val head = stringResource(R.string.settings_tak_status_head, bits.joinToString("  ·  "))
    return if (status.detail.isNotBlank() && status.sent == 0 && status.gone == 0) {
        stringResource(R.string.settings_tak_status_detail, head, status.detail)
    } else {
        head
    }
}

@Composable
private fun takGnssHoldLabel(item: TakGnssHold): String = when (item) {
    TakGnssHold.HOUR -> stringResource(R.string.tak_hold_1_hour)
    TakGnssHold.HOURS_6 -> stringResource(R.string.tak_hold_6_hours)
    TakGnssHold.HOURS_24 -> stringResource(R.string.tak_hold_24_hours)
    TakGnssHold.UNTIL_DELETED -> stringResource(R.string.tak_hold_until_deleted)
}

@Composable
private fun takGnssSendLabel(item: TakGnssSend): String = when (item) {
    TakGnssSend.OFF -> stringResource(R.string.tak_option_off)
    TakGnssSend.ALERT -> stringResource(R.string.tak_option_while_alerting)
    TakGnssSend.RED_LINE -> stringResource(R.string.tak_option_red_line)
    TakGnssSend.ANY -> stringResource(R.string.tak_option_any_hit)
}

@Composable
private fun takFloodSendLabel(item: TakFloodSend): String = when (item) {
    TakFloodSend.OFF -> stringResource(R.string.tak_option_off)
    TakFloodSend.ON -> stringResource(R.string.tak_option_on)
}

private fun takStatusWhen(at: Long): String {
    if (at <= 0L) return ""
    return java.time.Instant.ofEpochMilli(at)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}

private fun localIpv4Addresses(): List<String> {
    val found = LinkedHashSet<String>()
    val nifs = runCatching {
        java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
    }.getOrDefault(emptyList())
    for (nif in nifs) {
        if (!nif.isUp || nif.isLoopback) continue
        for (addr in java.util.Collections.list(nif.inetAddresses)) {
            if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                addr.hostAddress?.let { found += it }
            }
        }
    }
    return found.toList()
}

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

internal fun isBackgroundUsageAllowed(context: Context): Boolean =
    context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted != true

private enum class BatteryAndroidGate { BACKGROUND, UNRESTRICTED }

/**
 * Fieldwatch’s per-app Battery page. Samsung keeps Allow background usage and
 * Unrestricted on this same screen. [highlightBackground] asks Settings to
 * focus the background-usage switch when the OEM supports it.
 */
private fun openAppBatteryPage(context: Context, highlightBackground: Boolean) {
    val pkgUri = Uri.fromParts("package", context.packageName, null)
    val attempts = listOf(
        Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").apply {
            data = pkgUri
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("request_ignore_background_restriction", highlightBackground)
            if (!highlightBackground) {
                putExtra(":settings:fragment_args_key", "unrestricted_pref")
            }
        },
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = pkgUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
    for (intent in attempts) {
        if (intent.resolveActivity(context.packageManager) == null) continue
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
