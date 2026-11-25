package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.view.View
import android.widget.Toast
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.BasePreferenceFragmentCompat
import com.lagradost.cloudstream3.ui.player.ZaynSmartStreams
import com.lagradost.cloudstream3.ui.player.source_priority.QualityProfileDialog
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getFolderSize
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.hideOn
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.hidePrefs
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.ui.subtitles.ChromecastSubtitlesFragment
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

class SettingsPlayer : BasePreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_player)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_player, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        //Hide specific prefs on TV/EMULATOR
        hidePrefs(
            listOf(
                R.string.pref_category_gestures_key,
                R.string.rotate_video_key,
                R.string.auto_rotate_video_key,
                R.string.speedup_key
            ),
            TV or EMULATOR
        )

        getPref(R.string.preview_seekbar_key)?.hideOn(TV)
        getPref(R.string.pref_category_android_tv_key)?.hideOn(PHONE)

        getPref(R.string.video_buffer_length_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.video_buffer_length_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_length_values)

            val currentPrefSize =
                settingsManager.getInt(getString(R.string.video_buffer_length_key), 0)

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefSize),
                getString(R.string.video_buffer_length_settings),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.video_buffer_length_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.prefer_limit_title_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.limit_title_pref_names)
            val prefValues = resources.getIntArray(R.array.limit_title_pref_values)
            val current = settingsManager.getInt(getString(R.string.prefer_limit_title_key), 0)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.limit_title),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.prefer_limit_title_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.software_decoding_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.software_decoding_switch)
            val prefValues = resources.getIntArray(R.array.software_decoding_switch_values)
            val current = settingsManager.getInt(getString(R.string.software_decoding_key), -1)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.software_decoding),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.software_decoding_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.prefer_limit_show_player_info)?.setOnPreferenceClickListener {
            val ctx = context ?: return@setOnPreferenceClickListener false

            val prefNames = resources.getStringArray(R.array.title_info_pref_names)
            val keys = resources.getStringArray(R.array.title_info_pref_values)

            // Player defaults
            val playerDefaults = mapOf(
                ctx.getString(R.string.show_name_key) to true,
                ctx.getString(R.string.show_resolution_key) to true,
                ctx.getString(R.string.show_media_info_key) to false
            )

            val selectedIndices = keys.map { key ->
                settingsManager.getBoolean(key, playerDefaults[key] ?: false)
            }.mapIndexedNotNull { index, enabled ->
                if (enabled) index else null
            }

            activity?.showMultiDialog(
                prefNames.toList(),
                selectedIndices,
                getString(R.string.limit_title_rez),
                {}
            ) { selected ->
                settingsManager.edit {
                    for ((index, key) in keys.withIndex()) {
                        putBoolean(key, selected.contains(index))
                    }
                }
            }

            true
        }

        getPref(R.string.hide_player_control_names_key)?.hideOn(TV)

        getPref(R.string.quality_pref_key)?.setOnPreferenceClickListener {
            val prefValues = Qualities.entries.map { it.value }.reversed().toMutableList()
            prefValues.remove(Qualities.Unknown.value)

            val prefNames = prefValues.map { Qualities.getStringByInt(it) }

            val currentQuality =
                settingsManager.getInt(
                    getString(R.string.quality_pref_key),
                    Qualities.entries.last().value
                )

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentQuality),
                getString(R.string.watch_quality_pref),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.quality_pref_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.quality_pref_mobile_data_key)?.setOnPreferenceClickListener {
            val prefValues = Qualities.entries.map { it.value }.reversed().toMutableList()
            prefValues.remove(Qualities.Unknown.value)

            val prefNames = prefValues.map { Qualities.getStringByInt(it) }

            val currentQuality =
                settingsManager.getInt(
                    getString(R.string.quality_pref_mobile_data_key),
                    Qualities.entries.last().value
                )

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentQuality),
                getString(R.string.watch_quality_pref_data),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.quality_pref_mobile_data_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.player_default_key)?.setOnPreferenceClickListener {
            // Pair each player with its display name, dropping any with none,
            // which would mean something is definitely wrong.
            val players = VideoClickActionHolder.getPlayers(activity).mapNotNull { player ->
                (player.name.asStringNull(activity) ?: player::class.simpleName)?.let { player to it }
            }

            val prefNames = buildList {
                add(getString(R.string.player_settings_play_in_app)) // built-in player display name
                addAll(players.map { (_, name) -> name })
            }

            val prefValues = buildList {
                add("") // "" = built-in player, matches default
                addAll(players.map { (player, _) -> player.uniqueId() })
            }

            val current = settingsManager.getString(getString(R.string.player_default_key), "") ?: ""
            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current), // finds index of currently selected player
                getString(R.string.player_pref),
                true,
                {},
            ) {
                settingsManager.edit {
                    putString(getString(R.string.player_default_key), prefValues[it])
                }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.subtitle_settings_key)?.setOnPreferenceClickListener {
            SubtitlesFragment.push(activity, false)
            return@setOnPreferenceClickListener true
        }

        // ---- Zayn Play: Nuvio Smart-Streams + player choice ----
        // Keys are the literal ZaynSmartStreams consts (another agent reads
        // them verbatim). Values are stored as strings, like the spec says.
        fun zaynPref(key: String): Preference? = findPreference(key)

        fun langDisplayName(code: String): String {
            if (code.isBlank()) return getString(R.string.zayn_audio_none)
            return appLanguages.firstOrNull { it.second == code }?.first ?: code
        }

        fun refreshZaynSummaries() {
            val streamMode = ZaynSmartStreams.streamMode(settingsManager)
            zaynPref(ZaynSmartStreams.KEY_STREAM_MODE)?.summary =
                if (streamMode == "manual") getString(R.string.zayn_stream_mode_manual)
                else getString(R.string.zayn_stream_mode_auto)

            val resolution = settingsManager.getString(ZaynSmartStreams.KEY_PREF_RESOLUTION, "auto") ?: "auto"
            zaynPref(ZaynSmartStreams.KEY_PREF_RESOLUTION)?.summary =
                if (resolution == "auto") getString(R.string.zayn_pref_resolution_auto) else "${resolution}p"

            zaynPref(ZaynSmartStreams.KEY_AUDIO_PRIMARY)?.summary =
                langDisplayName(ZaynSmartStreams.audioPrimary(settingsManager))
            zaynPref(ZaynSmartStreams.KEY_AUDIO_SECONDARY)?.summary =
                langDisplayName(ZaynSmartStreams.audioSecondary(settingsManager))

            val timeout = settingsManager.getString(ZaynSmartStreams.KEY_STREAM_TIMEOUT, "10") ?: "10"
            zaynPref(ZaynSmartStreams.KEY_STREAM_TIMEOUT)?.summary = when (timeout) {
                "instant" -> getString(R.string.zayn_stream_timeout_instant)
                "unlimited" -> getString(R.string.zayn_stream_timeout_unlimited)
                else -> getString(R.string.zayn_stream_timeout_seconds, timeout)
            }

            zaynPref(ZaynSmartStreams.KEY_REUSE_LAST_LINK)?.summary =
                if (ZaynSmartStreams.reuseLastLink(settingsManager)) getString(R.string.zayn_on)
                else getString(R.string.zayn_off)

            val choice = ZaynSmartStreams.playerChoice(settingsManager)
            zaynPref(ZaynSmartStreams.KEY_PLAYER_CHOICE)?.summary =
                if (choice == "external") getString(R.string.zayn_player_choice_external)
                else getString(R.string.zayn_player_choice_internal)
            zaynPref(ZaynSmartStreams.KEY_EXTERNAL_PLAYER)?.summary =
                ZaynSmartStreams.EXTERNAL_PLAYER_OPTIONS
                    .firstOrNull { it.second == ZaynSmartStreams.externalPlayerPackage(settingsManager) }
                    ?.first ?: ZaynSmartStreams.externalPlayerPackage(settingsManager)
        }

        fun showZaynListDialog(key: String, titleRes: Int, names: List<String>, values: List<String>, currentValue: String) {
            activity?.showBottomDialog(
                names,
                values.indexOf(currentValue),
                getString(titleRes),
                true,
                {}
            ) {
                settingsManager.edit { putString(key, values[it]) }
                refreshZaynSummaries()
            }
        }

        refreshZaynSummaries()

        zaynPref(ZaynSmartStreams.KEY_STREAM_MODE)?.setOnPreferenceClickListener {
            showZaynListDialog(
                ZaynSmartStreams.KEY_STREAM_MODE,
                R.string.zayn_stream_mode_title,
                resources.getStringArray(R.array.zayn_stream_mode_names).toList(),
                resources.getStringArray(R.array.zayn_stream_mode_values).toList(),
                ZaynSmartStreams.streamMode(settingsManager)
            )
            true
        }

        zaynPref(ZaynSmartStreams.KEY_PREF_RESOLUTION)?.setOnPreferenceClickListener {
            showZaynListDialog(
                ZaynSmartStreams.KEY_PREF_RESOLUTION,
                R.string.zayn_pref_resolution_title,
                resources.getStringArray(R.array.zayn_pref_resolution_names).toList(),
                resources.getStringArray(R.array.zayn_pref_resolution_values).toList(),
                settingsManager.getString(ZaynSmartStreams.KEY_PREF_RESOLUTION, "auto") ?: "auto"
            )
            true
        }

        zaynPref(ZaynSmartStreams.KEY_AUDIO_PRIMARY)?.setOnPreferenceClickListener {
            val codes = appLanguages.map { it.second }
            showZaynListDialog(
                ZaynSmartStreams.KEY_AUDIO_PRIMARY,
                R.string.zayn_audio_primary_title,
                appLanguages.map { it.first },
                codes,
                ZaynSmartStreams.audioPrimary(settingsManager)
            )
            true
        }

        zaynPref(ZaynSmartStreams.KEY_AUDIO_SECONDARY)?.setOnPreferenceClickListener {
            val names = listOf(getString(R.string.zayn_audio_none)) + appLanguages.map { it.first }
            val values = listOf("") + appLanguages.map { it.second }
            showZaynListDialog(
                ZaynSmartStreams.KEY_AUDIO_SECONDARY,
                R.string.zayn_audio_secondary_title,
                names,
                values,
                ZaynSmartStreams.audioSecondary(settingsManager)
            )
            true
        }

        zaynPref(ZaynSmartStreams.KEY_STREAM_TIMEOUT)?.setOnPreferenceClickListener {
            showZaynListDialog(
                ZaynSmartStreams.KEY_STREAM_TIMEOUT,
                R.string.zayn_stream_timeout_title,
                resources.getStringArray(R.array.zayn_stream_timeout_names).toList(),
                resources.getStringArray(R.array.zayn_stream_timeout_values).toList(),
                settingsManager.getString(ZaynSmartStreams.KEY_STREAM_TIMEOUT, "10") ?: "10"
            )
            true
        }

        zaynPref(ZaynSmartStreams.KEY_REUSE_LAST_LINK)?.setOnPreferenceClickListener {
            val values = listOf("true", "false")
            val current = if (ZaynSmartStreams.reuseLastLink(settingsManager)) "true" else "false"
            showZaynListDialog(
                ZaynSmartStreams.KEY_REUSE_LAST_LINK,
                R.string.zayn_reuse_last_link_title,
                listOf(getString(R.string.zayn_on), getString(R.string.zayn_off)),
                values,
                current
            )
            true
        }

        zaynPref(ZaynSmartStreams.KEY_PLAYER_CHOICE)?.setOnPreferenceClickListener {
            val ctx = requireContext()
            val values = resources.getStringArray(R.array.zayn_player_choice_values).toList()
            activity?.showBottomDialog(
                resources.getStringArray(R.array.zayn_player_choice_names).toList(),
                values.indexOf(ZaynSmartStreams.playerChoice(settingsManager)),
                getString(R.string.zayn_player_choice_title),
                true,
                {}
            ) {
                val result = ZaynSmartStreams.applyPlayerChoice(
                    ctx, values[it], ZaynSmartStreams.externalPlayerPackage(settingsManager)
                )
                if (result is ZaynSmartStreams.PlayerChoiceResult.SavedButUnsupported) {
                    showToast(R.string.zayn_external_player_not_found, Toast.LENGTH_LONG)
                }
                refreshZaynSummaries()
            }
            true
        }

        zaynPref(ZaynSmartStreams.KEY_EXTERNAL_PLAYER)?.setOnPreferenceClickListener {
            val ctx = requireContext()
            val values = resources.getStringArray(R.array.zayn_external_player_values).toList()
            activity?.showBottomDialog(
                resources.getStringArray(R.array.zayn_external_player_names).toList(),
                values.indexOf(ZaynSmartStreams.externalPlayerPackage(settingsManager)),
                getString(R.string.zayn_external_player_title),
                true,
                {}
            ) {
                val pkg = values[it]
                if (ZaynSmartStreams.playerChoice(settingsManager) == "external") {
                    val result = ZaynSmartStreams.applyPlayerChoice(ctx, "external", pkg)
                    if (result is ZaynSmartStreams.PlayerChoiceResult.SavedButUnsupported) {
                        showToast(R.string.zayn_external_player_not_found, Toast.LENGTH_LONG)
                    }
                } else {
                    settingsManager.edit { putString(ZaynSmartStreams.KEY_EXTERNAL_PLAYER, pkg) }
                }
                refreshZaynSummaries()
            }
            true
        }

        getPref(R.string.subtitle_settings_chromecast_key)?.setOnPreferenceClickListener {
            ChromecastSubtitlesFragment.push(activity, false)
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.player_source_priority_key)?.setOnPreferenceClickListener {
            ioSafe {
                val defaultSources = QualityProfileDialog.getAllDefaultSources()
                val activity = activity ?: return@ioSafe
                activity.runOnUiThread {
                    QualityProfileDialog(
                        activity,
                        R.style.DialogFullscreenPlayer,
                        defaultSources,
                    ).show()
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.video_buffer_disk_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.video_buffer_size_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_size_values)

            val currentPrefSize =
                settingsManager.getInt(getString(R.string.video_buffer_disk_key), 0)

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefSize),
                getString(R.string.video_buffer_disk_settings),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.video_buffer_disk_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }
        getPref(R.string.video_buffer_size_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.video_buffer_size_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_size_values)

            val currentPrefSize =
                settingsManager.getInt(getString(R.string.video_buffer_size_key), 0)

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefSize),
                getString(R.string.video_buffer_size_settings),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.video_buffer_size_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.video_buffer_clear_key)?.let { pref ->
            val cacheDir = context?.cacheDir ?: return@let

            fun updateSummary() {
                try {
                    pref.summary = formatShortFileSize(pref.context, getFolderSize(cacheDir))
                } catch (e: Exception) {
                    logError(e)
                }
            }

            updateSummary()

            pref.setOnPreferenceClickListener {
                try {
                    cacheDir.deleteRecursively()
                    updateSummary()
                } catch (e: Exception) {
                    logError(e)
                }
                return@setOnPreferenceClickListener true
            }
        }
    }
}
