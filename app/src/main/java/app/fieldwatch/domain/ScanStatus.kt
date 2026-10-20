package app.fieldwatch.domain

import android.content.res.Resources
import app.fieldwatch.AppText
import app.fieldwatch.R

/**
 * Settings → Diagnostics. Phone and scan facts only.
 * No network names, MACs, or GPS coordinates.
 */
enum class HeaderTone { OFF, IDLE, HEALTHY, PROBLEM }

/** Wi-Fi icon in the list header. Green is healthy, including the wait between scans. */
fun wifiHeader(res: Resources, scanning: Boolean, locationOn: Boolean, radio: ScanRadioFacts): Pair<HeaderTone, String> {
    if (!radio.wifiOn) return HeaderTone.OFF to res.getString(R.string.diag_header_wifi_off)
    if (!scanning) return HeaderTone.IDLE to res.getString(R.string.diag_header_wifi_idle)
    if (!locationOn) return HeaderTone.PROBLEM to res.getString(R.string.diag_header_wifi_location_off)
    if (radio.wifiWaitingOnOs) return HeaderTone.PROBLEM to res.getString(R.string.diag_header_wifi_waiting)
    return HeaderTone.HEALTHY to res.getString(R.string.diag_header_wifi_on)
}

/**
 * GPS icon in the list header. Null when the check is off.
 * Green is armed and quiet. A hit is red. A pause, a Poor phone, and the warmup stay muted.
 */
fun gpsHeader(
    res: Resources,
    monitorOn: Boolean,
    suppressAlerts: Boolean,
    scanning: Boolean,
    statusLine: String,
    alertShowing: Boolean,
): Pair<HeaderTone, String>? {
    if (!monitorOn) return null
    if (alertShowing) return HeaderTone.PROBLEM to res.getString(R.string.diag_header_gps_alert)
    if (statusLine == GnssCopy.PAUSED) return HeaderTone.IDLE to res.getString(R.string.diag_header_gps_paused)
    if (!scanning || suppressAlerts) return HeaderTone.IDLE to res.getString(R.string.diag_header_gps_on)
    val ready = statusLine == res.getString(R.string.gnss_msg_armed) ||
        statusLine.startsWith(res.getString(R.string.gnss_msg_no_agc))
    return if (ready) {
        HeaderTone.HEALTHY to res.getString(R.string.diag_header_gps_on)
    } else {
        HeaderTone.IDLE to res.getString(R.string.diag_header_gps_on)
    }
}

/** Bluetooth icon in the list header. A planned rest stays healthy. */
fun bleHeader(res: Resources, scanning: Boolean, locationOn: Boolean, radio: ScanRadioFacts): Pair<HeaderTone, String> {
    if (!radio.bleOn) return HeaderTone.OFF to res.getString(R.string.diag_header_ble_off)
    if (!scanning) return HeaderTone.IDLE to res.getString(R.string.diag_header_ble_idle)
    if (!locationOn) return HeaderTone.PROBLEM to res.getString(R.string.diag_header_ble_location_off)
    val stuck = !radio.bleRunning && !radio.bleParked && !radio.bleRetrying && !radio.bleStarting
    if (radio.bleRetrying || stuck) return HeaderTone.PROBLEM to res.getString(R.string.diag_header_ble_waiting)
    return HeaderTone.HEALTHY to res.getString(R.string.diag_header_ble_on)
}

data class ScanRadioFacts(
    val scanning: Boolean = false,
    val wifiOn: Boolean = false,
    val wifiWaitingOnOs: Boolean = false,
    val lastWifiScanAt: Long = 0L,
    val bleOn: Boolean = false,
    val bleRunning: Boolean = false,
    val bleParked: Boolean = false,
    val bleRetrying: Boolean = false,
    val bleStarting: Boolean = false,
    val bleHitsLastMin: Int = 0,
)

