package com.lagradost.cloudstream3.ui.settings.extensions

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.home.ZaynProviders
import com.lagradost.cloudstream4.compose.BlackButton
import com.lagradost.cloudstream4.compose.WhiteButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Zayn repositories" section inside the Settings > Extensions screen:
 * the 27 bundled real repos (name, shortcode, provider count, ON count) with
 * search/filter, Install (existing repo-install path), per-repo Test, and an
 * expandable per-provider ON/OFF list. Every provider row shows a brand tile
 * (deterministic colour + initial) — no provider is text-only.
 */
@Composable
fun ZaynReposSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allRepos = remember { ZaynProviders.loadRepos(context) }
    var query by remember { mutableStateOf("") }
    var expandedIds by remember { mutableStateOf(setOf<String>()) }
    // Bumped on every toggle/install so ON counts + installed states recompose.
    var version by remember { mutableStateOf(0) }
    var busyInstall by remember { mutableStateOf<String?>(null) }
    var busyTest by remember { mutableStateOf<String?>(null) }

    val tick = version
    val installedUrls = remember(tick) {
        RepositoryManager.getRepositories().map { it.url }.toSet()
    }
    val enabled = remember(tick) { ZaynProviders.getEnabledProviders() }

    val filtered = remember(query, allRepos) {
        val q = query.trim().lowercase()
        if (q.isBlank()) allRepos
        else allRepos.filter { repo ->
            repo.name.lowercase().contains(q) ||
                repo.shortcode.lowercase().contains(q) ||
                repo.plugins.any { it.lowercase().contains(q) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = context.getString(R.string.zayn_repos_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${allRepos.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(text = context.getString(R.string.zayn_repos_search_hint)) },
        )
        Spacer(modifier = Modifier.height(8.dp))

        for (repo in filtered) {
            val installed = installedUrls.contains(repo.url)
            val onCount = repo.plugins.count { enabled.contains(it) }
            val expanded = expandedIds.contains(repo.id)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Brand tile: deterministic colour + initial letter.
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color(ZaynProviders.providerTileColor(repo.name)),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ZaynProviders.providerInitial(repo.name),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = repo.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (repo.shortcode.isNotBlank()) {
                            Text(
                                text = "· ${repo.shortcode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (repo.desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = repo.desc,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${repo.plugins.size} ${context.getString(R.string.zayn_repos_providers)} · " +
                        "$onCount ${context.getString(R.string.zayn_repos_on)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busyInstall == repo.id) {
                        BlackButton(text = context.getString(R.string.zayn_repos_installing)) {}
                    } else if (installed) {
                        BlackButton(text = context.getString(R.string.zayn_repos_installed)) {}
                    } else {
                        WhiteButton(text = context.getString(R.string.zayn_repos_install)) {
                            busyInstall = repo.id
                            scope.launch(Dispatchers.IO) {
                                val activity = context as? Activity
                                val error = ZaynRepoActions.installRepo(activity, repo)
                                withContext(Dispatchers.Main) {
                                    busyInstall = null
                                    if (error == null) {
                                        Toast.makeText(
                                            context,
                                            R.string.zayn_repos_install_ok,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            R.string.zayn_repos_install_fail,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    version++
                                }
                            }
                        }
                    }
                    if (busyTest == repo.id) {
                        BlackButton(text = context.getString(R.string.zayn_repos_testing)) {}
                    } else {
                        BlackButton(text = context.getString(R.string.zayn_repos_test)) {
                            busyTest = repo.id
                            scope.launch(Dispatchers.IO) {
                                val count = ZaynRepoActions.testRepo(repo)
                                withContext(Dispatchers.Main) {
                                    busyTest = null
                                    if (count != null) {
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.zayn_repos_test_ok, count
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            R.string.zayn_repos_test_fail,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                    BlackButton(
                        text = context.getString(
                            if (expanded) R.string.zayn_repos_hide_providers
                            else R.string.zayn_repos_show_providers
                        )
                    ) {
                        expandedIds = if (expanded) expandedIds - repo.id else expandedIds + repo.id
                    }
                }
                if (expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WhiteButton(text = context.getString(R.string.zayn_repos_enable_all)) {
                            val current = ZaynProviders.getEnabledProviders().toMutableSet()
                            current.addAll(repo.plugins)
                            ZaynProviders.setEnabledProviders(current)
                            version++
                        }
                        BlackButton(text = context.getString(R.string.zayn_repos_disable_all)) {
                            val current = ZaynProviders.getEnabledProviders().toMutableSet()
                            current.removeAll(repo.plugins.toSet())
                            ZaynProviders.setEnabledProviders(current)
                            version++
                        }
                    }
                    for (providerName in repo.plugins) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        color = Color(
                                            ZaynProviders.providerTileColor(providerName)
                                        ),
                                        shape = RoundedCornerShape(7.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ZaynProviders.providerInitial(providerName),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = providerName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = enabled.contains(providerName),
                                onCheckedChange = { checked ->
                                    ZaynProviders.setProviderEnabled(providerName, checked)
                                    version++
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
