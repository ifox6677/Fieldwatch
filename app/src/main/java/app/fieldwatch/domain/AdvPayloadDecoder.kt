package app.fieldwatch.domain

import android.content.res.Resources
import app.fieldwatch.R

/**
 * Decode well-known BLE advertisement payloads: Apple Continuity / iBeacon,
 * Google Fast Pair, Eddystone, Microsoft CDP. After company ID / UUID the rest
 * is proprietary; only published or well-reverse-engineered layouts are named.
 */
object AdvPayloadDecoder {
    data class Field(val label: String, val value: String)

    data class RoleHint(
        val bucket: String,
        val label: String,
        val reason: String,
        val weight: Int,
    )

    fun decodeManufacturer(res: Resources, record: MfgRecord): List<Field> {
        val bytes = hexToBytes(record.dataHex) ?: return emptyList()
        return when (record.companyId) {
            0x004C -> decodeApple(res, bytes)
            0x0006 -> decodeMicrosoft(res, bytes)
            0x0157 -> decodeAltBeacon(bytes)
            0x00E0 -> listOf(Field(res.getString(R.string.apd_google_manufacturer_data), res.getString(R.string.apd_bytes, bytes.size)))
            else -> emptyList()
        }
    }

    fun decodeService(res: Resources, record: ServiceDataRecord): List<Field> {
        val bytes = hexToBytes(record.dataHex) ?: return emptyList()
        val short = uuid16(record.uuid) ?: return emptyList()
        return when (short) {
            0xFE2C -> decodeFastPair(res, bytes)
            0xFEAA -> decodeEddystone(res, bytes)
            else -> emptyList()
        }
    }

