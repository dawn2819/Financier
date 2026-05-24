package com.financier.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "password_hash")
    val passwordHash: String,

    @ColumnInfo(name = "role")
    val role: String = "user", // "admin" | "user"

    @ColumnInfo(name = "avatar_color")
    val avatarColor: String = "#78DC77", // màu avatar mặc định

    @ColumnInfo(name = "avatar_path")
    val avatarPath: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