data class ScanPhoneFacts(
    val versionName: String,
    val versionCode: Int,
    val catalogVersion: Int,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val fingerprint: String,
    val locationOn: Boolean,
    val locationPermission: Boolean,
    /** Milliseconds since the last GPS fix. Null when this phone has none. */
    val gpsFixAgeMs: Long?,
    val arrivalsOnly: Boolean = false,
    val watchedOnly: Boolean = false,
    val customNamesOnly: Boolean = false,
    val showOnly: Boolean = false,
    val movingWithYou: Boolean = false,
    val showWifi: Boolean = true,
    val showBle: Boolean = true,
    val nameFilter: Boolean = false,
    val ouiFilter: Boolean = false,
    /** Settings switch. Fast scans run only when this is on and the OS is not throttling. */
    val wifiFastScan: Boolean = false,
    val wifiOsThrottled: Boolean = false,
    val backgroundUsage: Boolean = false,
    val unrestrictedBattery: Boolean = false,
)

data class ScanStatusReport(
    val rows: List<Pair<String, String>>,
    val verdict: String,
    /** Plain steps for the person holding the phone. Empty never happens. */
    val checks: List<String>,
    val text: String,
    val blocked: Boolean,
)

object ScanStatus {
    fun report(
        res: Resources,
        radio: ScanRadioFacts,
        phone: ScanPhoneFacts,
        now: Long,
        radiosOnAir: Int = 0,
        gnssRows: List<Pair<String, String>> = emptyList(),
    ): ScanStatusReport {
        val rows = listOf(
            res.getString(R.string.diag_row_app) to "${phone.versionName} (${phone.versionCode})",
            res.getString(R.string.diag_row_catalog) to phone.catalogVersion.toString(),
            res.getString(R.string.diag_row_phone) to "${phone.manufacturer} ${phone.model}".trim(),
            res.getString(R.string.diag_row_android) to "${phone.androidRelease} (API ${phone.sdkInt})",
            res.getString(R.string.diag_row_fingerprint) to phone.fingerprint,
            res.getString(R.string.diag_row_wifi) to onOff(res, radio.wifiOn),
            res.getString(R.string.diag_row_last_wifi_scan) to lastWifi(res, radio, now),
            res.getString(R.string.diag_row_bluetooth) to onOff(res, radio.bleOn),
            res.getString(R.string.diag_row_ble) to bleState(res, radio),
            res.getString(R.string.diag_row_ble_results) to radio.bleHitsLastMin.coerceAtLeast(0).toString(),
            res.getString(R.string.diag_row_faster_wifi) to fasterWifi(res, phone),
            res.getString(R.string.diag_row_background) to onOff(res, phone.backgroundUsage),
            res.getString(R.string.diag_row_battery) to onOff(res, phone.unrestrictedBattery),
            res.getString(R.string.diag_row_location) to onOff(res, phone.locationOn),
            res.getString(R.string.diag_row_location_permission) to if (phone.locationPermission) {
                res.getString(R.string.diag_value_granted)
            } else {
                res.getString(R.string.diag_value_missing)
            },
            res.getString(R.string.diag_row_gps_fix) to ageLabel(res, phone.gpsFixAgeMs),
            res.getString(R.string.diag_row_filters) to filtersLabel(res, phone),
        ) + gnssRows
        val verdict = verdict(res, radio, phone, radiosOnAir)
        val checks = checks(res, radio, phone, radiosOnAir)
        val blocked = isBlocked(radio, phone.locationOn, phone.locationPermission)
        val text = buildString {
            rows.forEach { (k, v) -> append(k).append(": ").append(v).append('\n') }
            append(res.getString(R.string.diag_verdict_prefix)).append(verdict).append('\n')
            append(res.getString(R.string.diag_check_heading)).append('\n')
            checks.forEach { append("- ").append(it).append('\n') }
        }
        return ScanStatusReport(rows, verdict, checks, text, blocked)
    }

    fun isBlocked(radio: ScanRadioFacts, locationOn: Boolean, locationPermission: Boolean): Boolean {
        if (!radio.scanning) return true
        if (!locationOn || !locationPermission) return true
        if (radio.bleStarting && !radio.bleRunning) return false
        return !wifiUsable(radio) && !bleUsable(radio)
    }

    /** Appended to an empty Live list while a scan is blocked. */
    val EMPTY_POINTER: String
        get() = AppText.get(R.string.scan_empty_pointer, "Settings → Diagnostics.")

