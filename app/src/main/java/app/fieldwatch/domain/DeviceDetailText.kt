package app.fieldwatch.domain

import android.content.res.Resources
import app.fieldwatch.R

import app.fieldwatch.radio.BleAdParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-text dump of the device-detail screen. Same fields, no sparkline/presence art.
 * Not a legal identity.
 */
class DeviceDetailText(private val res: Resources) {
    fun build(
        device: Sighting,
        signatureNames: List<String>,
        now: Long = System.currentTimeMillis(),
        attentionNotes: List<Pair<String, String>> = emptyList(),
        signatureNotes: List<Pair<String, String>> = emptyList(),
        fleets: List<Fleet> = emptyList(),
        mine: Boolean = false,
    ): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val iso = SimpleDateFormat(res.getString(R.string.ddt_yyyy_mm_dd_hh_mm_ss_z), Locale.US)
        val facts = device.facts
        val title = device.listTitle(res, signatureNames)
        val guess = DeviceExplain.guess(res, device, signatureNames)
        val out = StringBuilder()

        fun line(label: String, value: String) {
            out.append(label).append(": ").append(value.trim()).append('\n')
        }
        fun section(title: String) {
            out.append('\n').append("## ").append(title).append('\n')
        }

        out.append(res.getString(R.string.ddt_fieldwatch_device_detail_n))
        out.append(iso.format(Date(now))).append('\n')
        out.append(
            res.getString(R.string.ddt_experimental_not_a_legal_identity_stock_android_radios_this_) +
                res.getString(R.string.ddt_exposed_not_a_guarantee_a_tracker_or_camera_is_present_n),
        )
        out.append('\n')
        out.append(title).append('\n')
        line(res.getString(R.string.ddt_line_mac), device.mac)
        if (device.name.isNotBlank()) line(res.getString(R.string.ddt_advertised_name), device.name)
        if (mine) line(res.getString(R.string.ddt_marked_mine), res.getString(R.string.ddt_no_beep_while_this_is_on_still_listed))

        out.append('\n')
        out.append(res.getString(R.string.ddt_what_this_looks_like)).append(guess.headline).append('\n')
        out.append(guess.because).append('\n')
        if (attentionNotes.isNotEmpty()) {
            section(res.getString(R.string.ddt_extra_attention))
            attentionNotes.forEach { (name, note) ->
                out.append(res.getString(R.string.ddt_extra_attention_9, name)).append(note.trim()).append('\n')
            }
            out.append(res.getString(R.string.ddt_pattern_match_not_identity_not_a_safety_finding_n))
        }
        if (signatureNotes.isNotEmpty()) {
            section(res.getString(R.string.ddt_section_notes))
            signatureNotes.forEach { (name, note) ->
                out.append(name).append(": ").append(note.trim()).append('\n')
            }
        }

        section(res.getString(R.string.ddt_section_identity))
        line(
            res.getString(R.string.ddt_line_radio),
            if (device.kind == RadioKind.WIFI) {
                res.getString(R.string.ddt_wi_fi_access_point_beaconing_a_network)
            } else {
                res.getString(R.string.ddt_bluetooth_low_energy_advertiser)
            },
        )
        line(res.getString(R.string.ddt_address), DeviceExplain.addressExplain(res, device))
        vendorLine(device)?.let { line(res.getString(R.string.ddt_who_made_it), it.replace('\n', ' ')) }
            ?: line(res.getString(R.string.ddt_oui_vendor_prefix), res.getString(R.string.ddt_no_ieee_match_randomized_addresses_usually_have_none, device.oui))
        if (device.hiddenSsid) {
            line(res.getString(R.string.ddt_network_name_ssid), res.getString(R.string.ddt_hidden_the_ap_is_beaconing_but_not_publishing_a_name))
        }

        section(res.getString(R.string.ddt_section_signal))
        if (device.gone) {
            line(res.getString(R.string.ddt_how_loud_here_rssi), res.getString(R.string.ddt_not_available))
            val last = Rssi.lastMeasured(device.rssi, device.rssiHistory)
            line(res.getString(R.string.ddt_last_heard), last?.let { res.getString(R.string.ddt_dbm, it) } ?: res.getString(R.string.ddt_not_available))
        } else {
            line(res.getString(R.string.ddt_how_loud_here_rssi), DeviceExplain.rssiExplain(res, device.rssi))
            out.append(res.getString(R.string.ddt_closer_to_0_dbm_is_louder_here_not_a_distance_n))
        }
        line(res.getString(R.string.ddt_heard_range_this_session), Rssi.sessionRange(device.rssiMin, device.rssiMax, device.rssiHistory))
        facts.txPowerDbm?.let {
            line(res.getString(R.string.ddt_claimed_transmit_power), res.getString(R.string.ddt_dbm_how_loud_it_says_it_transmits_not_a_distance, it))
        }
        if (device.channel != 0 || device.frequencyMhz != 0) {
            line(
                res.getString(R.string.ddt_channel_frequency),
                buildString {
                    if (device.channel != 0) append(res.getString(R.string.ddt_channel, device.channel))
                    if (device.frequencyMhz != 0) {
                        if (isNotEmpty()) append("  ·  ")
                        append(res.getString(R.string.ddt_mhz, device.frequencyMhz))
                    }
                    facts.channelWidth?.let { append(res.getString(R.string.ddt_wide, it)) }
                },
            )
        }
        facts.wifiStandard?.let { line(res.getString(R.string.ddt_wi_fi_generation), it) }
        if (facts.centerFreq0 != null || facts.centerFreq1 != null) {
            line(
                res.getString(R.string.ddt_center_frequencies),
                listOfNotNull(
                    facts.centerFreq0?.let { res.getString(R.string.ddt_mhz, it) },
                    facts.centerFreq1?.let { res.getString(R.string.ddt_mhz, it) },
                ).joinToString("  ·  "),
            )
        }
        val rssiTail = device.rssiHistory.filter { Rssi.measured(it.rssi) }.takeLast(24)
        if (rssiTail.isNotEmpty()) {
            line(
                res.getString(R.string.ddt_recent_rssi_oldest_newest),
                rssiTail.joinToString(", ") { it.rssi.toString() },
            )
        }

