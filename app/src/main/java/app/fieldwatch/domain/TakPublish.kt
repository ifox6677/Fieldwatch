package app.fieldwatch.domain

import app.fieldwatch.AppText
import app.fieldwatch.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** ATAK CIV CoT input is the default port. SA multicast is 239.2.3.1:6969. UDP only. */
object TakDefaults {
    const val HOST = "239.2.3.1"
    const val PORT = 10011
    const val LOOPBACK = "127.0.0.1"
    const val SA_HOST = "239.2.3.1"
    const val SA_PORT = 6969
    const val MIN_INTERVAL_MS = 10_000L
    const val MOVE_M = 30.0
    const val STALE_MS = 120_000L
    const val MAX_PER_TICK = 24
    const val REMARKS_MAX = 800
}

enum class TakUdpPreset {
    THIS_PHONE,
    LAN_MULTICAST,
    CUSTOM,
}

data class TakFeedStatus(
    val at: Long = 0L,
    val sent: Int = 0,
    val gone: Int = 0,
    val onFeed: Int = 0,
    val dest: String = "",
    val error: String? = null,
    val paused: Boolean = false,
    val detail: String = "",
)

data class TakSent(
    val uid: String,
    val deviceKey: String,
    val at: Long,
    val lat: Double,
    val lon: Double,
    val rssi: Int = Int.MIN_VALUE,
    /** Last GNSS line sent on the phone marker. Empty when that line is off. */
    val note: String = "",
    /** Label last sent for this radio. Null on the phone marker. */
    val face: TakFace? = null,
    /** Stale time last sent. The phone marker uses the 2-minute refresh. */
    val staleMs: Long = TakDefaults.STALE_MS,
)

/** Map label and card for one radio spot. Kept so a departure can be resent without renaming it. */
data class TakFace(
    val callsign: String,
    val remarks: String,
    val cotType: String,
    val group: String,
    val role: String = "Team Member",
    val hae: Double = 9999999.0,
    val course: Double? = null,
    val speed: Double? = null,
    val linkUid: String? = null,
    val linkType: String? = null,
)

/** When a GNSS hit is written onto this phone's TAK marker, and onto its own map mark. */
@Serializable
enum class TakGnssSend {
    OFF,
    ALERT,
    RED_LINE,
    ANY,
    ;

    fun label(): String = when (this) {
        OFF -> "Off"
        ALERT -> "While alerting"
        RED_LINE -> "Red line"
        ANY -> "Any hit"
    }

    val labelRes: Int
        get() = when (this) {
            OFF -> R.string.tak_option_off
            ALERT -> R.string.tak_option_while_alerting
            RED_LINE -> R.string.tak_option_red_line
            ANY -> R.string.tak_option_any_hit
        }
}

/** How long a GNSS map mark stays after its last update. */
@Serializable
enum class TakGnssHold {
    HOUR,
    HOURS_6,
    HOURS_24,
    UNTIL_DELETED,
    ;

    fun label(): String = when (this) {
        HOUR -> "1 hour"
        HOURS_6 -> "6 hours"
        HOURS_24 -> "24 hours"
        UNTIL_DELETED -> "Until deleted"
    }

    val labelRes: Int
        get() = when (this) {
            HOUR -> R.string.tak_hold_1_hour
            HOURS_6 -> R.string.tak_hold_6_hours
            HOURS_24 -> R.string.tak_hold_24_hours
            UNTIL_DELETED -> R.string.tak_hold_until_deleted
        }

    fun staleMs(): Long = when (this) {
        HOUR -> 3_600_000L
        HOURS_6 -> 6L * 3_600_000L
        HOURS_24 -> 24L * 3_600_000L
        UNTIL_DELETED -> 10L * 365L * 24L * 3_600_000L
    }
}

/** When a flood line is written onto this phone's TAK marker. Not a separate pin. */
@Serializable(with = TakFloodSendSerializer::class)
enum class TakFloodSend {
    OFF,
    ON,
    ;

    fun label(): String = when (this) {
        OFF -> "Off"
        ON -> "On"
    }

    val labelRes: Int
        get() = when (this) {
            OFF -> R.string.tak_option_off
            ON -> R.string.tak_option_on
        }
}

