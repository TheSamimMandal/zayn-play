package com.lagradost.cloudstream3.ui.setup

import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentSetupProfileBinding
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

/**
 * Zayn Play setup step 0 — Profile. FIRST setup step (after splash, before
 * Language), skippable like CloudStream's Skip.
 *
 * Fields: display name + profile picture (8 preset avatars + own-photo upload,
 * live preview). Stored via [ZaynProfile] keys "zayn_profile_name" /
 * "zayn_profile_avatar".
 *
 * NAV WIRING STILL NEEDED (mobile_navigation.xml + MainActivity.kt are outside
 * this agent's file list — see final report for the exact edits):
 * - Add destination navigation_setup_profile for this fragment.
 * - MainActivity setup entry + Settings "Redo setup" must navigate to
 *   navigation_setup_profile instead of navigation_setup_language.
 * Until then this fragment is reachable only via direct navigation to its
 * destination id. Next goes to navigation_setup_language (existing).
 */
class SetupFragmentProfile : BaseFragment<FragmentSetupProfileBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentSetupProfileBinding::inflate)
) {
    private var selectedAvatar: String? = null

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val ctx = context ?: return@registerForActivityResult
            if (uri == null) return@registerForActivityResult
            ioSafe {
                val bytes = try {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (_: Exception) {
                    null
                }
                val savedPath = bytes?.let { ZaynProfile.saveUploadedPhoto(ctx, it) }
                activity?.runOnUiThread {
                    if (savedPath != null) {
                        selectedAvatar = savedPath
                        binding?.profilePreviewImage?.let { ZaynProfile.loadInto(it, ctx) }
                        (binding?.avatarGrid?.adapter as? AvatarAdapter)?.notifyDataSetChanged()
                        Toast.makeText(ctx, R.string.zayn_setup_profile_photo_saved, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, R.string.zayn_setup_profile_photo_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(view)
    }

    override fun onBindingCreated(binding: FragmentSetupProfileBinding) {
        safe {
            val ctx = context ?: return@safe

            selectedAvatar = ZaynProfile.getAvatar(ctx)
                ?: ZaynProfile.PRESETS.first().first

            binding.apply {
                profileNameInput.setText(ZaynProfile.getName(ctx) ?: "")
                profileNameInput.doAfterTextChanged { text ->
                    ZaynProfile.saveName(ctx, text?.toString() ?: "")
                }

                if (!ZaynProfile.loadInto(profilePreviewImage, ctx)) {
                    profilePreviewImage.setImageResource(ZaynProfile.PRESETS.first().second)
                }

                val adapter = AvatarAdapter()
                avatarGrid.adapter = adapter
                avatarGrid.setOnItemClickListener { _, _, position, _ ->
                    selectedAvatar = ZaynProfile.PRESETS[position].first
                    ZaynProfile.saveAvatar(ctx, selectedAvatar!!)
                    profilePreviewImage.setImageResource(ZaynProfile.PRESETS[position].second)
                    adapter.notifyDataSetChanged()
                }

                uploadPhotoBtt.setOnClickListener {
                    pickPhoto.launch("image/*")
                }

                nextBtt.setOnClickListener {
                    ZaynProfile.saveName(ctx, profileNameInput.text?.toString() ?: "")
                    selectedAvatar?.let { ZaynProfile.saveAvatar(ctx, it) }
                    findNavController().navigate(R.id.navigation_setup_language)
                }

                skipBtt.setOnClickListener {
                    setKey(HAS_DONE_SETUP_KEY, true)
                    findNavController().navigate(R.id.navigation_home)
                }
            }
        }
    }

    private inner class AvatarAdapter : BaseAdapter() {
        override fun getCount(): Int = ZaynProfile.PRESETS.size
        override fun getItem(position: Int): Any = ZaynProfile.PRESETS[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val density = parent.context.resources.displayMetrics.density
            val sizePx = (72 * density).toInt()
            val imageView = (convertView as? ImageView) ?: ImageView(parent.context).apply {
                layoutParams = AbsListView.LayoutParams(sizePx, sizePx)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            imageView.setImageResource(ZaynProfile.PRESETS[position].second)
            val isSelected = ZaynProfile.PRESETS[position].first == selectedAvatar
            if (isSelected) {
                imageView.setBackgroundResource(R.drawable.outline_big_15_gray)
                imageView.setPadding(4, 4, 4, 4)
            } else {
                imageView.background = null
                imageView.setPadding(0, 0, 0, 0)
            }
            return imageView
        }
    }
}
