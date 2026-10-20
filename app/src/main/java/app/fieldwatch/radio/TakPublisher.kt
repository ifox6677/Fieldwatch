package app.fieldwatch.radio

import android.util.Log
import app.fieldwatch.AppText
import app.fieldwatch.R
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.CotEvent
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.GnssLive
import app.fieldwatch.domain.GnssNotice
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakFeedStatus
import app.fieldwatch.domain.TakGnss
import app.fieldwatch.domain.TakGnssTrack
import app.fieldwatch.domain.TakHeardHere
import app.fieldwatch.domain.TakPublish
import app.fieldwatch.domain.TakSent
import app.fieldwatch.domain.WatchTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends Cursor-on-Target UDP to ATAK SA multicast or a unicast host:port.
 * Same-phone ATAK often ignores multicast loopback, so unicast also hits
 * 127.0.0.1 on the same port. Privacy mode skips the feed.
 */
class TakPublisher {
    private val last = ConcurrentHashMap<String, TakSent>()
    /** Radios that left the feed and were resent once with the spot hold. */
    private val parked = ConcurrentHashMap<String, TakSent>()
    private val gnssMarks = LinkedHashMap<String, TakGnssTrack>()
    private val gate = Any()
    @Volatile private var multicast: MulticastSocket? = null
    @Volatile private var unicast: DatagramSocket? = null
    private val _status = MutableStateFlow(TakFeedStatus())
    val status: StateFlow<TakFeedStatus> = _status.asStateFlow()

    fun publish(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        watchlist: List<WatchTarget>,
        now: Long = System.currentTimeMillis(),
        selfFix: Pair<Double, Double>? = null,
        gnssRemark: String? = null,
        gnssNotice: GnssNotice? = null,
        gnssLives: List<GnssLive> = emptyList(),
    ) {
        if (!settings.takEnabled || settings.demoMode) {
            retractRadios(settings, now)
            if (last.isEmpty() && parked.isEmpty()) gnssMarks.clear()
            _status.value = if (!settings.takEnabled) {
                TakFeedStatus(detail = AppText.get(R.string.tak_status_off, "Off"))
            } else {
                TakFeedStatus(
                    paused = true,
                    detail = AppText.get(R.string.tak_status_privacy, "Privacy mode — feed paused"),
                )
            }
            return
        }
        val host = settings.takHost.trim().ifBlank { TakDefaults.HOST }
        val port = settings.takPort.coerceIn(1, 65_535)
        val dests = destinations(host, port)
        if (dests.isEmpty()) {
            Log.w(TAG, "TAK host $host did not resolve")
            _status.value = TakFeedStatus(
                at = now,
                dest = "$host:$port",
                error = AppText.get(
                    R.string.tak_status_host_unresolved,
                    "Host %1\$s did not resolve",
                    host,
                ),
            )
            return
        }
        val destLabel = dests.joinToString { "${it.first.hostAddress}:${it.second}" }
        val selfOk = selfFix != null && PayloadOk(selfFix)
        var sent = 0
        var gone = 0
        var lastErr: String? = null

        if (selfOk) {
            val prev = last[SELF_UID]
            val gnssLine = gnssRemark?.trim().orEmpty()
            val noteChanged = (prev?.note ?: "") != gnssLine
            if (noteChanged || TakPublish.shouldEmit(prev?.at, prev?.lat, prev?.lon, now, selfFix!!.first, selfFix.second)) {
                val xml = CotEvent.selfXml(
                    selfFix.first,
                    selfFix.second,
                    now,
                    remarksExtra = gnssLine.ifBlank { null },
                )
                if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                    last[SELF_UID] = TakSent(
                        SELF_UID,
                        SELF_UID,
                        now,
                        selfFix.first,
                        selfFix.second,
                        note = gnssLine,
                    )
                    sent++
                } else {
                    lastErr = AppText.get(R.string.tak_status_send_failed, "send failed")
                }
            }
        }

