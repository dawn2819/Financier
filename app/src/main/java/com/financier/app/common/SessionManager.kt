package com.financier.app.common

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.financier.app.BuildConfig

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
    private const val KEY_BACKGROUNDED_AT = "backgrounded_at"
    private const val KEY_CUSTOM_GEMINI_KEY = "custom_gemini_api_key"
    const val REAUTHENTICATION_TIMEOUT_MS = 3 * 60 * 1000L

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
            remove(KEY_BACKGROUNDED_AT)
            apply()
        }
    }

    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    fun markBackgrounded(context: Context) {
        if (isLoggedIn(context)) {
            val prefs = getPrefs(context)
            val current = prefs.getLong(KEY_BACKGROUNDED_AT, 0L)
            if (current == 0L) {
                prefs.edit().putLong(KEY_BACKGROUNDED_AT, System.currentTimeMillis()).apply()
            }
        }
    }

    fun requiresReauthentication(context: Context): Boolean {
        if (!isLoggedIn(context)) return false
        val backgroundedAt = getPrefs(context).getLong(KEY_BACKGROUNDED_AT, 0L)
        if (backgroundedAt == 0L) return false
        val elapsed = System.currentTimeMillis() - backgroundedAt
        return elapsed < 0L || elapsed >= REAUTHENTICATION_TIMEOUT_MS
    }

    fun clearBackgroundedAt(context: Context) {
        getPrefs(context).edit().remove(KEY_BACKGROUNDED_AT).apply()
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
        return getPrefs(context).getString(KEY_AVATAR_COLOR, "#7046D2") ?: "#7046D2"
    }

    fun getAvatarPath(context: Context): String? {
        return getPrefs(context).getString(KEY_AVATAR_PATH, null)
    }

    fun saveAvatarPath(context: Context, path: String?) {
        getPrefs(context).edit().putString(KEY_AVATAR_PATH, path).apply()
    }

    /**
     * Chỉ duy nhất tài khoản admin @dawn mới có quyền tạo mã hóa đơn doanh nghiệp
     */
    fun isDawnAdmin(context: Context): Boolean {
        val username = getUsername(context).trim().lowercase()
        return username == "dawn"
    }

    /**
     * Quản lý API Key của Google Gemini do người dùng thiết lập
     */
    fun getGeminiApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_GEMINI_KEY, "")?.trim().orEmpty()
    }

    fun saveGeminiApiKey(context: Context, key: String?) {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            getPrefs(context).edit().remove(KEY_CUSTOM_GEMINI_KEY).apply()
        } else {
            getPrefs(context).edit().putString(KEY_CUSTOM_GEMINI_KEY, trimmed).apply()
        }
    }

    fun getEffectiveGeminiApiKey(context: Context): String {
        val customKey = getGeminiApiKey(context)
        if (customKey.isNotBlank()) return customKey
        return BuildConfig.GEMINI_API_KEY.trim()
    }

    fun hasGeminiApiKey(context: Context): Boolean {
        return getEffectiveGeminiApiKey(context).isNotBlank()
    }
}