    fun roleHints(res: Resources, device: Sighting): List<RoleHint> {
        val out = ArrayList<RoleHint>(4)
        val mfg = device.facts.mfgRecords.ifEmpty {
            device.manufacturerId?.let { listOf(MfgRecord(it, device.manufacturerDataHex)) } ?: emptyList()
        }
        for (rec in mfg) {
            if (rec.companyId != 0x004C) continue
            val bytes = hexToBytes(rec.dataHex) ?: continue
            for (tlv in appleTlvs(bytes)) {
                when (tlv.type) {
                    0x02 -> if (tlv.data.size >= 20) {
                        val hex = tlv.data.toHexUpper()
                        val teslaPrefix = DefaultCatalog.TESLA_IBEACON_MFG_PREFIX
                        val targetPrefix = DefaultCatalog.TARGET_ATRIUS_IBEACON_MFG_PREFIX
                        if (hex.startsWith(teslaPrefix) || hex.startsWith(teslaPrefix.drop(4))) {
                            out += RoleHint(
                                "vehicle",
                                res.getString(R.string.de_a_tesla_vehicle_or_phone_as_key),
                                res.getString(R.string.de_tesla_phone_key_ibeacon_uuid_ios_background_find),
                                8,
                            )
                        } else if (hex.startsWith(targetPrefix) || hex.startsWith(targetPrefix.drop(4))) {
                            out += RoleHint(
                                "beacon",
                                res.getString(R.string.de_an_atrius_cart_tag),
                                res.getString(R.string.de_atrius_cart_tag_ibeacon_the_advertisement_does_not_name_the_store),
                                8,
                            )
                        } else {
                            out += RoleHint("beacon", res.getString(R.string.de_an_ibeacon),
                                res.getString(R.string.de_apple_ibeacon_payload), 7)
                        }
                    }
                    0x05 -> out += RoleHint("phone", res.getString(R.string.de_an_iphone_or_ipad_offering_airdrop),
                                res.getString(R.string.de_apple_airdrop_advertisement), 5)
                    0x07 -> {
                        val model = airPodsModel(tlv.data)
                        out += RoleHint(
                            "audio-personal",
                            model ?: res.getString(R.string.de_airpods_or_beats_headphones),
                            if (model != null) res.getString(R.string.de_apple_proximity_pairing_model, model)
                            else res.getString(R.string.de_apple_proximity_pairing_airpods_beats),
                            8,
                        )
                    }
                    0x08 -> out += RoleHint("siri", res.getString(R.string.de_an_apple_device_that_just_heard_hey_siri),
                                res.getString(R.string.de_hey_siri_advertisement), 6)
                    0x09 -> out += RoleHint("audio-speaker", res.getString(R.string.de_an_airplay_speaker_or_apple_tv),
                                res.getString(R.string.de_airplay_advertisement), 5)
                    0x0B -> out += RoleHint("phone", res.getString(R.string.de_an_apple_device_doing_handoff),
                                res.getString(R.string.de_handoff_advertisement), 4)
                    0x0C -> out += RoleHint("phone", res.getString(R.string.de_an_apple_device_looking_for_instant_hotspot),
                                res.getString(R.string.de_tethering_target_advertisement), 5)
                    0x0D, 0x0E -> out += RoleHint("hotspot", res.getString(R.string.de_an_iphone_ipad_offering_instant_hotspot),
                                res.getString(R.string.de_tethering_source_advertisement), 6)
                    0x0F -> out += RoleHint("phone", res.getString(R.string.de_an_apple_device_nearby_action),
                                nearbyActionReason(res, tlv.data), 4)
                    0x10 -> out += RoleHint("phone", res.getString(R.string.de_an_iphone_ipad_mac_nearby_info),
                                nearbyInfoReason(res, tlv.data), 5)
                    0x12 -> out += RoleHint(
                        "tag",
                        res.getString(R.string.de_a_find_my_network_radio),
                        res.getString(R.string.de_apple_offline_finding_airtag_find_my_accessory_or_an_apple_device_locating_itself),
                        4,
                    )
                }
            }
        }
        for (sd in device.facts.serviceData) {
            when (uuid16(sd.uuid)) {
                0xFE2C -> {
                    val bytes = hexToBytes(sd.dataHex) ?: continue
                    if (bytes.size == 3) {
                        val id = modelId24(bytes)
                        val name = FastPairModels.name(id)
                        out += RoleHint(
                            "audio-personal",
                            name ?: res.getString(R.string.de_a_fast_pair_accessory_often_earbuds_or_a_speaker),
                            if (name != null) res.getString(R.string.de_google_fast_pair_model_named_in_pairing_mode, name, id)
                            else res.getString(R.string.de_google_fast_pair_model_in_pairing_mode, id),
                            if (name != null) 8 else 6,
                        )
                    } else {
                        out += RoleHint(
                            "audio-personal",
                            res.getString(R.string.de_a_fast_pair_accessory_already_paired_to_someone),
                            res.getString(R.string.de_google_fast_pair_account_key_broadcast_not_in_pairing_mode),
                            4,
                        )
                    }
                }
                0xFEAA -> {
                    val frame = hexToBytes(sd.dataHex)?.firstOrNull()?.toInt()?.and(0xFF)
                    when (frame) {
                        0x40, 0x41 -> out += RoleHint(
                            "tag",
                            res.getString(R.string.de_a_google_find_hub_tag),
                            if (frame == 0x41) res.getString(R.string.de_find_hub_separated_unwanted_tracking_frame)
                            else res.getString(R.string.de_find_hub_nearby_frame),
                            8,
                        )
                        else -> out += RoleHint("beacon", res.getString(R.string.de_an_eddystone_beacon),
                                res.getString(R.string.de_eddystone_service_data), 6)
                    }
                }
            }
        }
        return out
    }

    private data class Tlv(val type: Int, val data: ByteArray)