        val gnssFix = if (selfOk) selfFix else null
        val gnssPlan = TakGnss.plan(
            open = gnssMarks.values.firstOrNull { it.open },
            mode = settings.takGnss,
            notice = gnssNotice,
            lives = gnssLives,
            lat = gnssFix?.first,
            lon = gnssFix?.second,
            now = now,
            hold = settings.takGnssHold,
            hasMarks = gnssMarks.isNotEmpty(),
        )
        if (gnssPlan.clearAll) {
            for (mark in gnssMarks.values) {
                val xml = CotEvent.tombstoneXml(
                    uid = mark.uid,
                    lat = mark.startLat,
                    lon = mark.startLon,
                    now = now,
                    callsign = TakGnss.CALLOUT,
                    remarks = AppText.get(
                        R.string.tak_gnss_where,
                        "Where this phone was. Not the source.",
                    ),
                )
                if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                    gone++
                } else {
                    lastErr = AppText.get(R.string.tak_status_send_failed, "send failed")
                }
            }
            if (lastErr == null) gnssMarks.clear()
        } else {
            var gnssOk = true
            val drawn = gnssPlan.plot
            val gnssUid = gnssPlan.track?.uid
            if (gnssOk && gnssPlan.send && drawn != null && gnssUid != null) {
                val xml = if (drawn.pin) {
                    CotEvent.gnssPinXml(
                        uid = gnssUid,
                        lat = drawn.lat,
                        lon = drawn.lon,
                        now = now,
                        staleMs = gnssPlan.staleMs,
                        remarks = gnssPlan.remarks,
                    )
                } else {
                    CotEvent.gnssOvalXml(
                        uid = gnssUid,
                        lat = drawn.lat,
                        lon = drawn.lon,
                        semiMajorM = drawn.semiMajorM,
                        semiMinorM = drawn.semiMinorM,
                        angleDeg = drawn.angleDeg,
                        now = now,
                        staleMs = gnssPlan.staleMs,
                        remarks = gnssPlan.remarks,
                    )
                }
                if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                    sent++
                } else {
                    gnssOk = false
                    lastErr = AppText.get(R.string.tak_status_send_failed, "send failed")
                }
            }
            if (gnssOk) {
                val track = gnssPlan.track
                if (track != null) gnssMarks[track.uid] = track
            }
        }

        val chosen = devices.asSequence()
            .filter { TakPublish.eligible(it, settings, fleets, watchlist) }
            .sortedWith(
                compareByDescending<Sighting> { TakPublish.rank(it, fleets, watchlist) }
                    .thenByDescending { it.rssi },
            )
            .toList()
        var emitted = sent
        for (device in chosen) {
            if (emitted >= TakDefaults.MAX_PER_TICK) break
            val marks = TakPublish.markers(device, settings, fleets, watchlist)
            for (mark in marks) {
                if (emitted >= TakDefaults.MAX_PER_TICK) break
                val prev = last[mark.uid]
                val lat: Double
                val lon: Double
                val peakRssi: Int
                if (mark.advertised || mark.pilot) {
                    if (!TakPublish.shouldEmit(prev?.at, prev?.lat, prev?.lon, now, mark.lat, mark.lon)) {
                        continue
                    }
                    lat = mark.lat
                    lon = mark.lon
                    peakRssi = mark.device.rssi
                } else {
                    when (TakPublish.heardHereAction(prev?.at, prev?.rssi, now, mark.device.rssi)) {
                        TakHeardHere.SKIP -> continue
                        TakHeardHere.MOVE -> {
                            lat = mark.lat
                            lon = mark.lon
                            peakRssi = mark.device.rssi
                        }
                        TakHeardHere.REFRESH -> {
                            val held = prev ?: continue
                            lat = held.lat
                            lon = held.lon
                            peakRssi = held.rssi
                        }
                    }
                }
                val face = CotEvent.face(
                    device = mark.device,
                    fleets = fleets,
                    watchlist = watchlist,
                    advertised = mark.advertised,
                    pilot = mark.pilot,
                )
                val xml = CotEvent.render(
                    uid = mark.uid,
                    face = face,
                    lat = lat,
                    lon = lon,
                    now = now,
                    staleMs = TakDefaults.STALE_MS,
                )
                if (!sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                    lastErr = AppText.get(R.string.tak_status_send_failed, "send failed")
                    continue
                }
                last[mark.uid] = TakSent(
                    mark.uid,
                    mark.deviceKey,
                    now,
                    lat,
                    lon,
                    peakRssi,
                    face = face,
                    staleMs = TakDefaults.STALE_MS,
                )
                parked.remove(mark.uid)
                sent++
                emitted++
            }
        }

        val keep = TakPublish.keepUids(
            devices = devices,
            settings = settings,
            fleets = fleets,
            watchlist = watchlist,
            previous = last.values,
            selfUid = SELF_UID,
            selfOk = selfOk,
        )
        val holdMs = settings.takRadioHold.staleMs()
        var holdBudget = TakDefaults.MAX_PER_TICK
        for (uid in last.keys.filter { it !in keep }) {
            val prev = last[uid] ?: continue
            val face = prev.face
            if (uid == SELF_UID || face == null) {
                last.remove(uid)
                continue
            }
            if (holdBudget <= 0) break
            val xml = CotEvent.render(uid, face, prev.lat, prev.lon, now, holdMs)
            if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                last.remove(uid)
                parked[uid] = prev.copy(at = now, staleMs = holdMs)
                sent++
                holdBudget--
            } else {
                lastErr = AppText.get(R.string.tak_status_send_failed, "send failed")
                break
            }
        }
        for (spot in parked.values.toList()) {
            if (spot.uid in keep || spot.staleMs == holdMs) continue
            val face = spot.face
            if (face == null) {
                parked.remove(spot.uid)
                continue
            }
            if (holdBudget <= 0) break
            val xml = CotEvent.render(spot.uid, face, spot.lat, spot.lon, now, holdMs)
            if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                parked[spot.uid] = spot.copy(at = now, staleMs = holdMs)
                sent++
                holdBudget--
            } else {
                lastErr = AppText.get(R.string.tak_status_send_failed, "send failed")
                break
            }
        }

        if (sent == 0 && gone == 0 && lastErr == null && chosen.isEmpty() && !selfOk) {
            Log.i(TAG, "TAK on, 0 eligible radios (need Extra attention / payload latlon / GPS stamp)")
        }
        if (sent > 0 || gone > 0) {
            Log.i(TAG, "TAK sent $sent marker(s), $gone gone to $destLabel")
        }
        val detail = when {
            lastErr != null -> lastErr
            sent == 0 && gone == 0 && chosen.isEmpty() && !selfOk ->
                AppText.get(
                    R.string.tak_status_no_eligible,
                    "0 eligible radios (need Extra attention / payload latlon / GPS stamp)",
                )
            else -> ""
        }
        _status.value = TakFeedStatus(
            at = now,
            sent = sent,
            gone = gone,
            onFeed = last.size,
            dest = destLabel,
            error = lastErr,
            detail = detail,
        )
    }

    fun close() {
        synchronized(gate) {
            runCatching { multicast?.close() }
            runCatching { unicast?.close() }
            multicast = null
            unicast = null
        }
        last.clear()
        parked.clear()
        gnssMarks.clear()
        _status.value = TakFeedStatus(detail = AppText.get(R.string.tak_status_off, "Off"))
    }

    /** Feed off or Privacy. Drop radio spots under their own names. GNSS marks are left for ATAK. */
    private fun retractRadios(settings: AppSettings, now: Long) {
        if (last.isEmpty() && parked.isEmpty()) return
        val host = settings.takHost.trim().ifBlank { TakDefaults.HOST }
        val port = settings.takPort.coerceIn(1, 65_535)
        val dests = destinations(host, port)
        if (dests.isEmpty()) return
        val spots = LinkedHashMap<String, TakSent>()
        last.forEach { (uid, spot) -> spots[uid] = spot }
        parked.forEach { (uid, spot) -> spots.putIfAbsent(uid, spot) }
        for ((uid, spot) in spots) {
            val face = spot.face
            val xml = if (face != null) {
                CotEvent.tombstoneXml(uid, spot.lat, spot.lon, now, face.callsign, face.remarks)
            } else {
                CotEvent.tombstoneXml(uid, spot.lat, spot.lon, now)
            }
            if (!sendAll(dests, xml.toByteArray(Charsets.UTF_8))) return
            last.remove(uid)
            parked.remove(uid)
        }
    }

    private fun destinations(host: String, port: Int): List<Pair<InetAddress, Int>> {
        val primary = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, Pair<InetAddress, Int>>()
        fun add(addr: InetAddress, p: Int) {
            val key = "${addr.hostAddress}:$p"
            out.putIfAbsent(key, addr to p)
        }
        add(primary, port)
        if (!primary.isLoopbackAddress) {
            runCatching { InetAddress.getByName("127.0.0.1") }.getOrNull()?.let { add(it, port) }
        }
        return out.values.toList()
    }

    private fun sendAll(dests: List<Pair<InetAddress, Int>>, bytes: ByteArray): Boolean {
        var ok = false
        for ((addr, port) in dests) {
            if (send(addr, port, bytes)) ok = true
        }
        return ok
    }

    private fun send(addr: InetAddress, port: Int, bytes: ByteArray): Boolean {
        return synchronized(gate) {
            runCatching {
                val packet = DatagramPacket(bytes, bytes.size, addr, port)
                if (addr.isMulticastAddress) {
                    val sock = multicastSock()
                    runCatching { sock.timeToLive = 1 }
                    sock.send(packet)
                } else {
                    unicastSock().send(packet)
                }
                true
            }.onFailure { err ->
                Log.w(TAG, "TAK send ${addr.hostAddress}:$port failed: $err")
            }.getOrDefault(false)
        }
    }

    private fun multicastSock(): MulticastSocket = synchronized(gate) {
        val open = multicast
        if (open != null && !open.isClosed) return open
        MulticastSocket().also {
            it.reuseAddress = true
            runCatching { it.timeToLive = 1 }
            multicast = it
        }
    }

    private fun unicastSock(): DatagramSocket = synchronized(gate) {
        val open = unicast
        if (open != null && !open.isClosed) return open
        DatagramSocket().also {
            it.reuseAddress = true
            unicast = it
        }
    }

    companion object {
        private const val TAG = "FieldwatchTak"
        private const val SELF_UID = "FIELDWATCH-SELF"
        private fun PayloadOk(fix: Pair<Double, Double>): Boolean =
            app.fieldwatch.domain.PayloadLocation.validCoord(fix.first, fix.second)
    }
}
