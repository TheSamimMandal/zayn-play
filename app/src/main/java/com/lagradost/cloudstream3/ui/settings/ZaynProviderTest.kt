package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.ui.home.ZaynProviders
import com.lagradost.cloudstream3.utils.TestingUtils

/**
 * Zayn "Test all providers": tests every enabled provider and auto-turns off
 * any whose video test fails.
 *
 * Enabled set = DataStore key "zayn_enabled_providers" (comma-separated
 * MainAPI.name values), owned by [ZaynProviders] — this file MUST read/write
 * it only through [ZaynProviders.getEnabledProviders] /
 * [ZaynProviders.setEnabledProviders] so the tabs, the repo toggles and the
 * tests all see the same set. When the set is empty/absent, ALL installed
 * providers are tested.
 *
 * Per provider, reusing CloudStream's existing health checks
 * ([TestingUtils]):
 * - load ✓/✗: [TestingUtils.testHomepage] succeeds (homepage loads).
 * - content ✓/✗: the homepage actually returns items
 *   ([TestingUtils.TestResultList] with non-empty results).
 * - video ✓/✗: [TestingUtils.runSingleProviderTest] succeeds — it loads a
 *   search result and loads its video links, so success means video links
 *   were found.
 *
 * Auto-off: when a provider that was in the enabled set fails the video test
 * it is removed from "zayn_enabled_providers" and reported with
 * [ZaynProviderTestResult.autoOff] = true. When the set was empty (test-all
 * mode) and at least one provider fails video, the set is written as
 * (all tested names minus video-failed names) so the failures stay off.
 *
 * NOTE: the stream timeout (zayn_stream_timeout) is deliberately NOT applied
 * here — provider health tests legitimately take longer than a playback
 * source search. The timeout applies to playback source discovery only.
 */
object ZaynProviderTest {
    data class ZaynProviderTestResult(
        val name: String,
        val loadOk: Boolean,
        val contentOk: Boolean,
        val videoOk: Boolean,
        val autoOff: Boolean,
    )

    fun getEnabledProviderNames(context: Context): Set<String> {
        return ZaynProviders.getEnabledProviders()
    }

    suspend fun runAllTests(context: Context): List<ZaynProviderTestResult> {
        val enabled = getEnabledProviderNames(context)
        val apis = APIHolder.apis.withLock { APIHolder.apis.toList() }
        val toTest = if (enabled.isEmpty()) apis else apis.filter { it.name in enabled }
        if (toTest.isEmpty()) return emptyList()

        val results = mutableListOf<ZaynProviderTestResult>()
        for (api in toTest) {
            val result = try {
                val homepage = TestingUtils.testHomepage(api, TestingUtils.Logger())
                val loadOk = homepage.success
                val contentOk = (homepage as? TestingUtils.TestResultList)
                    ?.results?.isNotEmpty() == true
                val videoOk = if (!loadOk) {
                    false
                } else {
                    TestingUtils.runSingleProviderTest(api, TestingUtils.Logger()).success
                }
                val wasEnabled = api.name in enabled
                ZaynProviderTestResult(api.name, loadOk, contentOk, videoOk, autoOff = wasEnabled && !videoOk)
            } catch (_: Exception) {
                ZaynProviderTestResult(api.name, loadOk = false, contentOk = false, videoOk = false, autoOff = api.name in enabled)
            }
            results.add(result)
        }

        // Auto-off: persist the enabled set minus video-failed providers.
        val failedVideo = results.filter { !it.videoOk }.map { it.name }.toSet()
        if (failedVideo.isNotEmpty()) {
            val newEnabled = if (enabled.isEmpty()) {
                toTest.map { it.name }.toSet() - failedVideo
            } else {
                enabled - failedVideo
            }
            ZaynProviders.setEnabledProviders(newEnabled)
        }

        return results
    }
}