/** Reads the first field-test name (Red line) so an already-saved setup still loads. */
object TakFloodSendSerializer : KSerializer<TakFloodSend> {
    override val descriptor = PrimitiveSerialDescriptor("TakFloodSend", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TakFloodSend) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): TakFloodSend = when (val raw = decoder.decodeString()) {
        "OFF" -> TakFloodSend.OFF
        "ON", "RED_LINE" -> TakFloodSend.ON
        else -> throw SerializationException("Unknown TAK flood choice: $raw")
    }
}

object TakFlood {
    /** The Live flood line, from the moment the flood is detected until it ends. */
    fun line(mode: TakFloodSend, notice: PairingFlood.Notice?): String? {
        if (mode != TakFloodSend.ON) return null
        return notice?.line()?.trim()?.ifBlank { null }
    }
}

object TakGnss {
    /**
     * One line for the Fieldwatch heartbeat, or null when this mode sends nothing.
     * The line is the same words as the Live banner. No coordinates.
     */
    fun line(mode: TakGnssSend, notice: GnssNotice?, lives: List<GnssLive>): String? {
        val active = notice?.takeIf { !it.ended }?.line?.takeIf { it.isNotBlank() }
        val ended = notice?.takeIf { it.ended }?.line?.takeIf { it.isNotBlank() }
        val anyLive = if (lives.isEmpty()) {
            null
        } else {
            GnssCopy.banner(lives.map { it.kind }.toSet(), lives.maxOf { it.confidence })
        }
        return when (mode) {
            TakGnssSend.OFF -> null
            TakGnssSend.ALERT -> active
            TakGnssSend.RED_LINE -> active ?: ended
            TakGnssSend.ANY -> active ?: anyLive
        }
    }

    /** Under this span the mark stays a pin at the first fix. */
    const val SPAN_PIN_M = 100.0

    val CALLOUT: String
        get() = AppText.get(R.string.tak_gnss_callout, "Possible GNSS interference")

    /**
     * Pin at the start, or an oval whose tips are the start and the latest fix.
     * The full width is half the length. [TakGnssPlot.semiMajorM] is center to tip.
     */
    fun plot(startLat: Double, startLon: Double, endLat: Double, endLon: Double): TakGnssPlot {
        val span = Geo.meters(startLat, startLon, endLat, endLon)
        if (span < SPAN_PIN_M) {
            return TakGnssPlot(pin = true, lat = startLat, lon = startLon)
        }
        val (lat, lon) = Geo.midpoint(startLat, startLon, endLat, endLon)
        return TakGnssPlot(
            pin = false,
            lat = lat,
            lon = lon,
            semiMajorM = span / 2.0,
            semiMinorM = span / 4.0,
            angleDeg = Geo.bearingDegrees(startLat, startLon, endLat, endLon),
        )
    }

    /** True while the hit is still on. The 10-minute ended line does not grow the mark. */
    fun growing(mode: TakGnssSend, notice: GnssNotice?, lives: List<GnssLive>): Boolean {
        if (mode == TakGnssSend.OFF || notice?.ended == true) return false
        if (notice?.line?.isNotBlank() == true) return true
        return mode == TakGnssSend.ANY && lives.isNotEmpty()
    }

    fun remarks(notice: GnssNotice?, lives: List<GnssLive>): String {
        val line = notice?.line?.trim().orEmpty().ifBlank {
            if (lives.isEmpty()) {
                CALLOUT
            } else {
                GnssCopy.banner(lives.map { it.kind }.toSet(), lives.maxOf { it.confidence })
            }
        }
        return AppText.get(
            R.string.tak_gnss_remarks,
            "%1\$s\nWhere this phone was. Not the source.",
            line,
        )
    }

