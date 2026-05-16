package com.financier.app.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.financier.app.data.local.entity.FinancialAccountEntity

@Dao
interface FinancialAccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: FinancialAccountEntity): Long

    @Update
    suspend fun updateAccount(account: FinancialAccountEntity)

    @Delete
    suspend fun deleteAccount(account: FinancialAccountEntity)

    @Query("SELECT * FROM financial_accounts WHERE user_id = :userId AND is_active = 1 ORDER BY created_at ASC")
    fun getAccountsByUser(userId: Long): LiveData<List<FinancialAccountEntity>>

    @Query("SELECT * FROM financial_accounts WHERE user_id = :userId AND is_active = 1 ORDER BY created_at ASC")
    suspend fun getAccountsByUserSync(userId: Long): List<FinancialAccountEntity>

    @Query("SELECT * FROM financial_accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: Long): FinancialAccountEntity?

    @Query("SELECT COUNT(*) FROM financial_accounts WHERE user_id = :userId")
    suspend fun getAccountCount(userId: Long): Int
}
