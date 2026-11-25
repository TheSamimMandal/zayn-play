package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.graphics.Color
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.DataStoreHelper
import org.json.JSONArray

/**
 * Zayn Play provider tabs + per-provider ON/OFF.
 *
 * Shared contract (other Zayn agents rely on the exact key):
 * enabled set = DataStore key "zayn_enabled_providers", ONE comma-separated
 * string of exact MainAPI.name values, default empty (all OFF). There is no
 * `SettingsManager` class in this codebase; the app-wide settings store is
 * CloudStreamApp/DataStore getKey/setKey, which is what this uses.
 */
object ZaynProviders {
    const val ENABLED_PROVIDERS_KEY = "zayn_enabled_providers"
    const val ZAYN_REPOS_ASSET = "zayn_repos.json"

    data class ZaynRepo(
        val id: String,
        val name: String,
        val desc: String,
        val shortcode: String,
        val url: String,
        val plugins: List<String>,
    )

    private var cachedRepos: List<ZaynRepo>? = null

    /** Bundled 27-repo catalogue from assets/zayn_repos.json (cloudstreamrepo.com). */
    fun loadRepos(context: Context): List<ZaynRepo> {
        cachedRepos?.let { return it }
        val parsed = try {
            val json = context.assets.open(ZAYN_REPOS_ASSET).bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val plugins = o.optJSONArray("plugins")
                ZaynRepo(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    desc = o.optString("desc"),
                    shortcode = o.optString("shortcode"),
                    url = o.optString("url"),
                    plugins = List(plugins?.length() ?: 0) { j -> plugins!!.getString(j) },
                )
            }
        } catch (e: Throwable) {
            logError(e)
            emptyList()
        }
        cachedRepos = parsed
        return parsed
    }

    fun getEnabledProviders(): Set<String> {
        val raw = getKey<String>(ENABLED_PROVIDERS_KEY).orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setEnabledProviders(names: Set<String>) {
        setKey(ENABLED_PROVIDERS_KEY, names.sorted().joinToString(","))
    }

    fun isProviderEnabled(name: String): Boolean = getEnabledProviders().contains(name)

    /** Returns the new state. Default is OFF: a name not in the set is OFF. */
    fun setProviderEnabled(name: String, enabled: Boolean): Boolean {
        val current = getEnabledProviders().toMutableSet()
        if (enabled) current.add(name) else current.remove(name)
        setEnabledProviders(current)
        return enabled
    }

    /** How many of this repo's providers are currently ON. */
    fun onCountForRepo(repo: ZaynRepo): Int {
        val enabled = getEnabledProviders()
        return repo.plugins.count { enabled.contains(it) }
    }

    /** Deterministic brand tile colour from the provider name (same name = same colour). */
    fun providerTileColor(name: String): Int {
        val palette = intArrayOf(
            Color.parseColor("#E50914"), // NetMirror red
            Color.parseColor("#6C63FF"),
            Color.parseColor("#0D9488"),
            Color.parseColor("#F59E0B"),
            Color.parseColor("#EC4899"),
            Color.parseColor("#2563EB"),
            Color.parseColor("#16A34A"),
            Color.parseColor("#EA580C"),
            Color.parseColor("#4F46E5"),
            Color.parseColor("#0891B2"),
        )
        val hash = name.hashCode() and Int.MAX_VALUE
        return palette[hash % palette.size]
    }

    fun providerInitial(name: String): String =
        name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

    /**
     * Providers shown as home tabs: hasMainPage + enabled + matching the
     * selected languages/media types. NSFW-only providers are hidden unless
     * NSFW is among the selected media types (handled inside
     * filterProviderByPreferredMedia). Order: pinned first, then A-Z.
     * CustomSite clones already appear as distinct MainAPI entries (see
     * MainActivity.onAllPluginsLoaded) so each site is its own tab; names are
     * de-duplicated so no provider appears twice in the tab row.
     */
    fun Context.visibleTabProviders(): List<MainAPI> {
        val enabled = getEnabledProviders()
        if (enabled.isEmpty()) return emptyList()
        val pinned = DataStoreHelper.pinnedProviders.toHashSet()
        return filterProviderByPreferredMedia()
            .filter { it.hasMainPage && enabled.contains(it.name) }
            .distinctBy { it.name }
            .sortedWith(
                compareByDescending<MainAPI> { pinned.contains(it.name) }
                    .thenBy { it.name.lowercase() }
            )
    }
}