    /**
     * One step of the map mark. [open] is the episode still growing, or null.
     * A closed episode is left for the caller to keep until GNSS TAK is turned off.
     */
    fun plan(
        open: TakGnssTrack?,
        mode: TakGnssSend,
        notice: GnssNotice?,
        lives: List<GnssLive>,
        lat: Double?,
        lon: Double?,
        now: Long,
        hold: TakGnssHold,
        hasMarks: Boolean = open != null,
    ): TakGnssAction {
        if (mode == TakGnssSend.OFF) {
            return TakGnssAction(clearAll = hasMarks)
        }
        if (!growing(mode, notice, lives)) {
            if (open == null || !open.open) return TakGnssAction(track = open)
            val frozen = plot(open.startLat, open.startLon, open.endLat, open.endLon)
            return TakGnssAction(
                track = open.copy(
                    open = false,
                    lastSentAt = now,
                    sentEndLat = open.endLat,
                    sentEndLon = open.endLon,
                    sentPin = frozen.pin,
                ),
                plot = frozen,
                remarks = remarks(notice, lives),
                staleMs = hold.staleMs(),
                send = true,
            )
        }
        if (lat == null || lon == null) return TakGnssAction(track = open)
        val key = lives.minOfOrNull { it.startedWall }?.takeIf { it > 0L }
            ?: open?.takeIf { it.open }?.episodeKey
            ?: now
        if (open != null && open.open && open.episodeKey != key) {
            val frozen = plot(open.startLat, open.startLon, open.endLat, open.endLon)
            return TakGnssAction(
                track = open.copy(open = false, lastSentAt = now, sentPin = frozen.pin),
                plot = frozen,
                remarks = remarks(notice, lives),
                staleMs = hold.staleMs(),
                send = true,
            )
        }
        val track = if (open == null || !open.open) {
            TakGnssTrack(
                uid = "FIELDWATCH-GNSS-$key",
                episodeKey = key,
                startLat = lat,
                startLon = lon,
                endLat = lat,
                endLon = lon,
                open = true,
            )
        } else {
            open.copy(endLat = lat, endLon = lon)
        }
        val drawn = plot(track.startLat, track.startLon, track.endLat, track.endLon)
        val switching = track.lastSentAt != 0L && track.sentPin && !drawn.pin
        val moved = Geo.meters(track.sentEndLat, track.sentEndLon, lat, lon) >= TakDefaults.MOVE_M
        val due = track.lastSentAt == 0L || now - track.lastSentAt >= TakDefaults.MIN_INTERVAL_MS
        val send = switching || moved || due
        val stored = if (send) {
            track.copy(
                lastSentAt = now,
                sentEndLat = lat,
                sentEndLon = lon,
                sentPin = drawn.pin,
            )
        } else {
            track
        }
        return TakGnssAction(
            track = stored,
            plot = if (send) drawn else null,
            remarks = remarks(notice, lives),
            staleMs = hold.staleMs(),
            send = send,
        )
    }
}

data class TakGnssPlot(
    val pin: Boolean,
    val lat: Double,
    val lon: Double,
    val semiMajorM: Double = 0.0,
    val semiMinorM: Double = 0.0,
    val angleDeg: Double = 0.0,
)

data class TakGnssTrack(
    val uid: String,
    val episodeKey: Long,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val open: Boolean,
    val lastSentAt: Long = 0L,
    val sentEndLat: Double = startLat,
    val sentEndLon: Double = startLon,
    val sentPin: Boolean = true,
)

data class TakGnssAction(
    val track: TakGnssTrack? = null,
    val plot: TakGnssPlot? = null,
    val remarks: String = "",
    val staleMs: Long = 0L,
    val send: Boolean = false,
    /** GNSS TAK turned off. Drop every mark this feed still knows. */
    val clearAll: Boolean = false,
)

enum class TakHeardHere {
    SKIP,
    MOVE,
    REFRESH,
}

data class TakMarker(
    val uid: String,
    val deviceKey: String,
    val lat: Double,
    val lon: Double,
    val advertised: Boolean,
    val pilot: Boolean,
    val device: Sighting,
)

/**
 * Who goes on the TAK/CoT feed and where the pin sits.
 * Pure so unit tests do not need Android.
 */
