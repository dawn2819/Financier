package com.financier.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "financial_accounts",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("user_id")]
)
data class FinancialAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "name")
    val name: String, // "Tiền mặt", "Vietcombank", "Ví MoMo", ...

    @ColumnInfo(name = "account_type")
    val accountType: String = "CASH", // CASH | BANK | EWALLET | SAVINGS

    @ColumnInfo(name = "initial_balance")
    val initialBalance: Double = 0.0,

    @ColumnInfo(name = "currency")
    val currency: String = "VND", // VND | USD

    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#78DC77",

    @ColumnInfo(name = "icon")
    val icon: String = "account_balance_wallet",

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
