package com.financier.app.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.financier.app.data.local.entity.BudgetEntity

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Delete
    suspend fun deleteBudget(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND month = :month AND year = :year")
    fun getBudgetsByMonth(userId: Long, month: Int, year: Int): LiveData<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND month = :month AND year = :year")
    suspend fun getBudgetsByMonthSync(userId: Long, month: Int, year: Int): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND category = :category AND month = :month AND year = :year LIMIT 1")
    suspend fun getBudgetByCategory(userId: Long, category: String, month: Int, year: Int): BudgetEntity?

    @Query("DELETE FROM budgets WHERE id = :budgetId")
    suspend fun deleteBudgetById(budgetId: Long)
}
