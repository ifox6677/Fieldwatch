package app.fieldwatch.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBarDefaults
import app.fieldwatch.R
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchDropdownField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fieldwatch.domain.HeaderTone
import app.fieldwatch.domain.ListLine
import app.fieldwatch.domain.bleHeader
import app.fieldwatch.domain.gpsHeader
import app.fieldwatch.domain.wifiHeader
import app.fieldwatch.domain.ListSort
import app.fieldwatch.domain.StrengthSort
import app.fieldwatch.domain.ViewMode
import app.fieldwatch.domain.FieldwatchDisclaimer
import app.fieldwatch.domain.disclaimerOk
import app.fieldwatch.radio.RadioPermissions
import app.fieldwatch.ui.screen.DeviceDetailScreen
import app.fieldwatch.ui.screen.HuntScreen
import app.fieldwatch.ui.screen.FiltersScreen
import app.fieldwatch.ui.screen.FleetsScreen
import app.fieldwatch.ui.screen.LivePane
import app.fieldwatch.ui.screen.CandidatesScreen
import app.fieldwatch.ui.screen.RadioBookmarksScreen
import app.fieldwatch.ui.screen.ReportsScreen
import app.fieldwatch.ui.screen.ScanStatusScreen
import app.fieldwatch.ui.screen.SettingsScreen
import app.fieldwatch.ui.theme.FieldwatchTheme
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.Phosphor
import app.fieldwatch.ui.theme.nightIf

@Composable
fun FieldwatchRoot(vm: FieldwatchViewModel, onRequestPermissions: () -> Unit) {
    val state by vm.ui.collectAsStateWithLifecycle()
    FieldwatchTheme(
        darkTheme = true,
        nightMode = state.settings.nightMode,
    ) {
        if (!state.settings.disclaimerOk()) {
            DisclaimerGate(onAccept = vm::acceptDisclaimer)
        } else if (!state.permissionsOk) {
            PermissionGate(onRequestPermissions)
        } else {
            FieldwatchShell(state, vm)
        }
    }
}

