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

    // Trạng thái search
    private val _searchQuery = MutableLiveData("")

    val transactions: LiveData<List<TransactionEntity>> = _searchQuery.switchMap { query ->
        if (query.isNullOrBlank()) {
            txDao.getAllTransactions(userId)
        } else {
            txDao.searchTransactions(userId, query)
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

    class Factory(private val ctx: Context, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TransactionsViewModel(ctx, userId) as T
        }
    }
}