object TakPublish {
    fun selected(
        device: Sighting,
        settings: AppSettings,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
    ): Boolean {
        if (!settings.takEnabled) return false
        if (settings.demoMode) return false
        val extra = hasAttention(device, fleets)
        val payload = PayloadLocation.validCoord(device.payloadLat, device.payloadLon)
        val watched = onWatchlist(device, watchlist)
        val signed = device.fleetIds.isNotEmpty()
        if (extra && settings.takAttention) return true
        if (payload && settings.takPayloadFix) return true
        if (watched && settings.takWatchlist) return true
        if (signed && settings.takAllSignatures) return true
        return false
    }

    /**
     * Advertised decode lat/lon win. Otherwise this hear’s operator GPS,
     * and only while GPS tagging is on. GPS off + no payload = no pin.
     * Heard-here TAK holds the loudest of these (see [heardHereAction]).
     */
    fun pin(device: Sighting, settings: AppSettings): Pair<Double, Double>? {
        if (PayloadLocation.validCoord(device.payloadLat, device.payloadLon)) {
            return device.payloadLat!! to device.payloadLon!!
        }
        if (!settings.tagLocation) return null
        if (PayloadLocation.validCoord(device.latitude, device.longitude)) {
            return device.latitude!! to device.longitude!!
        }
        return null
    }

    fun pilotPin(device: Sighting): Pair<Double, Double>? {
        if (!PayloadLocation.validCoord(device.payloadOpLat, device.payloadOpLon)) return null
        return device.payloadOpLat!! to device.payloadOpLon!!
    }

    fun eligible(
        device: Sighting,
        settings: AppSettings,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
    ): Boolean = selected(device, settings, fleets, watchlist) && pin(device, settings) != null

    fun advertisedPin(device: Sighting): Boolean =
        PayloadLocation.validCoord(device.payloadLat, device.payloadLon)

    fun rank(
        device: Sighting,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
    ): Int {
        var n = 0
        if (hasAttention(device, fleets)) n += 8
        if (advertisedPin(device)) n += 4
        if (onWatchlist(device, watchlist)) n += 2
        if (device.fleetIds.isNotEmpty()) n += 1
        return n
    }

    fun shouldEmit(
        lastAt: Long?,
        lastLat: Double?,
        lastLon: Double?,
        now: Long,
        lat: Double,
        lon: Double,
        minIntervalMs: Long = TakDefaults.MIN_INTERVAL_MS,
        moveM: Double = TakDefaults.MOVE_M,
    ): Boolean {
        if (lastAt == null || lastLat == null || lastLon == null) return true
        if (now - lastAt >= minIntervalMs) return true
        return Geo.meters(lastLat, lastLon, lat, lon) >= moveM
    }

    /**
     * Heard-here pins sit on operator GPS. Move only when this hear is
     * louder than the last send (closer). Weaker hears still refresh the
     * same lat/lon after [minIntervalMs] so ATAK does not stale-drop.
     */
    fun heardHereAction(
        lastAt: Long?,
        lastRssi: Int?,
        now: Long,
        rssi: Int,
        minIntervalMs: Long = TakDefaults.MIN_INTERVAL_MS,
    ): TakHeardHere {
        if (lastAt == null || lastRssi == null || lastRssi == Int.MIN_VALUE) return TakHeardHere.MOVE
        if (rssi > lastRssi) return TakHeardHere.MOVE
        if (now - lastAt >= minIntervalMs) return TakHeardHere.REFRESH
        return TakHeardHere.SKIP
    }

    fun hasAttention(device: Sighting, fleets: List<Fleet>): Boolean {
        if (device.fleetIds.isEmpty()) return false
        val byId = fleets.associateBy { it.id }
        return device.fleetIds.any { id -> byId[id]?.attentionNote?.isNotBlank() == true }
    }

    fun onWatchlist(device: Sighting, watchlist: List<WatchTarget>): Boolean =
        watchlist.any { target ->
            when {
                target.deviceKey != null -> target.deviceKey == device.key && target.alert
                target.fleetId != null -> target.fleetId in device.fleetIds
                else -> false
            }
        }

    fun callsign(device: Sighting, fleets: List<Fleet>, watchlist: List<WatchTarget>): String {
        val advertised = advertisedPin(device)
        val base = callsignBase(device, fleets, watchlist, advertised)
        if (advertised) return base.take(32)
        val suffix = AppText.get(R.string.tak_callsign_here, " (here)")
        return (base.take((32 - suffix.length).coerceAtLeast(1)) + suffix).take(32)
    }

