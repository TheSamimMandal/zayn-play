package com.lagradost.cloudstream3.ui.settings.extensions

import android.app.Activity
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.home.ZaynProviders.ZaynRepo

/**
 * Install / Test for a bundled Zayn repo. Reuses CloudStream's existing
 * repo-install path (parseRepoUrl -> parseRepository -> addRepository ->
 * downloadAll plugins); nothing here invents a new installer.
 */
object ZaynRepoActions {
    fun toRepositoryData(repo: ZaynRepo): RepositoryData =
        RepositoryData(iconUrl = null, name = repo.name, url = repo.url, description = repo.desc)

    /**
     * Installs the repo from its repo.json URL and downloads its plugins.
     * Returns null on success, or an error message otherwise. Call from ioSafe.
     */
    suspend fun installRepo(activity: Activity?, repo: ZaynRepo): String? {
        val url = RepositoryManager.parseRepoUrl(repo.url) ?: repo.url
        val repository = try {
            RepositoryManager.parseRepository(url)
        } catch (e: Throwable) {
            null
        } ?: return "Could not fetch ${repo.url}"
        val newRepo = RepositoryData(
            repository.iconUrl,
            repository.name,
            url,
            repository.description
        )
        RepositoryManager.addRepository(newRepo)
        // Same as the setup screen's download-all: fetch + install every plugin.
        PluginsViewModel.downloadAll(activity, newRepo, null)
        return null
    }

    /**
     * Per-repo Test: fetches the repo's plugin list over the network.
     * Returns the reachable plugin count, or null when unreachable/invalid.
     */
    suspend fun testRepo(repo: ZaynRepo): Int? {
        return try {
            RepositoryManager.getRepoPlugins(toRepositoryData(repo))?.size
        } catch (e: Throwable) {
            null
        }
    }
}
