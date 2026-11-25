package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.NavOptions
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.BasePreferenceFragmentCompat
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiDubstatusSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper.getNameNextToFlagEmoji
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsProviders : BasePreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_providers)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_providers, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        getPref(R.string.display_sub_key)?.setOnPreferenceClickListener {
            activity?.getApiDubstatusSettings()?.let { current ->
                val dublist = DubStatus.entries
                val names = dublist.map { it.name }

                val currentList = ArrayList<Int>()
                for (i in current) {
                    currentList.add(dublist.indexOf(i))
                }

                activity?.showMultiDialog(
                    names,
                    currentList,
                    getString(R.string.display_subbed_dubbed_settings),
                    {}
                ) { selectedList ->
                    APIRepository.dubStatusActive = selectedList.map { dublist[it] }.toHashSet()
                    settingsManager.edit {
                        putStringSet(
                            getString(R.string.display_sub_key),
                            selectedList.map { names[it] }.toMutableSet()
                        )
                    }
                }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.test_providers_key)?.setOnPreferenceClickListener {
            // Somehow animations do not work without this.
            val options = NavOptions.Builder()
                .setEnterAnim(R.anim.enter_anim)
                .setExitAnim(R.anim.exit_anim)
                .setPopEnterAnim(R.anim.pop_enter)
                .setPopExitAnim(R.anim.pop_exit)
                .build()

            this@SettingsProviders.findNavController()
                .navigate(R.id.navigation_test_providers, null, options)
            true
        }

        // Zayn Play: Test all providers (load/content/video + auto-off).
        // Runs ZaynProviderTest (reuses TestingUtils) over the enabled set
        // ("zayn_enabled_providers", or all installed providers when empty).
        findPreference<Preference>("zayn_test_all_providers")?.setOnPreferenceClickListener { pref ->
            val ctx = context ?: return@setOnPreferenceClickListener false
            pref.summary = getString(R.string.zayn_test_all_running)
            viewLifecycleOwner.lifecycleScope.launch {
                val results = withContext(Dispatchers.IO) { ZaynProviderTest.runAllTests(ctx) }
                pref.summary = getString(R.string.zayn_test_all_providers_summary)
                val failedVideo = results.count { !it.videoOk }
                val mark: (Boolean) -> String = { ok -> if (ok) "✓" else "✗" }
                val lines = results.joinToString("\n") { result ->
                    "${result.name}: load ${mark(result.loadOk)} " +
                        "content ${mark(result.contentOk)} " +
                        "video ${mark(result.videoOk)}" +
                        if (result.autoOff) " (${getString(R.string.zayn_test_all_auto_off)})" else ""
                }
                val message = if (results.isEmpty()) {
                    getString(R.string.zayn_test_all_none)
                } else {
                    getString(R.string.zayn_test_all_done, results.size, failedVideo) +
                        "\n\n" + lines
                }
                AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                    .setTitle(R.string.zayn_test_all_results_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                if (results.isNotEmpty()) {
                    showToast(
                        getString(R.string.zayn_test_all_done, results.size, failedVideo),
                        Toast.LENGTH_LONG
                    )
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.prefer_media_type_key)?.setOnPreferenceClickListener {
            val names = enumValues<TvType>().sorted().map { it.name }
            val default =
                enumValues<TvType>().sorted().filter { it != TvType.NSFW }.map { it.ordinal }
            val defaultSet = default.map { it.toString() }.toSet()
            val currentList = try {
                settingsManager.getStringSet(getString(R.string.prefer_media_type_key), defaultSet)
                    ?.map {
                        it.toInt()
                    }
            } catch (e: Throwable) {
                null
            } ?: default

            activity?.showMultiDialog(
                names,
                currentList,
                getString(R.string.preferred_media_settings),
                {}
            ) { selectedList ->
                settingsManager.edit {
                    putStringSet(
                        getString(R.string.prefer_media_type_key),
                        selectedList.map { it.toString() }.toMutableSet()
                    )
                }
                DataStoreHelper.currentHomePage = null
                //(context ?: CloudStreamApp.context)?.let { ctx -> app.initClient(ctx) }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.
        provider_lang_key)?.setOnPreferenceClickListener {
            activity?.getApiProviderLangSettings()?.let { currentLangTags ->
                val languagesTagName = APIHolder.apis.withLock {
                    listOf(Pair(AllLanguagesName, getString(R.string.all_languages_preference))) +
                        APIHolder.apis.map { Pair(it.lang, getNameNextToFlagEmoji(it.lang) ?: it.lang) }
                            .toSet().sortedBy { it.second.substringAfter("\u00a0").lowercase() }
                }

                val currentIndexList = currentLangTags.map { langTag ->
                    languagesTagName.indexOfFirst { lang -> lang.first == langTag }
                }

                activity?.showMultiDialog(
                    languagesTagName.map { it.second },
                    currentIndexList,
                    getString(R.string.provider_lang_settings),
                    {}
                ) { selectedList ->
                    // Zayn All-exclusivity, mirroring SetupFragmentProviderLanguage:
                    // showMultiDialog has no live per-item callback (its item
                    // clicks are handled internally in SingleSelectionHelper),
                    // so exclusivity is enforced here at Apply time, on the
                    // exact set that gets persisted — same stored result as
                    // setup's live unticking:
                    // - "All languages" picked (alone or with individuals):
                    //   save only AllLanguagesName, i.e. All selected, rest unticked.
                    // - An individual picked: AllLanguagesName is dropped.
                    // - Every individual picked manually: that is "all", so save
                    //   only AllLanguagesName (All shows checked next open).
                    val allIndex = languagesTagName.indexOfFirst { it.first == AllLanguagesName }
                    val selectedTags = selectedList.map { languagesTagName[it].first }
                    val individualTags =
                        languagesTagName.filter { it.first != AllLanguagesName }.map { it.first }.toSet()
                    val normalized: Set<String> = when {
                        allIndex >= 0 && selectedList.contains(allIndex) ->
                            setOf(AllLanguagesName)

                        selectedTags.isNotEmpty() &&
                            selectedTags.filter { it != AllLanguagesName }.toSet() == individualTags ->
                            setOf(AllLanguagesName)

                        else -> selectedTags.toSet()
                    }
                    settingsManager.edit {
                        putStringSet(
                            getString(R.string.provider_lang_key),
                            normalized
                        )
                    }
                    // APIRepository.providersActive = it.context.getApiSettings()
                }
            }

            return@setOnPreferenceClickListener true
        }
    }
}