@Composable
private fun DisclaimerGate(onAccept: () -> Unit) {
    val res = LocalContext.current.resources
    val ink = MaterialTheme.colorScheme.onSurface
    var agreed by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text(
            stringResource(R.string.disclaimer_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
        Text(
            stringResource(R.string.disclaimer_heading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            FieldwatchDisclaimer.firstRunDisclaimer(res),
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            FieldwatchDisclaimer.licenseTitle(res),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            FieldwatchDisclaimer.LICENSE_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            FieldwatchDisclaimer.accept(res),
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.padding(top = 20.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .toggleable(
                    value = agreed,
                    onValueChange = { agreed = it },
                    role = Role.Checkbox,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = agreed, onCheckedChange = null)
            Text(
                stringResource(R.string.disclaimer_agree),
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Button(
            onClick = onAccept,
            enabled = agreed,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) { Text(stringResource(R.string.continue_button)) }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.permissions_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRequest) { Text(stringResource(R.string.permissions_grant)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldwatchShell(state: FieldwatchUi, vm: FieldwatchViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: "live"
    val context = LocalContext.current
    val export by vm.export.collectAsStateWithLifecycle()
    val gnssStatus by vm.gnssStatus.collectAsStateWithLifecycle()
    val gnssNotice by vm.gnssNotice.collectAsStateWithLifecycle()
    val gnssProfile by vm.gnssProfile.collectAsStateWithLifecycle()
    val logKind by vm.logExportKind.collectAsStateWithLifecycle()
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(vm.exportMime()),
    ) { uri ->
        uri?.let(vm::startSaveToUri)
    }
    val sitSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(vm.sitExportMime()),
    ) { uri ->
        uri?.let(vm::startSitSaveToUri)
    }
    androidx.compose.runtime.LaunchedEffect(export.share) {
        export.share?.let { intent ->
            context.startActivity(Intent.createChooser(intent, export.shareTitle))
            vm.consumeShare()
        }
    }
    if (export.active) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(
                    if (export.titleRes != 0) {
                        stringResource(export.titleRes)
                    } else {
                        stringResource(R.string.export_title_default)
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(export.message.ifBlank { stringResource(R.string.export_please_wait) })
                    if (export.titleRes == 0 || export.titleRes == R.string.export_title_logs ||
                        export.titleRes == R.string.export_title_sit_export
                    ) {
                        Text(
                            stringResource(R.string.export_live_paused),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { export.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${(export.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = { },
        )
    }
    if (export.error != null) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text(export.errorTitle ?: stringResource(R.string.export_could_not)) },
            text = { Text(export.error ?: "") },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text(stringResource(R.string.ok)) }
            },
        )
    }
    if (export.saved) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text(stringResource(R.string.export_log_saved)) },
            text = {
                Text(stringResource(R.string.export_saved_detail))
            },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text(stringResource(R.string.ok)) }
            },
        )
    }
    if (export.cleared) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text(stringResource(R.string.export_log_cleared)) },
            text = { Text(stringResource(R.string.export_cleared_detail)) },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text(stringResource(R.string.ok)) }
            },
        )
    }
    if (export.noticeTitle != null) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text(export.noticeTitle ?: "") },
            text = { Text(export.noticeMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text(stringResource(R.string.ok)) }
            },
        )
    }
    val view = LocalView.current
    var tourTargets by remember { mutableStateOf(LiveTourTargets()) }
    val showTour = !state.settings.liveTourDone && route == "live"
    LaunchedEffect(showTour) {
        if (showTour && state.settings.scanControlsExpanded) {
            vm.setScanControlsExpanded(false)
        }
    }
    val keepAwake = state.settings.keepScreenOn || route == "hunt"
    DisposableEffect(keepAwake) {
        val window = (view.context as? android.app.Activity)?.window
        if (keepAwake) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (route != "detail" && route != "hunt") {
                TopAppBar(
                    expandedHeight = 52.dp,
                    title = {
                        val screenW = LocalConfiguration.current.screenWidthDp.dp
                        val actionW = if (route == "live") 56.dp else 16.dp
                        Column(
                            modifier = Modifier
                                .widthIn(max = (screenW - 20.dp - actionW).coerceAtLeast(120.dp))
                                .fillMaxWidth()
                                .pointerInput(route) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (route != "live") {
                                                nav.navigate("live") { launchSingleTop = true }
                                            }
                                            vm.focusLiveList()
                                        },
                                    )
                                },
                        ) {
                            Text(
                                when {
                                    state.sit.open != null && state.displayPaused ->
                                        stringResource(R.string.app_title_sit_paused)
                                    state.sit.open != null -> stringResource(R.string.app_title_sit)
                                    state.displayPaused -> stringResource(R.string.app_title_paused)
                                    else -> stringResource(R.string.app_title_default)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                                val night = LocalNightMode.current
                                val res = LocalContext.current.resources
                                val wifiHead = wifiHeader(res, state.scanning, state.locationOn, state.scanRadio)
                                val bleHead = bleHeader(res, state.scanning, state.locationOn, state.scanRadio)
                                HeaderCount(
                                    state.wifiNow,
                                    Icons.Outlined.Wifi,
                                    wifiHead.second,
                                    headerTint(wifiHead.first, muted, night),
                                )
                                HeaderCount(
                                    state.bleNow,
                                    Icons.Outlined.Bluetooth,
                                    bleHead.second,
                                    headerTint(bleHead.first, muted, night),
                                )
                                HeaderCount(
                                    state.namedNow,
                                    Icons.Outlined.Hub,
                                    if (state.scanning) stringResource(R.string.header_signatures) else stringResource(R.string.header_signatures_idle),
                                    headerTint(
                                        if (state.scanning) HeaderTone.HEALTHY else HeaderTone.IDLE,
                                        muted,
                                        night,
                                    ),
                                )
                                val notice = gnssNotice
                                val gnssAlert = notice != null && notice.line.isNotBlank() &&
                                    (notice.showDialog || !notice.ended)
                                val gps = gpsHeader(
                                    res = res,
                                    monitorOn = state.settings.gnssMonitor && gnssProfile != null,
                                    suppressAlerts = gnssProfile?.suppressAlerts == true,
                                    scanning = state.scanning,
                                    statusLine = gnssStatus,
                                    alertShowing = gnssAlert,
                                )
                                if (gps != null) {
                                    Icon(
                                        Icons.Outlined.GpsFixed,
                                        contentDescription = gps.second,
                                        modifier = Modifier.size(13.dp),
                                        tint = headerTint(gps.first, muted, night),
                                    )
                                }
                                if (state.throttleHint.isNotBlank()) {
                                    Text(
                                        stringResource(R.string.app_throttle_hint_prefix, state.throttleHint),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = muted,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (route == "live") {
                            IconButton(
                                onClick = {
                                    vm.setScanControlsExpanded(!state.settings.scanControlsExpanded)
                                },
                            ) {
                                Icon(
                                    if (state.settings.scanControlsExpanded) {
                                        Icons.Outlined.ExpandLess
                                    } else {
                                        Icons.Outlined.Tune
                                    },
                                    if (state.settings.scanControlsExpanded) {
                                        stringResource(R.string.hide_scan_options)
                                    } else {
                                        stringResource(R.string.show_scan_options)
                                    },
                                    modifier = Modifier.onGloballyPositioned {
                                        tourTargets = tourTargets.copy(tune = it.boundsInRoot())
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (route != "detail" && route != "hunt") {
                Column {
                    if (route == "live" && (state.filter.arrivalsOnly || state.filter.movingWithYou)) {
                        LiveSessionBar(
                            arrivalsOnly = state.filter.arrivalsOnly,
                            movingWithYou = state.filter.movingWithYou,
                            onMarkSeen = vm::markArrivalsSeen,
                            onResetSeen = vm::resetArrivalsSeen,
                            onStartOverFollow = vm::resetFollowSession,
                        )
                    }
                    Surface(
                        color = NavigationBarDefaults.containerColor,
                        tonalElevation = NavigationBarDefaults.Elevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 1.dp)
                                .height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                        FieldwatchNavTab(
                            weight = 1f,
                            selected = route == "live",
                            onBounds = { tourTargets = tourTargets.copy(pause = it) },
                            onClick = {
                                if (route == "live") {
                                    vm.toggleLiveDisplay()
                                } else {
                                    nav.navigate("live") { launchSingleTop = true }
                                }
                            },
                            icon = {
                                Icon(
                                    when {
                                        route == "live" && !state.displayPaused -> Icons.Outlined.Pause
                                        route == "live" && state.displayPaused -> Icons.Outlined.PlayArrow
                                        else -> Icons.Outlined.CellTower
                                    },
                                    if (route == "live" && !state.displayPaused) stringResource(R.string.pause_display) else stringResource(R.string.live_tab),
                                )
                            },
                            label = if (route == "live" && !state.displayPaused) stringResource(R.string.pause_tab) else stringResource(R.string.live_tab),
                        )
                        FieldwatchNavTab(
                            weight = 1f,
                            selected = route == "filters",
                            onBounds = { tourTargets = tourTargets.copy(filters = it) },
                            onClick = { nav.navigate("filters") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.FilterAlt, null) },
                            label = stringResource(R.string.filters_tab),
                        )
                        FieldwatchNavTab(
                            weight = 1.45f,
                            selected = route == "fleets",
                            onBounds = { tourTargets = tourTargets.copy(signatures = it) },
                            onClick = { nav.navigate("fleets") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Hub, null) },
                            label = stringResource(R.string.signatures_tab),
                        )
                        FieldwatchNavTab(
                            weight = 1f,
                            selected = route == "reports" || route == "candidates",
                            onBounds = { tourTargets = tourTargets.copy(reports = it) },
                            onClick = { nav.navigate("reports") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Description, null) },
                            label = stringResource(R.string.report_title),
                        )
                        FieldwatchNavTab(
                            weight = 1.05f,
                            selected = route == "settings" || route == "radio-bookmarks" || route == "scan-status",
                            onBounds = { tourTargets = tourTargets.copy(settings = it) },
                            onClick = { nav.navigate("settings") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Settings, null) },
                            label = stringResource(R.string.settings),
                        )
                        }
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            nav,
            startDestination = "live",
            modifier = Modifier.padding(pad),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("live") {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    LivePane(
                        state = state,
                        vm = vm,
                        onOpen = {
                            vm.select(it)
                            nav.navigate("detail")
                        },
                    )
                    AnimatedVisibility(
                        visible = state.settings.scanControlsExpanded,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { vm.setScanControlsExpanded(false) },
                        )
                    }
                    AnimatedVisibility(
                        visible = state.settings.scanControlsExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .heightIn(max = maxHeight),
                    ) {
                        ViewPicker(
                            maxHeight = maxHeight,
                            mode = state.settings.viewMode,
                            sort = state.settings.strengthSort,
                            listSort = state.settings.listSort,
                            windowSec = state.settings.averageWindowSec,
                            decaySec = state.settings.decaySec,
                            showBar = state.settings.showRssiBar,
                            showFleet = state.settings.showFleetName,
                            showFrequency = state.settings.showFrequency,
                            showSeenTimes = state.settings.showSeenTimes,
                            titleLine = state.settings.listTitleLine,
                            subtitleLine = state.settings.listSubtitleLine,
                            onChangeView = vm::setViewMode,
                            onChangeSort = vm::setStrengthSort,
                            onChangeListSort = vm::setListSort,
                            onChangeDecay = vm::setDecaySec,
                            onToggleBar = vm::toggleRssiBar,
                            onToggleFleet = vm::toggleFleetName,
                            onToggleFrequency = vm::toggleFrequency,
                            onToggleSeenTimes = vm::toggleSeenTimes,
                            onChangeTitleLine = vm::setListTitleLine,
                            onChangeSubtitleLine = vm::setListSubtitleLine,
                        )
                    }
                }
            }
            composable("fleets") {
                FleetsScreen(
                    state = state,
                    vm = vm,
                    onCandidateDraftClosed = {
                        if (!nav.popBackStack("candidates", false)) {
                            nav.navigate("candidates")
                        }
                    },
                )
            }
            composable("filters") { FiltersScreen(state, vm) }
            composable("reports") {
                ReportsScreen(
                    state = state,
                    vm = vm,
                    exporting = export.active,
                    onSaveToStorage = { saveLauncher.launch(vm.suggestedExportName()) },
                    onSaveSitToStorage = { sitSaveLauncher.launch(vm.suggestedSitExportName()) },
                    onSignatureCandidates = {
                        vm.startSignatureCandidates()
                        nav.navigate("candidates")
                    },
                    onOpenPathRadio = { key ->
                        if (vm.openPathRadio(key)) nav.navigate("detail")
                    },
                )
            }
            composable("candidates") {
                CandidatesScreen(
                    vm = vm,
                    demoMode = state.settings.demoMode,
                    onBack = { nav.popBackStack() },
                    onCreate = { candidate ->
                        vm.beginCreateFromCandidate(candidate)
                        nav.navigate("fleets")
                    },
                )
            }
            composable("settings") {
                SettingsScreen(
                    state = state,
                    vm = vm,
                    onRadioBookmarks = { nav.navigate("radio-bookmarks") },
                    onScanStatus = { nav.navigate("scan-status") },
                    onShowLiveTour = {
                        vm.showLiveTour {
                            nav.navigate("live") { launchSingleTop = true }
                        }
                    },
                )
            }
            composable("scan-status") {
                ScanStatusScreen(state = state, onBack = { nav.popBackStack() })
            }
            composable("radio-bookmarks") {
                RadioBookmarksScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onOpen = { key ->
                        vm.select(key)
                        nav.navigate("detail")
                    },
                )
            }
            composable("detail") {
                val device = state.selected
                val onBack = { nav.popBackStack(); vm.select(null); Unit }
                if (device == null) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(R.string.detail)) },
                                navigationIcon = {
                                    IconButton(onClick = onBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                                    }
                                },
                            )
                        },
                    ) { pad ->
                        Text(
                            stringResource(R.string.no_radio_selected),
                            Modifier.padding(pad).padding(24.dp),
                        )
                    }
                } else {
                    DeviceDetailScreen(
                        device = device,
                        vm = vm,
                        watched = vm.isWatched(device.key),
                        onBack = onBack,
                        onCreateFleet = { vm.beginCreateFrom(device); nav.navigate("fleets") },
                        onHunt = {
                            vm.startHunt(device)
                            nav.navigate("hunt")
                        },
                        demoMode = state.settings.demoMode,
                    )
                }
            }
            composable("hunt") {
                HuntScreen(
                    vm = vm,
                    demoMode = state.settings.demoMode,
                    huntBeep = state.settings.huntBeep,
                    huntVibrate = state.settings.huntVibrate,
                    onBack = {
                        vm.stopHunt()
                        nav.popBackStack()
                    },
                )
            }
        }
    }
    if (showTour) {
        LiveChromeTour(targets = tourTargets, onDismiss = vm::dismissLiveTour)
    }
    }
}

