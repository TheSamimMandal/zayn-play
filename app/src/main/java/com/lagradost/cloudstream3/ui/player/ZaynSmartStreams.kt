package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Nuvio Smart-Streams + player choice. Single owner of the Zayn stream keys
 * (literal SharedPreferences keys — do not rename, another agent reads them):
 *
 * - [KEY_STREAM_MODE]: "auto" | "manual", default "auto".
 * - [KEY_PREF_RESOLUTION]: "auto" | "1080" | "720" | "480" | "360", default "auto".
 * - [KEY_AUDIO_PRIMARY]: language code, default "en".
 * - [KEY_AUDIO_SECONDARY]: language code, default "" (= none).
 * - [KEY_STREAM_TIMEOUT]: "instant" | "5" | "10" | "15" | "30" | "unlimited",
 *   default "10".
 * - [KEY_REUSE_LAST_LINK]: "true" | "false", default "true".
 * - [KEY_PLAYER_CHOICE]: "internal" | "external", default "internal".
 * - [KEY_EXTERNAL_PLAYER]: package name, default "org.videolan.vlc".
 * - Last-link memory: [lastLinkKey] = "zayn_last_link_<title-hash>" -> link url.
 *
 * Audio language choices come from the same 11-language list as the app
 * language picker ([com.lagradost.cloudstream3.ui.settings.appLanguages]);
 * [AUDIO_ALIASES] maps each code to the Latin words providers actually put in
 * link names, because [ExtractorLink.audioTracks] carries no language field.
 */
object ZaynSmartStreams {
    const val KEY_STREAM_MODE = "zayn_stream_mode"
    const val KEY_PREF_RESOLUTION = "zayn_pref_resolution"
    const val KEY_AUDIO_PRIMARY = "zayn_audio_primary"
    const val KEY_AUDIO_SECONDARY = "zayn_audio_secondary"
    const val KEY_STREAM_TIMEOUT = "zayn_stream_timeout"
    const val KEY_REUSE_LAST_LINK = "zayn_reuse_last_link"
    const val KEY_PLAYER_CHOICE = "zayn_player_choice"
    const val KEY_EXTERNAL_PLAYER = "zayn_external_player"

    /** Prefix for per-title last-link memory: key = prefix + title.hashCode(). */
    const val KEY_LAST_LINK_PREFIX = "zayn_last_link_"

    const val DEFAULT_EXTERNAL_PLAYER = "org.videolan.vlc"

    /** External player options offered in Settings > Player (name to package). */
    val EXTERNAL_PLAYER_OPTIONS: List<Pair<String, String>> = listOf(
        "VLC" to "org.videolan.vlc",
        "MX Player" to "com.mxtech.videoplayer.ad",
        "JustPlayer" to "com.brouken.player",
    )

    fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun streamMode(prefs: SharedPreferences): String =
        prefs.getString(KEY_STREAM_MODE, "auto") ?: "auto"

    fun isManualMode(prefs: SharedPreferences): Boolean = streamMode(prefs) == "manual"

    /** Preferred resolution as an int quality (1080/720/…), or null for "auto". */
    fun prefResolutionInt(prefs: SharedPreferences): Int? {
        val raw = prefs.getString(KEY_PREF_RESOLUTION, "auto") ?: "auto"
        return if (raw == "auto") null else raw.toIntOrNull()
    }

    fun audioPrimary(prefs: SharedPreferences): String =
        prefs.getString(KEY_AUDIO_PRIMARY, "en") ?: "en"

    fun audioSecondary(prefs: SharedPreferences): String =
        prefs.getString(KEY_AUDIO_SECONDARY, "") ?: ""

    fun reuseLastLink(prefs: SharedPreferences): Boolean =
        prefs.getString(KEY_REUSE_LAST_LINK, "true") != "false"

    /**
     * Discovery timeout in seconds, or null for "unlimited" (no cap).
     * "instant" means fail fast: a 1s cap on waiting for sources.
     */
    fun streamTimeoutSeconds(prefs: SharedPreferences): Long? {
        return when (val raw = prefs.getString(KEY_STREAM_TIMEOUT, "10") ?: "10") {
            "instant" -> 1L
            "unlimited" -> null
            else -> raw.toLongOrNull() ?: 10L
        }
    }

    /**
     * Runs source discovery under the configured stream timeout. Returns null
     * when the timeout fires (caller treats it as "no sources in time").
     * WIRING NEEDED (outside this agent's file list): wrap the link-discovery
     * call with this — GeneratorPlayer/LinkGenerator loading path, i.e. where
     * viewModel.loadLinks() -> generator.generateLinks() runs. See report.
     */
    suspend fun <T> withStreamTimeout(prefs: SharedPreferences, block: suspend () -> T): T? {
        val seconds = streamTimeoutSeconds(prefs) ?: return block()
        return withTimeoutOrNull(seconds * 1000) { block() }
    }

    // ---- Smart-Streams ranking (used by VideoState.sortLinks) ----

    /**
     * Rank for a link quality against the preferred resolution. Lower is better.
     * Exact match first, then nearest below the target, then above it; unknown
     * quality (<= 0) sorts after every known quality. "auto" -> all rank 0.
     */
    fun resolutionRank(quality: Int, prefResolution: Int?): Int {
        if (prefResolution == null) return 0
        if (quality <= 0) return 100_000
        return when {
            quality == prefResolution -> 0
            quality < prefResolution -> prefResolution - quality
            else -> 1_000 + (quality - prefResolution)
        }
    }

