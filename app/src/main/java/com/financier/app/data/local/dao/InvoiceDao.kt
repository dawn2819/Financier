package com.financier.app.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.financier.app.data.local.entity.InvoiceEntity

@Dao
interface InvoiceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Delete
    suspend fun deleteInvoice(invoice: InvoiceEntity)

    @Query("SELECT * FROM invoices WHERE LOWER(bill_code) = LOWER(:billCode) LIMIT 1")
    suspend fun getInvoiceByCode(billCode: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE LOWER(target_username) = LOWER(:username) OR target_username = 'ALL' ORDER BY due_date_ms ASC")
    fun getInvoicesForUser(username: String): LiveData<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE (LOWER(target_username) = LOWER(:username) OR target_username = 'ALL') AND status = 'UNPAID' ORDER BY due_date_ms ASC")
    suspend fun getUnpaidInvoicesForUserSync(username: String): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE creator_id = :creatorId ORDER BY created_at DESC")
    suspend fun getInvoicesByCreatorSync(creatorId: Long): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE creator_id = :creatorId ORDER BY created_at DESC")
    fun getInvoicesByCreator(creatorId: Long): LiveData<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices ORDER BY created_at DESC")
    suspend fun getAllInvoicesSync(): List<InvoiceEntity>

    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun getInvoiceCount(): Int
}
