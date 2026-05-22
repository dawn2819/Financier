package com.financier.app.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.financier.app.data.local.entity.TransactionEntity

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteById(transactionId: Long)

    // Lấy tất cả giao dịch của user, sắp xếp theo ngày mới nhất
    @Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY date_ms DESC")
    fun getAllTransactions(userId: Long): LiveData<List<TransactionEntity>>

    // 5 giao dịch gần nhất cho Dashboard
    @Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY date_ms DESC LIMIT 5")
    fun getRecentTransactions(userId: Long): LiveData<List<TransactionEntity>>

    // Giao dịch theo tháng/năm
    @Query("""
        SELECT * FROM transactions 
        WHERE user_id = :userId 
        AND strftime('%m', date_ms / 1000, 'unixepoch') = printf('%02d', :month)
        AND strftime('%Y', date_ms / 1000, 'unixepoch') = :year
        ORDER BY date_ms DESC
    """)
    fun getTransactionsByMonth(userId: Long, month: Int, year: String): LiveData<List<TransactionEntity>>

    // Tổng thu nhập tháng
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions 
        WHERE user_id = :userId 
        AND type = 'INCOME'
        AND strftime('%m', date_ms / 1000, 'unixepoch') = printf('%02d', :month)
        AND strftime('%Y', date_ms / 1000, 'unixepoch') = :year
    """)
    suspend fun getTotalIncome(userId: Long, month: Int, year: String): Double

    // Tổng chi tiêu tháng
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions 
        WHERE user_id = :userId 
        AND type = 'EXPENSE'
        AND strftime('%m', date_ms / 1000, 'unixepoch') = printf('%02d', :month)
        AND strftime('%Y', date_ms / 1000, 'unixepoch') = :year
    """)
    suspend fun getTotalExpense(userId: Long, month: Int, year: String): Double

    // Chi tiêu theo category trong tháng (cho budget)
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions 
        WHERE user_id = :userId 
        AND type = 'EXPENSE'
        AND category = :category
        AND strftime('%m', date_ms / 1000, 'unixepoch') = printf('%02d', :month)
        AND strftime('%Y', date_ms / 1000, 'unixepoch') = :year
    """)
    suspend fun getExpenseByCategory(userId: Long, category: String, month: Int, year: String): Double

    // Chi tiêu theo ngày (7 ngày gần nhất, cho chart)
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions 
        WHERE user_id = :userId 
        AND type = 'EXPENSE'
        AND date_ms >= :fromMs AND date_ms <= :toMs
    """)
    suspend fun getExpenseBetween(userId: Long, fromMs: Long, toMs: Long): Double

    // Giao dịch theo account
    @Query("SELECT * FROM transactions WHERE account_id = :accountId ORDER BY date_ms DESC")
    fun getTransactionsByAccount(accountId: Long): LiveData<List<TransactionEntity>>

    // Search theo note hoặc category
    @Query("""
        SELECT * FROM transactions 
        WHERE user_id = :userId 
        AND (note LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%')
        ORDER BY date_ms DESC
    """)
    fun searchTransactions(userId: Long, query: String): LiveData<List<TransactionEntity>>

    // Chi tiêu theo category trong khoảng thời gian (cho Reports PieChart)
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions 
        WHERE user_id = :userId 
        AND type = 'EXPENSE'
        AND category = :category
        AND date_ms >= :fromMs AND date_ms <= :toMs
    """)
    suspend fun getExpenseBetweenByCategory(userId: Long, fromMs: Long, toMs: Long, category: String): Double

    // Thu nhập trong khoảng thời gian
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions 
        WHERE user_id = :userId 
        AND type = 'INCOME'
        AND date_ms >= :fromMs AND date_ms <= :toMs
    """)
    suspend fun getIncomeBetween(userId: Long, fromMs: Long, toMs: Long): Double

    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions 
        WHERE user_id = :userId 
        AND type = 'INCOME'
        AND date_ms >= :fromMs AND date_ms <= :toMs
    """)
    suspend fun getTotalIncomeRange(userId: Long, fromMs: Long, toMs: Long): Double

    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions 
        WHERE user_id = :userId 
        AND type = 'EXPENSE'
        AND date_ms >= :fromMs AND date_ms <= :toMs
    """)
    suspend fun getTotalExpenseRange(userId: Long, fromMs: Long, toMs: Long): Double

    @Query("""
        SELECT category, COALESCE(SUM(amount), 0) AS amount FROM transactions 
        WHERE user_id = :userId 
        AND type = 'EXPENSE'
        AND date_ms >= :fromMs AND date_ms <= :toMs
        GROUP BY category
    """)
    suspend fun getExpenseByCategoryRange(userId: Long, fromMs: Long, toMs: Long): List<CategorySum>
}

data class CategorySum(
    val category: String,
    val amount: Double
)
