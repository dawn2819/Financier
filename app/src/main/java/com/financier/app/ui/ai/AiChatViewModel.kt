package com.financier.app.ui.ai

import android.content.Context
import androidx.lifecycle.*
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.DateFormatter
import com.financier.app.common.SessionManager

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class AiChatViewModel(private val context: Context, private val userId: Long) : ViewModel() {

    private val db = AppDatabase.getDatabase(context)
    private val aiRepo = AiRepository()

    private val _chatMessages = MutableLiveData<List<ChatMessage>>(emptyList())
    val chatMessages: LiveData<List<ChatMessage>> = _chatMessages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        // Greeting đầu tiên từ AI
        addAiMessage("Xin chào! Tôi là Financier AI 🤖\nTôi có thể giúp bạn phân tích chi tiêu, lên kế hoạch ngân sách và đưa ra lời khuyên tài chính. Bạn cần giúp gì?")
    }

    fun sendMessage(userText: String) {
        // Thêm tin nhắn user
        addUserMessage(userText)
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            // Lấy dữ liệu tài chính để đưa vào context
            val settings = db.settingsDao().getSettingsByUser(userId)
            val currency = settings?.currency ?: "VND"
            val accounts = db.financialAccountDao().getAccountsByUserSync(userId)
            val accountCurrencies = accounts.associate { it.id to it.currency }
            val now = Calendar.getInstance()
            val month = now.get(Calendar.MONTH) + 1
            val year = now.get(Calendar.YEAR)
            val fromMs = DateFormatter.getStartOfMonth(month, year)
            val toMs = DateFormatter.getEndOfMonth(month, year)

            val allTransactions = db.transactionDao().getAllTransactionsSync(userId)
            val monthlyTransactions = allTransactions.filter { it.dateMs in fromMs..toMs }
            var income = 0.0
            var expense = 0.0
            val expensesByCategory = mutableMapOf<String, Double>()
            for (transaction in monthlyTransactions) {
                val fromCurrency = accountCurrencies[transaction.accountId] ?: "VND"
                val amount = CurrencyFormatter.convert(transaction.amount, fromCurrency, currency)
                when (transaction.type) {
                    "INCOME" -> income += amount
                    "EXPENSE" -> {
                        expense += amount
                        expensesByCategory[transaction.category] =
                            (expensesByCategory[transaction.category] ?: 0.0) + amount
                    }
                }
            }

            val initialBalance = accounts.sumOf {
                CurrencyFormatter.convert(it.initialBalance, it.currency, currency)
            }
            val lifetimeNet = allTransactions.sumOf { transaction ->
                val fromCurrency = accountCurrencies[transaction.accountId] ?: "VND"
                val amount = CurrencyFormatter.convert(transaction.amount, fromCurrency, currency)
                when (transaction.type) {
                    "INCOME" -> amount
                    "EXPENSE" -> -amount
                    else -> 0.0
                }
            }
            val balance = initialBalance + lifetimeNet
            val topCategories = if (expense > 0.0) {
                expensesByCategory.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .joinToString(", ") { (category, amount) ->
                        val share = amount / expense * 100.0
                        "$category (${String.format(java.util.Locale.getDefault(), "%.0f", share)}%)"
                    }
            } else ""

            val username = SessionManager.getUsername(context)
            val unpaidInvoices = try {
                db.invoiceDao().getUnpaidInvoicesForUserSync(username)
            } catch (e: Exception) {
                emptyList()
            }
            val unpaidBillsCount = unpaidInvoices.size
            val unpaidBillsTotal = unpaidInvoices.sumOf { it.amount }

            val result = aiRepo.sendMessage(
                context = context,
                userMessage = userText,
                totalBalance = balance,
                monthlyIncome = income,
                monthlyExpense = expense,
                currency = currency,
                topCategories = topCategories,
                unpaidBillsCount = unpaidBillsCount,
                unpaidBillsTotal = unpaidBillsTotal
            )

            withContext(Dispatchers.Main) {
                _isLoading.value = false
                result.onSuccess { text -> addAiMessage(text) }
                result.onFailure { addAiMessage("Xin lỗi, tôi gặp lỗi. Vui lòng thử lại.") }
            }
        }
    }

    private fun addUserMessage(text: String) {
        val current = _chatMessages.value?.toMutableList() ?: mutableListOf()
        current.add(ChatMessage(text, isUser = true))
        _chatMessages.value = current
    }

    private fun addAiMessage(text: String) {
        val current = _chatMessages.value?.toMutableList() ?: mutableListOf()
        current.add(ChatMessage(text, isUser = false))
        _chatMessages.value = current
    }

    class Factory(private val context: Context, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AiChatViewModel(context, userId) as T
        }
    }
}
