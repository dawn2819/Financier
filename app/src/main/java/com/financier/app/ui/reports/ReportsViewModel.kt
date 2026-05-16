package com.financier.app.ui.reports

import android.content.Context
import androidx.lifecycle.*
import com.financier.app.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class CategorySpending(val category: String, val amount: Double, val percentage: Float)
data class MonthlyComparison(val label: String, val income: Double, val expense: Double)

enum class TimeFilter { WEEK, MONTH, YEAR }

class ReportsViewModel(context: Context, private val userId: Long) : ViewModel() {

    private val db = AppDatabase.getDatabase(context)
    private val txDao = db.transactionDao()

    private val _filter = MutableLiveData(TimeFilter.MONTH)
    val filter: LiveData<TimeFilter> = _filter

    private val _categorySpending = MutableLiveData<List<CategorySpending>>()
    val categorySpending: LiveData<List<CategorySpending>> = _categorySpending

    private val _monthlyComparison = MutableLiveData<List<MonthlyComparison>>()
    val monthlyComparison: LiveData<List<MonthlyComparison>> = _monthlyComparison

    private val _totalExpense = MutableLiveData<Double>()
    val totalExpense: LiveData<Double> = _totalExpense

    private val _totalIncome = MutableLiveData<Double>()
    val totalIncome: LiveData<Double> = _totalIncome

    init { loadData() }

    fun setFilter(f: TimeFilter) {
        _filter.value = f
        loadData()
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            loadCategorySpending()
            loadMonthlyComparison()
        }
    }

    private suspend fun loadCategorySpending() {
        val cal = Calendar.getInstance()
        val currentFilter = _filter.value ?: TimeFilter.MONTH

        val (fromMs, toMs) = getDateRange(cal, currentFilter)

        val categories = listOf(
            "food", "transport", "shopping", "health", "entertainment",
            "housing", "education", "gym", "bills", "travel", "pets", "others"
        )

        val spending = mutableListOf<CategorySpending>()
        var total = 0.0

        for (cat in categories) {
            val amount = txDao.getExpenseBetweenByCategory(userId, fromMs, toMs, cat)
            if (amount > 0) {
                spending.add(CategorySpending(cat, amount, 0f))
                total += amount
            }
        }

        // Calculate percentages
        val withPercentage = spending.map {
            it.copy(percentage = if (total > 0) (it.amount / total * 100).toFloat() else 0f)
        }.sortedByDescending { it.amount }

        // Total income for the period
        val income = txDao.getIncomeBetween(userId, fromMs, toMs)

        withContext(Dispatchers.Main) {
            _categorySpending.value = withPercentage
            _totalExpense.value = total
            _totalIncome.value = income
        }
    }

    private suspend fun loadMonthlyComparison() {
        val result = mutableListOf<MonthlyComparison>()
        val cal = Calendar.getInstance()
        val monthNames = listOf("T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8", "T9", "T10", "T11", "T12")

        // Last 6 months
        cal.add(Calendar.MONTH, -5)
        for (i in 0..5) {
            val month = cal.get(Calendar.MONTH) + 1
            val year = cal.get(Calendar.YEAR).toString()
            val income = txDao.getTotalIncome(userId, month, year)
            val expense = txDao.getTotalExpense(userId, month, year)
            result.add(MonthlyComparison(monthNames[month - 1], income, expense))
            cal.add(Calendar.MONTH, 1)
        }

        withContext(Dispatchers.Main) {
            _monthlyComparison.value = result
        }
    }

    private fun getDateRange(cal: Calendar, filter: TimeFilter): Pair<Long, Long> {
        val toMs = System.currentTimeMillis()
        val fromCal = cal.clone() as Calendar

        when (filter) {
            TimeFilter.WEEK -> fromCal.add(Calendar.DAY_OF_YEAR, -7)
            TimeFilter.MONTH -> {
                fromCal.set(Calendar.DAY_OF_MONTH, 1)
                fromCal.set(Calendar.HOUR_OF_DAY, 0)
                fromCal.set(Calendar.MINUTE, 0)
                fromCal.set(Calendar.SECOND, 0)
            }
            TimeFilter.YEAR -> {
                fromCal.set(Calendar.MONTH, 0)
                fromCal.set(Calendar.DAY_OF_MONTH, 1)
                fromCal.set(Calendar.HOUR_OF_DAY, 0)
                fromCal.set(Calendar.MINUTE, 0)
                fromCal.set(Calendar.SECOND, 0)
            }
        }
        return Pair(fromCal.timeInMillis, toMs)
    }

    class Factory(private val ctx: Context, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ReportsViewModel(ctx, userId) as T
        }
    }
}
