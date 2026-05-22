package com.financier.app.data.local.dao

import androidx.room.*
import com.financier.app.data.local.entity.BankSmsLogEntity

@Dao
interface BankSmsLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: BankSmsLogEntity): Long

    @Query("SELECT COUNT(*) FROM bank_sms_logs WHERE sms_body = :smsBody AND timestamp = :timestamp")
    suspend fun isSmsParsed(smsBody: String, timestamp: Long): Int
}