    fun pilotCallsign(device: Sighting): String {
        val label = OperatorLoc.label(device.payloadOpKind)
        val id = device.payloadUasId?.trim()?.takeIf { it.isNotEmpty() }
            ?: device.payloadSelfId?.trim()?.takeIf { it.isNotEmpty() }
        return if (id != null) "$label · ${id.take(20)}".take(32) else label.take(32)
    }

    fun markers(
        device: Sighting,
        settings: AppSettings,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
    ): List<TakMarker> {
        if (device.gone) return emptyList()
        if (!selected(device, settings, fleets, watchlist)) return emptyList()
        val aircraft = pin(device, settings) ?: return emptyList()
        val advertised = advertisedPin(device)
        val out = ArrayList<TakMarker>(2)
        out += TakMarker(
            uid = CotEvent.uid(device),
            deviceKey = device.key,
            lat = aircraft.first,
            lon = aircraft.second,
            advertised = advertised,
            pilot = false,
            device = device,
        )
        if (advertised) {
            val pilot = pilotPin(device)
            if (pilot != null) {
                out += TakMarker(
                    uid = CotEvent.pilotUid(device),
                    deviceKey = device.key,
                    lat = pilot.first,
                    lon = pilot.second,
                    advertised = true,
                    pilot = true,
                    device = device,
                )
            }
        }
        return out
    }

    /**
     * UIDs that must stay on ATAK this tick. Includes every eligible marker, not only
     * the 24 we send — missing the cap is not gone. Selected radios with no pin this
     * tick (GPS blip) keep whatever we already sent for that device key.
     */
    fun keepUids(
        devices: List<Sighting>,
        settings: AppSettings,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        previous: Collection<TakSent>,
        selfUid: String? = null,
        selfOk: Boolean = false,
    ): Set<String> {
        val byKey = previous.groupBy { it.deviceKey }
        val keep = LinkedHashSet<String>()
        if (selfOk && selfUid != null) keep += selfUid
        for (device in devices) {
            if (device.gone) continue
            if (!selected(device, settings, fleets, watchlist)) continue
            val next = markers(device, settings, fleets, watchlist)
            if (next.isNotEmpty()) {
                next.forEach { keep += it.uid }
            } else if (settings.tagLocation) {
                byKey[device.key]?.forEach { keep += it.uid }
            }
        }
        return keep
    }

    fun udpPreset(host: String, port: Int): TakUdpPreset {
        val h = host.trim()
        return when {
            h == TakDefaults.LOOPBACK && port == TakDefaults.PORT -> TakUdpPreset.THIS_PHONE
            (h == TakDefaults.SA_HOST || h == TakDefaults.HOST) && port == TakDefaults.SA_PORT ->
                TakUdpPreset.LAN_MULTICAST
            else -> TakUdpPreset.CUSTOM
        }
    }

    fun applyPreset(preset: TakUdpPreset): Pair<String, Int> = when (preset) {
        TakUdpPreset.THIS_PHONE -> TakDefaults.LOOPBACK to TakDefaults.PORT
        TakUdpPreset.LAN_MULTICAST -> TakDefaults.SA_HOST to TakDefaults.SA_PORT
        TakUdpPreset.CUSTOM -> TakDefaults.HOST to TakDefaults.PORT
    }

    private fun callsignBase(
        device: Sighting,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        advertised: Boolean,
    ): String {
        watchlist.firstOrNull { it.deviceKey == device.key }
            ?.label?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return it.take(32) }
        if (advertised) {
            device.payloadSelfId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(32) }
            device.payloadUasId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(32) }
        }
        val byId = fleets.associateBy { it.id }
        device.fleetIds.firstOrNull { id -> byId[id]?.attentionNote?.isNotBlank() == true }
            ?.let { id -> byId[id]?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(32) } }
        device.fleetIds.firstOrNull()
            ?.let { id -> byId[id]?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(32) } }
        val advertisedName = device.name.trim()
        if (advertisedName.isNotEmpty() && !advertisedName.equals(device.mac, ignoreCase = true)) {
            return advertisedName.take(32)
        }
        return device.mac.takeLast(8)
    }
}

