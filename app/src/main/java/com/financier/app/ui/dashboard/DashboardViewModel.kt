package com.financier.app.ui.dashboard

import android.content.Context
import androidx.lifecycle.*
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.*

class DashboardViewModel(context: Context, private val userId: Long) : ViewModel() {

    private val db = AppDatabase.getDatabase(context)
    private val txDao = db.transactionDao()
    private val accountDao = db.financialAccountDao()
    private val settingsDao = db.settingsDao()

    private val _balance = MutableLiveData<Double>()
    val balance: LiveData<Double> = _balance

    private val _monthlyIncome = MutableLiveData<Double>()
    val monthlyIncome: LiveData<Double> = _monthlyIncome

    private val _monthlyExpense = MutableLiveData<Double>()
    val monthlyExpense: LiveData<Double> = _monthlyExpense

    private val _dailySpending = MutableLiveData<Double>()
    val dailySpending: LiveData<Double> = _dailySpending

    private val _weeklyTrend = MutableLiveData<List<Pair<String, Float>>>()
    val weeklyTrend: LiveData<List<Pair<String, Float>>> = _weeklyTrend

    private val _accountName = MutableLiveData<String>()
    val accountName: LiveData<String> = _accountName

    val recentTransactions: LiveData<List<TransactionEntity>> =
        txDao.getRecentTransactions(userId)

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            val month = cal.get(Calendar.MONTH) + 1
            val yearInt = cal.get(Calendar.YEAR)

            val fromMs = com.financier.app.common.DateFormatter.getStartOfMonth(month, yearInt)
            val toMs = com.financier.app.common.DateFormatter.getEndOfMonth(month, yearInt)

            // Income / Expense tháng này
            val income = txDao.getTotalIncomeRange(userId, fromMs, toMs)
            val expense = txDao.getTotalExpenseRange(userId, fromMs, toMs)

            // Balance = tổng tiền ban đầu + thu - chi
            val accounts = accountDao.getAccountsByUserSync(userId)
            val initialBalance = accounts.sumOf { it.initialBalance }
            val totalBalance = initialBalance + income - expense

            // Daily spending (hôm nay)
            val todayStart = getStartOfDay()
            val todayEnd = System.currentTimeMillis()
            val daily = txDao.getExpenseBetween(userId, todayStart, todayEnd)

            // Weekly trend (7 ngày)
            val trendData = getWeeklyTrend()

            // Account name
            val settings = settingsDao.getSettingsByUser(userId)
            val selAccountId = settings?.selectedAccountId ?: -1L
            val selAccount = if (selAccountId != -1L) accountDao.getAccountById(selAccountId) else accounts.firstOrNull()

            withContext(Dispatchers.Main) {
                _balance.value = totalBalance
                _monthlyIncome.value = income
                _monthlyExpense.value = expense
                _dailySpending.value = daily
                _weeklyTrend.value = trendData
                _accountName.value = selAccount?.name ?: "Tài khoản"
            }
        }
    }

    private suspend fun getWeeklyTrend(): List<Pair<String, Float>> = coroutineScope {
        val days = (0..6).map { i ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -6 + i)
            cal
        }
        val deferredList = days.map { cal ->
            val start = getStartOfDay(cal)
            val end = getEndOfDay(cal)
            val label = com.financier.app.common.DateFormatter.formatDay(cal.time).take(3)
            async(Dispatchers.IO) {
                val expense = txDao.getExpenseBetween(userId, start, end).toFloat()
                Pair(label, expense)
            }
        }
        deferredList.awaitAll()
    }

    private fun getStartOfDay(cal: Calendar = Calendar.getInstance()): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun getEndOfDay(cal: Calendar = Calendar.getInstance()): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }

    class Factory(private val context: Context, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(context, userId) as T
        }
    }
}