    /** Latin words providers put in link names for each of the 11 app languages. */
    private val AUDIO_ALIASES: Map<String, List<String>> = mapOf(
        "en" to listOf("english"),
        "bn" to listOf("bengali", "bangla"),
        "hi" to listOf("hindi"),
        "mr" to listOf("marathi"),
        "te" to listOf("telugu"),
        "ta" to listOf("tamil"),
        "gu" to listOf("gujarati"),
        "ur" to listOf("urdu"),
        "kn" to listOf("kannada"),
        "or" to listOf("odia", "oriya"),
        "ml" to listOf("malayalam"),
    )

    private fun matchesLanguage(haystack: String, code: String): Boolean {
        val aliases = AUDIO_ALIASES[code.lowercase()] ?: listOf(code)
        return aliases.any { alias ->
            Regex("\\b${Regex.escape(alias)}\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(haystack)
        }
    }

    /**
     * Audio rank for a link. 0 = primary language, 1 = secondary, 2 = neither.
     * Matches Latin language words in the link name (and audio-track urls);
     * ExtractorLink.audioTracks carries no language field, so name matching is
     * the only signal available.
     */
    fun audioScore(link: ExtractorLink?, primary: String, secondary: String): Int {
        if (link == null) return 2
        val haystack = buildString {
            append(link.name)
            link.audioTracks.forEach { append(' ').append(it.url) }
        }
        if (haystack.isBlank()) return 2
        if (primary.isNotBlank() && matchesLanguage(haystack, primary)) return 0
        if (secondary.isNotBlank() && matchesLanguage(haystack, secondary)) return 1
        return 2
    }

    /** Cache signature for sortLinks: ranking inputs that must invalidate the cache. */
    fun prefsSignature(prefs: SharedPreferences): String = listOf(
        streamMode(prefs),
        prefs.getString(KEY_PREF_RESOLUTION, "auto"),
        audioPrimary(prefs),
        audioSecondary(prefs),
        prefs.getString(KEY_REUSE_LAST_LINK, "true"),
    ).joinToString("|")

    // ---- Reuse last link ----

    fun lastLinkKey(title: String?): String =
        KEY_LAST_LINK_PREFIX + (title ?: "").hashCode()

    fun getLastLinkUrl(prefs: SharedPreferences, title: String?): String? =
        prefs.getString(lastLinkKey(title), null)?.takeIf { it.isNotBlank() }

    /**
     * Remember a link url as the last working link for a title.
     * WIRING NEEDED (outside this agent's file list): call from
     * GeneratorPlayer.loadLink() after a link starts playing successfully,
     * with title = viewModel.state.generatorState?.response?.name.
     */
    fun rememberLastLink(prefs: SharedPreferences, title: String?, linkUrl: String) {
        if (linkUrl.isBlank()) return
        prefs.edit { putString(lastLinkKey(title), linkUrl) }
    }

    // ---- Player choice ----

    sealed class PlayerChoiceResult {
        /** Built-in player; player_default_key set to "" (CloudStream internal). */
        object AppliedInternal : PlayerChoiceResult()

        /** External player resolved to an installed VideoClickAction. */
        data class AppliedExternal(val actionName: String) : PlayerChoiceResult()

        /**
         * Choice saved under the zayn keys, but no installed player action
         * exists for that package (e.g. MX Player has no VideoClickAction in
         * this codebase) — playback falls back to the built-in player until
         * such an action is added. See report.
         */
        data class SavedButUnsupported(val packageName: String) : PlayerChoiceResult()
    }

    fun playerChoice(prefs: SharedPreferences): String =
        prefs.getString(KEY_PLAYER_CHOICE, "internal") ?: "internal"

    fun externalPlayerPackage(prefs: SharedPreferences): String =
        prefs.getString(KEY_EXTERNAL_PLAYER, DEFAULT_EXTERNAL_PLAYER)
            ?: DEFAULT_EXTERNAL_PLAYER

    /**
     * Applies a player choice: always writes the zayn keys, and mirrors the
     * choice into CloudStream's existing default-player setting
     * (player_default_key), which is what EpisodeAdapter.getPlayerAction()
     * already honours — "" = built-in player, uniqueId = external player
     * action. Per-video "Play with external player" already exists via
     * VideoClickActionHolder.makeOptionMap() (ResultViewModel2) and is untouched.
     */
    fun applyPlayerChoice(context: Context, choice: String, externalPackage: String): PlayerChoiceResult {
        val prefs = prefs(context)
        prefs.edit {
            putString(KEY_PLAYER_CHOICE, choice)
            putString(KEY_EXTERNAL_PLAYER, externalPackage)
        }
        val defaultKey = context.getString(R.string.player_default_key)
        if (choice != "external") {
            prefs.edit { putString(defaultKey, "") }
            return PlayerChoiceResult.AppliedInternal
        }
        val action = VideoClickActionHolder.getPlayers(context)
            .filterIsInstance<OpenInAppAction>()
            .firstOrNull { it.packageName == externalPackage }
        if (action == null) {
            // No installed action for this package: fall back to built-in for
            // actual playback, but keep the user's zayn choice saved.
            prefs.edit { putString(defaultKey, "") }
            return PlayerChoiceResult.SavedButUnsupported(externalPackage)
        }
        prefs.edit { putString(defaultKey, action.uniqueId()) }
        return PlayerChoiceResult.AppliedExternal(
            action.name.asStringNull(context) ?: action.packageName
        )
    }
}