    private fun decodeApple(res: Resources, bytes: ByteArray): List<Field> {
        val tlvs = appleTlvs(bytes)
        if (tlvs.isEmpty()) return listOf(
            Field(
                res.getString(R.string.apd_apple_payload),
                res.getString(R.string.apd_bytes_unparsed, bytes.size),
            ),
        )
        val out = ArrayList<Field>(8)
        for (tlv in tlvs) {
            out += Field(
                res.getString(R.string.apd_apple_continuity_type),
                res.getString(R.string.apd_apple_continuity_type_value, tlv.type, appleTypeName(res, tlv.type)),
            )
            out += when (tlv.type) {
                0x02 -> decodeIBeacon(res, tlv.data)
                0x05 -> decodeAirDrop(res, tlv.data)
                0x06 -> listOf(
                    Field("HomeKit", res.getString(R.string.apd_homekit_setup, tlv.data.size)),
                )
                0x07 -> decodeAirPods(res, tlv.data)
                0x08 -> decodeHeySiri(res, tlv.data)
                0x09 -> listOf(Field("AirPlay", res.getString(R.string.apd_airplay)))
                0x0A -> listOf(Field("Magic Switch", res.getString(R.string.apd_magic_switch)))
                0x0B -> decodeHandoff(res, tlv.data)
                0x0C -> decodeHandoffOrTetherTarget(res, tlv.data)
                0x0D, 0x0E -> decodeTetherSource(res, tlv.data)
                0x0F -> decodeNearbyAction(res, tlv.data)
                0x10 -> decodeNearbyInfo(res, tlv.data)
                0x12 -> decodeFindMy(res, tlv.data)
                else -> listOf(
                    Field(
                        res.getString(R.string.apd_payload),
                        res.getString(R.string.apd_bytes, tlv.data.size),
                    ),
                )
            }
        }
        return out
    }

    private fun appleTlvs(bytes: ByteArray): List<Tlv> {
        val out = ArrayList<Tlv>(3)
        var i = 0
        while (i + 2 <= bytes.size) {
            val type = bytes[i].toInt() and 0xFF
            val len = bytes[i + 1].toInt() and 0xFF
            if (len <= 0 || i + 2 + len > bytes.size) break
            out += Tlv(type, bytes.copyOfRange(i + 2, i + 2 + len))
            i += 2 + len
        }
        return out
    }

    private fun appleTypeName(res: Resources, type: Int): String = when (type) {
        0x02 -> "iBeacon"
        0x03 -> "AirPrint"
        0x05 -> "AirDrop"
        0x06 -> "HomeKit"
        0x07 -> "Proximity Pairing (AirPods / Beats)"
        0x08 -> "Hey Siri"
        0x09 -> "AirPlay"
        0x0A -> "Magic Switch (Watch)"
        0x0B -> "Handoff"
        0x0C -> "Handoff or Instant Hotspot (target)"
        0x0D -> "Instant Hotspot (source)"
        0x0E -> "Instant Hotspot (source)"
        0x0F -> "Nearby Action"
        0x10 -> "Nearby Info"
        0x12 -> "Find My / Offline Finding"
        0x13 -> "Nearby Action (extended)"
        0x16 -> "Nearby Info"
        else -> res.getString(R.string.apd_unlisted)
    }

    private fun decodeIBeacon(res: Resources, data: ByteArray): List<Field> {
        // TLV payload is length-byte already consumed; data is 0x15 + 21 bytes OR 21 bytes.
        val body = when {
            data.size >= 22 && data[0] == 0x15.toByte() -> data.copyOfRange(1, 22)
            data.size >= 21 -> data.copyOfRange(0, 21)
            else -> return listOf(Field("iBeacon", res.getString(R.string.apd_truncated, data.size)))
        }
        val uuid = uuidFromBe(body, 0)
        val major = u16be(body, 16)
        val minor = u16be(body, 18)
        val tx = body[20].toInt()
        val teslaKey = uuid.filter { it.isLetterOrDigit() }.equals(
            DefaultCatalog.TESLA_IBEACON_MFG_PREFIX.drop(4),
            ignoreCase = true,
        )
        return listOf(
            Field(
                "iBeacon UUID",
                if (teslaKey) res.getString(R.string.apd_tesla_key_note, uuid) else uuid,
            ),
            Field("iBeacon major / minor", "$major / $minor"),
            Field("iBeacon calibrated TX", res.getString(R.string.apd_ibeacon_tx, tx)),
        )
    }

    private fun decodeAirDrop(res: Resources, data: ByteArray): List<Field> {
        // 8 zeros, version, appleID hash(2), phone(2), email(2), email2(2), 0
        if (data.size < 18) return listOf(
            Field("AirDrop", res.getString(R.string.apd_airdrop_short, data.size)),
        )
        return listOf(
            Field("AirDrop", res.getString(R.string.apd_airdrop_receiving)),
            Field("Apple ID hash (2 bytes)", data.copyOfRange(9, 11).toHexUpper()),
        )
    }

