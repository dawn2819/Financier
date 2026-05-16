package com.financier.app.common

import java.security.MessageDigest

object SecurityUtils {

    private const val SALT = "financier_salt_2024"

    /**
     * Hash password với SHA-256 + salt cố định
     * Đơn giản cho testing — production nên dùng bcrypt
     */
    fun hashPassword(password: String): String {
        val input = "$SALT:$password"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Kiểm tra password
     */
    fun verifyPassword(password: String, hash: String): Boolean {
        return hashPassword(password) == hash
    }
}
