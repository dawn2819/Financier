package com.financier.app.ui.budget

import android.content.Context
import androidx.lifecycle.*
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.BudgetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class BudgetItem(
    val budget: BudgetEntity,
    val spent: Double,
    val percentage: Int // 0-100
)

class BudgetViewModel(context: Context, private val userId: Long) : ViewModel() {

    private val db = AppDatabase.getDatabase(context)
    private val budgetDao = db.budgetDao()
    private val txDao = db.transactionDao()

    private val _currentMonth = MutableLiveData<Int>()
    private val _currentYear = MutableLiveData<Int>()

    private val _budgetItems = MutableLiveData<List<BudgetItem>>()
    val budgetItems: LiveData<List<BudgetItem>> = _budgetItems

    private val _totalBudget = MutableLiveData<Double>()
    val totalBudget: LiveData<Double> = _totalBudget

    private val _totalSpent = MutableLiveData<Double>()
    val totalSpent: LiveData<Double> = _totalSpent

    private val _overallPercentage = MutableLiveData<Int>()
    val overallPercentage: LiveData<Int> = _overallPercentage

    val monthLabel: LiveData<String> = MediatorLiveData<String>().apply {
        addSource(_currentMonth) { updateLabel(this) }
        addSource(_currentYear) { updateLabel(this) }
    }

    init {
        val cal = Calendar.getInstance()
        _currentMonth.value = cal.get(Calendar.MONTH) + 1
        _currentYear.value = cal.get(Calendar.YEAR)
        loadBudgets()
    }

    private fun updateLabel(liveData: MediatorLiveData<String>) {
        val month = _currentMonth.value ?: return
        val year = _currentYear.value ?: return
        val monthNames = listOf("", "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
            "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12")
        liveData.value = "${monthNames[month]}, $year"
    }

    fun loadBudgets() {
        val month = _currentMonth.value ?: return
        val year = _currentYear.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val budgets = budgetDao.getBudgetsByMonthSync(userId, month, year)
            val items = budgets.map { budget ->
                val spent = txDao.getExpenseByCategory(userId, budget.category, month, year.toString())
                val pct = if (budget.limitAmount > 0) ((spent / budget.limitAmount) * 100).toInt().coerceIn(0, 100) else 0
                BudgetItem(budget, spent, pct)
            }

            val total = budgets.sumOf { it.limitAmount }
            val spent = items.sumOf { it.spent }
            val overallPct = if (total > 0) ((spent / total) * 100).toInt().coerceIn(0, 100) else 0

            withContext(Dispatchers.Main) {
                _budgetItems.value = items
                _totalBudget.value = total
                _totalSpent.value = spent
                _overallPercentage.value = overallPct
            }
        }
    }

    fun previousMonth() {
        val m = _currentMonth.value ?: return
        val y = _currentYear.value ?: return
        if (m == 1) { _currentMonth.value = 12; _currentYear.value = y - 1 }
        else _currentMonth.value = m - 1
        loadBudgets()
    }

    fun nextMonth() {
        val m = _currentMonth.value ?: return
        val y = _currentYear.value ?: return
        if (m == 12) { _currentMonth.value = 1; _currentYear.value = y + 1 }
        else _currentMonth.value = m + 1
        loadBudgets()
    }

    fun addBudget(category: String, limit: Double) {
        val month = _currentMonth.value ?: return
        val year = _currentYear.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = budgetDao.getBudgetByCategory(userId, category, month, year)
            if (existing != null) {
                budgetDao.updateBudget(existing.copy(limitAmount = limit))
            } else {
                budgetDao.insertBudget(BudgetEntity(
                    userId = userId, category = category,
                    limitAmount = limit, month = month, year = year
                ))
            }
            withContext(Dispatchers.Main) { loadBudgets() }
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            budgetDao.deleteBudget(budget)
            withContext(Dispatchers.Main) { loadBudgets() }
        }
    }

    fun getCurrentMonth() = _currentMonth.value ?: 1
    fun getCurrentYear() = _currentYear.value ?: 2026

    class Factory(private val ctx: Context, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BudgetViewModel(ctx, userId) as T
        }
    }
}