    private fun decodeAirPods(res: Resources, data: ByteArray): List<Field> {
        // prefix 0x01, model u16be, status, batt nibble, charge+case, lid, color, 0x00, enc 16
        if (data.size < 5) return listOf(Field("AirPods", res.getString(R.string.apd_airpods_truncated)))
        val start = if (data[0] == 0x01.toByte()) 1 else 0
        if (data.size < start + 4) return listOf(Field("AirPods", res.getString(R.string.apd_airpods_pairing)))
        val model = ((data[start].toInt() and 0xFF) shl 8) or (data[start + 1].toInt() and 0xFF)
        val status = data[start + 2].toInt() and 0xFF
        val batt = data[start + 3].toInt() and 0xFF
        val left = batt and 0x0F
        val right = (batt shr 4) and 0x0F
        val out = ArrayList<Field>(6)
        out += Field(
            res.getString(R.string.apd_product),
            airPodsModelName(model) ?: res.getString(R.string.apd_apple_audio_model, model),
        )
        out += Field(res.getString(R.string.apd_pod_position), airPodsStatus(res, status))
        out += Field(
            res.getString(R.string.apd_battery_lr),
            "${nibblePct(res, left)} / ${nibblePct(res, right)}",
        )
        if (data.size > start + 4) {
            val ch = data[start + 4].toInt() and 0xFF
            val caseBatt = ch and 0x0F
            val charging = buildList {
                if (ch and 0x10 != 0) add(res.getString(R.string.apd_charge_case))
                if (ch and 0x20 != 0) add(res.getString(R.string.apd_charge_right))
                if (ch and 0x40 != 0) add(res.getString(R.string.apd_charge_left))
            }
            out += Field(res.getString(R.string.apd_case_battery), nibblePct(res, caseBatt))
            if (charging.isNotEmpty()) out += Field(res.getString(R.string.apd_charging), charging.joinToString(", "))
        }
        if (data.size > start + 6) {
            out += Field(res.getString(R.string.apd_color), airPodsColor(res, data[start + 6].toInt() and 0xFF))
        }
        return out
    }

    private fun airPodsModel(data: ByteArray): String? {
        if (data.size < 4) return null
        val start = if (data[0] == 0x01.toByte()) 1 else 0
        if (data.size < start + 2) return null
        val model = ((data[start].toInt() and 0xFF) shl 8) or (data[start + 1].toInt() and 0xFF)
        return airPodsModelName(model)
    }

    private fun airPodsModelName(id: Int): String? = when (id) {
        0x0220 -> "AirPods (1st generation)"
        0x0F20 -> "AirPods (2nd generation)"
        0x1320 -> "AirPods (3rd generation)"
        0x1920 -> "AirPods (4th generation)"
        0x1C20 -> "AirPods 4"
        0x0E20 -> "AirPods Pro"
        0x1420 -> "AirPods Pro (2nd generation)"
        0x2420 -> "AirPods Pro 2 (USB-C)"
        0x1F20 -> "AirPods Max"
        0x0A20 -> "Beats Solo3"
        0x0B20 -> "Powerbeats 3"
        0x0C20 -> "Beats Studio Buds"
        0x0D20 -> "Beats Fit Pro"
        0x1020 -> "Powerbeats Pro"
        0x1120 -> "Beats Studio Buds +"
        0x1220 -> "Beats Solo Pro"
        0x1720 -> "Beats Flex"
        0x1A20 -> "Beats Studio Pro"
        0x1B20 -> "Beats Fit Pro"
        0x0520 -> "BeatsX"
        0x0920 -> "Beats Studio³ Wireless"
        0x1620 -> "Beats Studio Buds +"
        0x2520 -> "Beats Solo 4"
        0x2620 -> "Beats Solo Buds"
        0x2D20 -> "AirPods Max 2"
        0x3820 -> "Beats 360"
        0x038F -> "Beats Studio Buds"
        else -> null
    }

