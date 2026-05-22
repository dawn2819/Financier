package com.financier.app.ui.reports
 
import android.content.Context
import androidx.lifecycle.*
import com.financier.app.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Calendar
 
data class CategorySpending(val category: String, val amount: Double, val percentage: Float)
data class MonthlyComparison(val label: String, val income: Double, val expense: Double)
 
enum class TimeFilter { DAY, WEEK, MONTH, YEAR }
 
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
 
        val categorySums = txDao.getExpenseByCategoryRange(userId, fromMs, toMs)
        val spending = categorySums.filter { it.amount > 0 }.map { sum ->
            CategorySpending(sum.category, sum.amount, 0f)
        }.toMutableList()
        val total = spending.sumOf { it.amount }
 
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
 
    private suspend fun loadMonthlyComparison() = coroutineScope {
        val result = mutableListOf<MonthlyComparison>()
        val monthNames = listOf("T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8", "T9", "T10", "T11", "T12")
 
        val months = (0..5).map { i ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -5 + i)
            cal
        }
 
        val deferredList = months.map { cal ->
            val month = cal.get(Calendar.MONTH) + 1
            val yearInt = cal.get(Calendar.YEAR)
            val fromMs = com.financier.app.common.DateFormatter.getStartOfMonth(month, yearInt)
            val toMs = com.financier.app.common.DateFormatter.getEndOfMonth(month, yearInt)
            val label = monthNames[month - 1]
            async(Dispatchers.IO) {
                val income = txDao.getTotalIncomeRange(userId, fromMs, toMs)
                val expense = txDao.getTotalExpenseRange(userId, fromMs, toMs)
                MonthlyComparison(label, income, expense)
            }
        }
 
        val finalResult = deferredList.awaitAll()
 
        withContext(Dispatchers.Main) {
            _monthlyComparison.value = finalResult
        }
    }

    private fun getDateRange(cal: Calendar, filter: TimeFilter): Pair<Long, Long> {
        val toMs = System.currentTimeMillis()
        val fromCal = cal.clone() as Calendar

        when (filter) {
            TimeFilter.DAY -> {
                fromCal.set(Calendar.HOUR_OF_DAY, 0)
                fromCal.set(Calendar.MINUTE, 0)
                fromCal.set(Calendar.SECOND, 0)
                fromCal.set(Calendar.MILLISECOND, 0)
            }
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
