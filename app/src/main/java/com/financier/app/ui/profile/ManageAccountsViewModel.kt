package com.financier.app.ui.profile

import android.content.Context
import androidx.lifecycle.*
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.FinancialAccountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ManageAccountsViewModel(context: Context, private val userId: Long) : ViewModel() {

    private val db = AppDatabase.getDatabase(context)
    private val accountDao = db.financialAccountDao()

    val accounts: LiveData<List<FinancialAccountEntity>> = accountDao.getAccountsByUser(userId)

    fun addAccount(
        name: String,
        type: String,
        currency: String,
        colorHex: String,
        cardNumber: String? = null,
        cardExpiry: String? = null,
        cardPin: String? = null,
        isLinked: Boolean = false,
        walletType: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            accountDao.insertAccount(
                FinancialAccountEntity(
                    userId = userId,
                    name = name,
                    accountType = type,
                    initialBalance = 0.0,
                    currency = currency,
                    colorHex = colorHex,
                    icon = getIconForType(type),
                    cardNumber = cardNumber,
                    cardExpiry = cardExpiry,
                    cardPin = cardPin,
                    isLinked = isLinked,
                    walletType = walletType
                )
            )
        }
    }

    fun deleteAccount(account: FinancialAccountEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            accountDao.deleteAccount(account)
        }
    }

    private fun getIconForType(type: String): String = when (type) {
        "CASH" -> "account_balance_wallet"
        "BANK" -> "account_balance"
        "CREDIT_CARD" -> "credit_card"
        "E_WALLET" -> "phone_android"
        else -> "account_balance_wallet"
    }

    class Factory(private val ctx: Context, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ManageAccountsViewModel(ctx, userId) as T
        }
    }
}