        if (device.kind == RadioKind.BLE) {
            section(res.getString(R.string.ddt_bluetooth_advertisement))
            facts.primaryPhy?.let {
                val phys = listOfNotNull(it, facts.secondaryPhy).distinct()
                line(res.getString(R.string.ddt_radio_phy), phys.joinToString(" / ") { phy -> DeviceExplain.phyExplain(res, phy) })
            }
            facts.connectable?.let {
                line(
                    res.getString(R.string.ddt_line_connectable),
                    if (it) res.getString(R.string.ddt_yes_a_phone_could_open_a_ble_connection)
                    else res.getString(R.string.ddt_no_broadcast_only_you_can_hear_it_not_join_it_from_this_scan),
                )
            }
            facts.advertisingIntervalMs?.let {
                line(res.getString(R.string.ddt_how_often_it_advertises),
                res.getString(R.string.ddt_advertise_interval_sentence, it))
            }
            facts.periodicIntervalMs?.let { line(res.getString(R.string.ddt_periodic_advertising), "%.0f ms".format(it)) }
            facts.advFlags?.let { flags ->
                line(res.getString(R.string.ddt_discoverability), DeviceExplain.flagsExplain(res, flags))
                line(res.getString(R.string.ddt_flags_raw), "0x%02X".format(flags))
            }
            facts.appearance?.let { value ->
                val name = RadioDb.appearance(value)
                line(
                    res.getString(R.string.ddt_what_it_says_it_is_appearance),
                    name ?: res.getString(R.string.ddt_unlisted_appearance, value),
                )
                line(res.getString(R.string.ddt_appearance_code), "0x%04X".format(value))
            }
            CodDecoder.decodeOrNull(facts.deviceClass)?.let { cod ->
                line(
                    res.getString(R.string.ddt_classic_bluetooth_class),
                    buildString {
                        append(cod.major)
                        if (cod.minor.isNotBlank()) append(" / ").append(cod.minor)
                        if (cod.services.isNotEmpty()) {
                            append(res.getString(R.string.ddt_also_offers))
                            append(cod.services.joinToString(", "))
                        }
                    },
                )
            }
        }

        if (device.kind == RadioKind.WIFI) {
            section(res.getString(R.string.ddt_wi_fi_access_point))
            facts.security?.let {
                line(res.getString(R.string.ddt_encryption_login), DeviceExplain.wifiSecurityExplain(res, it))
                if (it.isNotBlank()) line(res.getString(R.string.ddt_security_string), it)
            }
            facts.supportedRates?.let { line(res.getString(R.string.ddt_supported_rates), res.getString(R.string.ddt_mbps_required_basic_rate, it)) }
            facts.capabilities?.takeIf { it.isNotBlank() && it != facts.security }?.let {
                line(res.getString(R.string.ddt_capability_string), it)
            }
        }

        device.payloadAircraft?.trim()?.takeIf { it.isNotEmpty() }?.let {
            line(res.getString(R.string.ddt_line_aircraft), it)
        }

        if (fleets.isNotEmpty() && (device.kind == RadioKind.BLE || device.kind == RadioKind.WIFI)) {
            val decoded = SignatureFieldDecoder.decodeSighting(device, fleets)
            if (decoded.isNotEmpty()) {
                section(res.getString(R.string.ddt_decoded_fields))
                decoded.forEach { row ->
                    line(row.label, row.display)
                    if (row.note.isNotBlank()) line(res.getString(R.string.ddt_line_note), row.note)
                }
            }
        }