    private fun wifiUsable(radio: ScanRadioFacts): Boolean =
        radio.wifiOn && !radio.wifiWaitingOnOs

    private fun bleUsable(radio: ScanRadioFacts): Boolean =
        radio.bleOn && radio.bleRunning && !radio.bleParked && !radio.bleRetrying

    private fun verdict(res: Resources, radio: ScanRadioFacts, phone: ScanPhoneFacts, radiosOnAir: Int): String {
        val wifiOk = wifiUsable(radio)
        val bleOk = bleUsable(radio)
        return when {
            !radio.scanning -> res.getString(R.string.diag_verdict_not_scanning)
            !phone.locationOn -> res.getString(R.string.diag_verdict_location_off)
            !phone.locationPermission -> res.getString(R.string.diag_verdict_location_permission_off)
            !radio.wifiOn && !radio.bleOn -> res.getString(R.string.diag_verdict_both_off)
            radio.bleStarting && !radio.bleRunning && !radio.wifiWaitingOnOs -> res.getString(R.string.diag_verdict_starting)
            !wifiOk && !bleOk && radio.wifiWaitingOnOs && radio.bleParked ->
                res.getString(R.string.diag_verdict_wifi_refused_ble_parked)
            !wifiOk && !bleOk && radio.bleParked ->
                res.getString(R.string.diag_verdict_ble_parked)
            !wifiOk && !bleOk && radio.bleRetrying -> res.getString(R.string.diag_verdict_ble_retrying)
            !wifiOk && !bleOk && radio.wifiWaitingOnOs ->
                res.getString(R.string.diag_verdict_wifi_refused)
            !wifiOk && !bleOk -> res.getString(R.string.diag_verdict_no_scans)
            radio.wifiWaitingOnOs && bleOk -> res.getString(R.string.diag_verdict_wifi_refused_ble_running)
            radio.bleParked && wifiOk -> res.getString(R.string.diag_verdict_ble_parked_wifi_running)
            !radio.wifiOn && bleOk -> res.getString(R.string.diag_verdict_wifi_off_ble_running)
            !radio.bleOn && wifiOk -> res.getString(R.string.diag_verdict_ble_off_wifi_running)
            filterHiding(phone) -> res.getString(R.string.diag_verdict_filter_hiding)
            radiosOnAir > 0 -> res.getString(R.string.diag_verdict_running)
            else -> res.getString(R.string.diag_verdict_running_nothing)
        }
    }

    /**
     * What to turn on, in the order a person can fix it.
     * A radio that is off is named only when the other radio is still hearing.
     * Background usage, unrestricted battery, and a GPS fix are not required
     * while Fieldwatch is open, so they stay in the fact rows.
     */
    private fun checks(res: Resources, radio: ScanRadioFacts, phone: ScanPhoneFacts, radiosOnAir: Int): List<String> {
        val lines = mutableListOf<String>()
        if (!radio.scanning) {
            lines += res.getString(R.string.diag_check_not_scanning)
        }
        if (!phone.locationOn) {
            lines += res.getString(R.string.diag_check_location_off)
        }
        if (!phone.locationPermission) {
            lines += res.getString(R.string.diag_check_location_permission)
        }
        val wifiOk = wifiUsable(radio)
        val bleOk = bleUsable(radio)
        val locationReady = phone.locationOn && phone.locationPermission
        if (!radio.wifiOn && !radio.bleOn) {
            lines += res.getString(R.string.diag_check_both_off)
        } else {
            if (!radio.wifiOn && bleOk) {
                lines += res.getString(R.string.diag_check_wifi_off_ble_running)
            }
            if (!radio.bleOn && wifiOk) {
                lines += res.getString(R.string.diag_check_ble_off_wifi_running)
            }
            if (locationReady && radio.wifiOn && radio.wifiWaitingOnOs) {
                lines += res.getString(R.string.diag_check_wifi_refused)
            }
            if (locationReady && radio.bleOn && radio.bleParked) {
                lines += res.getString(R.string.diag_check_ble_parked)
            }
            if (radio.bleOn && radio.bleRetrying) {
                lines += res.getString(R.string.diag_check_ble_retrying)
            }
            if (radio.bleOn && radio.bleStarting && !radio.bleRunning) {
                lines += res.getString(R.string.diag_check_ble_starting)
            }
            if (radio.bleOn && !radio.bleRunning && !radio.bleParked && !radio.bleRetrying && !radio.bleStarting) {
                lines += res.getString(R.string.diag_check_ble_on_not_running)
            }
        }
        if (filterHiding(phone)) {
            lines += res.getString(R.string.diag_check_filter_hiding, filtersLabel(res, phone))
        }
        if (lines.isEmpty()) {
            lines += if (radiosOnAir > 0) {
                res.getString(R.string.diag_check_all_ok_running)
            } else {
                res.getString(R.string.diag_check_all_ok_running_nothing)
            }
        }
        return lines
    }

