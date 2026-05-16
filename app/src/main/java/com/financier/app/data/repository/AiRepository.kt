package com.financier.app.data.repository

import com.financier.app.BuildConfig
import com.financier.app.common.CurrencyFormatter
import com.financier.app.data.remote.*

class AiRepository {

    private val apiService = RetrofitClient.geminiService

    // Lịch sử chat (in-memory)
    private val chatHistory = mutableListOf<GeminiContent>()

    fun getChatHistory(): List<GeminiContent> = chatHistory.toList()

    /**
     * Gửi tin nhắn đến Gemini, kèm context tài chính của user
     */
    suspend fun sendMessage(
        userMessage: String,
        totalBalance: Double,
        monthlyIncome: Double,
        monthlyExpense: Double,
        currency: String,
        topCategories: String = ""
    ): Result<String> {
        return try {
            // Build system prompt
            val systemPrompt = buildSystemPrompt(
                totalBalance, monthlyIncome, monthlyExpense, currency, topCategories
            )

            // Thêm tin nhắn user vào history
            chatHistory.add(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(userMessage))
                )
            )

            val request = GeminiRequest(
                contents = chatHistory.toList(),
                systemInstruction = GeminiSystemInstruction(
                    parts = listOf(GeminiPart(systemPrompt))
                )
            )

            val response = apiService.generateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = request
            )

            if (response.isSuccessful) {
                val body = response.body()
                val aiText = body?.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull()?.text
                    ?: getDefaultResponse(userMessage)

                // Thêm response AI vào history
                chatHistory.add(
                    GeminiContent(
                        role = "model",
                        parts = listOf(GeminiPart(aiText))
                    )
                )

                Result.success(aiText)
            } else {
                val fallback = getDefaultResponse(userMessage)
                Result.success(fallback)
            }
        } catch (e: Exception) {
            // Fallback nếu không có internet hoặc API lỗi
            val fallback = getDefaultResponse(userMessage)
            Result.success(fallback)
        }
    }

    fun clearHistory() {
        chatHistory.clear()
    }

    private fun buildSystemPrompt(
        balance: Double, income: Double, expense: Double,
        currency: String, topCategories: String
    ): String {
        val balanceStr = CurrencyFormatter.format(balance, currency)
        val incomeStr = CurrencyFormatter.format(income, currency)
        val expenseStr = CurrencyFormatter.format(expense, currency)

        return """
Bạn là AI tư vấn tài chính cá nhân trong app Financier. Hãy trả lời ngắn gọn, thực tế, thân thiện.
Hỗ trợ cả tiếng Việt và tiếng Anh tùy theo câu hỏi của user.

Dữ liệu tài chính hiện tại của user:
- Số dư hiện tại: $balanceStr
- Thu nhập tháng này: $incomeStr  
- Chi tiêu tháng này: $expenseStr
- Danh mục chi tiêu nhiều nhất: $topCategories

Nguyên tắc:
1. Chỉ đưa ra lời khuyên tài chính cơ bản, không đảm bảo kết quả đầu tư
2. Không hỏi thông tin cá nhân nhạy cảm
3. Luôn khuyến khích tiết kiệm và quản lý chi tiêu có kế hoạch
4. Trả lời tối đa 3-4 câu, ngắn gọn
""".trimIndent()
    }

    // Fallback responses khi không có internet
    private fun getDefaultResponse(userMessage: String): String {
        val lowerMsg = userMessage.lowercase()
        return when {
            lowerMsg.contains("tiết kiệm") || lowerMsg.contains("saving") ->
                "💡 Để tiết kiệm hiệu quả, hãy áp dụng quy tắc 50/30/20: 50% cho nhu cầu thiết yếu, 30% cho mong muốn, 20% tiết kiệm & đầu tư."

            lowerMsg.contains("chi tiêu") || lowerMsg.contains("expense") || lowerMsg.contains("spending") ->
                "📊 Hãy xem tab Reports để phân tích chi tiêu theo danh mục. Tôi thấy bạn nên chú ý đến các khoản chi thường xuyên."

            lowerMsg.contains("budget") || lowerMsg.contains("ngân sách") ->
                "📝 Hãy đặt ngân sách cho từng danh mục trong tab Budget. Khi đạt 80% ngân sách, tôi sẽ nhắc bạn."

            lowerMsg.contains("thu nhập") || lowerMsg.contains("income") || lowerMsg.contains("lương") ->
                "💰 Đừng quên ghi lại tất cả nguồn thu nhập để theo dõi chính xác. Bạn có thể thêm thu nhập bằng nút + màu xanh."

            lowerMsg.contains("xin chào") || lowerMsg.contains("hello") || lowerMsg.contains("hi") ->
                "👋 Xin chào! Tôi là Financier AI. Tôi có thể giúp bạn phân tích chi tiêu, lên kế hoạch ngân sách, hoặc đưa ra lời khuyên tài chính. Bạn cần giúp gì?"

            else ->
                "🤖 Tôi đang xử lý câu hỏi của bạn. Hiện tại tôi chạy ở chế độ offline. Hãy kiểm tra kết nối internet để dùng đầy đủ tính năng AI."
        }
    }
}