        if (device.serviceUuids.isNotEmpty()) {
            section(res.getString(R.string.ddt_services_it_offers))
            line(
                res.getString(R.string.ddt_service_ids),
                device.serviceUuids.joinToString("; ") { uuid ->
                    DeviceExplain.uuidGloss(res, uuid)?.let { res.getString(R.string.ddt_text, uuid, it) } ?: uuid
                },
            )
        }
        if (facts.serviceData.isNotEmpty()) {
            facts.serviceData.forEach { sd ->
                val named = RadioDb.serviceUuid(sd.uuid)?.let { res.getString(R.string.ddt_text_57, it) } ?: ""
                AdvPayloadDecoder.decodeService(res, sd).forEach { field -> line(field.label, field.value) }
                line(
                    res.getString(R.string.ddt_service_data, uuidShort(sd.uuid), named),
                    sd.dataHex.hexSpaced().ifBlank { res.getString(R.string.empty) },
                )
            }
        }

        val mfg = facts.mfgRecords.ifEmpty {
            device.manufacturerId?.let {
                listOf(MfgRecord(it, device.manufacturerDataHex))
            } ?: emptyList()
        }
        if (mfg.isNotEmpty()) {
            section(res.getString(R.string.ddt_maker_data_inside_the_ad))
            mfg.forEach { rec ->
                val company = RadioDb.company(rec.companyId) ?: res.getString(R.string.ddt_not_in_the_bluetooth_company_list)
                line(res.getString(R.string.ddt_bluetooth_company, rec.companyId), company)
                BleAdParser.mfgDecodedFields(res, rec).forEach { (k, v) -> line(k, v) }
                if (rec.dataHex.isNotBlank()) {
                    line(res.getString(R.string.ddt_raw_payload_bytes, rec.dataHex.length / 2), rec.dataHex.hexSpaced())
                }
            }
        }

        if (facts.vendorIes.isNotEmpty() || device.vendorIeOuis.isNotEmpty()) {
            section(res.getString(R.string.ddt_wi_fi_vendor_tags))
            val rows = facts.vendorIes.ifEmpty {
                device.vendorIeOuis.map { VendorIeRecord(it, -1, "") }
            }
            rows.forEach { ie ->
                val org = RadioDb.vendorForOui24(ie.oui)
                val type = if (ie.type >= 0) res.getString(R.string.ddt_type_d).format(ie.type) else ""
                line(
                    res.getString(R.string.ddt_vendor_oui, ie.oui, type),
                    buildString {
                        append(org ?: res.getString(R.string.ddt_unknown_ieee_oui))
                        append(res.getString(R.string.ddt_extra_ap_information_element_not_the_ssid))
                        if (ie.dataHex.isNotBlank()) {
                            append(" ")
                            append(ie.dataHex.hexSpaced())
                        }
                    },
                )
            }
        }

        section(res.getString(R.string.ddt_section_session))
        line(res.getString(R.string.ddt_first_seen), fmt.format(Date(device.firstSeen)))
        line(res.getString(R.string.ddt_last_seen), fmt.format(Date(device.lastSeen)))
        line(res.getString(R.string.ddt_line_hits), device.hitCount.toString())
        Geo.screenCoord(device.latitude, device.longitude, false)?.let {
            line(res.getString(R.string.ddt_last_fix), it)
            out.append(res.getString(R.string.ddt_last_fix_is_the_phone_s_gps_at_hear_time_not_a_fix_on_this_r))
        }
        if (device.fleetIds.isNotEmpty()) {
            line(res.getString(R.string.ddt_matched_signatures), signatureNames.joinToString("; ").ifBlank {
                device.fleetIds.joinToString("; ")
            })
        }
        if (device.rawHex.isNotBlank() && device.kind == RadioKind.BLE) {
            line(res.getString(R.string.ddt_raw_advertisement), device.rawHex.hexSpaced())
        }
        presenceLine(device, now, fmt)?.let { line(res.getString(R.string.ddt_presence_15_min), it) }
        return out.toString().trimEnd() + "\n"
    }

    private fun vendorLine(device: Sighting): String? {
        val parts = ArrayList<String>(3)
        device.vendor?.let {
            parts += res.getString(R.string.ddt_ieee_board_chip_vendor_this_is_who_owns_the_mac_prefix_not_a, it, device.oui)
        }
        val mfgId = device.facts.mfgRecords.firstOrNull()?.companyId ?: device.manufacturerId
        if (mfgId != null) {
            val company = RadioDb.company(mfgId)
            parts += res.getString(
            R.string.ddt_bluetooth_company_in_the_ad_0x_04x,
            company ?: res.getString(R.string.detail_company_unlisted),
            mfgId,
        )
        }
        return parts.joinToString(" ").ifBlank { null }
    }

    private fun uuidShort(uuid: String): String {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return if (hex.length >= 8 && hex.startsWith("0000")) hex.substring(4, 8) else uuid.take(8)
    }

    private fun presenceLine(device: Sighting, now: Long, fmt: SimpleDateFormat): String? {
        if (device.presence.isEmpty()) return null
        val from = now - 15 * 60 * 1000L
        val spans = device.presence.filter { (it.end ?: now) >= from }
        if (spans.isEmpty()) return null
        return spans.joinToString("; ") { span ->
            val start = fmt.format(Date(span.start.coerceAtLeast(from)))
            val end = span.end?.let { fmt.format(Date(it)) } ?: res.getString(R.string.ddt_now)
            res.getString(R.string.ddt_text_77, start, end)
        }
    }
}
