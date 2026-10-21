package com.financier.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "invoices",
    indices = [
        Index("bill_code", unique = true),
        Index("target_username"),
        Index("creator_id")
    ]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "creator_id")
    val creatorId: Long,

    @ColumnInfo(name = "creator_username")
    val creatorUsername: String,

    @ColumnInfo(name = "bill_code")
    val billCode: String,

    @ColumnInfo(name = "target_username")
    val targetUsername: String, // username người cần đóng, hoặc "ALL"

    @ColumnInfo(name = "title")
    val title: String, // "Tiền điện EVN tháng 10", "Tiền thuê nhà P302", ...

    @ColumnInfo(name = "category")
    val category: String = "bills", // "bills" | "housing" | "electric" | "water" | "internet" | "education"

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "status")
    val status: String = "UNPAID", // "UNPAID" | "PAID"

    @ColumnInfo(name = "due_date_ms")
    val dueDateMs: Long = System.currentTimeMillis() + 7 * 24 * 3600 * 1000L,

    @ColumnInfo(name = "paid_at_ms")
    val paidAtMs: Long? = null,

    @ColumnInfo(name = "paid_by_user_id")
    val paidByUserId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