@Composable
private fun headerTint(tone: HeaderTone, muted: Color, night: Boolean): Color {
    val color = when (tone) {
        HeaderTone.HEALTHY -> Phosphor
        HeaderTone.PROBLEM -> MaterialTheme.colorScheme.error
        HeaderTone.OFF -> muted.copy(alpha = 0.38f)
        HeaderTone.IDLE -> muted
    }
    return color.nightIf(night)
}

@Composable
private fun HeaderCount(n: Int, icon: ImageVector, desc: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = desc,
            modifier = Modifier.size(13.dp),
            tint = tint,
        )
        Text(
            n.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = tint,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun RowScope.FieldwatchNavTab(
    weight: Float,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    onBounds: (Rect) -> Unit = {},
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier
            .weight(weight)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            Box(
                Modifier.height(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) }) {
                    icon()
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewPicker(
    maxHeight: Dp,
    mode: ViewMode,
    sort: StrengthSort,
    listSort: ListSort,
    windowSec: Int,
    decaySec: Int,
    showBar: Boolean,
    showFleet: Boolean,
    showFrequency: Boolean,
    showSeenTimes: Boolean,
    titleLine: ListLine,
    subtitleLine: ListLine,
    onChangeView: (ViewMode) -> Unit,
    onChangeSort: (StrengthSort, Int?) -> Unit,
    onChangeListSort: (ListSort) -> Unit,
    onChangeDecay: (Int) -> Unit,
    onToggleBar: () -> Unit,
    onToggleFleet: () -> Unit,
    onToggleFrequency: () -> Unit,
    onToggleSeenTimes: () -> Unit,
    onChangeTitleLine: (ListLine) -> Unit,
    onChangeSubtitleLine: (ListLine) -> Unit,
) {
    var openView by remember { mutableStateOf(false) }
    var openSort by remember { mutableStateOf(false) }
    val viewLabel = stringResource(mode.labelRes)
    var openDecay by remember { mutableStateOf(false) }
    var openTitle by remember { mutableStateOf(false) }
    var openSubtitle by remember { mutableStateOf(false) }
    val sortLabel = when (listSort) {
        ListSort.STRENGTH -> if (sort == StrengthSort.AVERAGE) {
            stringResource(R.string.sort_strongest_avg_format, windowSec)
        } else {
            stringResource(R.string.sort_strongest_signal)
        }
        ListSort.NEWEST -> stringResource(R.string.sort_newest_heard)
        ListSort.NEWEST_ALERT -> stringResource(R.string.sort_newest_alert)
        ListSort.FIRST_SEEN -> stringResource(R.string.sort_newest_arrival)
        ListSort.ARRIVAL -> stringResource(R.string.sort_new_at_bottom)
        ListSort.NAME -> stringResource(R.string.sort_name_az)
        ListSort.SIGNATURES -> stringResource(R.string.sort_signatures_first)
    }
    val decayLabel = if (decaySec <= 0) {
        stringResource(R.string.sort_decay_off)
    } else {
        stringResource(R.string.sort_decay_hold_format, decaySec)
    }
    val scroll = rememberScrollState()
    val panelMax = (maxHeight - 8.dp).coerceAtLeast(140.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .heightIn(max = panelMax),
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Box {
        Column(
            Modifier
                .verticalScroll(scroll)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .padding(bottom = if (scroll.canScrollForward) 20.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.display),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val dropdownPad = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ExposedDropdownMenuBox(openView, { openView = it }, dropdownPad) {
                FieldwatchDropdownField(stringResource(R.string.view_label), viewLabel, openView)
                ExposedDropdownMenu(openView, { openView = false }) {
                    ViewMode.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(stringResource(item.labelRes)) },
                            onClick = { onChangeView(item); openView = false },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(openSort, { openSort = it }, dropdownPad) {
                FieldwatchDropdownField(stringResource(R.string.sort_label), sortLabel, openSort)
                ExposedDropdownMenu(openSort, { openSort = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_strongest_signal)) },
                        onClick = { onChangeSort(StrengthSort.INSTANT, null); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_strongest_avg_30)) },
                        onClick = { onChangeSort(StrengthSort.AVERAGE, 30); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_newest_heard)) },
                        onClick = { onChangeListSort(ListSort.NEWEST); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_newest_alert)) },
                        onClick = { onChangeListSort(ListSort.NEWEST_ALERT); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_newest_arrival)) },
                        onClick = { onChangeListSort(ListSort.FIRST_SEEN); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_new_at_bottom)) },
                        onClick = { onChangeListSort(ListSort.ARRIVAL); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_name_az)) },
                        onClick = { onChangeListSort(ListSort.NAME); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_signatures_first)) },
                        onClick = { onChangeListSort(ListSort.SIGNATURES); openSort = false },
                    )
                }
            }
            ExposedDropdownMenuBox(openDecay, { openDecay = it }, dropdownPad) {
                FieldwatchDropdownField(stringResource(R.string.brief_hold_label), decayLabel, openDecay)
                ExposedDropdownMenu(openDecay, { openDecay = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_decay_off)) },
                        onClick = { onChangeDecay(0); openDecay = false },
                    )
                    listOf(10, 30, 60).forEach { sec ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_decay_hold_format, sec)) },
                            onClick = { onChangeDecay(sec); openDecay = false },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(openTitle, { openTitle = it }, dropdownPad) {
                FieldwatchDropdownField(stringResource(R.string.title_line_label), listLineLabel(titleLine), openTitle)
                ExposedDropdownMenu(openTitle, { openTitle = false }) {
                    listOf(ListLine.ADVERTISED_NAME, ListLine.NAME_AND_TYPE, ListLine.MAC).forEach { item ->
                        DropdownMenuItem(
                            text = { Text(listLineLabel(item)) },
                            onClick = { onChangeTitleLine(item); openTitle = false },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(openSubtitle, { openSubtitle = it }, dropdownPad) {
                FieldwatchDropdownField(stringResource(R.string.subtitle_line_label), listLineLabel(subtitleLine), openSubtitle)
                ExposedDropdownMenu(openSubtitle, { openSubtitle = false }) {
                    listOf(
                        ListLine.ADVERTISED_NAME,
                        ListLine.NAME_AND_TYPE,
                        ListLine.MAC,
                        ListLine.NONE,
                    ).forEach { item ->
                        DropdownMenuItem(
                            text = { Text(listLineLabel(item)) },
                            onClick = { onChangeSubtitleLine(item); openSubtitle = false },
                        )
                    }
                }
            }
            OptionSwitch(stringResource(R.string.option_rssi_bars), showBar, onToggleBar)
            OptionSwitch(stringResource(R.string.option_signature_names), showFleet, onToggleFleet)
            OptionSwitch(stringResource(R.string.option_frequency), showFrequency, onToggleFrequency)
            OptionSwitch(stringResource(R.string.option_first_last_seen), showSeenTimes, onToggleSeenTimes)
        }
        if (scroll.canScrollForward) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, surfaceColor),
                        ),
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.more_display_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun listLineLabel(line: ListLine): String = when (line) {
    ListLine.ADVERTISED_NAME -> stringResource(R.string.listline_advertised_name)
    ListLine.NAME_AND_TYPE -> stringResource(R.string.listline_name_type)
    ListLine.MAC -> stringResource(R.string.listline_mac)
    ListLine.NONE -> stringResource(R.string.listline_none)
}

@Composable
private fun LiveSessionBar(
    arrivalsOnly: Boolean,
    movingWithYou: Boolean,
    onMarkSeen: () -> Unit,
    onResetSeen: () -> Unit,
    onStartOverFollow: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (arrivalsOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldwatchActionButton(
                        onClick = onMarkSeen,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text(stringResource(R.string.mark_seen)) }
                    FieldwatchActionButton(
                        onClick = onResetSeen,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text(stringResource(R.string.reset_seen)) }
                }
            }
            if (movingWithYou) {
                FieldwatchActionButton(
                    onClick = onStartOverFollow,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) { Text(stringResource(R.string.start_over)) }
            }
        }
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        FieldwatchSwitch(checked, { onToggle() })
    }
}

@Suppress("unused")
private val unusedPerms = RadioPermissions