    private fun filterHiding(phone: ScanPhoneFacts): Boolean =
        phone.arrivalsOnly || phone.watchedOnly || phone.customNamesOnly || phone.showOnly ||
            phone.movingWithYou || !phone.showWifi || !phone.showBle || phone.nameFilter || phone.ouiFilter

    private fun filtersLabel(res: Resources, phone: ScanPhoneFacts): String {
        val parts = buildList {
            if (phone.arrivalsOnly) add(res.getString(R.string.diag_filter_arrivals_only))
            if (phone.watchedOnly) add(res.getString(R.string.diag_filter_watched_only))
            if (phone.customNamesOnly) add(res.getString(R.string.diag_filter_named_only))
            if (phone.showOnly) add(res.getString(R.string.diag_filter_show_only))
            if (phone.movingWithYou) add(res.getString(R.string.diag_filter_moving_with_you))
            if (!phone.showWifi) add(res.getString(R.string.diag_filter_wifi_hidden))
            if (!phone.showBle) add(res.getString(R.string.diag_filter_ble_hidden))
            if (phone.nameFilter) add(res.getString(R.string.diag_filter_name_on))
            if (phone.ouiFilter) add(res.getString(R.string.diag_filter_oui_on))
        }
        return if (parts.isEmpty()) {
            res.getString(R.string.diag_all_traffic)
        } else {
            parts.joinToString(", ")
        }
    }

    private fun onOff(res: Resources, on: Boolean): String =
        if (on) res.getString(R.string.diag_value_on) else res.getString(R.string.diag_value_off)

    private fun fasterWifi(res: Resources, phone: ScanPhoneFacts): String =
        if (phone.wifiOsThrottled) res.getString(R.string.diag_value_off) else res.getString(R.string.diag_value_on)

    private fun lastWifi(res: Resources, radio: ScanRadioFacts, now: Long): String {
        val age = ageLabel(res, if (radio.lastWifiScanAt > 0L) now - radio.lastWifiScanAt else null)
        return when {
            radio.wifiWaitingOnOs && radio.lastWifiScanAt > 0L ->
                res.getString(R.string.diag_waiting_on_os_with_age, age)
            radio.wifiWaitingOnOs -> res.getString(R.string.diag_waiting_on_os)
            else -> age
        }
    }

    private fun bleState(res: Resources, radio: ScanRadioFacts): String = when {
        !radio.bleOn -> res.getString(R.string.diag_ble_state_off)
        radio.bleParked -> res.getString(R.string.diag_ble_state_parked)
        radio.bleRetrying -> res.getString(R.string.diag_ble_state_retrying)
        radio.bleStarting && !radio.bleRunning -> res.getString(R.string.diag_ble_state_starting)
        radio.bleRunning -> res.getString(R.string.diag_ble_state_running)
        else -> res.getString(R.string.diag_ble_state_not_running)
    }

    private fun ageLabel(res: Resources, ageMs: Long?): String {
        if (ageMs == null || ageMs < 0L) return res.getString(R.string.diag_age_none)
        val sec = (ageMs / 1000L).toInt()
        return if (sec < 60) {
            res.getString(R.string.diag_age_seconds, sec)
        } else {
            res.getString(R.string.diag_age_minutes, sec / 60)
        }
    }
}
