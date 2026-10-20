package app.fieldwatch.domain

import app.fieldwatch.AppText
import app.fieldwatch.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SettingsPack(
    val format: String,
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAt: String = "",
    val appVersion: String = "",
    val settings: AppSettings = AppSettings(),
    val filter: FilterState = FilterState(),
    val presets: List<FilterPreset> = emptyList(),
    val watchlist: List<WatchTarget> = emptyList(),
    val hiddenPresetIds: Set<String> = emptySet(),
) {
    companion object {
        const val FORMAT = "fieldwatch-settings"
        const val FORMAT_VERSION = 1
    }
}

data class SettingsImportResult(
    val namedRadios: Int = 0,
    val signatureWatches: Int = 0,
    val presets: Int = 0,
    val error: String? = null,
) {
    fun summary(): String {
        error?.let { return it }
        val presetWord = if (presets == 1) {
            AppText.get(R.string.settings_import_preset_one, "preset")
        } else {
            AppText.get(R.string.settings_import_preset_many, "presets")
        }
        val radioWord = if (namedRadios == 1) {
            AppText.get(R.string.settings_import_radio_one, "named radio")
        } else {
            AppText.get(R.string.settings_import_radio_many, "named radios")
        }
        val watchWord = if (signatureWatches == 1) {
            AppText.get(R.string.settings_import_watch_one, "signature watch")
        } else {
            AppText.get(R.string.settings_import_watch_many, "signature watches")
        }
        return AppText.get(
            R.string.settings_import_summary,
            "Restored Settings, the current filter, and %1\$d %2\$s. %3\$d %4\$s, %5\$d %6\$s.",
            presets,
            presetWord,
            namedRadios,
            radioWord,
            signatureWatches,
            watchWord,
        )
    }
}

object SettingsExchange {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun pack(
        settings: AppSettings,
        filter: FilterState,
        presets: List<FilterPreset>,
        watchlist: List<WatchTarget>,
        hiddenPresetIds: Set<String>,
        appVersion: String,
        exportedAt: String,
    ): SettingsPack = SettingsPack(
        format = SettingsPack.FORMAT,
        formatVersion = SettingsPack.FORMAT_VERSION,
        exportedAt = exportedAt,
        appVersion = appVersion,
        settings = settings,
        filter = filter,
        presets = presets,
        watchlist = watchlist,
        hiddenPresetIds = hiddenPresetIds,
    )

    fun encode(pack: SettingsPack): String = json.encodeToString(SettingsPack.serializer(), pack)

    fun parse(text: String): SettingsPack {
        val trimmed = text.trim().trimStart('\uFEFF')
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException(
                AppText.get(R.string.settings_import_empty, "This file is empty."),
            )
        }
        val pack = try {
            json.decodeFromString(SettingsPack.serializer(), trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                AppText.get(
                    R.string.settings_import_not_pack,
                    "Not a Fieldwatch settings pack. Export from Settings → Export settings.",
                ),
                e,
            )
        }
        if (pack.format == SignaturePack.FORMAT || pack.format == SignaturePack.LEGACY_FORMAT) {
            throw IllegalArgumentException(
                AppText.get(
                    R.string.settings_import_is_signature,
                    "That is a signature pack. Use Import signatures.",
                ),
            )
        }
        if (pack.format != SettingsPack.FORMAT) {
            throw IllegalArgumentException(
                AppText.get(
                    R.string.settings_import_not_pack_json,
                    "Not a Fieldwatch settings pack (open a fieldwatch-settings JSON file).",
                ),
            )
        }
        return pack
    }

    /**
     * Replace Settings, the current filter, presets, and watchlist.
     * Keep the catalog, logs, GPS, already-seen keys, the local disclaimer click-through,
     * and whether this phone already showed the Live tour.
     */
    fun apply(local: PersistedConfig, pack: SettingsPack): Pair<PersistedConfig, SettingsImportResult> {
        val next = local.copy(
            settings = pack.settings.copy(
                disclaimerAccepted = local.settings.disclaimerAccepted,
                disclaimerRev = local.settings.disclaimerRev,
                liveTourDone = local.settings.liveTourDone,
                darkTheme = true,
                scanControlsExpanded = false,
            ),
            filter = pack.filter,
            presets = pack.presets,
            watchlist = pack.watchlist,
            hiddenPresetIds = pack.hiddenPresetIds,
        )
        val result = SettingsImportResult(
            namedRadios = pack.watchlist.count { !it.deviceKey.isNullOrBlank() },
            signatureWatches = pack.watchlist.count { !it.fleetId.isNullOrBlank() },
            presets = pack.presets.size,
        )
        return next to result
    }

    /**
     * Null when this import would not turn the TAK / CoT feed on and would not
     * change its destination. The feed is plain UDP.
     */
    fun takImportWarning(local: AppSettings, incoming: AppSettings): String? {
        val turnsOn = incoming.takEnabled && !local.takEnabled
        val host = incoming.takHost.trim().ifBlank {
            AppText.get(R.string.settings_import_blank, "(blank)")
        }
        val localHost = local.takHost.trim()
        val destChanges = !incoming.takHost.trim().equals(localHost, ignoreCase = true) ||
            incoming.takPort != local.takPort
        if (!turnsOn && !destChanges) return null
        val dest = "$host:${incoming.takPort}"
        return when {
            turnsOn && destChanges ->
                AppText.get(
                    R.string.settings_tak_warning_on_and_dest,
                    "This file turns the TAK / CoT feed on and sets the destination to %1\$s. The feed is plain UDP.",
                    dest,
                )
            turnsOn ->
                AppText.get(
                    R.string.settings_tak_warning_on,
                    "This file turns the TAK / CoT feed on. It sends plain UDP to %1\$s.",
                    dest,
                )
            incoming.takEnabled ->
                AppText.get(
                    R.string.settings_tak_warning_dest_on,
                    "This file changes the TAK / CoT destination to %1\$s. The feed is plain UDP and stays on.",
                    dest,
                )
            else ->
                AppText.get(
                    R.string.settings_tak_warning_dest_off,
                    "This file changes the TAK / CoT destination to %1\$s. The feed stays off until you turn it on.",
                    dest,
                )
        }
    }
}