    private fun airPodsStatus(res: Resources, status: Int): String = when (status) {
        0x01 -> res.getString(R.string.apd_airpods_status_1)
        0x02 -> res.getString(R.string.apd_airpods_status_2)
        0x03 -> res.getString(R.string.apd_airpods_status_3)
        0x05 -> res.getString(R.string.apd_airpods_status_5)
        0x09 -> res.getString(R.string.apd_airpods_status_9)
        0x0B -> res.getString(R.string.apd_airpods_status_b)
        0x11, 0x13 -> res.getString(R.string.apd_airpods_status_11)
        0x21 -> res.getString(R.string.apd_airpods_status_21)
        0x51 -> res.getString(R.string.apd_airpods_status_51)
        0x55 -> res.getString(R.string.apd_airpods_status_55)
        0x75 -> res.getString(R.string.apd_airpods_status_75)
        else -> res.getString(R.string.apd_airpods_status_hex, status)
    }

    private fun airPodsColor(res: Resources, v: Int): String = when (v) {
        0x00 -> res.getString(R.string.apd_color_white)
        0x01 -> res.getString(R.string.apd_color_black)
        0x02 -> res.getString(R.string.apd_color_red)
        0x03 -> res.getString(R.string.apd_color_blue)
        0x04 -> res.getString(R.string.apd_color_pink)
        0x05 -> res.getString(R.string.apd_color_gray)
        0x06 -> res.getString(R.string.apd_color_silver)
        0x07 -> res.getString(R.string.apd_color_gold)
        0x08 -> res.getString(R.string.apd_color_rose_gold)
        0x09 -> res.getString(R.string.apd_color_space_gray)
        0x0A -> res.getString(R.string.apd_color_dark_blue)
        0x0B -> res.getString(R.string.apd_color_light_blue)
        0x0C -> res.getString(R.string.apd_color_yellow)
        else -> "0x%02X".format(v)
    }

    private fun nibblePct(res: Resources, n: Int): String = when (n) {
        in 0..9 -> "${n * 10}%"
        10, 11, 12, 13, 14 -> "100%"
        15 -> res.getString(R.string.apd_unknown_presence)
        else -> "$n"
    }

    private fun decodeHeySiri(res: Resources, data: ByteArray): List<Field> {
        if (data.size < 6) return listOf(Field("Hey Siri", res.getString(R.string.apd_hey_siri_short)))
        val klass = u16be(data, 4)
        val device = when (klass) {
            0x0002 -> "iPhone"
            0x0003 -> "iPad"
            0x0007 -> "HomePod"
            0x0009 -> "Mac"
            0x000A -> "Watch"
            else -> res.getString(R.string.apd_class_hex, klass)
        }
        return listOf(
            Field("Hey Siri", res.getString(R.string.apd_hey_siri, device)),
        )
    }

    private fun decodeHandoff(res: Resources, data: ByteArray): List<Field> =
        listOf(Field("Handoff", res.getString(R.string.apd_handoff)))

    private fun decodeHandoffOrTetherTarget(res: Resources, data: ByteArray): List<Field> =
        if (data.size >= 14) decodeHandoff(res, data)
        else listOf(Field("Instant Hotspot (looking)", res.getString(R.string.apd_hotspot_looking)))

    private fun decodeTetherSource(res: Resources, data: ByteArray): List<Field> {
        if (data.size < 6) return listOf(Field("Instant Hotspot", res.getString(R.string.apd_hotspot_short)))
        val batt = data[2].toInt() and 0xFF
        val cell = if (data.size >= 5) u16be(data, 3) else -1
        val bars = if (data.size >= 6) data[5].toInt() and 0xFF else -1
        val cellName = when (cell) {
            0, 6 -> "4G"
            1 -> "1xRTT"
            2 -> "GPRS"
            3 -> "EDGE"
            4, 5 -> "3G"
            7 -> "LTE"
            8 -> "5G"
            else -> if (cell >= 0) res.getString(R.string.apd_cell_type, cell) else null
        }
        return listOf(
            Field(
                res.getString(R.string.apd_hotspot_offering),
                buildString {
                    append(res.getString(R.string.apd_hotspot_paired))
                    if (batt in 0..100) append(res.getString(R.string.apd_phone_battery, batt))
                    cellName?.let { append(" · $it") }
                    if (bars in 0..5) append(res.getString(R.string.apd_bars, bars))
                },
            ),
        )
    }

