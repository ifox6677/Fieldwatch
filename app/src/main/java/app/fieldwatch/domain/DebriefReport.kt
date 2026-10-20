package app.fieldwatch.domain

import android.content.res.Resources
import app.fieldwatch.AppText
import app.fieldwatch.R

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ReportBar(
    val label: String,
    val value: Int,
    val detail: String = "",
    val second: Int? = null,
)

data class ReportChart(
    val rows: List<ReportBar>,
    val split: Boolean = false,
    val caption: String = "",
) {
    fun asText(res: Resources): String = buildString {
        if (caption.isNotBlank()) appendLine(caption)
        rows.forEach { row ->
            if (row.second == null) {
                append(row.label).append("  ").append(row.value)
                if (row.detail.isNotBlank()) append("  ").append(row.detail)
                appendLine()
            } else {
                append(row.label).append(": ")
                append(row.value).append(res.getString(R.string.dr_wi_fi))
                append(row.second).append(res.getString(R.string.dr_ble))
                appendLine()
            }
        }
    }.trimEnd()
}

data class DebriefSection(
    val number: String,
    val title: String,
    val body: String,
    val alert: Boolean = false,
    val chart: ReportChart? = null,
    val after: String = "",
)

data class DebriefPlaces(
    val attempted: Boolean,
    val available: Boolean,
    val note: String,
    val lines: List<String> = emptyList(),
    val namesByCell: Map<String, String> = emptyMap(),
) {
    /** Exact GPS cell, then nearest named cell within [maxM]. */
    fun nameNear(lat: Double, lon: Double, maxM: Double = 90.0): String? {
        namesByCell[Geo.cellKey(lat, lon)]?.let { return it }
        var best: String? = null
        var bestD = maxM
        for ((key, name) in namesByCell) {
            val parts = key.split(',')
            if (parts.size != 2) continue
            val klat = parts[0].toDoubleOrNull() ?: continue
            val klon = parts[1].toDoubleOrNull() ?: continue
            val d = Geo.meters(lat, lon, klat, klon)
            if (d < bestD) {
                bestD = d
                best = name
            }
        }
        return best
    }

    fun areaLine(): String {
        if (!attempted) return AppText.get(R.string.debrief_off, "off")
        val named = namesByCell.values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (named.isEmpty()) return note
        return named.joinToString(" · ")
    }

    companion object {
        val Off = DebriefPlaces(false, false, AppText.get(R.string.debrief_off, "off"))
    }
}

data class ExtraAttentionHit(
    val signature: String,
    val radioLabel: String,
    val note: String,
)

data class DebriefDoc(
    val res: Resources,
    val generatedUtc: String,
    val windowLine: String,
    val meta: List<Pair<String, String>>,
    val disclaimer: String,
    val trackingAlert: Boolean,
    val takeaway: String,
    val sections: List<DebriefSection>,
    val extraAttention: List<ExtraAttentionHit> = emptyList(),
    val heading: String = res.getString(R.string.dr_fieldwatch_field_debrief),
    val pdfKicker: String = res.getString(R.string.dr_field_debrief),
    val pdfTitle: String = res.getString(R.string.dr_field_debrief_4),
    val pathFigure: SitPathPlot.Figure? = null,
    val extraFigures: List<SitPathPlot.Figure> = emptyList(),
) {
    fun toPlainText(): String = buildString {
        appendLine(heading)
        appendLine()
        appendLine(res.getString(R.string.debrief_disclaimer))
        appendLine(disclaimer)
        appendLine()
        meta.forEach { (k, v) -> appendLine(res.getString(R.string.dr_text, k.padEnd(14), v)) }
        appendLine()
        sections.forEach { sec ->
            appendLine(res.getString(R.string.dr_text_6, sec.number, sec.title.uppercase()))
            val body = sec.body.trimEnd()
            if (body.isNotEmpty()) appendLine(body)
            sec.chart?.asText(res)?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            val after = sec.after.trimEnd()
            if (after.isNotEmpty()) appendLine(after)
            appendLine()
        }
        appendLine("—")
        appendLine(res.getString(R.string.dr_takeaway, takeaway))
    }

    fun withDemoMacs(
        macs: Collection<String>,
        demo: Boolean,
        uasIds: Collection<String> = emptyList(),
    ): DebriefDoc {
        if (!demo) return this
        fun t(s: String) = MacUtil.redactPrivateText(s, macs, uasIds, true)
        val note = res.getString(R.string.dr_mac_tails_uas_ids_and_gps_coordinates_masked_logs_on_the_phon)
        return copy(
            meta = listOf(res.getString(R.string.debrief_privacy) to note) + meta.map { it.first to t(it.second) },
            disclaimer = t(disclaimer),
            takeaway = t(takeaway),
            sections = sections.map {
                it.copy(
                    title = t(it.title),
                    body = t(it.body),
                    after = t(it.after),
                    chart = it.chart?.let { chart ->
                        chart.copy(
                            caption = t(chart.caption),
                            rows = chart.rows.map { row ->
                                row.copy(label = t(row.label), detail = t(row.detail))
                            },
                        )
                    },
                )
            },
            extraAttention = extraAttention.map {
                it.copy(signature = t(it.signature), radioLabel = t(it.radioLabel), note = t(it.note))
            },
            pathFigure = pathFigure?.withDemoText(::t),
            extraFigures = extraFigures.map { it.withDemoText(::t) },
        )
    }
}

/** Path-key and caption text. The drawn track keeps its coordinates. */
private fun SitPathPlot.Figure.withDemoText(t: (String) -> String): SitPathPlot.Figure = copy(
    caption = t(caption),
    craftKeys = craftKeys.map(t),
    tracks = tracks.map { track -> track.copy(name = t(track.name)) },
    pilots = pilots.map { mark -> mark.copy(label = t(mark.label)) },
    dots = dots.map { dot ->
        dot.copy(
            label = t(dot.label),
            mac = MacUtil.screenMac(dot.mac, true),
            fleetNames = dot.fleetNames.map(t),
            observerNotes = t(dot.observerNotes),
            advertisedNote = t(dot.advertisedNote),
        )
    },
    gnss = gnss.map { pin -> pin.copy(detail = t(pin.detail), confidence = t(pin.confidence)) },
)

/**
 * Standalone field debrief (not an AI prompt). Heuristic sit report from
 * the last 15 minutes plus GPS co-travel of tracker-like radios.
 */
class DebriefReport(private val res: Resources) {
    private val WINDOW_MS = 15 * 60_000L
    private val SHORT_MS = 5 * 60_000L
    private val MOVE_M = 45.0
    /** Possible-tail extra gates. Own-kit uses a louder, longer “still here” window. */
    private val COVER_FRAC = 0.5
    private val FADE_DB = 12
    private val TRAIL_LOUD_DBM = CoTravel.TRAIL_LOUD_DBM
    /** Own-kit “still here” — AirTags advertise slowly and rotate. */
    private val OWN_HERE_MS = 180_000L
    private val TAIL_HERE_MS = 20_000L
    private val ON_BODY_MAX = -55
    private val ON_BODY_MIN = -70

    fun build(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
        floods: List<FloodBurst> = emptyList(),
        gnss: List<GnssMark> = emptyList(),
    ): String = document(
        devices, fleets, settings, operatorPath, now, places, window,
        customNames, observerNotes, bookmarkedKeys, watchedFleetIds, mineKeys,
        floods, gnss,
    ).toPlainText()

