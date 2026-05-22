package com.financier.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bank_sms_logs")
data class BankSmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sms_body")
    val smsBody: String,

    @ColumnInfo(name = "sender")
    val sender: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long
)