    private fun decodeNearbyAction(res: Resources, data: ByteArray): List<Field> {
        if (data.isEmpty()) return listOf(Field("Nearby Action", res.getString(R.string.apd_nearby_action_apple)))
        val action = if (data.size >= 2) data[1].toInt() and 0xFF else data[0].toInt() and 0xFF
        val name = nearbyActionName(res, action)
        return listOf(Field("Nearby Action", name))
    }

    private fun nearbyActionReason(res: Resources, data: ByteArray): String {
        val action = if (data.size >= 2) {
            data[1].toInt() and 0xFF
        } else {
            return res.getString(R.string.de_nearby_action_advertisement)
        }
        return res.getString(R.string.de_nearby_action_with_name, nearbyActionName(res, action))
    }

    private fun nearbyActionName(res: Resources, action: Int): String = when (action) {
        0x01 -> res.getString(R.string.de_apple_tv_setup)
        0x04 -> res.getString(R.string.de_mobile_backup)
        0x05 -> res.getString(R.string.de_watch_setup)
        0x06 -> res.getString(R.string.de_apple_tv_pair)
        0x08 -> res.getString(R.string.de_wifi_password_sharing_prompting_nearby_iphones)
        0x09 -> res.getString(R.string.de_ios_setup)
        0x0A -> res.getString(R.string.de_repair)
        0x0B -> res.getString(R.string.de_speaker_setup)
        0x0C -> res.getString(R.string.de_apple_pay)
        0x0D -> res.getString(R.string.de_whole_home_audio_setup)
        0x0F -> res.getString(R.string.de_answered_a_call)
        0x10 -> res.getString(R.string.de_ended_a_call)
        0x13 -> res.getString(R.string.de_remote_autofill)
        0x14 -> res.getString(R.string.de_companion_link_proximity)
        0x17 -> res.getString(R.string.de_remote_display)
        else -> res.getString(R.string.de_action_hex, action)
    }

    private fun decodeNearbyInfo(res: Resources, data: ByteArray): List<Field> {
        if (data.isEmpty()) return listOf(Field("Nearby Info", res.getString(R.string.apd_nearby_info_state)))
        val status = data[0].toInt() and 0xFF
        val action = status and 0x0F
        val flagsHi = (status shr 4) and 0x0F
        val dataFlags = if (data.size > 1) data[1].toInt() and 0xFF else 0
        val activity = when (action) {
            0x00 -> res.getString(R.string.apd_activity_unknown)
            0x01 -> res.getString(R.string.apd_activity_reporting_off)
            0x03 -> res.getString(R.string.apd_activity_idle)
            0x05 -> res.getString(R.string.apd_activity_audio_locked)
            0x07 -> res.getString(R.string.apd_activity_active)
            0x09 -> res.getString(R.string.apd_activity_video)
            0x0A -> res.getString(R.string.apd_activity_watch)
            0x0B -> res.getString(R.string.apd_activity_interaction)
            0x0D -> res.getString(R.string.apd_activity_driving)
            0x0E -> res.getString(R.string.apd_activity_call)
            else -> res.getString(R.string.apd_activity_hex, action)
        }
        val extras = buildList {
            if (flagsHi and 0x1 != 0) add(res.getString(R.string.apd_extra_icloud))
            if (flagsHi and 0x4 != 0) add(res.getString(R.string.apd_extra_airdrop))
            if (dataFlags and 0x04 != 0) add(res.getString(R.string.apd_extra_wifi))
            if (dataFlags and 0x01 != 0) add(res.getString(R.string.apd_extra_airpods))
            if (dataFlags and 0x20 != 0) add(res.getString(R.string.apd_extra_watch_locked))
        }
        return listOf(
            Field(
                res.getString(R.string.apd_what_doing),
                buildString {
                    append(activity.replaceFirstChar { it.uppercase() })
                    val end = res.getString(R.string.apd_sentence_end)
                    if (extras.isNotEmpty()) {
                        append(end).append(' ')
                        append(extras.joinToString(res.getString(R.string.apd_list_sep)))
                    }
                    append(end)
                },
            ),
        )
    }

