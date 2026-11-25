package com.lagradost.cloudstream3.ui.setup

import android.content.Context
import android.content.SharedPreferences
import android.widget.ImageView
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import java.io.File

/**
 * Zayn Play profile (setup Profile step + home header + Settings profile card).
 *
 * Storage (default SharedPreferences, literal keys — do not rename, other code
 * reads them verbatim):
 * - [KEY_NAME]: display name, String, may be blank/absent.
 * - [KEY_AVATAR]: String, either a preset id ("preset_blue", …) or an absolute
 *   file path to a user-uploaded photo (see [saveUploadedPhoto]).
 *
 * Display points (implemented OUTSIDE this file — see report):
 * - Home header avatar: fragment_home_head.xml home_head_profile_pic, driven from
 *   HomeFragment.kt (currently the OAuth profile block there is commented out).
 * - Settings profile card: SettingsFragmentScreen.kt account row.
 * Both should call [loadInto] / [getName] here and fall back to their existing
 * account image/name when this profile is empty.
 */
object ZaynProfile {
    const val KEY_NAME = "zayn_profile_name"
    const val KEY_AVATAR = "zayn_profile_avatar"

    /** File name for a user-uploaded profile photo inside Context.filesDir. */
    const val UPLOADED_PHOTO_FILE = "zayn_profile_photo.jpg"

    const val PRESET_PREFIX = "preset_"

    /**
     * The 8 preset avatars. The first 7 reuse CloudStream's own profile
     * backgrounds (also used by DataStoreHelper.profileImages); the 8th is the
     * Zayn Play gradient tile (no 8th profile background exists in res).
     */
    val PRESETS: List<Pair<String, Int>> = listOf(
        "preset_dark_blue" to R.drawable.profile_bg_dark_blue,
        "preset_blue" to R.drawable.profile_bg_blue,
        "preset_orange" to R.drawable.profile_bg_orange,
        "preset_pink" to R.drawable.profile_bg_pink,
        "preset_purple" to R.drawable.profile_bg_purple,
        "preset_red" to R.drawable.profile_bg_red,
        "preset_teal" to R.drawable.profile_bg_teal,
        "preset_zayn" to R.drawable.cloud_2_gradient,
    )

    fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun getName(context: Context): String? =
        prefs(context).getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }

    fun getAvatar(context: Context): String? =
        prefs(context).getString(KEY_AVATAR, null)?.takeIf { it.isNotBlank() }

    fun saveName(context: Context, name: String) {
        prefs(context).edit { putString(KEY_NAME, name.trim()) }
    }

    fun saveAvatar(context: Context, avatar: String) {
        prefs(context).edit { putString(KEY_AVATAR, avatar) }
    }

    /** Drawable res for a stored avatar value, or null when it is a file photo / empty. */
    fun presetDrawableRes(avatar: String?): Int? {
        if (avatar == null || !avatar.startsWith(PRESET_PREFIX)) return null
        return PRESETS.firstOrNull { it.first == avatar }?.second
    }

    /** True when the stored avatar value is a user-uploaded photo file path. */
    fun isFileAvatar(avatar: String?): Boolean =
        avatar != null && !avatar.startsWith(PRESET_PREFIX) && File(avatar).exists()

    /**
     * Copies an uploaded photo (content Uri bytes already read by the caller)
     * into internal storage and stores its path as the avatar. Returns the
     * stored path, or null when the write failed.
     */
    fun saveUploadedPhoto(context: Context, bytes: ByteArray): String? {
        return try {
            val file = File(context.filesDir, UPLOADED_PHOTO_FILE)
            file.writeBytes(bytes)
            val path = file.absolutePath
            saveAvatar(context, path)
            path
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Shows the stored avatar in [view]. Returns true when an avatar was set.
     * Presets are set from resources; uploaded photos are set from the file.
     * Callers keep their previous/fallback image when this returns false.
     */
    fun loadInto(view: ImageView, context: Context): Boolean {
        val avatar = getAvatar(context) ?: return false
        presetDrawableRes(avatar)?.let { resId ->
            view.setImageResource(resId)
            return true
        }
        if (isFileAvatar(avatar)) {
            view.setImageURI(android.net.Uri.fromFile(File(avatar)))
            return true
        }
        return false
    }
}