    fun document(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
        floods: List<FloodBurst> = emptyList(),
        gnss: List<GnssMark> = emptyList(),
    ): DebriefDoc {
        val names = fleets.associate { it.id to it.name }
        val win = window ?: DebriefWindow(now - WINDOW_MS, now)
        val windowStart = win.startAt
        val windowEnd = win.endAt
        val aside = FloodBurst.keysOf(floods, windowStart, windowEnd)
        val inWin = devices
            .filter { (it.lastSeen >= windowStart || it.firstSeen >= windowStart) && it.key !in aside }
            .sortedByDescending { it.rssi }
        val wifi = inWin.filter { it.kind == RadioKind.WIFI }
        val ble = inWin.filter { it.kind == RadioKind.BLE }
        val named = inWin.filter { it.fleetIds.isNotEmpty() }
        val hidden = wifi.filter { it.hiddenSsid }
        val randomized = ble.count { it.randomized }
        val arrived = inWin.filter { it.firstSeen >= windowStart }
        val persistent = inWin.filter { dwellMs(it, windowStart, windowEnd) >= win.durationMs * 2 / 3 }
        val path = operatorPath.filter { it.at in windowStart..windowEnd }
        val gnssInWindow = gnss.filter { mark ->
            mark.at in windowStart..windowEnd || (mark.endedAt > 0L && mark.endedAt in windowStart..windowEnd)
        }
        val gnssPins = SitPathPlot.gnssPins(gnssInWindow, Geo.despikePath(path))
        val gnssOnPath = gnssPins.isNotEmpty()
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val trackers = inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.FINDER }
        val follow = followAssessments(trackers, names, path, windowStart, windowEnd, TrackerMatch.Kind.FINDER)
        val beaconFollow = followAssessments(
            inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.BEACON },
            names, path, windowStart, windowEnd, TrackerMatch.Kind.BEACON,
        )
        val wearableFollow = followAssessments(
            inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.WEARABLE },
            names, path, windowStart, windowEnd, TrackerMatch.Kind.WEARABLE,
        )
        val assessed = follow + beaconFollow + wearableFollow
        fun List<FollowHit>.open(): List<FollowHit> = filter { it.device.key !in mineKeys }
        val following = follow.filter { it.verdict == Verdict.FOLLOWING }.open()
        val withYou = follow.filter { it.verdict == Verdict.MOVED_WITH_YOU }.open()
        val ownLikely = follow.filter { it.verdict == Verdict.OWN_LIKELY }.open()
        val wholeSit = ownLikely + withYou
        val beaconsWithYou = stayedWithYou(beaconFollow).open()
        val wearablesWithYou = stayedWithYou(wearableFollow).open()
        val mineHeard = inWin.count { it.key in mineKeys }

        val showAll = settings.debriefShowAllRadios
        fun Sighting.listedWhenShort(): Boolean =
            key in customNames || key in mineKeys || key in bookmarkedKeys
        val byCh = wifi.groupBy { it.channel }.toSortedMap()
        val channelChart = if (byCh.isEmpty()) {
            null
        } else {
            ReportChart(
                rows = byCh.map { (ch, list) ->
                    val label = if (ch == 0) res.getString(R.string.debrief_unknown) else res.getString(R.string.dr_ch, ch)
                    ReportBar(label, list.size, detail = res.getString(R.string.dr_strongest_dbm, list.maxOf { it.rssi }))
                },
            )
        }
        val networks = res.getString(R.string.dr_heard_ap_s_hidden_ssid_sat_most_of_the_window, wifi.size, hidden.size, persistent.count { it.kind == RadioKind.WIFI })
        val networksAfter = if (!showAll) {
            wifiLines(
                wifi.filter { it.listedWhenShort() && it.fleetIds.isEmpty() },
                names, windowStart, now, customNames, fleets, mineKeys,
            )
        } else {
            buildString {
                appendLine(res.getString(R.string.dr_loudest_aps))
                wifi.take(12).forEach { d ->
                    appendLine(res.getString(R.string.dr_text_13, wifiLine(d, names, windowStart, now, customNames)))
                    if (d.key in mineKeys) appendLine(res.getString(R.string.dr_marked_mine))
                    d.attentionNotes(fleets).forEach { (sig, note) ->
                        appendLine(res.getString(R.string.dr_extra_attention, sig, note))
                    }
                }
                if (hidden.isNotEmpty()) {
                    appendLine(res.getString(R.string.dr_hidden_ssids))
                    hidden.forEach { appendLine(res.getString(R.string.dr_dbm_ch, it.mac, it.vendor ?: "", it.rssi, it.channel)) }
                }
            }.trimEnd()
        }
        val notable = ble.filter {
            inventoryKeep(it, settings, bookmarkedKeys) &&
                (it.fleetIds.isNotEmpty() || it.name.isNotBlank() || it.rssi >= -65 || it.manufacturerId != null)
        }.sortedByDescending { it.rssi }.take(20)
        val omittedRand = ble.count { !inventoryKeep(it, settings, bookmarkedKeys) }
        val bleBody = buildString {
            appendLine(res.getString(R.string.dr_heard_advertiser_s_with_randomized_addresses_signature_matche, ble.size, randomized, named.count { it.kind == RadioKind.BLE }))
            if (omittedRand > 0) {
                appendLine(res.getString(R.string.dr_unmatched_rotating_ble_omitted_from_lists_counts_include_them, omittedRand))
            }
        }.trimEnd()
        val bleAfter = if (showAll && notable.isNotEmpty()) {
            buildString {
                appendLine(res.getString(R.string.dr_notable_ble))
                notable.forEach { d ->
                    val guess = DeviceExplain.guess(res, d, d.fleetIds.map { names[it] ?: it })
                    appendLine(res.getString(R.string.dr_text_21, bleLine(d, names, windowStart, now, customNames), guess.headline))
                    if (d.key in mineKeys) appendLine(res.getString(R.string.dr_marked_mine))
                    d.attentionNotes(fleets).forEach { (sig, note) ->
                        appendLine(res.getString(R.string.dr_extra_attention, sig, note))
                    }
                    val decoded = SignatureFieldDecoder.decodeSighting(d, fleets)
                    if (decoded.isNotEmpty()) {
                        decoded.forEach { row ->
                            appendLine(res.getString(R.string.dr_text_22, row.label, row.display))
                            if (row.note.isNotBlank()) appendLine(res.getString(R.string.dr_text_23, row.note))
                        }
                    } else {
                        d.liveDecode.forEach { chip ->
                            append(res.getString(R.string.dr_text_24, chip.reportLabel()))
                            if (chip.note.isNotBlank()) append("  ").append(chip.note)
                            appendLine()
                        }
                    }
                }
            }.trimEnd()
        } else if (!showAll) {
            bleLines(
                ble.filter { it.listedWhenShort() && it.fleetIds.isEmpty() },
                names, windowStart, now, customNames, mineKeys,
            )
        } else {
            ""
        }
        val sigChart = signatureChart(named, names)
        val sigBody = if (named.isEmpty()) res.getString(R.string.dr_none_in_this_window) else ""
        val sigAfter = if (named.isEmpty()) {
            ""
        } else if (showAll) {
            buildString {
                named.groupBy { it.fleetIds.joinToString("+") { id -> names[id] ?: id } }
                    .toList().sortedByDescending { it.second.size }
                    .forEach { (sig, list) ->
                        appendLine(sig)
                        list.sortedByDescending { it.rssi }.take(8).forEach { d ->
                            append(res.getString(R.string.dr_dbm, d.reportName(customNames), d.mac, d.rssi))
                            if (d.key in mineKeys) append(res.getString(R.string.dr_marked_mine_27))
                            val labels = d.liveDecode.reportLabels()
                            if (labels.isNotEmpty()) append("  ").append(labels.joinToString(", "))
                            appendLine()
                        }
                        list.flatMap { it.attentionNotes(fleets) }.distinct().forEach { (name, note) ->
                            appendLine(res.getString(R.string.dr_extra_attention_28, name, note))
                        }
                    }
            }.trimEnd()
        } else {
            buildString {
                val marked = named.filter { it.listedWhenShort() }.sortedByDescending { it.rssi }
                if (marked.isNotEmpty()) appendLine(res.getString(R.string.dr_named_or_marked))
                marked.forEach { d ->
                    append(res.getString(R.string.dr_dbm, d.reportName(customNames), d.mac, d.rssi))
                    if (d.key in mineKeys) append(res.getString(R.string.dr_marked_mine_27))
                    val labels = d.liveDecode.reportLabels()
                    if (labels.isNotEmpty()) append("  ").append(labels.joinToString(", "))
                    appendLine()
                }
            }.trimEnd()
        }
        val persistBody = buildString {
            appendLine(res.getString(R.string.dr_sat_most_of_this_window, persistent.size))
            append(res.getString(R.string.dr_first_seen_in_this_window, arrived.size))
        }.trimEnd()
        val persistAfter = if (!showAll) {
            ""
        } else {
            buildString {
                persistent.filter { inventoryKeep(it, settings, bookmarkedKeys) }.take(15).forEach {
                    appendLine(res.getString(R.string.dr_dwell, it.reportName(customNames), it.mac, fmtDur(dwellMs(it, windowStart, now))))
                }
                if (persistent.isEmpty()) appendLine(res.getString(R.string.dr_none))
                appendLine(res.getString(R.string.dr_loudest_first_seen))
                arrived.filter { inventoryKeep(it, settings, bookmarkedKeys) }.sortedByDescending { it.rssi }.take(8).forEach {
                    appendLine(res.getString(R.string.dr_dbm, it.reportName(customNames), it.mac, it.rssi))
                }
            }.trimEnd()
        }
        val flags = anomalyLines(inWin, customNames, settings, bookmarkedKeys, showAll)
        val anomalyBody = if (flags.isEmpty()) {
            res.getString(R.string.dr_no_extra_flags_signature_hits_extra_attention_and_tracking_ca)
        } else flags.joinToString("\n") { res.getString(R.string.dr_text_37, it) }
        val attentionHits = inWin.flatMap { d ->
            d.attentionNotes(fleets).map { (sig, note) -> Triple(d, sig, note) }
        }
        val actionBody = actions(following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, settings, pathSpan)
            .joinToString("\n") { res.getString(R.string.dr_text_37, it) }

        val distanceLine = when {
            !settings.tagLocation -> res.getString(R.string.dr_gps_tagging_off_no_path)
            path.size < 2 -> res.getString(R.string.dr_gps_tagging_on_fewer_than_2_fixes_in_this_window)
            else -> res.getString(R.string.dr_traveled_along_path_span_fixes, fmtDist(pathLen), fmtDist(pathSpan), path.size)
        }
        val lookupLine = when {
            !places.attempted -> res.getString(R.string.debrief_off)
            places.namesByCell.isNotEmpty() -> places.areaLine()
            else -> places.note
        }
        val pictures = AircraftTrail.pictures(
            inWin.mapNotNull { d ->
                AircraftTrail.source(d, d.reportName(customNames))
            },
            path,
        )
        val aircraftBody = AircraftTrail.body(pictures)
        var n = 1
        fun next() = (n++).toString()
        val sections = buildList {
            add(DebriefSection(
                next(),
                res.getString(R.string.dr_executive_summary),
                execSummary(wifi, ble, named, hidden, randomized, pathSpan, pathLen, following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, settings, places, win, mineHeard) + craftSentence(pictures),
                chart = classChart(inWin, fleets),
            ))
            add(DebriefSection(next(), res.getString(R.string.dr_where_you_were), whereYouWere(settings, path, pathLen, pathSpan, inWin, names, places, windowEnd, customNames, bookmarkedKeys)))
            if (aircraftBody.isNotEmpty()) {
                add(DebriefSection(next(), res.getString(R.string.debrief_aircraft), aircraftBody))
            }
            observerNotesSection(inWin, customNames, observerNotes)?.let { body ->
                add(DebriefSection(next(), res.getString(R.string.dr_observer_notes), body))
            }
            markedMineSection(inWin, customNames, mineKeys, assessed)?.let { body ->
                add(DebriefSection(next(), res.getString(R.string.dr_marked_mine_45), body))
            }
            add(
                DebriefSection(
                    next(),
                    res.getString(R.string.dr_tracking_assessment),
                    trackingSection(settings, path, pathSpan, pathLen, following, wholeSit, beaconsWithYou, wearablesWithYou),
                ),
            )
            if (wholeSit.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        res.getString(R.string.dr_possible_trackers_with_you),
                        trackerCallout(
                            res.getString(R.string.dr_finder_tags_airtag_find_my_smarttag_tile_chipolo_pebblebee_an) +
                                res.getString(R.string.dr_these_radios_stayed_with_your_gps_path_for_this_sit) +
                                res.getString(R.string.dr_fieldwatch_cannot_tell_your_own_tag_or_phone_from_a_tracker_p) +
                                res.getString(R.string.dr_account_for_each_mac_not_a_finding_and_not_identity),
                            wholeSit,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            if (following.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        res.getString(R.string.dr_possible_tail),
                        trackerCallout(
                            res.getString(R.string.dr_finder_tags_that_were_not_heard_when_this_sit_started_then_st) +
                                res.getString(R.string.dr_that_can_mean_someone_started_following_you_their_phone_or_ta) +
                                res.getString(R.string.dr_not_a_finding_and_not_identity),
                            following,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            if (beaconsWithYou.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        res.getString(R.string.dr_retail_beacons_with_you),
                        trackerCallout(
                            res.getString(R.string.dr_ibeacon_minew_estimote_kontakt_io_atrius_cart_tag_radios_that) +
                                res.getString(R.string.dr_location_beacons_are_usually_fixtures_in_a_store_or_venue_the) +
                                res.getString(R.string.dr_if_one_did_account_for_it_a_cart_you_pushed_your_own_test_tag) +
                                res.getString(R.string.dr_not_the_same_as_a_find_my_tail_not_a_finding_and_not_identity),
                            beaconsWithYou,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            if (wearablesWithYou.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        res.getString(R.string.dr_wearables_with_you),
                        trackerCallout(
                            res.getString(R.string.dr_garmin_fitbit_oura_polar_radios_that_stayed_with_your_gps_pat) +
                                res.getString(R.string.dr_watches_and_rings_usually_move_with_the_person_wearing_them_o) +
                                res.getString(R.string.dr_they_are_not_typically_planted_trackers_account_for_each_mac_),
                            wearablesWithYou,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            add(DebriefSection(next(), res.getString(R.string.debrief_environment), environment(wifi, ble, randomized, persistent, pathSpan, pathLen)))
            add(DebriefSection(
                next(),
                res.getString(R.string.dr_networks_wi_fi_access_points),
                networks.trimEnd(),
                chart = channelChart,
                after = networksAfter.trimEnd(),
            ))
            add(DebriefSection(next(), res.getString(R.string.dr_bluetooth_le), bleBody.trimEnd(), after = bleAfter.trimEnd()))
            add(DebriefSection(next(), res.getString(R.string.dr_signature_hits), sigBody.trimEnd(), chart = sigChart, after = sigAfter.trimEnd()))
            add(DebriefSection(next(), res.getString(R.string.debrief_persistence), persistBody.trimEnd(), after = persistAfter.trimEnd()))
            if (attentionHits.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        res.getString(R.string.dr_extra_attention_68),
                        buildString {
                            appendLine(res.getString(R.string.dr_pattern_match_not_identity_not_a_skimmer_detector_not_a_safet))
                            attentionHits.forEach { (d, sig, note) ->
                                append(res.getString(R.string.dr_dbm_70, d.reportName(customNames), d.mac, d.rssi, sig))
                                if (d.key in mineKeys) append(res.getString(R.string.dr_marked_mine_27))
                                appendLine()
                                appendLine(res.getString(R.string.dr_text_72, note))
                            }
                        }.trimEnd(),
                        alert = true,
                    ),
                )
            }
            add(DebriefSection(next(), res.getString(R.string.debrief_anomalies), anomalyBody))
            floodBody(floods, windowStart, windowEnd)?.let { body ->
                add(DebriefSection(next(), res.getString(R.string.debrief_flood), body))
            }
            gnssBody(gnss, windowStart, windowEnd, onPath = gnssOnPath)?.let { body ->
                add(DebriefSection(next(), res.getString(R.string.debrief_gnss), body, alert = true))
            }
            add(DebriefSection(next(), res.getString(R.string.debrief_privacy), privacy(wifi, ble, randomized, hidden, settings, places, pictures.isNotEmpty())))
            add(DebriefSection(next(), res.getString(R.string.dr_recommended_actions), actionBody))
        }

        val windowLine = if (win.sitName != null) {
            res.getString(R.string.dr_sit_utc, win.sitName, utc(windowStart), utc(windowEnd))
        } else {
            res.getString(R.string.dr_last_15_minutes_utc, utc(windowStart), utc(windowEnd))
        }
        val heading = if (win.sitName != null) {
            res.getString(R.string.dr_fieldwatch_sit, win.sitName)
        } else {
            res.getString(R.string.dr_fieldwatch_field_debrief)
        }
        val meta = buildList {
            add(res.getString(R.string.debrief_generated) to res.getString(R.string.dr_utc, utc(now)))
            if (win.sitName != null) add(res.getString(R.string.debrief_sit) to win.sitName)
            add(res.getString(R.string.debrief_window) to windowLine)
            add(res.getString(R.string.debrief_radios) to res.getString(R.string.dr_text_78, inWin.size))
            add(res.getString(R.string.debrief_tool) to res.getString(R.string.dr_fieldwatch_app_fieldwatch_stock_android_receive_only_wi_fi_ap))
            add(res.getString(R.string.debrief_scan) to res.getString(R.string.dr_stale_s_brief_hold_s, settings.intensity.name.lowercase(), settings.staleSec, settings.decaySec))
            add(
                res.getString(R.string.dr_gps_tag) to if (settings.tagLocation) {
                    res.getString(R.string.debrief_on)
                } else {
                    res.getString(R.string.debrief_off)
                },
            )
            add(res.getString(R.string.debrief_distance) to distanceLine)
            add(res.getString(R.string.debrief_places) to lookupLine)
            add(
                res.getString(R.string.debrief_classification) to if (pictures.isNotEmpty()) {
                    res.getString(R.string.dr_operationally_sensitive_neighbor_ssids_macs_operator_gps_adve)
                } else {
                    res.getString(R.string.dr_operationally_sensitive_neighbor_ssids_macs_operator_gps)
                },
            )
        }
        return DebriefDoc(
            res = res,
            generatedUtc = utc(now),
            windowLine = windowLine,
            meta = meta,
            disclaimer = FieldwatchDisclaimer.report(res, win),
            trackingAlert = following.isNotEmpty() || ownLikely.isNotEmpty() || withYou.isNotEmpty(),
            takeaway = takeaway(following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, pathSpan, settings, named),
            sections = sections,
            extraAttention = attentionHits.map { (d, sig, note) ->
                ExtraAttentionHit(
                    signature = sig,
                    radioLabel = buildString {
                        append(res.getString(R.string.dr_dbm_84, d.reportName(customNames), d.mac, d.rssi))
                        if (d.key in mineKeys) append(res.getString(R.string.dr_marked_mine_27))
                    },
                    note = note,
                )
            },
            heading = heading,
            pdfKicker = if (win.sitName != null) {
                res.getString(R.string.debrief_sit_kicker)
            } else {
                res.getString(R.string.dr_field_debrief)
            },
            pdfTitle = if (win.sitName != null) res.getString(R.string.dr_sit, win.sitName) else res.getString(R.string.dr_field_debrief_4),
            pathFigure = AircraftTrail.applyWalk(
                pathFigure(
                    win.sitName ?: res.getString(R.string.dr_last_15_minutes), path, inWin, fleets,
                    customNames, observerNotes, bookmarkedKeys, watchedFleetIds, mineKeys,
                    gnssPins,
                ),
                pictures,
                secondary = false,
            ),
            extraFigures = AircraftTrail.ownFigures(pictures),
        )
    }

    private fun pathFigure(
        title: String,
        path: List<GpsSample>,
        devices: List<Sighting>,
        fleets: List<Fleet>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
        gnss: List<SitPathPlot.GnssPin> = emptyList(),
    ): SitPathPlot.Figure? {
        val path = Geo.despikePath(path)
        if (path.size < 2) return null
        val plot = SitPathPlot.dotsFrom(
            devices, fleets, namedKeys = customNames.keys,
            customNames = customNames, observerNotes = observerNotes,
            bookmarkedKeys = bookmarkedKeys,
            watchedFleetIds = watchedFleetIds,
            alertsOnly = true,
            mineKeys = mineKeys,
        )
        val caption = buildString {
            append(res.getString(R.string.dr_north_up_line_is_this_phone, path.lengthM()))
            append(res.getString(R.string.dr_a_mac_alert_or_a_signature_alert_is_drawn_once))
            append(res.getString(R.string.dr_a_decoded_latitude_and_longitude_is_the_last_advertised_posit))
            append(res.getString(R.string.dr_anything_else_is_the_strongest_hear_a_number_is_that_place_pa))
            if (gnss.isNotEmpty()) {
                append(res.getString(R.string.dr_a_red_diamond_is_where_this_phone_was_during_a_gnss_detection))
                append(res.getString(R.string.dr_the_path_key_has_the_time_and_what_changed))
                append(res.getString(R.string.dr_it_does_not_say_where_the_interference_came_from))
            }
        }
        return SitPathPlot.Figure(
            kicker = res.getString(R.string.dr_operator_path),
            tracks = listOf(SitPathPlot.FigureTrack(title, path)),
            dots = plot.points,
            lengthM = Geo.pathLengthM(path),
            spanM = Geo.spanM(path),
            caption = caption,
            gnss = gnss,
        )
    }

    private fun List<GpsSample>.lengthM(): String {
        val m = Geo.pathLengthM(this)
        return if (m >= 1000) res.getString(R.string.dr_km, "%.1f".format(java.util.Locale.US, m / 1000)) else res.getString(R.string.dr_m, m.toInt())
    }

    /**
     * GPS / places / co-travel block for the AI Export prompt. Same heuristics
     * as the field debrief; markdown so a chat model can cite it.
     */
    fun gpsAnalystMarkdown(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
    ): String = buildString {
        val names = fleets.associate { it.id to it.name }
        val win = window ?: DebriefWindow(now - WINDOW_MS, now)
        val windowStart = win.startAt
        val windowEnd = win.endAt
        val path = operatorPath.filter { it.at in windowStart..windowEnd }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val inWin = devices.filter { it.lastSeen >= windowStart || it.firstSeen >= windowStart }
        val trackers = inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.FINDER }
        val follow = followAssessments(trackers, names, path, windowStart, windowEnd, TrackerMatch.Kind.FINDER)
        val beaconsMd = stayedWithYou(
            followAssessments(
                inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.BEACON },
                names, path, windowStart, windowEnd, TrackerMatch.Kind.BEACON,
            ),
        )
        val wearablesMd = stayedWithYou(
            followAssessments(
                inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.WEARABLE },
                names, path, windowStart, windowEnd, TrackerMatch.Kind.WEARABLE,
            ),
        )

        appendLine(res.getString(R.string.dr_where_you_were_operator_gps))
        appendLine(
            res.getString(
                R.string.dr_tag_detections_with_gps,
                if (settings.tagLocation) res.getString(R.string.debrief_on)
                else res.getString(R.string.debrief_off),
            ),
        )
        appendLine(
            res.getString(R.string.dr_online_place_names) +
                if (places.attempted) places.note
                else res.getString(R.string.dr_off_settings_online_place_names_in_debrief_no_reverse_geocode),
        )
        append(whereYouWere(settings, path, pathLen, pathSpan, inWin, names, places, windowEnd, customNames, bookmarkedKeys).trimEnd())
        appendLine()
        appendLine()
        if (path.size < 2 || pathSpan < MOVE_M) {
            appendLine(res.getString(R.string.dr_following_test_insufficient_movement_need_45_m_span_do_not_in))
            appendLine()
        }
        appendLine(res.getString(R.string.dr_gps_co_travel))
        appendLine(
            res.getString(R.string.dr_only_radios_that_stayed_with_the_operator_path_are_listed) +
                res.getString(R.string.dr_house_tags_and_other_radios_the_operator_only_passed_are_omit) +
                res.getString(R.string.dr_not_identity_find_my_mac_rotation_will_not_stitch_a_tail_that) +
                res.getString(R.string.dr_possible_tail_extra_gates_walks_trail_covers_half_the_operato) +
                res.getString(R.string.dr_x2_3_of_gps_stamps_at_75_dbm_or_louder_last_stamp_not_12_db_b) +
                res.getString(R.string.dr_fail_any_one_omit_pass_by_not_a_tail) +
                res.getString(R.string.dr_finder_tags_airtag_smarttag_tile_chipolo_pebblebee_find_my_lo) +
                res.getString(R.string.dr_are_the_tracking_test_retail_beacons_and_wearables_that_co_tr) +
                res.getString(R.string.dr_they_do_not_typically_move_with_you_beacons_or_are_usually_ow),
        )
        fun List<FollowHit>.open(): List<FollowHit> = filter { it.device.key !in mineKeys }
        val followingMd = follow.filter { it.verdict == Verdict.FOLLOWING }.open()
        val wholeSitMd = follow.filter {
            it.verdict == Verdict.OWN_LIKELY || it.verdict == Verdict.MOVED_WITH_YOU
        }.open()
        val beaconsOpen = beaconsMd.open()
        val wearablesOpen = wearablesMd.open()
        if (followingMd.isEmpty() && wholeSitMd.isEmpty() && beaconsOpen.isEmpty() && wearablesOpen.isEmpty()) {
            appendLine(res.getString(R.string.dr_none_stayed_with_the_path))
        } else {
            fun dump(title: String, rows: List<FollowHit>) {
                if (rows.isEmpty()) return
                appendLine()
                appendLine(res.getString(R.string.dr_text_115, title))
                rows.forEach { h ->
                    val d = h.device
                    appendLine(
                        res.getString(R.string.dr_rssi_dbm, h.label, d.reportName(customNames), d.mac, d.rssi) +
                            res.getString(R.string.dr_min_max_trail_fixes_span_m, d.rssiMin, d.rssiMax, h.samples, h.spanM.toInt()),
                    )
                    appendLine(res.getString(R.string.dr_text_118, h.detail))
                }
            }
            dump(
                res.getString(R.string.dr_possible_trackers_with_you_finder_tags_whole_sit_yours_or_pla),
                wholeSitMd,
            )
            dump(
                res.getString(R.string.dr_possible_tail_finder_tags_first_heard_after_this_sit_started_),
                followingMd,
            )
            dump(
                res.getString(R.string.dr_retail_beacons_with_you_ibeacon_minew_estimote_kontakt_io_atr),
                beaconsOpen,
            )
            dump(
                res.getString(R.string.dr_wearables_with_you_garmin_fitbit_oura_polar_usually_own_kit_o),
                wearablesOpen,
            )
        }
    }

    private enum class Verdict { FOLLOWING, MOVED_WITH_YOU, OWN_LIKELY, STATIONARY, INSUFFICIENT }

    private data class FollowHit(
        val device: Sighting,
        val label: String,
        val verdict: Verdict,
        val detail: String,
        val spanM: Double,
        val samples: Int,
    )

    private fun stayedWithYou(hits: List<FollowHit>): List<FollowHit> =
        hits.filter {
            it.verdict == Verdict.FOLLOWING ||
                it.verdict == Verdict.MOVED_WITH_YOU ||
                it.verdict == Verdict.OWN_LIKELY
        }

    private fun followAssessments(
        trackers: List<Sighting>,
        names: Map<String, String>,
        operatorPath: List<GpsSample>,
        windowStart: Long,
        now: Long,
        kind: TrackerMatch.Kind,
    ): List<FollowHit> {
        val opSpan = Geo.spanM(operatorPath)
        val opLen = Geo.pathLengthM(operatorPath)
        return trackers.map { d ->
            val label = TrackerMatch.label(d, names)
            val trail = d.gpsTrail.filter { it.at >= windowStart }
            val span = Geo.spanM(trail)
            val trailLen = Geo.pathLengthM(trail)
            val presentAtStart = d.firstSeen <= windowStart + 15_000L
            val stillHere = now - d.lastSeen <= TAIL_HERE_MS
            val ownHere = now - d.lastSeen <= OWN_HERE_MS
            val onBody = d.rssiMax >= ON_BODY_MAX && d.rssiMin >= ON_BODY_MIN && trail.size >= 2
            val cover = opLen > 0.0 && trailLen >= COVER_FRAC * opLen
            val (verdict, detail) = when {
                operatorPath.size < 2 || opSpan < MOVE_M ->
                    Verdict.INSUFFICIENT to res.getString(R.string.dr_operator_gps_path_too_short_m_to_test_following, opSpan.toInt())
                trail.size < 2 ->
                    Verdict.INSUFFICIENT to res.getString(R.string.dr_heard_but_not_at_two_gps_points_cannot_test_co_travel)
                onBody && ownHere ->
                    Verdict.OWN_LIKELY to onBodyLine(kind, d, trail.size)
                cover && ownHere && d.rssiMax >= ON_BODY_MAX ->
                    Verdict.OWN_LIKELY to
                        res.getString(R.string.dr_heard_along_m_of_your_m_path_and_still_loud_dbm, trailLen.toInt(), opLen.toInt(), d.rssiMax) +
                        withYouNote(kind, d)
                span < MOVE_M * 0.6 ->
                    Verdict.STATIONARY to res.getString(R.string.dr_heard_near_one_place_m_span_while_you_moved_m_looks_stationar, span.toInt(), opSpan.toInt())
                presentAtStart && stillHere && d.rssiMax >= ON_BODY_MAX ->
                    Verdict.OWN_LIKELY to
                        res.getString(R.string.dr_moved_m_with_you_already_on_the_air_when_this_15_minute_windo, span.toInt(), d.rssi) +
                        withYouNote(kind, d)
                presentAtStart && stillHere ->
                    Verdict.MOVED_WITH_YOU to
                        res.getString(R.string.dr_gps_samples_span_m_along_your_path_fixes_already_on_the_air_w, span.toInt(), trail.size) +
                        withYouNote(kind, d)
                !presentAtStart && span >= MOVE_M && trail.size >= 3 ->
                    possibleTail(trail, span, opLen, kind, d)
                else ->
                    Verdict.STATIONARY to
                        res.getString(R.string.dr_heard_along_m_gps_stamps_but_did_not_stay_loud_on_you_neighbo, span.toInt(), trail.size)
            }
            FollowHit(d, label, verdict, detail, span, trail.size)
        }.sortedBy { it.verdict.ordinal }
    }

    private fun onBodyLine(kind: TrackerMatch.Kind, d: Sighting, stamps: Int): String {
        val loud = res.getString(R.string.dr_stayed_loud_with_you_the_whole_sit_to_dbm_gps_stamps, d.rssiMax, d.rssiMin, stamps)
        return loud + withYouNote(kind, d)
    }

    /**
     * Catalog sentence for a live decode, when the signature wrote one.
     * A label with no sentence is named only. No fleet id is special.
     */
    private fun liveDecodeSentence(device: Sighting): String? {
        val chips = device.liveDecode
        if (chips.isEmpty()) return null
        val notes = chips.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct()
        if (notes.isNotEmpty()) return notes.joinToString(" ")
        val labels = chips.reportLabels()
        if (labels.isEmpty()) return null
        return res.getString(R.string.dr_decoded, labels.joinToString(", "))
    }

    private fun withYouNote(kind: TrackerMatch.Kind, device: Sighting): String {
        val decoded = liveDecodeSentence(device)
        val base = when (kind) {
            TrackerMatch.Kind.FINDER ->
                res.getString(R.string.dr_with_you_the_whole_sit_yours_or_planted_before_you_started_ac)
            TrackerMatch.Kind.BEACON ->
                res.getString(R.string.dr_location_beacons_do_not_typically_move_with_you_account_for_i)
            TrackerMatch.Kind.WEARABLE ->
                res.getString(R.string.dr_typical_of_a_watch_or_ring_you_or_a_companion_are_wearing_not)
        }
        return when {
            decoded != null -> res.getString(R.string.dr_text_135, base, decoded)
            kind == TrackerMatch.Kind.FINDER ->
                res.getString(R.string.dr_find_my_iphone_addresses_rotate_this_mac_is_this_session, base)
            else -> base
        }
    }

    /**
     * Extra gates on possible tail only. A neighborhood radio heard on a sidewalk
     * arc, or that faded as you walked, is stationary — not a follower.
     * Bag/car tags still cover most of the path and stay loud.
     */
    private fun possibleTail(
        trail: List<GpsSample>,
        span: Double,
        opLen: Double,
        kind: TrackerMatch.Kind,
        device: Sighting,
    ): Pair<Verdict, String> {
        val trailLen = Geo.pathLengthM(trail)
        val peak = trail.maxOf { it.rssi }
        val last = trail.last().rssi
        val fade = peak - last
        val loudN = trail.count { it.rssi >= TRAIL_LOUD_DBM }
        val loudNeed = (trail.size * 2 + 2) / 3
        val coverNeed = opLen * COVER_FRAC
        val coverPct = if (opLen <= 0.0) 0 else ((trailLen / opLen) * 100.0).toInt()
        return when {
            fade >= FADE_DB ->
                Verdict.STATIONARY to
                    res.getString(R.string.dr_appeared_after_the_sit_started_but_last_gps_stamp_was_dbm_aft, last, peak, fade)
            loudN < loudNeed ->
                Verdict.STATIONARY to
                    res.getString(R.string.dr_appeared_after_the_sit_started_and_gps_span_was_m_but_only_st, span.toInt(), loudN, trail.size)
            trailLen < coverNeed ->
                Verdict.STATIONARY to
                    res.getString(R.string.dr_appeared_after_the_sit_started_but_was_only_heard_along_m_of_, trailLen.toInt(), opLen.toInt(), coverPct)
            else -> {
                val stats =
                    res.getString(R.string.dr_appeared_after_the_sit_started_then_stayed_loud_with_you_acro, span.toInt()) +
                        res.getString(R.string.dr_m_of_your_m_path, trailLen.toInt(), opLen.toInt(), coverPct) +
                        res.getString(R.string.dr_gps_stamps_75_dbm, loudN, trail.size)
                val note = when (kind) {
                    TrackerMatch.Kind.FINDER ->
                        res.getString(R.string.dr_treat_as_a_possible_tail_until_you_visually_account_for_it)
                    TrackerMatch.Kind.BEACON ->
                        res.getString(R.string.dr_unusual_for_a_retail_location_beacon_they_do_not_typically_mo)
                    TrackerMatch.Kind.WEARABLE ->
                        res.getString(R.string.dr_typical_of_a_watch_that_joined_the_sit_you_put_it_on_or_someo)
                }
                Verdict.FOLLOWING to stats + note
            }
        }.let { (verdict, text) ->
            val extra = liveDecodeSentence(device)
            verdict to if (extra == null) text else res.getString(R.string.dr_text_146, text, extra)
        }
    }

    private fun execSummary(
        wifi: List<Sighting>,
        ble: List<Sighting>,
        named: List<Sighting>,
        hidden: List<Sighting>,
        randomized: Int,
        pathSpan: Double,
        pathLen: Double,
        following: List<FollowHit>,
        withYou: List<FollowHit>,
        ownLikely: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        settings: AppSettings,
        places: DebriefPlaces,
        window: DebriefWindow,
        mineHeard: Int = 0,
    ): String = buildString {
        val whenPhrase = if (window.sitName != null) {
            res.getString(R.string.dr_in_sit, window.sitName)
        } else {
            res.getString(R.string.dr_in_the_last_15_minutes)
        }
        append(res.getString(R.string.dr_fieldwatch_heard_wi_fi_access_points_and_ble_advertisers, whenPhrase, wifi.size, ble.size))
        append(res.getString(R.string.dr_signature_matched, named.size))
        if (mineHeard > 0) append(res.getString(R.string.dr_marked_mine_151, mineHeard))
        append(res.getString(R.string.dr_hidden_ssids_randomized_ble, hidden.size, randomized))
        if (settings.tagLocation && pathLen > 0) {
            append(res.getString(R.string.dr_overall_distance_traveled_along_the_gps_path_straight_line_sp, fmtDist(pathLen), fmtDist(pathSpan)))
        }
        if (places.namesByCell.isNotEmpty()) {
            append(res.getString(R.string.dr_stops_area, places.areaLine()))
        } else if (places.attempted && settings.tagLocation) {
            append(res.getString(R.string.dr_text_155, places.note))
        }
        val wholeSit = ownLikely + withYou
        when {
            following.isNotEmpty() || wholeSit.isNotEmpty() -> {
                append(res.getString(R.string.dr_tracking_note))
                if (wholeSit.isNotEmpty()) {
                    append(res.getString(R.string.dr_finder_tag_s_with_you_the_whole_sit_your_kit_or_planted_befor, wholeSit.size))
                    append(wholeSit.joinToString { trackId(it) })
                    append(". ")
                }
                if (following.isNotEmpty()) {
                    append(res.getString(R.string.dr_possible_tail_s_first_heard_after_this_sit_started, following.size))
                    append(following.joinToString { trackId(it) })
                    append(". ")
                }
                append(res.getString(R.string.dr_account_for_every_mac_fieldwatch_cannot_tell_yours_from_a_pla))
            }
            !settings.tagLocation -> {
                append(res.getString(R.string.dr_gps_tagging_is_off_so_a_following_test_was_not_performed_enab))
            }
            pathSpan < MOVE_M -> {
                append(res.getString(R.string.dr_gps_displacement_was_only_m_too_short_to_test_whether_a_track, pathSpan.toInt()))
            }
            else -> append(res.getString(R.string.dr_no_finder_tag_clearly_stayed_with_the_gps_path_in_this_window))
        }
        if (beaconsWithYou.isNotEmpty()) {
            append(res.getString(R.string.dr_retail_beacon_s_also_stayed_with_the_path_unusual_fixtures_do))
            append(beaconsWithYou.joinToString { res.getString(R.string.dr_text_164, it.label, it.device.mac) })
            append(". ")
        }
        if (wearablesWithYou.isNotEmpty()) {
            append(res.getString(R.string.dr_wearable_s_stayed_with_the_path_usually_your_watch_ring_or_a_))
            append(wearablesWithYou.joinToString { res.getString(R.string.dr_text_164, it.label, it.device.mac) })
            append(".")
        }
    }

    private fun trackingSection(
        settings: AppSettings,
        path: List<GpsSample>,
        pathSpan: Double,
        pathLen: Double,
        following: List<FollowHit>,
        wholeSit: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
    ): String = buildString {
        if (!settings.tagLocation) {
            appendLine(res.getString(R.string.dr_gps_tagging_is_off_fieldwatch_cannot_test_whether_a_radio_mov))
            appendLine(res.getString(R.string.dr_turn_on_settings_tag_detections_with_gps_walk_or_drive_50_m_t))
            return@buildString
        }
        appendLine(res.getString(R.string.dr_overall_distance_traveled_along_the_gps_path_samples_straight, fmtDist(pathLen), path.size, fmtDist(pathSpan)))
        appendLine(res.getString(R.string.dr_co_travel_is_split_by_class_finder_tags_airtag_find_my_smartt))
        if (path.size < 2 || pathSpan < MOVE_M) {
            appendLine(res.getString(R.string.dr_insufficient_movement_to_distinguish_a_radio_that_stayed_with))
            return@buildString
        }
        if (following.isEmpty() && wholeSit.isEmpty() && beaconsWithYou.isEmpty() && wearablesWithYou.isEmpty()) {
            appendLine(res.getString(R.string.dr_no_finder_tag_retail_beacon_or_wearable_stayed_with_you_house))
        } else {
            appendLine(res.getString(R.string.dr_callouts_below_are_only_radios_that_stayed_with_the_path_radi))
        }
    }

    private fun markedMineSection(
        devices: List<Sighting>,
        customNames: Map<String, String>,
        mineKeys: Set<String>,
        assessed: List<FollowHit>,
    ): String? {
        val hits = devices.filter { it.key in mineKeys }
        if (hits.isEmpty()) return null
        return buildString {
            appendLine(res.getString(R.string.dr_radios_you_marked_mine_heard_in_this_window_still_listed_no_b))
            hits.sortedWith(
                compareByDescending<Sighting> { it.rssi }.thenBy { it.mac },
            ).forEach { d ->
                val kind = if (d.kind == RadioKind.WIFI) "WIFI" else "BLE"
                appendLine(res.getString(R.string.dr_dbm_175, kind, d.mac, d.reportName(customNames), d.rssi))
                val verdict = assessed.firstOrNull { it.device.key == d.key }?.verdict
                val line = when (verdict) {
                    Verdict.FOLLOWING ->
                        res.getString(R.string.dr_marked_mine_first_heard_after_the_sit_started_and_stayed_with)
                    Verdict.OWN_LIKELY, Verdict.MOVED_WITH_YOU ->
                        res.getString(R.string.dr_marked_mine_with_you_the_whole_sit)
                    else -> res.getString(R.string.dr_marked_mine_178)
                }
                appendLine(res.getString(R.string.dr_text_179, line))
            }
        }.trimEnd()
    }

    private fun observerNotesSection(
        devices: List<Sighting>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String>,
    ): String? {
        val hits = devices.mapNotNull { d ->
            val note = observerNotes[d.key]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            d to note
        }
        if (hits.isEmpty()) return null
        return buildString {
            appendLine(res.getString(R.string.dr_your_captions_on_radios_heard_in_this_window_same_kind_mac_as))
            hits.sortedWith(
                compareByDescending<Pair<Sighting, String>> { it.first.rssi }.thenBy { it.first.mac },
            ).forEach { (d, note) ->
                val kind = if (d.kind == RadioKind.WIFI) "WIFI" else "BLE"
                appendLine(res.getString(R.string.dr_dbm_181, kind, d.reportName(customNames), d.mac, d.rssi))
                appendLine(res.getString(R.string.dr_text_72, note))
            }
        }.trimEnd()
    }

    private fun trackerCallout(
        intro: String,
        rows: List<FollowHit>,
        customNames: Map<String, String> = emptyMap(),
    ): String = buildString {
        appendLine(intro)
        appendLine()
        rows.forEach { h ->
            val d = h.device
            appendLine(res.getString(R.string.dr_text_183, h.label))
            appendLine(res.getString(R.string.dr_rssi_dbm_min_max, d.reportName(customNames), d.mac, d.rssi, d.rssiMin, d.rssiMax))
            appendLine(res.getString(R.string.dr_text_185, h.detail))
        }
    }.trimEnd()

    private fun whereYouWere(
        settings: AppSettings,
        path: List<GpsSample>,
        pathLen: Double,
        pathSpan: Double,
        devices: List<Sighting>,
        names: Map<String, String>,
        places: DebriefPlaces,
        now: Long,
        customNames: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
    ): String = buildString {
        appendLine(res.getString(R.string.dr_phone_gps_at_hear_time_not_the_other_radio_s_location_and_not))
        if (!settings.tagLocation) {
            appendLine(res.getString(R.string.dr_gps_tagging_is_off_turn_on_settings_tag_detections_with_gps_t))
            return@buildString
        }
        if (path.isEmpty()) {
            appendLine(res.getString(R.string.dr_gps_tagging_is_on_but_this_window_has_no_fixes_yet))
            return@buildString
        }
        appendLine(res.getString(R.string.dr_overall_along_track_span_fixes, fmtDist(pathLen), fmtDist(pathSpan), path.size))
        if (places.attempted) {
            appendLine(places.note)
            appendLine(res.getString(R.string.dr_street_names_are_approximate_do_not_treat_a_street_as_the_loc))
        }
        val legs = Geo.legs(path, now = now)
        if (legs.isEmpty()) {
            appendLine(res.getString(R.string.dr_no_path_legs))
            return@buildString
        }
        val stopNames = legs.filter { it.stay }.mapNotNull { places.nameNear(it.lat, it.lon) }
        if (stopNames.isNotEmpty()) {
            appendLine(res.getString(R.string.dr_stops) + stopNames.joinToString(" → "))
        }
        var stayN = 0
        legs.forEachIndexed { i, leg ->
            if (leg.stay) {
                stayN++
                appendLine()
                appendLine(res.getString(R.string.dr_stay_utc, i + 1, clock(leg.startAt), clock(leg.endAt), fmtDur(leg.durationMs)))
                appendLine(res.getString(R.string.dr_text_194, placeAndGps(leg.lat, leg.lon, places)))
                val here = devices.filter { heardAt(it, leg) }
                val aps = here.count { it.kind == RadioKind.WIFI }
                val ble = here.count { it.kind == RadioKind.BLE }
                val sigs = here.flatMap { d -> d.fleetIds.map { names[it] ?: it } }.distinct()
                append(res.getString(R.string.dr_heard_here_ap_s_ble, aps, ble))
                if (sigs.isNotEmpty()) append(res.getString(R.string.dr_text_196, sigs.take(6).joinToString(", ")))
                appendLine()
                here.filter { inventoryKeep(it, settings, bookmarkedKeys) }.sortedByDescending { it.rssi }.take(4).forEach { d ->
                    appendLine(res.getString(R.string.dr_dbm_197, d.reportName(customNames), d.mac, d.rssi))
                }
                if (here.isEmpty()) appendLine(res.getString(R.string.dr_no_gps_stamped_radios_tied_to_this_stay_tagging_may_have_star))
            } else {
                appendLine()
                appendLine(
                    res.getString(R.string.dr_transit_utc, i + 1, clock(leg.startAt), clock(leg.endAt)) +
                        res.getString(R.string.dr_along_track, fmtDist(leg.pathM)),
                )
                appendLine(res.getString(R.string.dr_text_194, placeAndGps(leg.lat, leg.lon, places)))
                appendLine(res.getString(R.string.dr_text_202, placeAndGps(leg.endLat, leg.endLon, places)))
            }
        }
        val stays = legs.count { it.stay }
        if (stays == 1 && pathSpan < MOVE_M) {
            appendLine()
            appendLine(res.getString(R.string.dr_one_stay_you_did_not_move_far_enough_in_this_window_to_split_))
        }
    }

    private fun heardAt(device: Sighting, leg: Geo.PathLeg): Boolean {
        val nearM = 60.0
        val trail = device.gpsTrail.filter { it.at >= leg.startAt && it.at <= leg.endAt }
        if (trail.isNotEmpty()) {
            return trail.any { Geo.meters(it.lat, it.lon, leg.lat, leg.lon) <= nearM }
        }
        val lat = device.latitude ?: return false
        val lon = device.longitude ?: return false
        if (device.lastSeen < leg.startAt || device.firstSeen > leg.endAt) return false
        return Geo.meters(lat, lon, leg.lat, leg.lon) <= nearM
    }

    private fun environment(
        wifi: List<Sighting>,
        ble: List<Sighting>,
        randomized: Int,
        persistent: List<Sighting>,
        pathSpan: Double,
        pathLen: Double,
    ): String {
        val ap = wifi.size
        val persistAp = persistent.count { it.kind == RadioKind.WIFI }
        val guess = when {
            pathSpan > 200 && ap in 1..25 -> res.getString(R.string.dr_in_motion_walk_vehicle_through_mixed_rf)
            ap <= 4 && ble.size < 30 && persistAp >= 1 -> res.getString(R.string.dr_likely_a_dwelling_or_small_office_few_sitting_aps_limited_ble)
            ap >= 15 && randomized >= 40 -> res.getString(R.string.dr_dense_public_retail_street_many_aps_and_phone_like_randomized)
            ap >= 8 && persistAp >= 4 -> res.getString(R.string.dr_likely_a_building_with_standing_infrastructure_aps_plus_patro)
            else -> res.getString(R.string.dr_mixed_or_under_sampled_environment)
        }
        return res.getString(R.string.dr_aps_ble_persistent_aps_traveled_span, guess, ap, ble.size, persistAp, fmtDist(pathLen), fmtDist(pathSpan))
    }

    private fun wifiLine(
        d: Sighting,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(d.reportName(customNames)).append("  ").append(d.mac)
        d.vendor?.let { append("  ").append(it) }
        append("  ").append(d.rssi).append(res.getString(R.string.dr_dbm_210))
        if (d.channel != 0) append(res.getString(R.string.dr_ch_211)).append(d.channel)
        if (d.hiddenSsid) append(res.getString(R.string.dr_hidden))
        if (d.fleetIds.isNotEmpty()) append("  ").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        append(res.getString(R.string.dr_dwell_213)).append(fmtDur(dwellMs(d, from, now)))
    }

    private fun bleLine(
        d: Sighting,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(d.reportName(customNames)).append("  ").append(d.mac)
        if (d.randomized) append(res.getString(R.string.dr_rand))
        append("  ").append(d.rssi).append(res.getString(R.string.dr_dbm_210))
        if (d.fleetIds.isNotEmpty()) append("  ").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        append(res.getString(R.string.dr_dwell_213)).append(fmtDur(dwellMs(d, from, now)))
    }

    /** Unmatched rotating BLE stays in counts/export; inventories omit it unless Extra attention, named, bookmark, or payload. */
    private fun inventoryKeep(
        d: Sighting,
        settings: AppSettings,
        bookmarkedKeys: Set<String>,
    ): Boolean {
        if (settings.debriefShowUnmatchedRandomBle && settings.debriefShowAllRadios) return true
        if (d.kind != RadioKind.BLE) return true
        if (!d.randomized) return true
        if (d.fleetIds.isNotEmpty()) return true
        if (d.payloadLat != null && d.payloadLon != null) return true
        if (d.key in bookmarkedKeys) return true
        return false
    }

    private fun classChart(devices: List<Sighting>, fleets: List<Fleet>): ReportChart? {
        if (devices.isEmpty()) return null
        val byId = fleets.associate { it.id to it.kind }
        val counts = linkedMapOf<SignatureClass, Int>()
        var unmatched = 0
        var multi = false
        for (d in devices) {
            val classes = d.fleetIds.mapNotNull { byId[it] }.toSet()
            if (classes.isEmpty()) {
                unmatched++
            } else {
                if (classes.size > 1) multi = true
                classes.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            }
        }
        val rows = SignatureClass.entries.mapNotNull { kind ->
            val n = counts[kind] ?: return@mapNotNull null
            ReportBar(kind.label(res), n)
        }.toMutableList()
        if (unmatched > 0) rows += ReportBar(res.getString(R.string.class_unmatched), unmatched)
        if (rows.isEmpty()) return null
        return ReportChart(
            rows = rows,
            caption = if (multi) res.getString(R.string.dr_a_radio_in_two_classes_counts_in_each) else "",
        )
    }

    private fun signatureChart(named: List<Sighting>, names: Map<String, String>): ReportChart? {
        if (named.isEmpty()) return null
        val counts = linkedMapOf<String, Int>()
        for (d in named) {
            val sigs = d.fleetIds.map { names[it] ?: it }.filter { it.isNotBlank() }.distinct()
            val key = if (sigs.isEmpty()) res.getString(R.string.class_unmatched) else sigs.joinToString(" + ")
            counts[key] = (counts[key] ?: 0) + 1
        }
        val rows = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { ReportBar(it.key, it.value) }
        return ReportChart(rows = rows)
    }

    private fun wifiLines(
        rows: List<Sighting>,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String>,
        fleets: List<Fleet>,
        mineKeys: Set<String>,
    ): String {
        if (rows.isEmpty()) return ""
        return buildString {
            appendLine(res.getString(R.string.dr_named_or_marked))
            rows.sortedByDescending { it.rssi }.forEach { d ->
                appendLine(res.getString(R.string.dr_text_218, wifiLine(d, names, from, now, customNames)))
                if (d.key in mineKeys) appendLine(res.getString(R.string.dr_marked_mine))
                d.attentionNotes(fleets).forEach { (sig, note) ->
                    appendLine(res.getString(R.string.dr_extra_attention, sig, note))
                }
            }
        }.trimEnd()
    }

    private fun bleLines(
        rows: List<Sighting>,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String>,
        mineKeys: Set<String>,
    ): String {
        if (rows.isEmpty()) return ""
        return buildString {
            appendLine(res.getString(R.string.dr_named_or_marked))
            rows.sortedByDescending { it.rssi }.forEach { d ->
                appendLine(res.getString(R.string.dr_text_219, bleLine(d, names, from, now, customNames)))
                if (d.key in mineKeys) appendLine(res.getString(R.string.dr_marked_mine))
            }
        }.trimEnd()
    }

    private fun gnssBody(marks: List<GnssMark>, start: Long, end: Long, onPath: Boolean): String? {
        val rows = marks.filter { it.at in start..end || (it.endedAt > 0L && it.endedAt in start..end) }
            .sortedBy { it.at }
        if (rows.isEmpty()) return null
        return buildString {
            appendLine(GnssCopy.INTRO)
            appendLine()
            appendLine(GnssCopy.ROUTER)
            if (rows.any { it.kind == "spoofing" }) {
                appendLine()
                appendLine(GnssCopy.SPOOF_LIMIT)
            }
            appendLine()
            rows.forEach { appendLine(it.reportLine()) }
            if (onPath) {
                appendLine()
                appendLine(res.getString(R.string.dr_the_path_map_marks_where_this_phone_was_it_does_not_say_where))
            }
        }.trimEnd()
    }

    private fun floodBody(floods: List<FloodBurst>, start: Long, end: Long): String? {
        val rows = floods.filter { it.at in start..end }.sortedBy { it.at }
        if (rows.isEmpty()) return null
        return buildString {
            appendLine(FloodBurst.intro(rows))
            appendLine()
            rows.forEach { appendLine(it.reportLine(FloodBurst.clock(it.at))) }
        }.trimEnd()
    }

    private fun anomalyLines(
        devices: List<Sighting>,
        customNames: Map<String, String> = emptyMap(),
        settings: AppSettings,
        bookmarkedKeys: Set<String>,
        showAll: Boolean,
    ): List<String> {
        val out = ArrayList<String>()
        val pairing = devices.filter { d ->
            d.facts.serviceData.any { it.uuid.contains("FE2C", true) && it.dataHex.length == 6 }
        }
        if (pairing.isNotEmpty()) {
            out += if (showAll || pairing.size <= 8) {
                res.getString(R.string.dr_google_fast_pair_in_pairing_mode) +
                    pairing.joinToString { res.getString(R.string.dr_text_222, it.reportName(customNames), it.mac) }
            } else {
                res.getString(R.string.dr_google_fast_pair_in_pairing_mode_radios, pairing.size)
            }
        }
        val loudUnknown = devices.filter {
            it.rssi >= -50 && it.fleetIds.isEmpty() && it.name.isBlank() &&
                inventoryKeep(it, settings, bookmarkedKeys)
        }
        if (loudUnknown.isNotEmpty()) {
            out += res.getString(R.string.dr_very_strong_unnamed_radios_50_dbm) +
                loudUnknown.take(8).joinToString { res.getString(R.string.dr_dbm_225, it.mac, it.rssi) }
        }
        val rand = devices.count { it.kind == RadioKind.BLE && it.randomized }
        if (rand >= 20) {
            out += res.getString(R.string.dr_high_randomized_ble_typical_of_phones_not_a_tracking_finding, rand)
        }
        return out
    }

    private fun privacy(
        wifi: List<Sighting>,
        ble: List<Sighting>,
        randomized: Int,
        hidden: List<Sighting>,
        settings: AppSettings,
        places: DebriefPlaces,
        includeAircraft: Boolean,
    ): String = buildString {
        append(res.getString(R.string.dr_a_passive_observer_with_the_same_radios_would_see_named_hidde, wifi.size))
        append(res.getString(R.string.dr_and_ble_advertisers_randomized, ble.size, randomized))
        if (hidden.isNotEmpty()) append(res.getString(R.string.dr_hidden_ssids_still_beacon_and_identify_the_ap_by_bssid))
        if (settings.tagLocation) append(res.getString(R.string.dr_this_debrief_includes_operator_gps_samples_used_for_distance_))
        if (includeAircraft) {
            append(res.getString(R.string.dr_this_debrief_includes_advertised_aircraft_positions_from_radi))
        }
        if (places.attempted && places.available) {
            append(res.getString(R.string.dr_street_names_came_from_the_phone_s_system_geocoder_while_onli))
        }
        append(res.getString(R.string.dr_do_not_share_this_file_off_device_without_redaction))
    }

    private fun actions(
        following: List<FollowHit>,
        withYou: List<FollowHit>,
        ownLikely: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        settings: AppSettings,
        pathSpan: Double,
    ): List<String> = buildList {
        if (following.isNotEmpty()) {
            add(res.getString(R.string.dr_possible_tail_appeared_after_this_sit_started_pause_live_open, following.joinToString { trackId(it) }))
        }
        if (ownLikely.isNotEmpty() || withYou.isNotEmpty()) {
            add(
                res.getString(R.string.dr_possible_trackers_with_you_235, (ownLikely + withYou).joinToString { trackId(it) }) +
                    res.getString(R.string.dr_could_be_yours_or_planted_in_the_car_bag_on_you_before_you_st),
            )
        }
        if (beaconsWithYou.isNotEmpty()) {
            add(
                res.getString(R.string.dr_retail_beacons_with_you_unusual_fixtures_do_not_typically_mov) +
                    beaconsWithYou.joinToString { it.label + " " + it.device.mac } +
                    res.getString(R.string.dr_account_for_a_test_tag_or_badge_before_treating_it_as_a_follo),
            )
        }
        if (wearablesWithYou.isNotEmpty()) {
            add(
                res.getString(R.string.dr_wearables_with_you_usually_own_kit_or_a_companion) +
                    wearablesWithYou.joinToString { it.label + " " + it.device.mac } +
                    ".",
            )
        }
        if (!settings.tagLocation) add(res.getString(R.string.dr_enable_tag_detections_with_gps_and_walk_50_m_then_run_debrief))
        else if (pathSpan < MOVE_M) add(res.getString(R.string.dr_walk_farther_50_m_with_gps_tagging_on_then_re_run_debrief))
        add(res.getString(R.string.dr_use_live_pause_to_inspect_a_busy_list_watch_tracker_signature))
        add(res.getString(R.string.dr_station_side_wi_fi_probes_clients_still_needs_a_dedicated_sni))
    }

    private fun takeaway(
        following: List<FollowHit>,
        withYou: List<FollowHit>,
        ownLikely: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        pathSpan: Double,
        settings: AppSettings,
        named: List<Sighting>,
    ): String {
        val extra = buildString {
            if (beaconsWithYou.isNotEmpty()) {
                append(res.getString(R.string.dr_retail_beacon_s_also_with_the_path_unusual))
                append(beaconsWithYou.joinToString { it.label + " (" + it.device.mac + ")" })
                append(".")
            }
            if (wearablesWithYou.isNotEmpty()) {
                append(res.getString(R.string.dr_wearable_s_with_the_path_usually_own_kit))
                append(wearablesWithYou.joinToString { it.label + " (" + it.device.mac + ")" })
                append(".")
            }
        }
        val core = when {
            following.isNotEmpty() && (ownLikely.isNotEmpty() || withYou.isNotEmpty()) ->
                res.getString(R.string.dr_possible_tail_appeared_after_sit_started, following.joinToString { trackId(it) }) +
                    res.getString(R.string.dr_also_finder_tags_with_you_yours_or_planted_before_account_for, (ownLikely + withYou).joinToString { trackId(it) })
            following.isNotEmpty() ->
                res.getString(R.string.dr_possible_tail_appeared_after_this_sit_started_account_for_it_, following.joinToString { trackId(it) })
            !settings.tagLocation ->
                res.getString(R.string.dr_turn_on_gps_tagging_and_walk_before_you_can_test_whether_a_tr)
            pathSpan < MOVE_M ->
                res.getString(R.string.dr_not_enough_gps_movement_m_to_test_following_walk_and_re_run_d, pathSpan.toInt())
            ownLikely.isNotEmpty() || withYou.isNotEmpty() ->
                res.getString(R.string.dr_finder_tags_with_you_yours_or_planted_before_you_started_no_n, (ownLikely + withYou).joinToString { trackId(it) })
            beaconsWithYou.isNotEmpty() || wearablesWithYou.isNotEmpty() ->
                res.getString(R.string.dr_no_finder_tag_stayed_with_the_path)
            named.isEmpty() ->
                res.getString(R.string.dr_no_signature_hits_and_no_gps_co_travel_of_trackers_in_this_15)
            else ->
                res.getString(R.string.dr_no_finder_tag_retail_beacon_or_wearable_clearly_stayed_with_y)
        }
        return (core + extra).trim()
    }

    /** Label and MAC, plus the live-decode name when the signature asked for one. */
    private fun trackId(hit: FollowHit): String {
        val labels = hit.device.liveDecode.reportLabels()
        val id = res.getString(R.string.dr_text_255, hit.label, hit.device.mac)
        return if (labels.isEmpty()) id else res.getString(R.string.dr_text_256, id, labels.joinToString(", "))
    }

    private fun craftSentence(pictures: List<AircraftTrail.Picture>): String {
        if (pictures.isEmpty()) return ""
        val bits = pictures.take(3).joinToString { pic ->
            if (pic.status.isBlank()) pic.title else res.getString(R.string.dr_text_257, pic.title, pic.status)
        }
        val more = if (pictures.size > 3) res.getString(R.string.dr_and_more, pictures.size - 3) else ""
        return res.getString(R.string.dr_advertised_position, bits, more)
    }

    private fun dwellMs(d: Sighting, from: Long, to: Long): Long {
        var sum = 0L
        val spans = d.presence.ifEmpty { listOf(PresenceSpan(d.firstSeen, if (d.gone) d.lastSeen else null)) }
        for (span in spans) {
            val a = maxOf(span.start, from)
            val b = minOf(span.end ?: to, to)
            if (b > a) sum += b - a
        }
        return sum
    }

    private fun absDelta(a: Long, b: Long) = kotlin.math.abs(a - b)

    private fun utc(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun clock(ms: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun fmtDist(m: Double): String =
        if (m >= 1000.0) String.format(Locale.US, "%.2f km", m / 1000.0) else res.getString(R.string.dr_m, m.toInt())

    private fun fmtCoord(s: GpsSample): String =
        String.format(Locale.US, "%.5f, %.5f", s.lat, s.lon)

    private fun placeAndGps(lat: Double, lon: Double, places: DebriefPlaces): String {
        val gps = fmtCoord(GpsSample(0L, lat, lon))
        val name = places.nameNear(lat, lon)
        return if (!name.isNullOrBlank()) {
            res.getString(R.string.dr_operator_phone, name, gps)
        } else if (places.attempted) {
            res.getString(R.string.dr_operator_phone_no_street_name_this_export, gps)
        } else {
            res.getString(R.string.dr_operator_phone_264, gps)
        }
    }

    private fun fmtDur(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return if (m >= 60) res.getString(R.string.dr_h_m, m / 60, m % 60) else if (m > 0) res.getString(R.string.dr_m_s, m, r) else res.getString(R.string.dr_s, r)
    }
}
