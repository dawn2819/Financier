package com.financier.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_settings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AppSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "language")
    val language: String = "en", // "en" | "vi"

    @ColumnInfo(name = "currency")
    val currency: String = "VND", // "VND" | "USD"

    @ColumnInfo(name = "dark_mode")
    val darkMode: Boolean = true,

    @ColumnInfo(name = "notifications_on")
    val notificationsOn: Boolean = true,

    @ColumnInfo(name = "biometric_enabled")
    val biometricEnabled: Boolean = false,

    @ColumnInfo(name = "selected_account_id")
    val selectedAccountId: Long = -1L // tài khoản đang được chọn
)
