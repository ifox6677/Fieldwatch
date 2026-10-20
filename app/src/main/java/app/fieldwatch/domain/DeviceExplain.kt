package app.fieldwatch.domain

import android.content.res.Resources
import app.fieldwatch.R

/**
 * Plain-language decode of advertised identity. Guesses are what the radio
 * is broadcasting, not a visual identification.
 */
object DeviceExplain {
    data class Guess(
        val headline: String,
        val because: String,
        val confidence: Confidence,
        /** The label without the hedge, tidied for a compact row. */
        val coreLabel: String,
        /** True when the radio is only known as a generic advertiser or access point. */
        val generic: Boolean,
    )

    enum class Confidence { HIGH, MEDIUM, LOW }

    fun guess(res: Resources, device: Sighting, signatureNames: List<String>): Guess {
        val hints = ArrayList<Hint>(8)
        val appearance = device.facts.appearance?.let { RadioDb.appearance(it) }
        appearanceHint(res, appearance)?.let { hints += it }
        CodDecoder.decodeOrNull(device.facts.deviceClass)?.let { codHint(res, it)?.let { h -> hints += h } }
        hints += uuidHints(res, device.serviceUuids + device.facts.serviceData.map { it.uuid })
        hints += AdvPayloadDecoder.roleHints(res, device).map {
            Hint(it.bucket, it.label, it.reason, it.weight)
        }
        hints += signatureHints(res, signatureNames)
        if (device.kind == RadioKind.WIFI) hints += wifiHints(res, device, signatureNames)

        if (hints.isEmpty()) {
            return Guess(
                headline = if (device.kind == RadioKind.WIFI) {
                    res.getString(R.string.de_generic_wifi_headline)
                } else {
                    res.getString(R.string.de_generic_ble_headline)
                },
                because = res.getString(R.string.de_generic_because),
                confidence = Confidence.LOW,
                coreLabel = "",
                generic = true,
            )
        }
        val grouped = LinkedHashMap<String, Hint>()
        for (hint in hints.sortedByDescending { it.weight }) {
            val key = hint.bucket
            val prev = grouped[key]
            if (prev == null || hint.weight > prev.weight) grouped[key] = hint
        }
        val best = grouped.values.maxBy { it.weight }
        val support = grouped.values
            .filter { it.bucket == best.bucket || it.weight >= 3 }
            .map { it.reason }
            .distinct()
        val confidence = when {
            best.weight >= 6 -> Confidence.HIGH
            best.weight >= 3 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        val hedge = when (confidence) {
            Confidence.HIGH -> res.getString(R.string.de_hedge_most_likely)
            Confidence.MEDIUM -> res.getString(R.string.de_hedge_probably)
            Confidence.LOW -> res.getString(R.string.de_hedge_could_be)
        }
        return Guess(
            headline = res.getString(R.string.de_headline_format, hedge, best.label),
            because = support.joinToString(" ") +
                res.getString(R.string.de_advertising_not_visual_id),
            confidence = confidence,
            coreLabel = tidyHeadline(best.label),
            generic = false,
        )
    }

    /**
     * Compact Live-row title from the same guess as detail. Null if we only
     * know it is an unnamed advertiser — caller may fall back to vendor.
     */
    fun listLabel(res: Resources, device: Sighting, signatureNames: List<String> = emptyList()): String? {
        val guess = guess(res, device, signatureNames)
        val core = if (guess.generic) null else guess.coreLabel
        val vendor = device.vendor?.trim()?.takeIf { it.isNotBlank() && it.length <= 24 }
        if (core != null) {
            return if (vendor != null && !core.contains(vendor, ignoreCase = true)) {
                "$vendor · $core"
            } else {
                core
            }
        }
        if (vendor != null) return res.getString(R.string.de_vendor_device, vendor)
        return null
    }

    private fun tidyHeadline(headline: String): String {
        var s = headline.trim()
        s = s.replace(Regex("""\s*\([^)]*\)"""), "").trim()
        s = s.removePrefix("an ").removePrefix("a ").trim()
        if (s.isEmpty()) return headline
        return s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun flagsExplain(res: Resources, flags: Int): String = buildList {
        if (flags and 0x01 != 0) {
            add(res.getString(R.string.de_flag_limited_discoverable))
        }
        if (flags and 0x02 != 0) {
            add(res.getString(R.string.de_flag_discoverable))
        }
        if (flags and 0x04 != 0) {
            add(res.getString(R.string.de_flag_ble_only))
        } else {
            add(res.getString(R.string.de_flag_may_classic))
        }
        if (flags and 0x08 != 0 || flags and 0x10 != 0) {
            add(res.getString(R.string.de_flag_dual_mode))
        }
    }.joinToString(" ")

    fun phyExplain(res: Resources, label: String): String = when {
        label.contains("Coded") -> res.getString(R.string.de_phy_coded, label)
        label.contains("2M") -> res.getString(R.string.de_phy_2m, label)
        label.contains("1M") -> res.getString(R.string.de_phy_1m, label)
        else -> label
    }

    fun addressExplain(res: Resources, device: Sighting): String {
        val type = device.facts.addressType
        return when {
            device.kind == RadioKind.WIFI && device.randomized ->
                res.getString(R.string.de_addr_locally_admin_bssid)
            MacUtil.isLocallyAdministered(device.mac) && !device.randomized ->
                res.getString(R.string.de_addr_locally_admin)
            type.equals("Public", true) && !device.randomized ->
                res.getString(R.string.de_addr_public_factory)
            type.equals("Random", true) || device.randomized ->
                res.getString(R.string.de_addr_random_privacy)
            type.equals("Anonymous", true) ->
                res.getString(R.string.de_addr_anonymous)
            else ->
                listOfNotNull(type, res.getString(R.string.de_addr_universal)).joinToString(" · ")
        }
    }

    fun rssiBand(res: Resources, rssi: Int): String = when {
        !Rssi.measured(rssi) -> res.getString(R.string.de_rssi_not_available)
        rssi >= -45 -> res.getString(R.string.de_rssi_very_strong)
        rssi >= -60 -> res.getString(R.string.de_rssi_strong)
        rssi >= -75 -> res.getString(R.string.de_rssi_medium)
        rssi >= -88 -> res.getString(R.string.de_rssi_weak)
        else -> res.getString(R.string.de_rssi_very_weak)
    }

    fun rssiExplain(res: Resources, rssi: Int): String =
        if (!Rssi.measured(rssi)) res.getString(R.string.de_rssi_not_available_cap)
        else res.getString(R.string.de_rssi_explain, rssi, rssiBand(res, rssi))

    fun wifiSecurityExplain(res: Resources, raw: String): String {
        val bits = ArrayList<String>(4)
        val u = raw.uppercase()
        when {
            "SAE" in u || "WPA3" in u -> bits += res.getString(R.string.de_sec_wpa3)
            "OWE" in u -> bits += res.getString(R.string.de_sec_owe)
            "PSK" in u && "WPA2" in u -> bits += res.getString(R.string.de_sec_wpa2)
            "PSK" in u || "WPA" in u -> bits += res.getString(R.string.de_sec_wifi_psk)
            "802.1X" in u || "EAP" in u -> bits += res.getString(R.string.de_sec_enterprise)
            "WEP" in u -> bits += res.getString(R.string.de_sec_wep)
            "ESS" in u && bits.isEmpty() -> bits += res.getString(R.string.de_sec_open_unparsed)
        }
        when {
            "CCMP" in u || "GCMP" in u -> bits += res.getString(R.string.de_sec_aes)
            "TKIP" in u -> bits += res.getString(R.string.de_sec_tkip)
        }
        if ("WPS" in u) bits += res.getString(R.string.de_sec_wps)
        if ("MESH" in u) bits += res.getString(R.string.de_sec_mesh_node)
        if ("IBSS" in u) bits += res.getString(R.string.de_sec_adhoc)
        if ("ESS" in u) bits += res.getString(R.string.de_sec_infrastructure_ap)
        return if (bits.isEmpty()) raw else bits.distinct().joinToString(". ") + "."
    }

    fun uuidGloss(res: Resources, uuid: String): String? {
        val name = RadioDb.serviceUuid(uuid)
        val short = uuid16(uuid) ?: return name
        val extra = when (short) {
            0x1800 -> res.getString(R.string.de_uuid_1800)
            0x1801 -> res.getString(R.string.de_uuid_1801)
            0x180A -> res.getString(R.string.de_uuid_180a)
            0x180F -> res.getString(R.string.de_uuid_180f)
            0x1812 -> res.getString(R.string.de_uuid_1812)
            0x180D -> res.getString(R.string.de_uuid_180d)
            0x1810 -> res.getString(R.string.de_uuid_1810)
            0x181A -> res.getString(R.string.de_uuid_181a)
            0x1844, 0x1845, 0x1846 -> res.getString(R.string.de_uuid_1844)
            0x1850, 0x184E, 0x184F -> res.getString(R.string.de_uuid_1850)
            0xFE2C -> res.getString(R.string.de_uuid_fe2c)
            0xFD5A -> res.getString(R.string.de_uuid_fd5a)
            0xFD44 -> res.getString(R.string.de_uuid_fd44)
            0xFEED, 0xFEDD, 0xFEEC -> res.getString(R.string.de_uuid_feed)
            0xFEEE -> res.getString(R.string.de_uuid_feee)
            0xFD50 -> res.getString(R.string.de_uuid_fd50)
            0xFEBE, 0xFE21 -> res.getString(R.string.de_uuid_febe)
            0xFE78 -> res.getString(R.string.de_uuid_fe78)
            0xFE07 -> res.getString(R.string.de_uuid_fe07)
            0xFEAF, 0xFEB0 -> res.getString(R.string.de_uuid_feaf)
            0xFCBF -> res.getString(R.string.de_uuid_fcbf)
            0xFE24 -> res.getString(R.string.de_uuid_fe24)
            0xFCF4 -> res.getString(R.string.de_uuid_fcf4)
            0xFCB2 -> res.getString(R.string.de_uuid_fcb2)
            else -> null
        }
        return when {
            name != null && extra != null -> res.getString(R.string.de_uuid_name_extra, name, extra)
            name != null -> name
            extra != null -> extra
            else -> null
        }
    }

    private data class Hint(
        val bucket: String,
        val label: String,
        val reason: String,
        val weight: Int,
    )

    private fun appearanceHint(res: Resources, name: String?): Hint? {
        if (name.isNullOrBlank() || name.equals("Unknown", true)) return null
        val n = name.lowercase()
        val (bucket, label, w) = when {
            "ear" in n || "headphone" in n || "headset" in n || "hearable" in n || "hearing" in n ->
                Triple("audio-personal", res.getString(R.string.de_earbuds_or_headphones), 7)
            "speaker" in n || "loudspeaker" in n || "hifi" in n ->
                Triple("audio-speaker", res.getString(R.string.de_a_speaker), 7)
            "mouse" in n -> Triple("mouse", res.getString(R.string.de_a_mouse), 8)
            "keyboard" in n -> Triple("keyboard", res.getString(R.string.de_a_keyboard), 8)
            "gamepad" in n || "joystick" in n -> Triple("gamepad", res.getString(R.string.de_a_game_controller), 7)
            "watch" in n -> Triple("watch", res.getString(R.string.de_a_watch_or_wrist_wearable), 7)
            "phone" in n -> Triple("phone", res.getString(R.string.de_a_phone), 6)
            "laptop" in n || "computer" in n || "desktop" in n || "tablet" in n ->
                Triple("computer", res.getString(R.string.de_a_computer_or_tablet), 6)
            "tag" in n || "keyring" in n -> Triple("tag", res.getString(R.string.de_a_finder_tag_tracker), 6)
            "remote" in n -> Triple("remote", res.getString(R.string.de_a_remote_control), 6)
            "hid" in n -> Triple("hid", res.getString(R.string.de_an_input_device_keyboard_mouse_or_similar), 4)
            "heart" in n -> Triple("health", res.getString(R.string.de_a_heart_rate_monitor), 7)
            "glucose" in n || "oximeter" in n || "blood pressure" in n || "thermometer" in n ->
                Triple("health", res.getString(R.string.de_a_health_sensor), 6)
            "display" in n || "monitor" in n -> Triple("display", res.getString(R.string.de_a_display_or_tv_stick), 4)
            "clock" in n -> Triple("clock", res.getString(R.string.de_a_clock), 5)
            "glasses" in n -> Triple("glasses", res.getString(R.string.de_smart_glasses), 6)
            else -> Triple("other", name, 3)
        }
        return Hint(bucket, label, res.getString(R.string.de_advertises_appearance, name), w)
    }

    private fun codHint(res: Resources, cod: CodDecoder.Decoded): Hint? {
        val minor = cod.minor.lowercase()
        val major = cod.major.lowercase()
        val (bucket, label, w) = when {
            "headphone" in minor || "headset" in minor || "hands-free" in minor ->
                Triple("audio-personal", res.getString(R.string.de_earbuds_or_a_headset), 6)
            "loudspeaker" in minor || "portable audio" in minor || "hifi" in minor || "car audio" in minor ->
                Triple("audio-speaker", res.getString(R.string.de_a_speaker), 6)
            "pointing" in minor || minor == "mouse" -> Triple("mouse", res.getString(R.string.de_a_mouse), 7)
            "keyboard" in minor -> Triple("keyboard", res.getString(R.string.de_a_keyboard), 7)
            "gamepad" in minor || "joystick" in minor -> Triple("gamepad", res.getString(R.string.de_a_game_controller), 6)
            "smartphone" in minor || (major == "phone" && "uncategorized" !in minor) ->
                Triple("phone", res.getString(R.string.de_a_phone), 5)
            "laptop" in minor || "tablet" in minor || "desktop" in minor ->
                Triple("computer", res.getString(R.string.de_a_computer), 5)
            "wristwatch" in minor -> Triple("watch", res.getString(R.string.de_a_watch), 6)
            "heart" in minor || "pulse" in minor || "glucose" in minor || "oximeter" in minor ->
                Triple("health", res.getString(R.string.de_a_health_sensor), 6)
            "audio" in major -> Triple("audio-personal", res.getString(R.string.de_an_audio_device), 3)
            "peripheral" in major -> Triple("hid", res.getString(R.string.de_an_input_accessory), 3)
            "uncategorized" in major || "miscellaneous" in major -> return null
            else -> return null
        }
        val shown = if (cod.minor.isNotBlank() && cod.minor != "Uncategorized") {
            "${cod.major} / ${cod.minor}"
        } else {
            cod.major
        }
        return Hint(bucket, label, res.getString(R.string.de_cod_says, shown), w)
    }

    private fun uuidHints(res: Resources, uuids: List<String>): List<Hint> {
        val out = ArrayList<Hint>(4)
        for (uuid in uuids) {
            val id = uuid16(uuid) ?: continue
            when (id) {
                0x1812 -> out += Hint("hid", res.getString(R.string.de_a_keyboard_mouse_or_gamepad), res.getString(R.string.de_it_offers_the_hid_human_interface_service), 5)
                0x1108, 0x1112, 0x111E, 0x110B, 0x110A, 0x1131, 0x1203 ->
                    out += Hint("audio-personal", res.getString(R.string.de_headphones_a_headset_or_a_speaker), res.getString(R.string.de_it_offers_a_classic_audio_headset_service), 5)
                0x184E, 0x184F, 0x1850, 0x1851 ->
                    out += Hint("audio-personal", res.getString(R.string.de_le_audio_earbuds_or_a_speaker), res.getString(R.string.de_it_offers_bluetooth_le_audio_services), 6)
                0x180D -> out += Hint("health", res.getString(R.string.de_a_heart_rate_monitor), res.getString(R.string.de_it_offers_the_heart_rate_service), 6)
                0x1810 -> out += Hint("health", res.getString(R.string.de_a_blood_pressure_monitor), res.getString(R.string.de_it_offers_the_blood_pressure_service), 6)
                0x181A -> out += Hint("sensor", res.getString(R.string.de_an_environmental_sensor), res.getString(R.string.de_it_offers_environmental_sensing), 4)
                0xFE2C -> out += Hint("audio-personal", res.getString(R.string.de_earbuds_or_a_speaker), res.getString(R.string.de_google_fast_pair_is_present_common_on_buds_and_speakers), 4)
                0xFD5A -> out += Hint("tag", res.getString(R.string.de_a_samsung_smarttag), res.getString(R.string.de_smarttag_service_uuid), 7)
                0xFD44 -> out += Hint("tag", res.getString(R.string.de_an_apple_find_my_accessory), res.getString(R.string.de_find_my_related_uuid), 6)
                0xFEED, 0xFEDD, 0xFEEC -> out += Hint("tag", res.getString(R.string.de_a_tile_tracker), res.getString(R.string.de_tile_service_uuid), 7)
                0xFEEE -> out += Hint("wearable", res.getString(R.string.de_a_polar_heart_rate_strap_or_watch), res.getString(R.string.de_polar_service_uuid), 7)
            }
        }
        return out
    }

    private fun signatureHints(res: Resources, names: List<String>): List<Hint> {
        return names.mapNotNull { raw ->
            if (isGenericSignatureName(raw)) return@mapNotNull null
            val n = raw.lowercase()
            when {
                "airtag" in n || n == "find my" || "find hub" in n || "dult" in n ->
                    Hint(
                        "tag",
                        when {
                            "dult" in n -> res.getString(R.string.de_a_dult_finder_tag)
                            "find hub" in n -> res.getString(R.string.de_a_google_find_hub_tag)
                            else -> res.getString(R.string.de_an_apple_airtag_find_my_tag)
                        },
                        res.getString(R.string.de_matched_signature, raw),
                        8,
                    )
                "apple device" in n ->
                    Hint("phone", res.getString(R.string.de_an_iphone_ipad_or_mac), res.getString(R.string.de_matched_signature, raw), 7)
                "apple audio" in n ->
                    Hint("audio-personal", res.getString(R.string.de_airpods_beats_or_airplay), res.getString(R.string.de_matched_signature, raw), 7)
                "microsoft" in n ->
                    Hint("computer", res.getString(R.string.de_a_windows_surface_xbox_radio), res.getString(R.string.de_matched_signature, raw), 6)
                n == "tesla tstpms" ->
                    Hint("vehicle", res.getString(R.string.de_a_tesla_ble_tire_sensor), res.getString(R.string.de_matched_signature, raw), 7)
                "tpms" in n || n == "tirecheck" || n == "sytpms" ->
                    Hint("vehicle", res.getString(R.string.de_a_ble_tire_pressure_sensor), res.getString(R.string.de_matched_signature, raw), 7)
                n == "vuzix" ->
                    Hint("glasses", res.getString(R.string.de_vuzix_smart_glasses), res.getString(R.string.de_matched_signature, raw), 7)
                n == "tesla" ->
                    Hint("vehicle", res.getString(R.string.de_a_tesla_vehicle_including_cybertruck_or_phone_as_key), res.getString(R.string.de_matched_signature, raw), 7)
                n == "google" ->
                    Hint("phone", res.getString(R.string.de_a_pixel_or_other_google_radio), res.getString(R.string.de_matched_signature, raw), 6)
                n == "sony" ->
                    Hint("audio-personal", res.getString(R.string.de_sony_headphones_a_tv_or_a_camera), res.getString(R.string.de_matched_signature, raw), 6)
                n == "bose" ->
                    Hint("audio-personal", res.getString(R.string.de_bose_headphones_or_a_speaker), res.getString(R.string.de_matched_signature, raw), 7)
                n == "garmin" ->
                    Hint("watch", res.getString(R.string.de_a_garmin_watch_or_inreach), res.getString(R.string.de_matched_signature, raw), 7)
                n == "amazon" ->
                    Hint("speaker", res.getString(R.string.de_an_echo_fire_or_other_amazon_radio), res.getString(R.string.de_matched_signature, raw), 6)
                n == "fitbit" ->
                    Hint("watch", res.getString(R.string.de_a_fitbit), res.getString(R.string.de_matched_signature, raw), 7)
                n == "oura" ->
                    Hint("wearable", res.getString(R.string.de_an_oura_ring), res.getString(R.string.de_matched_signature, raw), 7)
                n == "logitech" ->
                    Hint("hid", res.getString(R.string.de_a_logitech_mouse_keyboard_or_webcam), res.getString(R.string.de_matched_signature, raw), 6)
                "jbl" in n || n == "harman" ->
                    Hint("audio-personal", res.getString(R.string.de_jbl_or_harman_audio), res.getString(R.string.de_matched_signature, raw), 6)
                n == "sonos" ->
                    Hint("audio-speaker", res.getString(R.string.de_a_sonos_speaker), res.getString(R.string.de_matched_signature, raw), 7)
                n == "gopro" ->
                    Hint("camera", res.getString(R.string.de_a_gopro), res.getString(R.string.de_matched_signature, raw), 7)
                n == "osmo" ->
                    Hint("camera", res.getString(R.string.de_a_dji_osmo_action_camera), res.getString(R.string.de_matched_signature, raw), 7)
                n == "insta360" ->
                    Hint("camera", res.getString(R.string.de_an_insta360_camera), res.getString(R.string.de_matched_signature, raw), 7)
                n == "dji power" ->
                    Hint("iot", res.getString(R.string.de_a_dji_power_station), res.getString(R.string.de_matched_signature, raw), 7)
                n == "dji" ->
                    Hint("drone", res.getString(R.string.de_a_dji_drone_or_controller), res.getString(R.string.de_matched_signature, raw), 7)
                n == "remote id" ->
                    Hint("drone", res.getString(R.string.de_a_drone_broadcasting_astm_remote_id), res.getString(R.string.de_matched_signature, raw), 8)
                n == "skydio" ->
                    Hint("drone", res.getString(R.string.de_a_skydio_drone), res.getString(R.string.de_matched_signature, raw), 7)
                n == "autel" ->
                    Hint("drone", res.getString(R.string.de_an_autel_drone), res.getString(R.string.de_matched_signature, raw), 7)
                n == "parrot" ->
                    Hint("drone", res.getString(R.string.de_a_parrot_anafi_or_bebop_drone), res.getString(R.string.de_matched_signature, raw), 7)
                n == "hoverair" ->
                    Hint("drone", res.getString(R.string.de_a_hoverair_flying_camera), res.getString(R.string.de_matched_signature, raw), 7)
                n == "netgear" || n == "orbi" ->
                    Hint("ap", res.getString(R.string.de_a_netgear_or_orbi_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "tp-link" ->
                    Hint("ap", res.getString(R.string.de_a_tp_link_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "asus" ->
                    Hint("ap", res.getString(R.string.de_an_asus_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "linksys" ->
                    Hint("ap", res.getString(R.string.de_a_linksys_or_velop_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "eero" ->
                    Hint("ap", res.getString(R.string.de_an_eero_mesh_node), res.getString(R.string.de_matched_signature, raw), 6)
                n == "google wifi" ->
                    Hint("ap", res.getString(R.string.de_a_google_wifi_or_nest_wifi_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "d-link" ->
                    Hint("ap", res.getString(R.string.de_a_d_link_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "belkin" ->
                    Hint("ap", res.getString(R.string.de_a_belkin_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "xfinity" ->
                    Hint("ap", res.getString(R.string.de_an_xfinity_gateway_or_hotspot), res.getString(R.string.de_matched_signature, raw), 6)
                n == "spectrum" ->
                    Hint("ap", res.getString(R.string.de_a_spectrum_gateway_or_spectrum_mobile_hotspot), res.getString(R.string.de_matched_signature, raw), 6)
                n == "at&t" ->
                    Hint("ap", res.getString(R.string.de_an_at_t_gateway_or_attwifi_hotspot), res.getString(R.string.de_matched_signature, raw), 6)
                n == "verizon" ->
                    Hint("ap", res.getString(R.string.de_a_verizon_or_fios_gateway), res.getString(R.string.de_matched_signature, raw), 6)
                n == "starlink" ->
                    Hint("ap", res.getString(R.string.de_a_starlink_router), res.getString(R.string.de_matched_signature, raw), 7)
                n == "meraki" ->
                    Hint("ap", res.getString(R.string.de_a_cisco_meraki_access_point), res.getString(R.string.de_matched_signature, raw), 7)
                n == "cisco" ->
                    Hint("ap", res.getString(R.string.de_a_cisco_aironet_catalyst_business_rv_or_spvtg_access_point), res.getString(R.string.de_matched_signature, raw), 7)
                n == "mist" ->
                    Hint("ap", res.getString(R.string.de_a_juniper_mist_access_point), res.getString(R.string.de_matched_signature, raw), 7)
                n == "t-mobile" ->
                    Hint("ap", res.getString(R.string.de_a_t_mobile_home_internet_gateway_or_hotspot), res.getString(R.string.de_matched_signature, raw), 6)
                n == "humax" ->
                    Hint("ap", res.getString(R.string.de_a_humax_gateway_often_t_mobile_home_internet), res.getString(R.string.de_matched_signature, raw), 6)
                n == "sagemcom" ->
                    Hint("ap", res.getString(R.string.de_a_sagemcom_isp_gateway), res.getString(R.string.de_matched_signature, raw), 6)
                n == "arcadyan" ->
                    Hint("ap", res.getString(R.string.de_an_arcadyan_isp_gateway), res.getString(R.string.de_matched_signature, raw), 6)
                n == "askey" ->
                    Hint("ap", res.getString(R.string.de_an_askey_isp_5g_gateway), res.getString(R.string.de_matched_signature, raw), 6)
                n == "calix" ->
                    Hint("ap", res.getString(R.string.de_a_calix_fiber_gateway), res.getString(R.string.de_matched_signature, raw), 6)
                n == "nokia" ->
                    Hint("ap", res.getString(R.string.de_a_nokia_solutions_and_networks_gateway), res.getString(R.string.de_matched_signature, raw), 6)
                n == "airties" ->
                    Hint("ap", res.getString(R.string.de_an_airties_isp_mesh_node), res.getString(R.string.de_matched_signature, raw), 6)
                n == "tenda" ->
                    Hint("ap", res.getString(R.string.de_a_tenda_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "ruijie" ->
                    Hint("ap", res.getString(R.string.de_a_ruijie_or_reyee_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "dwnet" ->
                    Hint("ap", res.getString(R.string.de_a_dwnet_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "wavlink" ->
                    Hint("ap", res.getString(R.string.de_a_wavlink_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "sercomm" ->
                    Hint("ap", res.getString(R.string.de_a_sercomm_isp_gateway), res.getString(R.string.de_matched_signature, raw), 6)
                n == "luxul" ->
                    Hint("ap", res.getString(R.string.de_a_luxul_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "sophos" ->
                    Hint("ap", res.getString(R.string.de_a_sophos_firewall_or_access_point), res.getString(R.string.de_matched_signature, raw), 7)
                n == "aumovio" ->
                    Hint("hotspot", res.getString(R.string.de_an_aumovio_continental_vehicle_wi_fi_radio), res.getString(R.string.de_matched_signature, raw), 6)
                n == "centurylink" ->
                    Hint("ap", res.getString(R.string.de_a_centurylink_gateway), res.getString(R.string.de_matched_signature, raw), 6)
                n == "gm hotspot" ->
                    Hint("hotspot", res.getString(R.string.de_a_gm_in_car_hotspot_cadillac_gmc_buick_chevrolet), res.getString(R.string.de_matched_signature, raw), 6)
                n == "audi mmi" ->
                    Hint("hotspot", res.getString(R.string.de_an_audi_mmi_in_car_hotspot), res.getString(R.string.de_matched_signature, raw), 6)
                n == "extreme" ->
                    Hint("ap", res.getString(R.string.de_an_extreme_networks_access_point), res.getString(R.string.de_matched_signature, raw), 7)
                n == "adtran" ->
                    Hint("ap", res.getString(R.string.de_an_adtran_fiber_gateway_often_centurylink_quantum_fiber_oem), res.getString(R.string.de_matched_signature, raw), 6)
                n == "cambium" ->
                    Hint("ap", res.getString(R.string.de_a_cambium_or_ignitenet_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "trendnet" ->
                    Hint("ap", res.getString(R.string.de_a_trendnet_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "cudy" ->
                    Hint("ap", res.getString(R.string.de_a_cudy_travel_or_home_router), res.getString(R.string.de_matched_signature, raw), 6)
                n == "snapav" ->
                    Hint("ap", res.getString(R.string.de_a_snapav_control4_wattbox_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "arlo" ->
                    Hint("camera", res.getString(R.string.de_an_arlo_camera_or_vmb_base_station), res.getString(R.string.de_matched_signature, raw), 6)
                n == "vantiva" ->
                    Hint("ap", res.getString(R.string.de_a_vantiva_or_technicolor_isp_gateway), res.getString(R.string.de_matched_signature, raw), 6)
                n == "hitron" ->
                    Hint("ap", res.getString(R.string.de_a_hitron_cable_gateway_often_xfinity_oem), res.getString(R.string.de_matched_signature, raw), 6)
                n == "actiontec" ->
                    Hint("ap", res.getString(R.string.de_an_actiontec_fios_or_frontier_gateway), res.getString(R.string.de_matched_signature, raw), 6)
                n == "buffalo" ->
                    Hint("ap", res.getString(R.string.de_a_buffalo_airstation_or_router), res.getString(R.string.de_matched_signature, raw), 6)
                n == "grandstream" ->
                    Hint("ap", res.getString(R.string.de_a_grandstream_gwn_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "edgecore" ->
                    Hint("ap", res.getString(R.string.de_an_edgecore_access_point), res.getString(R.string.de_matched_signature, raw), 7)
                n == "watchguard ap" ->
                    Hint("ap", res.getString(R.string.de_a_watchguard_firewall_or_access_point), res.getString(R.string.de_matched_signature, raw), 7)
                n == "mojo" ->
                    Hint("ap", res.getString(R.string.de_a_mojo_networks_arista_cognitive_wi_fi_access_point), res.getString(R.string.de_matched_signature, raw), 7)
                n == "winegard" ->
                    Hint("hotspot", res.getString(R.string.de_a_winegard_rv_or_marine_wi_fi_radio), res.getString(R.string.de_matched_signature, raw), 6)
                n == "inseego" ->
                    Hint("ap", res.getString(R.string.de_an_inseego_5g_or_mifi_hotspot), res.getString(R.string.de_matched_signature, raw), 6)
                n == "franklin" ->
                    Hint("ap", res.getString(R.string.de_a_franklin_technology_5g_home_internet_gateway_rg3100_class), res.getString(R.string.de_matched_signature, raw), 6)
                n == "synology" ->
                    Hint("ap", res.getString(R.string.de_a_synology_nas_or_router_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "aruba" ->
                    Hint("ap", res.getString(R.string.de_an_hpe_aruba_instant_or_instant_on_access_point), res.getString(R.string.de_matched_signature, raw), 7)
                n == "ruckus" ->
                    Hint("ap", res.getString(R.string.de_a_ruckus_access_point), res.getString(R.string.de_matched_signature, raw), 7)
                n == "fortinet" ->
                    Hint("ap", res.getString(R.string.de_a_fortinet_fortiap_or_fortiwifi), res.getString(R.string.de_matched_signature, raw), 7)
                n == "mikrotik" ->
                    Hint("ap", res.getString(R.string.de_a_mikrotik_router_or_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "engenius" ->
                    Hint("ap", res.getString(R.string.de_an_engenius_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "zyxel" ->
                    Hint("ap", res.getString(R.string.de_a_zyxel_gateway_or_access_point), res.getString(R.string.de_matched_signature, raw), 6)
                n == "peplink" ->
                    Hint("ap", res.getString(R.string.de_a_peplink_or_pepwave_router), res.getString(R.string.de_matched_signature, raw), 6)
                n == "openwrt" ->
                    Hint("ap", res.getString(R.string.de_an_openwrt_router), res.getString(R.string.de_matched_signature, raw), 6)
                n == "arris" ->
                    Hint("ap", res.getString(R.string.de_an_arris_or_surfboard_cable_gateway), res.getString(R.string.de_matched_signature, raw), 6)
                n == "unifi ap" ->
                    Hint("ap", res.getString(R.string.de_a_ubiquiti_unifi_access_point), res.getString(R.string.de_matched_signature, raw), 7)
                n == "unifi protect" ->
                    Hint("camera", res.getString(R.string.de_a_unifi_protect_instant_camera), res.getString(R.string.de_matched_signature, raw), 7)
                n == "unifi" ->
                    Hint("ap", res.getString(R.string.de_a_unifi_ubiquiti_name), res.getString(R.string.de_matched_signature, raw), 5)
                n == "ecobee" ->
                    Hint("thermostat", res.getString(R.string.de_an_ecobee_thermostat), res.getString(R.string.de_matched_signature, raw), 7)
                n == "sensi" ->
                    Hint("thermostat", res.getString(R.string.de_a_sensi_thermostat), res.getString(R.string.de_matched_signature, raw), 6)
                n == "honeywell home" ->
                    Hint("thermostat", res.getString(R.string.de_a_honeywell_home_or_lyric_thermostat), res.getString(R.string.de_matched_signature, raw), 6)
                "honeywell xenon" in n ->
                    Hint("health", res.getString(R.string.de_a_honeywell_xenon_healthcare_barcode_scanner), res.getString(R.string.de_matched_signature, raw), 7)
                n == "omron" ->
                    Hint("health", res.getString(R.string.de_an_omron_blood_pressure_cuff_or_scale), res.getString(R.string.de_matched_signature, raw), 7)
                n == "withings" ->
                    Hint("health", res.getString(R.string.de_a_withings_scale_or_blood_pressure_monitor), res.getString(R.string.de_matched_signature, raw), 7)
                n == "dexcom" ->
                    Hint("health", res.getString(R.string.de_a_dexcom_glucose_sensor), res.getString(R.string.de_matched_signature, raw), 7)
                n == "nest thermostat" ->
                    Hint("thermostat", res.getString(R.string.de_a_nest_thermostat_or_nest_labs_ble_sensor), res.getString(R.string.de_matched_signature, raw), 6)
                n == "nest weave" ->
                    Hint("sensor", res.getString(R.string.de_a_nest_protect_camera_or_other_weave_ble_device), res.getString(R.string.de_matched_signature, raw), 7)
                n == "haiku fan" || n == "haiku" ->
                    Hint("fan", res.getString(R.string.de_a_haiku_or_mammoth_ceiling_fan), res.getString(R.string.de_matched_signature, raw), 7)
                n == "tuya" ->
                    Hint("iot", res.getString(R.string.de_a_tuya_ble_gadget_plug_light_camera_sensor), res.getString(R.string.de_matched_signature, raw), 6)
                n == "seos" || n == "assa abloy" ->
                    Hint("access", res.getString(R.string.de_an_assa_abloy_lock_yale_lock_hid_reader_or_seos_credential), res.getString(R.string.de_matched_signature, raw), 7)
                n == "august" ->
                    Hint("lock", res.getString(R.string.de_an_august_smart_lock), res.getString(R.string.de_matched_signature, raw), 7)
                n == "schlage" ->
                    Hint("lock", res.getString(R.string.de_a_schlage_or_allegion_lock), res.getString(R.string.de_matched_signature, raw), 7)
                n == "nuki" ->
                    Hint("lock", res.getString(R.string.de_a_nuki_lock_or_opener), res.getString(R.string.de_matched_signature, raw), 7)
                n == "salto" ->
                    Hint("access", res.getString(R.string.de_a_salto_access_reader_or_lock), res.getString(R.string.de_matched_signature, raw), 7)
                n == "dormakaba" ->
                    Hint("access", res.getString(R.string.de_a_dormakaba_saflok_or_oracode_lock), res.getString(R.string.de_matched_signature, raw), 7)
                n == "lockly" ->
                    Hint("lock", res.getString(R.string.de_a_lockly_smart_lock), res.getString(R.string.de_matched_signature, raw), 6)
                n == "kevo" ->
                    Hint("lock", res.getString(R.string.de_a_kwikset_kevo_or_unikey_lock), res.getString(R.string.de_matched_signature, raw), 7)
                n == "master lock" ->
                    Hint("lock", res.getString(R.string.de_a_master_lock_padlock), res.getString(R.string.de_matched_signature, raw), 7)
                n == "igloohome" ->
                    Hint("lock", res.getString(R.string.de_an_igloohome_lock_or_keybox), res.getString(R.string.de_matched_signature, raw), 7)
                n == "tedee" ->
                    Hint("lock", res.getString(R.string.de_a_tedee_smart_lock), res.getString(R.string.de_matched_signature, raw), 7)
                n == "paxton" ->
                    Hint("access", res.getString(R.string.de_a_paxton_reader_or_net2_access_point), res.getString(R.string.de_matched_signature, raw), 7)
                n == "kwikset" ->
                    Hint("lock", res.getString(R.string.de_a_kwikset_lock), res.getString(R.string.de_matched_signature, raw), 6)
                n == "myq" ->
                    Hint("garage", res.getString(R.string.de_a_chamberlain_myq_garage_hub), res.getString(R.string.de_matched_signature, raw), 7)
                n == "chevrolet hotspot" ->
                    Hint("hotspot", res.getString(R.string.de_a_chevrolet_in_car_wi_fi_hotspot), res.getString(R.string.de_matched_signature, raw), 7)
                n == "rivian" ->
                    Hint("vehicle", res.getString(R.string.de_a_rivian_vehicle_phone_key_or_sensor), res.getString(R.string.de_matched_signature, raw), 7)
                n == "ford" ->
                    Hint("vehicle", res.getString(R.string.de_a_ford_or_lincoln_vehicle_or_phone_as_key), res.getString(R.string.de_matched_signature, raw), 7)
                n == "honda" ->
                    Hint("vehicle", res.getString(R.string.de_a_honda_or_acura_vehicle_or_phone_as_key), res.getString(R.string.de_matched_signature, raw), 7)
                n == "hyundai" ->
                    Hint("vehicle", res.getString(R.string.de_a_hyundai_or_genesis_vehicle_or_phone_as_key), res.getString(R.string.de_matched_signature, raw), 7)
                n == "toyota" ->
                    Hint("vehicle", res.getString(R.string.de_a_toyota_or_lexus_vehicle_or_phone_as_key), res.getString(R.string.de_matched_signature, raw), 7)
                n == "nissan" ->
                    Hint("vehicle", res.getString(R.string.de_a_nissan_or_infiniti_vehicle_or_phone_as_key), res.getString(R.string.de_matched_signature, raw), 7)
                n == "subaru" ->
                    Hint("vehicle", res.getString(R.string.de_a_subaru_vehicle_or_phone_as_key), res.getString(R.string.de_matched_signature, raw), 7)
                n == "bmw" ->
                    Hint("vehicle", res.getString(R.string.de_a_bmw_vehicle_phone_as_key_or_factory_hotspot), res.getString(R.string.de_matched_signature, raw), 7)
                n == "volkswagen" ->
                    Hint("vehicle", res.getString(R.string.de_a_volkswagen_vehicle_or_phone_as_key), res.getString(R.string.de_matched_signature, raw), 7)
                n == "porsche" ->
                    Hint("vehicle", res.getString(R.string.de_a_porsche_vehicle_or_phone_as_key), res.getString(R.string.de_matched_signature, raw), 7)
                n == "jaguar land rover" ->
                    Hint("vehicle", res.getString(R.string.de_a_jaguar_land_rover_or_range_rover), res.getString(R.string.de_matched_signature, raw), 7)
                n == "byd" ->
                    Hint("vehicle", res.getString(R.string.de_a_byd_vehicle_or_phone_as_key), res.getString(R.string.de_matched_signature, raw), 7)
                n == "govee" ->
                    Hint("light", res.getString(R.string.de_a_govee_light_or_sensor), res.getString(R.string.de_matched_signature, raw), 6)
                n == "hp" ->
                    Hint("printer", res.getString(R.string.de_an_hp_printer), res.getString(R.string.de_matched_signature, raw), 6)
                n == "epson" ->
                    Hint("printer", res.getString(R.string.de_an_epson_ecotank_or_workforce_printer), res.getString(R.string.de_matched_signature, raw), 6)
                n == "lg webos tv" ->
                    Hint("tv", res.getString(R.string.de_an_lg_webos_tv), res.getString(R.string.de_matched_signature, raw), 7)
                n == "roku" ->
                    Hint("tv", res.getString(R.string.de_a_roku_streaming_stick_or_roku_tv_often_a_hidden_wi_fi_direct), res.getString(R.string.de_matched_signature, raw), 7)
                n == "samsung appliance" ->
                    Hint("iot", res.getString(R.string.de_a_samsung_fridge_range_oven_or_cooktop_setup_ap), res.getString(R.string.de_matched_signature, raw), 6)
                n == "ecowater" ->
                    Hint("iot", res.getString(R.string.de_an_ecowater_water_softener_setup_ap), res.getString(R.string.de_matched_signature, raw), 6)
                n == "nespresso" ->
                    Hint("iot", res.getString(R.string.de_a_nespresso_coffee_machine), res.getString(R.string.de_matched_signature, raw), 7)
                n == "radiacode" ->
                    Hint("sensor", res.getString(R.string.de_a_radiacode_radiation_detector), res.getString(R.string.de_matched_signature, raw), 7)
                n == "shokz" ->
                    Hint("audio-personal", res.getString(R.string.de_shokz_openrun_or_openfit_headphones), res.getString(R.string.de_matched_signature, raw), 7)
                n == "mercedes mbux" ->
                    Hint("hotspot", res.getString(R.string.de_a_mercedes_mbux_in_car_hotspot), res.getString(R.string.de_matched_signature, raw), 7)
                n == "motive" ->
                    Hint("hotspot", res.getString(R.string.de_a_motive_keeptruckin_fleet_eld_hotspot), res.getString(R.string.de_matched_signature, raw), 6)
                n == "peoplenet" ->
                    Hint("hotspot", res.getString(R.string.de_a_peoplenet_fleet_eld_hotspot), res.getString(R.string.de_matched_signature, raw), 6)
                n == "uconnect" ->
                    Hint("hotspot", res.getString(R.string.de_a_uconnect_in_car_hotspot), res.getString(R.string.de_matched_signature, raw), 6)
                n == "carplay" ->
                    Hint("hotspot", res.getString(R.string.de_a_carplay_in_car_hotspot), res.getString(R.string.de_matched_signature, raw), 6)
                n == "cradlepoint" ->
                    Hint("hotspot", res.getString(R.string.de_a_cradlepoint_vehicle_router_often_public_safety_fleet), res.getString(R.string.de_matched_signature, raw), 7)
                n == "airlink" ->
                    Hint("hotspot", res.getString(R.string.de_a_sierra_wireless_airlink_vehicle_gateway), res.getString(R.string.de_matched_signature, raw), 7)
                n == "compex" ->
                    Hint("hotspot", res.getString(R.string.de_a_compex_access_point_sometimes_public_safety_fleet), res.getString(R.string.de_matched_signature, raw), 6)
                n == "novatel wireless" ->
                    Hint("hotspot", res.getString(R.string.de_a_novatel_wireless_inseego_vehicle_radio), res.getString(R.string.de_matched_signature, raw), 6)
                n == "utility inc" ->
                    Hint("hotspot", res.getString(R.string.de_a_utility_inc_vehicle_or_public_safety_radio), res.getString(R.string.de_matched_signature, raw), 6)
                "gl.inet" in n || n == "glinet" ->
                    Hint("ap", res.getString(R.string.de_a_gl_inet_travel_router), res.getString(R.string.de_matched_signature, raw), 6)
                "smarttag" in n ->
                    Hint("tag", res.getString(R.string.de_a_samsung_smarttag), res.getString(R.string.de_matched_signature, raw), 8)
                "tile" in n ->
                    Hint("tag", res.getString(R.string.de_a_tile_tracker), res.getString(R.string.de_matched_signature, raw), 8)
                n == "ibeacon" ->
                    Hint("beacon", res.getString(R.string.de_an_ibeacon), res.getString(R.string.de_matched_signature, raw), 7)
                "atrius" in n ->
                    Hint("beacon", res.getString(R.string.de_an_atrius_cart_tag), res.getString(R.string.de_matched_signature, raw), 8)
                n == "minew" ->
                    Hint("beacon", res.getString(R.string.de_a_minew_ble_beacon_or_sensor), res.getString(R.string.de_matched_signature, raw), 7)
                n == "estimote" ->
                    Hint("beacon", res.getString(R.string.de_an_estimote_beacon), res.getString(R.string.de_matched_signature, raw), 7)
                n == "kontakt.io" || n == "kontakt" ->
                    Hint("beacon", res.getString(R.string.de_a_kontakt_io_beacon), res.getString(R.string.de_matched_signature, raw), 7)
                "bluetoad" in n ->
                    Hint(
                        "roadside",
                        res.getString(R.string.de_an_iteris_bluetoad_vantage_velocity_roadside_bluetooth_travel),
                        res.getString(R.string.de_matched_signature, raw),
                        7,
                    )
                "bliptrack" in n ->
                    Hint(
                        "roadside",
                        res.getString(R.string.de_a_blip_systems_bliptrack_roadside_travel_time_sensor),
                        res.getString(R.string.de_matched_signature, raw),
                        7,
                    )
                "raven" in n || "shotspotter" in n || "soundthinking" in n ->
                    Hint(
                        "acoustic",
                        res.getString(R.string.de_a_flock_raven_or_shotspotter_acoustic_gunshot_sensor),
                        res.getString(R.string.de_matched_signature, raw),
                        8,
                    )
                "digital ally" in n ->
                    Hint("camera", res.getString(R.string.de_a_digital_ally_body_worn_or_in_car_camera), res.getString(R.string.de_matched_signature, raw), 8)
                "reveal media" in n || "bodyworn" in n ->
                    Hint("camera", res.getString(R.string.de_a_reveal_media_body_worn_camera), res.getString(R.string.de_matched_signature, raw), 8)
                n == "wolfcom" ->
                    Hint("camera", res.getString(R.string.de_a_wolfcom_body_worn_or_in_car_camera), res.getString(R.string.de_matched_signature, raw), 8)
                "i-pro" in n || "arbitrator" in n ->
                    Hint("camera", res.getString(R.string.de_a_panasonic_i_pro_camera_or_arbitrator_in_car_system), res.getString(R.string.de_matched_signature, raw), 8)
                "limitless" in n ->
                    Hint("wearable", res.getString(R.string.de_a_limitless_pendant_conversation_recorder), res.getString(R.string.de_matched_signature, raw), 8)
                n == "bee pendant" || "bee pioneer" in n ->
                    Hint("wearable", res.getString(R.string.de_a_bee_pioneer_wearable_recorder), res.getString(R.string.de_matched_signature, raw), 8)
                n == "omi" || "openglass" in n ->
                    Hint("wearable", res.getString(R.string.de_an_omi_pendant_or_openglass_camera_glasses), res.getString(R.string.de_matched_signature, raw), 8)
                "friend pendant" in n ->
                    Hint("wearable", res.getString(R.string.de_a_friend_pendant_necklace), res.getString(R.string.de_matched_signature, raw), 8)
                "brilliant frame" in n ->
                    Hint("glasses", res.getString(R.string.de_brilliant_labs_frame_ar_glasses), res.getString(R.string.de_matched_signature, raw), 8)
                n == "even g1" ->
                    Hint("glasses", res.getString(R.string.de_even_realities_g1_glasses), res.getString(R.string.de_matched_signature, raw), 8)
                "hayden" in n ->
                    Hint("camera", res.getString(R.string.de_a_hayden_ai_bus_or_vehicle_mounted_camera), res.getString(R.string.de_matched_signature, raw), 8)
                "miovision" in n ->
                    Hint("camera", res.getString(R.string.de_a_miovision_intersection_traffic_camera), res.getString(R.string.de_matched_signature, raw), 8)
                n == "tattile" ->
                    Hint("camera", res.getString(R.string.de_a_tattile_plate_reader), res.getString(R.string.de_matched_signature, raw), 8)
                "lvt" in n || "liveview" in n ->
                    Hint("camera", res.getString(R.string.de_an_lvt_liveview_solar_surveillance_trailer), res.getString(R.string.de_matched_signature, raw), 8)
                "hanwha" in n || "wisenet" in n ->
                    Hint("camera", res.getString(R.string.de_a_hanwha_vision_wisenet_camera), res.getString(R.string.de_matched_signature, raw), 7)
                n == "uniview" ->
                    Hint("camera", res.getString(R.string.de_a_uniview_unv_camera), res.getString(R.string.de_matched_signature, raw), 7)
                n == "rhombus" ->
                    Hint("camera", res.getString(R.string.de_a_rhombus_cloud_camera), res.getString(R.string.de_matched_signature, raw), 7)
                n == "meshcore" ->
                    Hint("mesh", res.getString(R.string.de_a_meshcore_lora_companion_radio), res.getString(R.string.de_matched_signature, raw), 7)
                "gotenna" in n ->
                    Hint("mesh", res.getString(R.string.de_a_gotenna_mesh_or_pro_radio), res.getString(R.string.de_matched_signature, raw), 7)
                n == "sensecap" ->
                    Hint("mesh", res.getString(R.string.de_a_sensecap_lorawan_helium_gateway), res.getString(R.string.de_matched_signature, raw), 7)
                "wisgate" in n || n == "rak wisgate" ->
                    Hint("mesh", res.getString(R.string.de_a_rak_wisgate_lorawan_gateway), res.getString(R.string.de_matched_signature, raw), 7)
                n == "ghostesp" ->
                    Hint("pentest", res.getString(R.string.de_a_ghostesp_esp32_audit_board), res.getString(R.string.de_matched_signature, raw), 7)
                n == "bruce" ->
                    Hint("pentest", res.getString(R.string.de_a_bruce_esp32_pentest_board), res.getString(R.string.de_matched_signature, raw), 7)
                n == "liteon camera radio" ->
                    Hint(
                        "module",
                        res.getString(R.string.de_a_camera_module_radio_liteon_or_similar),
                        res.getString(R.string.de_matched_signature, raw),
                        3,
                    )
                "chipolo" in n || "pebblebee" in n || "moto tag" in n ->
                    Hint("tag", res.getString(R.string.de_a_finder_tag), res.getString(R.string.de_matched_signature, raw), 7)
                "airpods" in n ->
                    Hint("audio-personal", res.getString(R.string.de_airpods), res.getString(R.string.de_matched_signature, raw), 8)
                else -> Hint("named", raw, res.getString(R.string.de_matched_signature, raw), 7)
            }
        }
    }

    private fun isGenericSignatureName(name: String): Boolean {
        val n = name.trim()
        return n.equals("Unknown Signature", ignoreCase = true) ||
            n.equals("Unknown Fleet", ignoreCase = true)
    }

    private fun wifiHints(res: Resources, device: Sighting, signatureNames: List<String>): List<Hint> {
        val name = device.name
        val caps = (device.facts.capabilities ?: "").uppercase()
        val specific = signatureNames.any { !isGenericSignatureName(it) }
        val out = ArrayList<Hint>(2)
        when {
            name.startsWith("DIRECT-", true) ->
                out += if (specific) {
                    Hint("wifi-direct", res.getString(R.string.de_a_wi_fi_direct_access_point), res.getString(R.string.de_ssid_starts_with_direct), 4)
                } else {
                    Hint("wifi-direct", res.getString(R.string.de_a_phone_or_tv_using_wi_fi_direct), res.getString(R.string.de_ssid_starts_with_direct), 6)
                }
            name.startsWith("ANDROID-", true) || name.contains("hotspot", true) ->
                if (!specific) {
                    out += Hint("hotspot", res.getString(R.string.de_a_phone_hotspot), res.getString(R.string.de_ssid_looks_like_a_phone_hotspot), 6)
                }
            "MESH" in caps ->
                out += Hint("mesh", res.getString(R.string.de_a_mesh_wi_fi_node), res.getString(R.string.de_capability_list_includes_mesh), 5)
            device.hiddenSsid ->
                out += Hint("ap", res.getString(R.string.de_a_hidden_wi_fi_access_point), res.getString(R.string.de_ssid_is_hidden_the_radio_is_still_beaconing), 4)
            else ->
                if (!specific) {
                    out += Hint("ap", res.getString(R.string.de_a_wi_fi_access_point), res.getString(R.string.de_stock_android_only_reports_beaconing_aps), 3)
                }
        }
        return out
    }

    private fun uuid16(uuid: String): Int? {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex.length == 4 -> hex.toIntOrNull(16)
            hex.length == 32 && hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB") ->
                hex.substring(4, 8).toIntOrNull(16)
            hex.length == 8 -> hex.takeLast(4).toIntOrNull(16)
            else -> null
        }
    }
}
