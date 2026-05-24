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

    private val _currency = MutableLiveData<String>()
    val currency: LiveData<String> = _currency

    private val _accountCurrencyMap = MutableLiveData<Map<Long, String>>()
    val accountCurrencyMap: LiveData<Map<Long, String>> = _accountCurrencyMap

    val recentTransactions: LiveData<List<TransactionEntity>> =
        txDao.getRecentTransactions(userId)

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = settingsDao.getSettingsByUser(userId)
            val targetCurrency = settings?.currency ?: "VND"

            val accounts = accountDao.getAccountsByUserSync(userId)
            val accountCurrencyMap = accounts.associate { it.id to it.currency }

            val cal = Calendar.getInstance()
            val month = cal.get(Calendar.MONTH) + 1
            val yearInt = cal.get(Calendar.YEAR)

            val fromMs = com.financier.app.common.DateFormatter.getStartOfMonth(month, yearInt)
            val toMs = com.financier.app.common.DateFormatter.getEndOfMonth(month, yearInt)

            // Income / Expense tháng này
            val txList = txDao.getTransactionsInRangeSync(userId, fromMs, toMs)
            var income = 0.0
            var expense = 0.0
            for (tx in txList) {
                val accCurrency = accountCurrencyMap[tx.accountId] ?: "VND"
                val converted = com.financier.app.common.CurrencyFormatter.convert(tx.amount, accCurrency, targetCurrency)
                if (tx.type == "INCOME") {
                    income += converted
                } else {
                    expense += converted
                }
            }

            // Balance = tổng tiền ban đầu + thu - chi
            val allTxList = txDao.getAllTransactionsSync(userId)
            val initialBalance = accounts.sumOf {
                com.financier.app.common.CurrencyFormatter.convert(it.initialBalance, it.currency, targetCurrency)
            }
            var netTx = 0.0
            for (tx in allTxList) {
                val accCurrency = accountCurrencyMap[tx.accountId] ?: "VND"
                val converted = com.financier.app.common.CurrencyFormatter.convert(tx.amount, accCurrency, targetCurrency)
                if (tx.type == "INCOME") {
                    netTx += converted
                } else {
                    netTx -= converted
                }
            }
            val totalBalance = initialBalance + netTx

            // Daily spending (hôm nay)
            val todayStart = getStartOfDay()
            val todayEnd = System.currentTimeMillis()
            val todayTxList = txDao.getTransactionsInRangeSync(userId, todayStart, todayEnd)
            val daily = todayTxList.filter { it.type == "EXPENSE" }.sumOf { tx ->
                val accCurrency = accountCurrencyMap[tx.accountId] ?: "VND"
                com.financier.app.common.CurrencyFormatter.convert(tx.amount, accCurrency, targetCurrency)
            }

            // Weekly trend (7 ngày)
            val days = (0..6).map { i ->
                val c = Calendar.getInstance()
                c.add(Calendar.DAY_OF_YEAR, -6 + i)
                c
            }
            val startWeekly = getStartOfDay(days.first())
            val endWeekly = getEndOfDay(days.last())
            val weeklyTxList = txDao.getTransactionsInRangeSync(userId, startWeekly, endWeekly)
            val trendData = days.map { c ->
                val start = getStartOfDay(c)
                val end = getEndOfDay(c)
                val label = com.financier.app.common.DateFormatter.formatDay(c.time).take(3)
                val dayExpense = weeklyTxList.filter { it.type == "EXPENSE" && it.dateMs in start..end }.sumOf { tx ->
                    val accCurrency = accountCurrencyMap[tx.accountId] ?: "VND"
                    com.financier.app.common.CurrencyFormatter.convert(tx.amount, accCurrency, targetCurrency)
                }
                Pair(label, dayExpense.toFloat())
            }

            // Account name
            val selAccountId = settings?.selectedAccountId ?: -1L
            val selAccount = if (selAccountId != -1L) accountDao.getAccountById(selAccountId) else accounts.firstOrNull()

            withContext(Dispatchers.Main) {
                _accountCurrencyMap.value = accountCurrencyMap
                _currency.value = targetCurrency
                _balance.value = totalBalance
                _monthlyIncome.value = income
                _monthlyExpense.value = expense
                _dailySpending.value = daily
                _weeklyTrend.value = trendData
                _accountName.value = selAccount?.name ?: "Tài khoản"
            }
        }
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