    private fun nearbyInfoReason(res: Resources, data: ByteArray): String {
        if (data.isEmpty()) return res.getString(R.string.de_nearby_info_advertisement)
        val action = data[0].toInt() and 0x0F
        return when (action) {
            0x03 -> res.getString(R.string.de_phone_is_idle_locked)
            0x05 -> res.getString(R.string.de_audio_playing_with_the_screen_locked)
            0x07 -> res.getString(R.string.de_screen_is_on_someone_is_using_it)
            0x0D -> res.getString(R.string.de_device_reports_the_user_is_driving)
            0x0E -> res.getString(R.string.de_in_a_phone_or_facetime_call)
            else -> res.getString(R.string.de_nearby_info_advertisement)
        }
    }

    private fun decodeFindMy(res: Resources, data: ByteArray): List<Field> {
        if (data.isEmpty()) return listOf(Field("Find My", res.getString(R.string.apd_findmy_ad)))
        val status = data[0].toInt() and 0xFF
        val maintained = status and 0x04 != 0
        val batt = (status shr 6) and 0x3
        val battName = when (batt) {
            0 -> res.getString(R.string.apd_batt_full)
            1 -> res.getString(R.string.apd_batt_medium)
            2 -> res.getString(R.string.apd_batt_low)
            else -> res.getString(R.string.apd_batt_critical)
        }
        val keyLen = (data.size - 1).coerceAtLeast(0)
        return listOf(
            Field(
                "Find My / Offline Finding",
                buildString {
                    append(res.getString(R.string.apd_findmy_intro))
                    append(res.getString(R.string.apd_findmy_used_by))
                    if (maintained) append(res.getString(R.string.apd_findmy_owner_seen))
                    else append(res.getString(R.string.apd_findmy_owner_not_seen))
                    if (maintained || batt in 0..3) append(res.getString(R.string.apd_findmy_battery, battName))
                    append(res.getString(R.string.apd_findmy_key, keyLen))
                },
            ),
        )
    }

    private fun decodeFastPair(res: Resources, bytes: ByteArray): List<Field> {
        if (bytes.size == 3) {
            val id = modelId24(bytes)
            val name = FastPairModels.name(id)
            return listOf(
                Field("Google Fast Pair", res.getString(R.string.apd_fastpair_pairing)),
                Field(
                    res.getString(R.string.apd_model_id),
                    if (name != null) "$name  (0x%06X)".format(id)
                    else res.getString(R.string.apd_fastpair_unknown_model, id),
                ),
            )
        }
        if (bytes.isEmpty()) return emptyList()
        val verFlags = bytes[0].toInt() and 0xFF
        val version = (verFlags shr 4) and 0x0F
        val ui = if (bytes.size > 1) {
            val lt = bytes[1].toInt() and 0xFF
            val type = lt and 0x0F
            when (type) {
                0x0 -> res.getString(R.string.apd_fastpair_show_card)
                0x2 -> res.getString(R.string.apd_fastpair_hide_card)
                else -> res.getString(R.string.apd_fastpair_filter_type, type)
            }
        } else res.getString(R.string.apd_fastpair_account_key)
        return listOf(
            Field(
                "Google Fast Pair",
                res.getString(R.string.apd_fastpair_paired, ui, version),
            ),
        )
    }

