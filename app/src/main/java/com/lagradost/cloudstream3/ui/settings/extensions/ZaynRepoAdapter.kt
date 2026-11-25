package com.lagradost.cloudstream3.ui.settings.extensions

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.home.ZaynProviders
import com.lagradost.cloudstream3.ui.home.ZaynProviders.ZaynRepo

/**
 * View-based list of the 27 bundled Zayn repos (setup Extensions step).
 * Item views are built in code so no new layout XML is needed. Each card shows
 * name, shortcode, provider count + ON count, Install / Test, and an expandable
 * per-provider ON/OFF list (default OFF).
 */
class ZaynRepoAdapter(
    private var repos: List<ZaynRepo>,
    private val isInstalled: (ZaynRepo) -> Boolean,
    private val onCount: (ZaynRepo) -> Int,
    private val isProviderOn: (String) -> Boolean,
    private val onInstall: (ZaynRepo) -> Unit,
    private val onTest: (ZaynRepo) -> Unit,
    private val onToggleProvider: (String, Boolean) -> Unit,
    private val onToggleAll: (ZaynRepo, Boolean) -> Unit,
) : RecyclerView.Adapter<ZaynRepoAdapter.ViewHolder>() {

    var busyInstallIds: Set<String> = emptySet()
    var busyTestIds: Set<String> = emptySet()
    private val expandedIds = mutableSetOf<String>()

    fun submitList(list: List<ZaynRepo>) {
        repos = list
        notifyDataSetChanged()
    }

    fun refresh() = notifyDataSetChanged()

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()

    private fun tileDrawable(color: Int, radiusDp: Int, context: Context): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp.dp(context).toFloat()
            setColor(color)
        }

    inner class ViewHolder(val card: CardView) : RecyclerView.ViewHolder(card) {
        lateinit var tile: TextView
        lateinit var nameText: TextView
        lateinit var shortText: TextView
        lateinit var descText: TextView
        lateinit var countText: TextView
        lateinit var installButton: MaterialButton
        lateinit var testButton: MaterialButton
        lateinit var expandButton: MaterialButton
        lateinit var providersContainer: LinearLayout
        lateinit var allOnButton: MaterialButton
        lateinit var allOffButton: MaterialButton
        lateinit var providerRows: LinearLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val card = CardView(context).apply {
            radius = 16.dp(context).toFloat()
            cardElevation = 2.dp(context).toFloat()
            useCompatPadding = true
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(12.dp(context), 6.dp(context), 12.dp(context), 6.dp(context))
            }
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(context), 12.dp(context), 12.dp(context), 12.dp(context))
        }
        card.addView(root)

        val holder = ViewHolder(card)

        // Header row: brand tile + name + shortcode
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        holder.tile = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            layoutParams = LinearLayout.LayoutParams(40.dp(context), 40.dp(context))
        }
        holder.nameText = TextView(context).apply {
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 10.dp(context) }
        }
        holder.shortText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 8.dp(context) }
        }
        header.addView(holder.tile)
        header.addView(holder.nameText)
        header.addView(holder.shortText)
        root.addView(header)

        holder.descText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp(context) }
        }
        root.addView(holder.descText)

        holder.countText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.dp(context) }
        }
        root.addView(holder.countText)

        // Buttons row: Install / Test / Show providers
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp(context) }
        }
        holder.installButton = MaterialButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8.dp(context) }
        }
        holder.testButton = MaterialButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8.dp(context) }
        }
        holder.expandButton = MaterialButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        buttons.addView(holder.installButton)
        buttons.addView(holder.testButton)
        buttons.addView(holder.expandButton)
        root.addView(buttons)

        // Expandable provider list
        holder.providersContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp(context) }
        }
        val allRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        holder.allOnButton = MaterialButton(context).apply {
            text = context.getString(R.string.zayn_repos_enable_all)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8.dp(context) }
        }
        holder.allOffButton = MaterialButton(context).apply {
            text = context.getString(R.string.zayn_repos_disable_all)
        }
        allRow.addView(holder.allOnButton)
        allRow.addView(holder.allOffButton)
        holder.providerRows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        holder.providersContainer.addView(allRow)
        holder.providersContainer.addView(holder.providerRows)
        root.addView(holder.providersContainer)

        return holder
    }

    override fun getItemCount(): Int = repos.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val context = holder.card.context
        val repo = repos[position]
        val expanded = expandedIds.contains(repo.id)

        holder.tile.text = ZaynProviders.providerInitial(repo.name)
        holder.tile.background = tileDrawable(ZaynProviders.providerTileColor(repo.name), 10, context)
        holder.nameText.text = repo.name
        holder.shortText.text = repo.shortcode.takeIf { it.isNotBlank() }?.let { "· $it" } ?: ""
        holder.descText.text = repo.desc
        holder.countText.text = context.getString(
            R.string.zayn_repos_providers
        ).let { "${repo.plugins.size} $it · ${onCount(repo)} ${context.getString(R.string.zayn_repos_on)}" }

        val installed = isInstalled(repo)
        holder.installButton.apply {
            when {
                busyInstallIds.contains(repo.id) -> {
                    text = context.getString(R.string.zayn_repos_installing)
                    isEnabled = false
                }
                installed -> {
                    text = context.getString(R.string.zayn_repos_installed)
                    isEnabled = false
                }
                else -> {
                    text = context.getString(R.string.zayn_repos_install)
                    isEnabled = true
                }
            }
            setOnClickListener { onInstall(repo) }
        }
        holder.testButton.apply {
            text = if (busyTestIds.contains(repo.id)) context.getString(R.string.zayn_repos_testing)
            else context.getString(R.string.zayn_repos_test)
            isEnabled = !busyTestIds.contains(repo.id)
            setOnClickListener { onTest(repo) }
        }
        holder.expandButton.apply {
            text = context.getString(
                if (expanded) R.string.zayn_repos_hide_providers
                else R.string.zayn_repos_show_providers
            )
            setOnClickListener {
                if (expandedIds.contains(repo.id)) expandedIds.remove(repo.id)
                else expandedIds.add(repo.id)
                notifyItemChanged(position)
            }
        }

        holder.providersContainer.visibility = if (expanded) View.VISIBLE else View.GONE
        if (expanded) {
            holder.allOnButton.setOnClickListener { onToggleAll(repo, true) }
            holder.allOffButton.setOnClickListener { onToggleAll(repo, false) }
            holder.providerRows.removeAllViews()
            for (providerName in repo.plugins) {
                holder.providerRows.addView(buildProviderRow(context, providerName))
            }
        }
    }

    private fun buildProviderRow(context: Context, providerName: String): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.dp(context) }
        }
        val tile = TextView(context).apply {
            text = ZaynProviders.providerInitial(providerName)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = tileDrawable(ZaynProviders.providerTileColor(providerName), 7, context)
            layoutParams = LinearLayout.LayoutParams(28.dp(context), 28.dp(context))
        }
        val name = TextView(context).apply {
            text = providerName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 10.dp(context) }
        }
        val toggle = SwitchCompat(context).apply {
            setOnCheckedChangeListener(null)
            isChecked = isProviderOn(providerName)
            setOnCheckedChangeListener { _, checked ->
                onToggleProvider(providerName, checked)
            }
        }
        row.addView(tile)
        row.addView(name)
        row.addView(toggle)
        return row
    }
}
