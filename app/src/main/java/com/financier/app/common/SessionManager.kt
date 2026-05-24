package com.financier.app.common

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Quản lý session đăng nhập, lưu trong EncryptedSharedPreferences
 */
object SessionManager {

    private const val PREF_NAME = "financier_session"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_ROLE = "role"
    private const val KEY_AVATAR_COLOR = "avatar_color"
    private const val KEY_AVATAR_PATH = "avatar_path"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    private fun getPrefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback sang SharedPreferences thường nếu lỗi (test env)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveSession(
        context: Context,
        userId: Long,
        username: String,
        displayName: String,
        role: String,
        avatarColor: String,
        avatarPath: String? = null
    ) {
        getPrefs(context).edit().apply {
            putLong(KEY_USER_ID, userId)
            putString(KEY_USERNAME, username)
            putString(KEY_DISPLAY_NAME, displayName)
            putString(KEY_ROLE, role)
            putString(KEY_AVATAR_COLOR, avatarColor)
            putString(KEY_AVATAR_PATH, avatarPath)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getUserId(context: Context): Long {
        return getPrefs(context).getLong(KEY_USER_ID, -1L)
    }

    fun getUsername(context: Context): String {
        return getPrefs(context).getString(KEY_USERNAME, "") ?: ""
    }

    fun getDisplayName(context: Context): String {
        return getPrefs(context).getString(KEY_DISPLAY_NAME, "User") ?: "User"
    }

    fun getRole(context: Context): String {
        return getPrefs(context).getString(KEY_ROLE, "user") ?: "user"
    }

    fun getAvatarColor(context: Context): String {
        return getPrefs(context).getString(KEY_AVATAR_COLOR, "#78DC77") ?: "#78DC77"
    }

    fun getAvatarPath(context: Context): String? {
        return getPrefs(context).getString(KEY_AVATAR_PATH, null)
    }

    fun saveAvatarPath(context: Context, path: String?) {
        getPrefs(context).edit().putString(KEY_AVATAR_PATH, path).apply()
    }
}
