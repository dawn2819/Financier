package com.financier.app.ui.transactions

import android.content.Context
import androidx.lifecycle.*
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TransactionsViewModel(context: Context, private val userId: Long) : ViewModel() {
    private val db = AppDatabase.getDatabase(context)
    private val txDao = db.transactionDao()
    private val accountDao = db.financialAccountDao()
    private val settingsDao = db.settingsDao()

    private val _searchQuery = MutableLiveData("")
    private val _filterType = MutableLiveData("ALL")

    val currency = MutableLiveData<String>()
    val accountCurrencyMap = MutableLiveData<Map<Long, String>>()

    private val _filterParams = MediatorLiveData<Pair<String, String>>().apply {
        var query = ""
        var type = "ALL"
        addSource(_searchQuery) { query = it ?: ""; value = Pair(query, type) }
        addSource(_filterType) { type = it ?: "ALL"; value = Pair(query, type) }
    }

    val transactions: LiveData<List<TransactionEntity>> = _filterParams.switchMap { (query, type) ->
        val source = if (query.isBlank()) {
            txDao.getAllTransactions(userId)
        } else {
            txDao.searchTransactions(userId, query)
        }
        source.map { list ->
            if (type == "ALL") list else list.filter { it.type.equals(type, ignoreCase = true) }
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

    class Factory(private val ctx: Context, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TransactionsViewModel(ctx, userId) as T
        }
    }
}