object CotEvent {
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)
    private const val SELF_UID = "FIELDWATCH-SELF"

    fun uid(device: Sighting): String {
        stableId(device.payloadUasId)?.let { return "FIELDWATCH-RID-$it" }
        val mac = MacUtil.normalize(device.mac).replace(":", "")
        return "FIELDWATCH-${device.kind.name}-$mac"
    }

    fun pilotUid(device: Sighting): String {
        stableId(device.payloadUasId)?.let { return "FIELDWATCH-PILOT-$it" }
        val mac = MacUtil.normalize(device.mac).replace(":", "")
        return "FIELDWATCH-PILOT-${device.kind.name}-$mac"
    }

    fun selfUid(): String = SELF_UID

    fun stableId(raw: String?): String? {
        val trimmed = raw?.trim()?.trimEnd('\u0000') ?: return null
        val cleaned = buildString(trimmed.length) {
            for (ch in trimmed) {
                when {
                    ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' -> append(ch)
                    ch == ' ' -> append('-')
                }
            }
        }
        return cleaned.take(24).takeIf { it.length >= 4 }
    }

    fun type(device: Sighting, fleets: List<Fleet>, advertised: Boolean, pilot: Boolean = false): String {
        if (pilot) return "a-u-G"
        if (advertised && fleets.any { it.id in device.fleetIds && it.kind == SignatureClass.DRONE }) {
            return "a-u-A-M-H-Q"
        }
        return "a-u-G"
    }

    fun group(
        device: Sighting,
        fleets: List<Fleet>,
        advertised: Boolean,
        pilot: Boolean = false,
    ): String {
        if (pilot) return "Orange"
        if (advertised && fleets.any { it.id in device.fleetIds && it.kind == SignatureClass.DRONE }) {
            return "Yellow"
        }
        if (!advertised && TakPublish.hasAttention(device, fleets)) return "Maroon"
        return "Cyan"
    }

    fun face(
        device: Sighting,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        advertised: Boolean,
        pilot: Boolean = false,
    ): TakFace {
        val linkUid = when {
            pilot -> uid(device)
            advertised && TakPublish.pilotPin(device) != null -> pilotUid(device)
            else -> null
        }
        val linkType = when {
            linkUid == null -> null
            pilot -> type(device, fleets, advertised, pilot = false)
            else -> type(device, fleets, advertised, pilot = true)
        }
        return TakFace(
            callsign = if (pilot) TakPublish.pilotCallsign(device)
            else TakPublish.callsign(device, fleets, watchlist),
            remarks = remarks(device, fleets, advertised, pilot, watchlist),
            cotType = type(device, fleets, advertised, pilot),
            group = group(device, fleets, advertised, pilot),
            hae = if (pilot) OperatorLoc.hae(device.payloadOpAlt) else device.payloadAlt?.takeIf { it.isFinite() } ?: 9999999.0,
            course = if (advertised && !pilot) device.payloadHeading?.takeIf { it.isFinite() } else null,
            speed = if (advertised && !pilot) device.payloadSpeed?.takeIf { it.isFinite() && it >= 0 } else null,
            linkUid = linkUid,
            linkType = linkType,
        )
    }

    fun xml(
        device: Sighting,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        lat: Double,
        lon: Double,
        advertised: Boolean,
        now: Long,
        staleMs: Long = TakDefaults.STALE_MS,
        pilot: Boolean = false,
    ): String = render(
        uid = if (pilot) pilotUid(device) else uid(device),
        face = face(device, fleets, watchlist, advertised, pilot),
        lat = lat,
        lon = lon,
        now = now,
        staleMs = staleMs,
    )

    /** Same spot again. [staleMs] is how long ATAK keeps it from [now]. */
    fun render(
        uid: String,
        face: TakFace,
        lat: Double,
        lon: Double,
        now: Long,
        staleMs: Long,
        staleNow: Boolean = false,
    ): String = eventXml(
        uid = uid,
        cotType = face.cotType,
        lat = lat,
        lon = lon,
        hae = face.hae,
        course = face.course,
        speed = face.speed,
        callsign = xmlEscape(face.callsign),
        remarks = xmlEscape(face.remarks),
        group = face.group,
        now = now,
        staleMs = staleMs,
        staleNow = staleNow,
        linkUid = face.linkUid,
        linkType = face.linkType,
        role = face.role,
    )

    fun gnssPinXml(
        uid: String,
        lat: Double,
        lon: Double,
        now: Long,
        staleMs: Long,
        remarks: String,
    ): String = eventXml(
        uid = uid,
        cotType = "b-m-p-s-m",
        lat = lat,
        lon = lon,
        hae = 9999999.0,
        callsign = TakGnss.CALLOUT,
        remarks = xmlEscape(remarks),
        group = "Magenta",
        now = now,
        staleMs = staleMs,
        staleNow = false,
        linkUid = null,
        linkType = null,
        role = "Team Member",
    )

    fun gnssOvalXml(
        uid: String,
        lat: Double,
        lon: Double,
        semiMajorM: Double,
        semiMinorM: Double,
        angleDeg: Double,
        now: Long,
        staleMs: Long,
        remarks: String,
    ): String = eventXml(
        uid = uid,
        cotType = "u-d-c-e",
        lat = lat,
        lon = lon,
        hae = 9999999.0,
        callsign = TakGnss.CALLOUT,
        remarks = xmlEscape(remarks),
        group = "Magenta",
        now = now,
        staleMs = staleMs,
        staleNow = false,
        linkUid = null,
        linkType = null,
        role = "Team Member",
        extraDetail = buildString {
            append("<shape><ellipse major=\"${fmt(semiMajorM)}\" minor=\"${fmt(semiMinorM)}\" ")
            append("angle=\"${fmt(angleDeg)}\"/></shape>")
            append("<strokeColor value=\"-65281\"/>")
            append("<strokeWeight value=\"3.0\"/>")
            append("<fillColor value=\"1090453759\"/>")
            append("<labels_on value=\"true\"/>")
        },
    )

    fun tombstoneXml(
        uid: String,
        lat: Double,
        lon: Double,
        now: Long,
        callsign: String = "Fieldwatch",
        remarks: String = AppText.get(R.string.tak_callsign_gone, "Fieldwatch · gone"),
    ): String = eventXml(
        uid = uid,
        cotType = "a-u-G",
        lat = lat,
        lon = lon,
        hae = 9999999.0,
        callsign = xmlEscape(callsign),
        remarks = xmlEscape(remarks),
        group = "Cyan",
        now = now,
        staleMs = 0L,
        staleNow = true,
        linkUid = null,
        linkType = null,
    )

    fun selfXml(
        lat: Double,
        lon: Double,
        now: Long,
        staleMs: Long = TakDefaults.STALE_MS,
        remarksExtra: String? = null,
    ): String {
        val remarks = buildString {
            append(AppText.get(R.string.tak_heartbeat, "Fieldwatch TAK heartbeat (this phone)"))
            val extra = remarksExtra?.trim().orEmpty()
            if (extra.isNotEmpty()) {
                append('\n')
                append(extra)
            }
        }.take(TakDefaults.REMARKS_MAX)
        return eventXml(
            uid = SELF_UID,
            cotType = "a-f-G-U-C",
            lat = lat,
            lon = lon,
            hae = 9999999.0,
            callsign = "Fieldwatch",
            remarks = xmlEscape(remarks),
            group = "Cyan",
            now = now,
            staleMs = staleMs,
            staleNow = false,
            linkUid = null,
            linkType = null,
            role = "Team Member",
        )
    }

    fun remarks(
        device: Sighting,
        fleets: List<Fleet>,
        advertised: Boolean,
        pilot: Boolean = false,
        watchlist: List<WatchTarget> = emptyList(),
    ): String {
        val callsign = if (pilot) TakPublish.pilotCallsign(device)
            else TakPublish.callsign(device, fleets, watchlist)
        val names = fleets.filter { it.id in device.fleetIds }.map { it.name }.distinct()
        val kind = if (device.kind == RadioKind.WIFI) "Wi-Fi" else "BLE"
        val where = when {
            pilot -> OperatorLoc.where(device.payloadOpKind)
            advertised -> AppText.get(R.string.tak_advertised_position, "advertised position")
            else -> AppText.get(
                R.string.tak_heard_here,
                "heard here (operator GPS)",
            )
        }
        val radio = buildString {
            append(kind)
            append("  ")
            append(device.mac)
            append("  ")
            append(device.rssi)
            append(" dBm")
            if (device.kind == RadioKind.WIFI && device.channel > 0) {
                append("  ch ")
                append(device.channel)
            } else if (device.kind == RadioKind.WIFI && device.frequencyMhz > 0) {
                append("  ")
                append(device.frequencyMhz)
                append(" MHz")
            }
        }
        val advertisedName = device.name.trim()
        val showName = advertisedName.isNotEmpty() &&
            !advertisedName.equals(device.mac, ignoreCase = true) &&
            !callsign.contains(advertisedName, ignoreCase = true)
        val uas = device.payloadUasId?.trim()?.takeIf { it.isNotEmpty() }
        val sigLine = names.take(3).filter { name ->
            !callsign.contains(name, ignoreCase = true)
        }.joinToString(", ")
        val extra = device.attentionNotes(fleets).firstOrNull()
        return buildString {
            append(callsign)
            append('\n')
            append(radio)
            append('\n')
            append(where)
            if (uas != null) {
                append(" · UAS ID ")
                append(uas.take(24))
            }
            if (showName) {
                append('\n')
                append(advertisedName.take(32))
            }
            if (sigLine.isNotEmpty()) {
                append('\n')
                append(sigLine)
            }
            extra?.let { (name, note) ->
                append('\n')
                append(AppText.get(R.string.pdf_extra_attention, "Extra attention"))
                if (!callsign.contains(name, ignoreCase = true) &&
                    !sigLine.contains(name, ignoreCase = true)
                ) {
                    append(" (")
                    append(name)
                    append(")")
                }
                append(": ")
                append(note.take(120))
            }
        }.trimEnd().take(TakDefaults.REMARKS_MAX)
    }

    private fun eventXml(
        uid: String,
        cotType: String,
        lat: Double,
        lon: Double,
        hae: Double,
        course: Double? = null,
        speed: Double? = null,
        callsign: String,
        remarks: String,
        group: String,
        now: Long,
        staleMs: Long,
        staleNow: Boolean,
        linkUid: String?,
        linkType: String?,
        role: String = "Team Member",
        extraDetail: String = "",
    ): String {
        val t = TIME.format(Instant.ofEpochMilli(now))
        val stale = TIME.format(Instant.ofEpochMilli(if (staleNow) now else now + staleMs))
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<event version=\"2.0\" uid=\"${xmlEscape(uid)}\" type=\"$cotType\" ")
            append("time=\"$t\" start=\"$t\" stale=\"$stale\" how=\"m-g\">")
            append("<point lat=\"${fmt(lat)}\" lon=\"${fmt(lon)}\" hae=\"${fmt(hae)}\" ")
            append("ce=\"9999999\" le=\"9999999\"/>")
            append("<detail>")
            append("<contact callsign=\"$callsign\"/>")
            append("<__group name=\"$group\" role=\"$role\"/>")
            if (linkUid != null && linkType != null) {
                append("<link uid=\"${xmlEscape(linkUid)}\" type=\"$linkType\" relation=\"p-p\"/>")
            }
            if (course != null || speed != null) {
                val c = course ?: 0.0
                val s = speed ?: 0.0
                append("<track course=\"${fmt(c)}\" speed=\"${fmt(s)}\"/>")
            }
            append("<remarks>$remarks</remarks>")
            append(extraDetail)
            append("</detail>")
            append("</event>")
        }
    }

    private fun fmt(n: Double): String {
        if (!n.isFinite()) return "9999999"
        return "%.7f".format(Locale.US, n).trimEnd('0').trimEnd('.')
    }

    internal fun xmlEscape(raw: String): String = buildString(raw.length + 8) {
        for (ch in raw) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                '\n' -> append("&#10;")
                '\r' -> { }
                else -> if (ch.code >= 32) append(ch)
            }
        }
    }
}
