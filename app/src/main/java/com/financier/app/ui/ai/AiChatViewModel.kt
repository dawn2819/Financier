package com.financier.app.ui.ai

import android.content.Context
import androidx.lifecycle.*
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

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
            val cal = Calendar.getInstance()
            val month = cal.get(Calendar.MONTH) + 1
            val year = cal.get(Calendar.YEAR).toString()

            val income = db.transactionDao().getTotalIncome(userId, month, year)
            val expense = db.transactionDao().getTotalExpense(userId, month, year)
            val settings = db.settingsDao().getSettingsByUser(userId)
            val currency = settings?.currency ?: "VND"
            val balance = income - expense

            val result = aiRepo.sendMessage(
                userMessage = userText,
                totalBalance = balance,
                monthlyIncome = income,
                monthlyExpense = expense,
                currency = currency
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
