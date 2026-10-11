package com.financier.app.ui.transactions

import android.content.Context
import androidx.lifecycle.*
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.TransactionEntity
import com.financier.app.data.local.entity.FinancialAccountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

private data class TransactionFilters(
    val query: String = "",
    val type: String = "ALL",
    val accountId: Long = -1L,
    val period: String = "ALL"
)

class TransactionsViewModel(context: Context, private val userId: Long) : ViewModel() {
    private val db = AppDatabase.getDatabase(context)
    private val txDao = db.transactionDao()
    private val accountDao = db.financialAccountDao()
    private val settingsDao = db.settingsDao()

    private val _searchQuery = MutableLiveData("")
    private val _filterType = MutableLiveData("ALL")
    private val _filterAccountId = MutableLiveData(-1L)
    private val _filterPeriod = MutableLiveData("ALL")

    val currency = MutableLiveData<String>()
    val accountCurrencyMap = MutableLiveData<Map<Long, String>>()
    val accounts: LiveData<List<FinancialAccountEntity>> = accountDao.getAccountsByUser(userId)

    private val _filterParams = MediatorLiveData<TransactionFilters>().apply {
        fun update() {
            value = TransactionFilters(
                query = _searchQuery.value.orEmpty(),
                type = _filterType.value ?: "ALL",
                accountId = _filterAccountId.value ?: -1L,
                period = _filterPeriod.value ?: "ALL"
            )
        }
        addSource(_searchQuery) { update() }
        addSource(_filterType) { update() }
        addSource(_filterAccountId) { update() }
        addSource(_filterPeriod) { update() }
    }

    val transactions: LiveData<List<TransactionEntity>> = _filterParams.switchMap { filters ->
        val source = if (filters.query.isBlank()) {
            txDao.getAllTransactions(userId)
        } else {
            txDao.searchTransactions(userId, filters.query)
        }
        source.map { list ->
            val startMs = getPeriodStart(filters.period)
            val endMs = if (filters.period == "ALL") null else System.currentTimeMillis()
            list.filter { transaction ->
                (filters.type == "ALL" || transaction.type.equals(filters.type, ignoreCase = true)) &&
                    (filters.accountId == -1L || transaction.accountId == filters.accountId) &&
                    (startMs == null || transaction.dateMs >= startMs) &&
                    (endMs == null || transaction.dateMs <= endMs)
            }
        }
    }

    init {
        loadSettingsAndAccounts()
    }

    private fun loadSettingsAndAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = settingsDao.getSettingsByUser(userId)
            val accounts = accountDao.getAccountsByUserSync(userId)
            val targetCurrency = settings?.currency ?: "VND"
            val map = accounts.associate { it.id to it.currency }
            currency.postValue(targetCurrency)
            accountCurrencyMap.postValue(map)
        }
    }

    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            txDao.deleteTransaction(tx)
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: String) {
        _filterType.value = type
    }

    fun setFilterAccount(accountId: Long) {
        _filterAccountId.value = accountId
    }

    fun setFilterPeriod(period: String) {
        _filterPeriod.value = period
    }

    private fun getPeriodStart(period: String): Long? {
        val calendar = Calendar.getInstance()
        return when (period) {
            "MONTH" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            "30_DAYS" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            else -> null
        }
    }

    class Factory(private val ctx: Context, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TransactionsViewModel(ctx, userId) as T
        }
    }
}