    private fun decodeEddystone(res: Resources, bytes: ByteArray): List<Field> {
        if (bytes.isEmpty()) return emptyList()
        return when (bytes[0].toInt() and 0xFF) {
            0x00 -> {
                if (bytes.size < 18) listOf(Field("Eddystone-UID", res.getString(R.string.apd_truncated_short)))
                else listOf(
                    Field("Eddystone-UID namespace", bytes.copyOfRange(2, 12).toHexUpper()),
                    Field("Eddystone-UID instance", bytes.copyOfRange(12, 18).toHexUpper()),
                )
            }
            0x10 -> listOf(
                Field("Eddystone-URL", eddystoneUrl(bytes) ?: res.getString(R.string.apd_bytes, bytes.size)),
            )
            0x20 -> listOf(Field("Eddystone-TLM", res.getString(R.string.apd_eddystone_tlm)))
            0x30 -> listOf(Field("Eddystone-EID", res.getString(R.string.apd_eddystone_eid)))
            0x40, 0x41 -> {
                val mode = if (bytes[0].toInt() and 0xFF == 0x41) {
                    res.getString(R.string.apd_findhub_separated)
                } else {
                    res.getString(R.string.apd_findhub_nearby)
                }
                val eidLen = when {
                    bytes.size >= 33 -> 32
                    bytes.size >= 21 -> 20
                    else -> (bytes.size - 1).coerceAtLeast(0)
                }
                val eid = if (eidLen > 0) bytes.copyOfRange(1, 1 + eidLen).toHexUpper() else ""
                listOf(
                    Field("Find Hub", mode),
                    Field("Find Hub EID", eid.ifBlank { res.getString(R.string.apd_bytes, bytes.size) }),
                )
            }
            else -> listOf(Field("Eddystone", res.getString(R.string.apd_frame_hex, bytes[0].toInt() and 0xFF)))
        }
    }

    private fun eddystoneUrl(bytes: ByteArray): String? {
        if (bytes.size < 3) return null
        val scheme = when (bytes[2].toInt() and 0xFF) {
            0 -> "http://www."
            1 -> "https://www."
            2 -> "http://"
            3 -> "https://"
            else -> return null
        }
        val expansions = arrayOf(
            ".com/", ".org/", ".edu/", ".net/", ".info/", ".biz/", ".gov/",
            ".com", ".org", ".edu", ".net", ".info", ".biz", ".gov",
        )
        val sb = StringBuilder(scheme)
        for (i in 3 until bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b < expansions.size) sb.append(expansions[b]) else if (b in 0x20..0x7E) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun decodeMicrosoft(res: Resources, bytes: ByteArray): List<Field> {
        if (bytes.isEmpty()) return emptyList()
        if (bytes[0] == 0x01.toByte() && bytes.size >= 2) {
            val type = bytes[1].toInt() and 0x1F
            val kind = when (type) {
                1 -> "Xbox"
                6 -> "iPhone"
                7 -> "iPad"
                8 -> "Android"
                9 -> "Windows desktop"
                11 -> "Windows phone"
                12 -> "Linux"
                13 -> "Windows IoT"
                14 -> "Surface Hub"
                15 -> "Windows laptop"
                16 -> "Windows tablet"
                else -> res.getString(R.string.apd_type_number, type)
            }
            return listOf(
                Field(
                    "Microsoft Nearby Sharing / Swift Pair",
                    res.getString(R.string.apd_microsoft_nearby, kind),
                ),
            )
        }
        return listOf(
            Field(
                res.getString(R.string.apd_microsoft_data),
                res.getString(R.string.apd_bytes, bytes.size),
            ),
        )
    }

    private fun decodeAltBeacon(bytes: ByteArray): List<Field> {
        if (bytes.size >= 22 && bytes[0] == 0xBE.toByte() && bytes[1] == 0xAC.toByte()) {
            return listOf(
                Field("AltBeacon UUID", uuidFromBe(bytes, 2)),
                Field("AltBeacon major / minor", "${u16be(bytes, 18)} / ${u16be(bytes, 20)}"),
            )
        }
        return emptyList()
    }

    private fun modelId24(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xFF) shl 16) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            (bytes[2].toInt() and 0xFF)

    private fun u16be(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun uuidFromBe(data: ByteArray, offset: Int): String {
        fun h(i: Int) = "%02x".format(data[offset + i].toInt() and 0xFF)
        return "${h(0)}${h(1)}${h(2)}${h(3)}-${h(4)}${h(5)}-${h(6)}${h(7)}-${h(8)}${h(9)}-${h(10)}${h(11)}${h(12)}${h(13)}${h(14)}${h(15)}"
    }

    private fun uuid16(uuid: String): Int? {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex.length == 4 -> hex.toIntOrNull(16)
            hex.length == 32 && hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB") ->
                hex.substring(4, 8).toIntOrNull(16)
            else -> null
        }
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val h = hex.filter { it.isLetterOrDigit() }
        if (h.isEmpty() || h.length % 2 != 0) return null
        return ByteArray(h.length / 2) { i ->
            h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
