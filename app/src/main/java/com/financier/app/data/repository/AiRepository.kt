package com.financier.app.data.repository

import android.content.Context
import com.financier.app.BuildConfig
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.SessionManager
import com.financier.app.data.remote.*
import java.text.Normalizer
import java.util.regex.Pattern

class AiRepository {

    private val apiService = RetrofitClient.geminiService

    // Lịch sử chat (in-memory)
    private val chatHistory = mutableListOf<GeminiContent>()

    fun getChatHistory(): List<GeminiContent> = chatHistory.toList()

    /**
     * Gửi tin nhắn đến Gemini hoặc bộ máy tài chính On-Device
     */
    suspend fun sendMessage(
        context: Context,
        userMessage: String,
        totalBalance: Double,
        monthlyIncome: Double,
        monthlyExpense: Double,
        currency: String,
        topCategories: String = "",
        unpaidBillsCount: Int = 0,
        unpaidBillsTotal: Double = 0.0
    ): Result<String> {
        val apiKey = SessionManager.getEffectiveGeminiApiKey(context)

        // Nếu người dùng đã cài đặt API key (hoặc có sẵn trong BuildConfig), thử gọi Google Gemini trước
        if (apiKey.isNotBlank()) {
            try {
                val systemPrompt = buildSystemPrompt(
                    totalBalance, monthlyIncome, monthlyExpense, currency, topCategories,
                    unpaidBillsCount, unpaidBillsTotal
                )

                val userContent = GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(userMessage))
                )

                val request = GeminiRequest(
                    contents = (chatHistory + userContent).takeLast(maxHistoryMessages),
                    systemInstruction = GeminiSystemInstruction(
                        parts = listOf(GeminiPart(systemPrompt))
                    )
                )

                val response = apiService.generateContent(
                    apiKey = apiKey,
                    request = request
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    val aiText = body?.candidates?.firstOrNull()
                        ?.content?.parts?.firstOrNull()?.text
                    if (!aiText.isNullOrBlank()) {
                        appendExchange(userMessage, aiText)
                        return Result.success(aiText)
                    }
                }

                // Nếu API trả về mã lỗi HTTP (400, 401, 403, 429...)
                val code = response.code()
                val localAnswer = generateLocalResponse(
                    userMessage = userMessage,
                    balance = totalBalance,
                    income = monthlyIncome,
                    expense = monthlyExpense,
                    currency = currency,
                    topCategories = topCategories,
                    unpaidBillsCount = unpaidBillsCount,
                    unpaidBillsTotal = unpaidBillsTotal
                )
                val notice = when (code) {
                    400, 401, 403 -> "⚠️ Khóa Gemini API Key không hợp lệ hoặc đã hết hạn (lỗi $code). Bạn có thể bấm biểu tượng 🔑 ở góc trên để cập nhật key mới. Phản hồi từ Trợ lý On-Device:\n\n"
                    429 -> "⏳ Gemini API đang bận hoặc quá hạn ngạch (lỗi 429). Phản hồi từ Trợ lý On-Device:\n\n"
                    else -> "⚠️ Google Gemini phản hồi mã lỗi $code. Phản hồi từ Trợ lý On-Device:\n\n"
                }
                val fullResponse = notice + localAnswer
                appendExchange(userMessage, fullResponse)
                return Result.success(fullResponse)
            } catch (e: Exception) {
                // Lỗi mạng hoặc timeout khi gọi Gemini -> Fallback mượt mà sang local engine
                val localAnswer = generateLocalResponse(
                    userMessage = userMessage,
                    balance = totalBalance,
                    income = monthlyIncome,
                    expense = monthlyExpense,
                    currency = currency,
                    topCategories = topCategories,
                    unpaidBillsCount = unpaidBillsCount,
                    unpaidBillsTotal = unpaidBillsTotal
                )
                val fullResponse = "🌐 Không thể kết nối tới Google Gemini. Trợ lý Financier On-Device phản hồi cho bạn:\n\n" + localAnswer
                appendExchange(userMessage, fullResponse)
                return Result.success(fullResponse)
            }
        }

        // Không cấu hình API key -> Dùng trực tiếp bộ máy trí tuệ tài chính On-Device (chạy mượt, không lỗi kết nối)
        val localResponse = generateLocalResponse(
            userMessage = userMessage,
            balance = totalBalance,
            income = monthlyIncome,
            expense = monthlyExpense,
            currency = currency,
            topCategories = topCategories,
            unpaidBillsCount = unpaidBillsCount,
            unpaidBillsTotal = unpaidBillsTotal
        )
        appendExchange(userMessage, localResponse)
        return Result.success(localResponse)
    }

    fun clearHistory() {
        chatHistory.clear()
    }

    private fun appendExchange(userMessage: String, assistantMessage: String) {
        chatHistory.add(GeminiContent("user", listOf(GeminiPart(userMessage))))
        chatHistory.add(GeminiContent("model", listOf(GeminiPart(assistantMessage))))
        if (chatHistory.size > maxHistoryMessages) {
            chatHistory.subList(0, chatHistory.size - maxHistoryMessages).clear()
        }
    }

    private val maxHistoryMessages = 40

    private fun buildSystemPrompt(
        balance: Double,
        income: Double,
        expense: Double,
        currency: String,
        topCategories: String,
        unpaidBillsCount: Int,
        unpaidBillsTotal: Double
    ): String {
        val balanceStr = CurrencyFormatter.format(balance, currency)
        val incomeStr = CurrencyFormatter.format(income, currency)
        val expenseStr = CurrencyFormatter.format(expense, currency)
        val unpaidBillsStr = CurrencyFormatter.format(unpaidBillsTotal, currency)

        return """
Bạn là AI tư vấn tài chính cá nhân trong app Financier. Hãy trả lời ngắn gọn, thiết thực, thân thiện và hữu ích.
Hỗ trợ cả tiếng Việt và tiếng Anh tùy theo câu hỏi của người dùng.

Dữ liệu tài chính thực tế của người dùng:
- Số dư hiện có: $balanceStr
- Thu nhập tháng này: $incomeStr
- Chi tiêu tháng này: $expenseStr
- Danh mục chi tiêu nhiều nhất: ${topCategories.ifBlank { "Chưa có" }}
- Hóa đơn sinh hoạt chưa thanh toán: $unpaidBillsCount hóa đơn (Tổng: $unpaidBillsStr)

Nguyên tắc:
1. Đưa ra lời khuyên tài chính thực tế, khích lệ tiết kiệm và chi tiêu có kỷ luật.
2. Không hỏi thông tin mật khẩu hay số thẻ nhạy cảm.
3. Khi người dùng hỏi về hóa đơn, ngân hàng, ATM, chuyển tiền, hãy hướng dẫn họ vào mục Dịch vụ trong ứng dụng.
4. Trả lời cô đọng, định dạng bullet points rõ ràng.
""".trimIndent()
    }

    /**
     * Chuẩn hóa văn bản tiếng Việt (loại bỏ dấu) để đối chiếu từ khóa chính xác
     */
    private fun normalizeText(text: String): String {
        val temp = Normalizer.normalize(text, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(temp).replaceAll("")
            .lowercase()
            .replace("đ", "d")
            .trim()
    }

    /**
     * Bộ máy phân tích và tư vấn tài chính On-Device thông minh
     */
    private fun generateLocalResponse(
        userMessage: String,
        balance: Double,
        income: Double,
        expense: Double,
        currency: String,
        topCategories: String,
        unpaidBillsCount: Int,
        unpaidBillsTotal: Double
    ): String {
        val norm = normalizeText(userMessage)

        return when {
            // 1. Lời chào & Giới thiệu
            norm.contains("xin chao") || norm.contains("chao") || norm.contains("hello") ||
            norm.contains("hi") || norm.contains("alo") || norm.contains("ban la ai") ||
            norm.contains("who are you") || norm.contains("tro ly") -> {
                "👋 **Xin chào! Tôi là Trợ lý tài chính Financier AI** 🤖\n\n" +
                "Tôi có thể hỗ trợ bạn mọi lúc mọi nơi:\n" +
                "• 📊 Phân tích thu chi & dòng tiền tháng này\n" +
                "• 💡 Lập kế hoạch tiết kiệm theo quy tắc 50/30/20\n" +
                "• 📄 Quản lý & nhắc nhở thanh toán hóa đơn\n" +
                "• 📈 Tư vấn đầu tư an toàn & quản lý hạn mức thẻ tín dụng\n" +
                "• 🏧 Tìm kiếm cây ATM & phòng giao dịch ngân hàng gần nhất\n\n" +
                "Bạn muốn tìm hiểu hoặc cần tư vấn điều gì hôm nay?"
            }

            // 2. Số dư & Tình hình tài khoản
            norm.contains("so du") || norm.contains("tai khoan") || norm.contains("tien hien co") ||
            norm.contains("balance") || norm.contains("tai san") || norm.contains("con bao nhieu") ||
            norm.contains("tong tien") -> {
                val balanceFormatted = CurrencyFormatter.format(balance, currency)
                val statusText = if (income > expense) {
                    val surplus = CurrencyFormatter.format(income - expense, currency)
                    "📈 Dòng tiền tháng này đang dương (+$surplus). Bạn đang kiểm soát tài chính rất tốt!"
                } else if (income > 0.0 && expense > income) {
                    val deficit = CurrencyFormatter.format(expense - income, currency)
                    "⚠️ Chi tiêu tháng này đang vượt thu nhập (-$deficit). Hãy cân đối lại các khoản chi không cấp thiết."
                } else {
                    "💡 Duy trì ghi nhận các khoản thu chi thường xuyên để tôi phân tích dòng tiền chính xác hơn."
                }
                "💰 **Số dư tài khoản hiện tại**: $balanceFormatted\n\n$statusText"
            }

            // 3. Chi tiêu & Phân tích chi tiêu
            norm.contains("chi tieu") || norm.contains("da tieu") || norm.contains("tieu dung") ||
            norm.contains("expense") || norm.contains("spending") || norm.contains("mua sam") ||
            norm.contains("thong ke chi") || norm.contains("phan tich chi tieu") || norm.contains("outcome") -> {
                val expenseStr = CurrencyFormatter.format(expense, currency)
                val catText = if (topCategories.isNotBlank()) "\n• Nhóm chi nhiều nhất: $topCategories" else ""
                val ratioText = if (income > 0.0) {
                    val ratio = (expense / income * 100).toInt()
                    val eval = if (ratio > 70) "⚠️ Cảnh báo: Tỷ lệ chi tiêu cao ($ratio%), nên rà soát lại nhu cầu cá nhân."
                               else "✅ An toàn: Tỷ lệ chi tiêu chiếm $ratio% thu nhập, được duy trì rất ổn định."
                    "\n• Tỷ lệ chi tiêu / thu nhập: $ratio%\n$eval"
                } else ""

                "📊 **Báo cáo Chi tiêu tháng này**:\n" +
                "• Tổng số tiền đã chi: $expenseStr$catText$ratioText\n\n" +
                "💡 Mẹo: Bạn có thể cài đặt hạn mức theo danh mục tại mục **Ngân sách** để nhận thông báo tự động khi chạm 80% ngân sách."
            }

            // 4. Thu nhập & Dòng tiền
            norm.contains("thu nhap") || norm.contains("tien luong") || norm.contains("luong") ||
            norm.contains("income") || norm.contains("tien vao") || norm.contains("dong tien") ||
            norm.contains("kiem tien") -> {
                val incomeStr = CurrencyFormatter.format(income, currency)
                val expenseStr = CurrencyFormatter.format(expense, currency)
                val netStr = CurrencyFormatter.format(income - expense, currency)

                "💵 **Báo cáo Thu nhập & Dòng tiền tháng này**:\n" +
                "• Tổng thu nhập: $incomeStr\n" +
                "• Tổng chi tiêu: $expenseStr\n" +
                "• Thặng dư tích lũy: $netStr\n\n" +
                "💡 Lời khuyên: Hãy thiết lập nguyên tắc 'Tự trả cho mình trước' — trích ngay 15-20% thu nhập sang tài khoản tiết kiệm ngay khi nhận lương."
            }

            // 5. Tiết kiệm & Quy tắc 50/30/20
            norm.contains("tiet kiem") || norm.contains("saving") || norm.contains("danh dum") ||
            norm.contains("50/30/20") || norm.contains("dat muc tieu tiet kiem") || norm.contains("tich luy") -> {
                val targetIncome = if (income > 0) income else 15000000.0
                val need = CurrencyFormatter.format(targetIncome * 0.5, currency)
                val want = CurrencyFormatter.format(targetIncome * 0.3, currency)
                val save = CurrencyFormatter.format(targetIncome * 0.2, currency)
                val emergencyFund = CurrencyFormatter.format(if (expense > 0) expense * 3 else 15000000.0, currency)

                "💡 **Kế hoạch Tiết kiệm thông minh theo Quy tắc 50/30/20**:\n" +
                (if (income > 0) "Phân bổ lý tưởng theo mức thu nhập ${CurrencyFormatter.format(income, currency)} của bạn:\n"
                 else "Khung phân bổ ngân sách chuẩn được khuyến nghị:\n") +
                "1. 🏠 **50% Nhu cầu thiết yếu ($need)**: Tiền thuê nhà, ăn uống, điện nước, xăng xe.\n" +
                "2. ☕ **30% Linh hoạt cá nhân ($want)**: Mua sắm, giải trí, cà phê, du lịch.\n" +
                "3. 📈 **20% Tích lũy & Đầu tư ($save)**: Quỹ khẩn cấp, sổ tiết kiệm, quỹ mở/ETF.\n\n" +
                "🛡️ **Quỹ dự phòng khẩn cấp**: Bạn nên có sẵn ít nhất $emergencyFund (tương đương 3 tháng chi tiêu) gửi tiết kiệm linh hoạt để sẵn sàng đối phó rủi ro phát sinh."
            }

            // 6. Hóa đơn & Tiện ích sinh hoạt
            norm.contains("hoa don") || norm.contains("bill") || norm.contains("tien dien") ||
            norm.contains("tien nuoc") || norm.contains("tien mang") || norm.contains("internet") ||
            norm.contains("evn") || norm.contains("thanh toan hoa don") || norm.contains("xem hoa don") -> {
                val billNotice = if (unpaidBillsCount > 0) {
                    val billsTotalStr = CurrencyFormatter.format(unpaidBillsTotal, currency)
                    "⚠️ Bạn đang có **$unpaidBillsCount** hóa đơn chưa thanh toán với tổng số tiền **$billsTotalStr**.\n" +
                    "👉 Hãy vào mục **Dịch vụ -> Hóa đơn sinh hoạt** để tra cứu mã hóa đơn và thanh toán ngay."
                } else {
                    "✅ Hiện tại bạn không có hóa đơn sinh hoạt nào bị quá hạn hoặc đang chờ thanh toán."
                }

                "📄 **Thông tin Hóa đơn & Sinh hoạt**:\n" +
                "$billNotice\n\n" +
                "💡 Mẹo tài chính: Các nhà cung cấp điện nước EVN thường phát hành hóa đơn vào ngày 10-15 hàng tháng. Bạn có thể lưu mã khách hàng trong Financier để theo dõi và thanh toán 1-chạm."
            }

            // 7. Đầu tư & Sinh lời / Lãi suất / Cổ phiếu / Vàng
            norm.contains("dau tu") || norm.contains("chung khoan") || norm.contains("co phieu") ||
            norm.contains("vang") || norm.contains("bat dong san") || norm.contains("quy etf") ||
            norm.contains("invest") || norm.contains("lai kep") || norm.contains("sinh loi") ||
            norm.contains("72") -> {
                "📈 **Chiến lược Đầu tư & Tăng trưởng tài sản an toàn**:\n" +
                "1. 🏦 **Tiết kiệm gửi góp kỳ hạn**: Dành cho tiền gửi ngắn hạn hoặc quỹ khẩn cấp (lãi suất hiện ~5.0% - 6.5%/năm).\n" +
                "2. 📊 **Quỹ ETF chỉ số (VN30, VNFinLead)**: Đầu tư đều đặn hàng tháng (DCA) từ 10-20% thu nhập để hưởng lợi từ sự phát triển kinh tế dài hạn.\n" +
                "3. 🪙 **Vàng nhẫn / SJC**: Nắm giữ 10-15% danh mục như một kênh phòng vệ lạm phát hiệu quả.\n" +
                "4. ⏱️ **Quy tắc 72**: Lấy 72 chia cho tỷ suất sinh lời (%) ra số năm cần để nhân đôi số tiền đầu tư (ví dụ lãi 9%/năm thì sau 8 năm tài sản sẽ gấp đôi)."
            }

            // 8. Vay vốn, Thẻ tín dụng & PayLater
            norm.contains("vay") || norm.contains("no") || norm.contains("credit") ||
            norm.contains("the tin dung") || norm.contains("tra gop") || norm.contains("dti") ||
            norm.contains("khoan vay") || norm.contains("paylater") || norm.contains("lai suat vay") -> {
                "💳 **Nguyên tắc Quản lý Vay & Thẻ tín dụng**:\n" +
                "• 📌 **Hạn mức DTI an toàn**: Tổng số tiền phải trả nợ và lãi vay mỗi tháng không nên vượt quá 35-40% thu nhập ròng.\n" +
                "• ⚡ **Tận dụng 45 ngày miễn lãi**: Luôn thanh toán 100% sao kê đúng hạn để không bị phạt phí trả chậm và lãi thẻ 25-35%/năm.\n" +
                "• 🛍️ **Dịch vụ Financier PayLater**: Hạn mức tiêu dùng 20.000.000đ miễn lãi 45 ngày được quản lý tại mục **Dịch vụ -> Khoản vay & Thẻ tín dụng**."
            }

            // 9. Chuyển tiền & Mã QR / VietQR
            norm.contains("chuyen tien") || norm.contains("transfer") || norm.contains("vietqr") ||
            norm.contains("napaso") || norm.contains("gui tien") || norm.contains("nhan tien") ||
            norm.contains("ma qr") || norm.contains("p2p") -> {
                "💸 **Chuyển tiền & Tạo mã VietQR trong Financier**:\n" +
                "• **Chuyển tiền nội bộ**: Chuyển tiền tức thì theo tên người dùng hoặc số tài khoản, miễn 100% phí giao dịch.\n" +
                "• **Tạo mã VietQR nhận tiền**: Vào mục **Dịch vụ -> Nhận tiền & VietQR** để tạo mã QR ngân hàng có sẵn số tiền và nội dung cho đối tác quét thanh toán cực kỳ tiện lợi!"
            }

            // 10. Tìm kiếm ATM & Ngân hàng
            norm.contains("atm") || norm.contains("ngan hang") || norm.contains("rut tien") ||
            norm.contains("chi nhanh") || norm.contains("bank") || norm.contains("cay atm") ||
            norm.contains("tim atm") || norm.contains("dia diem") -> {
                "🏦 **Tìm kiếm Cây ATM & Chi nhánh Ngân hàng gần nhất**:\n" +
                "Financier đã tích hợp sẵn công cụ định vị ngân hàng đa nguồn (Vietcombank, MB, Techcombank, BIDV, Agribank, VPBank...):\n" +
                "👉 Hãy mở mục **Dịch vụ -> Điểm dịch vụ & ATM** để xem danh sách điểm ATM gần vị trí của bạn nhất kèm khoảng cách, số hotline và nút chỉ đường Google Maps!"
            }

            // 11. Ví Crypto & Web3
            norm.contains("crypto") || norm.contains("web3") || norm.contains("bitcoin") ||
            norm.contains("eth") || norm.contains("tien ao") || norm.contains("blockchain") ||
            norm.contains("vi crypto") -> {
                "🪙 **Ví Crypto Web3 trong Financier**:\n" +
                "• Hỗ trợ quản lý số dư đa chuỗi (Bitcoin, Ethereum, Solana, USDT).\n" +
                "• Thiết kế Non-custodial bảo mật, người dùng tự lưu giữ khóa cá nhân.\n" +
                "⚠️ Lưu ý: Tài sản crypto có độ biến động cao, bạn chỉ nên phân bổ tối đa 5-10% danh mục đầu tư."
            }

            // 12. Giải trí & Mini-game
            norm.contains("game") || norm.contains("vong quay") || norm.contains("diem thuong") ||
            norm.contains("san thuong") || norm.contains("giai tri") || norm.contains("su kien") ||
            norm.contains("doi qua") -> {
                "🎉 **Giải trí & Điểm thưởng Financier**:\n" +
                "• Tham gia quay **Vòng quay may mắn** mỗi ngày để rinh điểm thưởng và voucher thanh toán hóa đơn.\n" +
                "• Tích lũy điểm Financier Point từ các giao dịch thanh toán để đổi quà tại mục **Dịch vụ -> Giải trí & Sự kiện**!"
            }

            // 13. Hướng dẫn kết nối AI / Cài đặt Gemini API Key
            norm.contains("ket noi") || norm.contains("api key") || norm.contains("gemini") ||
            norm.contains("offline") || norm.contains("sao khong ket noi") || norm.contains("khong ket noi") ||
            norm.contains("loi mang") || norm.contains("cai dat ai") -> {
                "⚙️ **Trạng thái Trợ lý Financier AI**:\n" +
                "• Hệ thống hiện đang kích hoạt: **Trí tuệ nhân tạo On-Device**.\n" +
                "• Ưu điểm: Phản hồi siêu tốc, hoạt động độc lập không phụ thuộc máy chủ, bảo mật tuyệt đối dữ liệu tài chính của bạn.\n" +
                "• Nếu muốn mở rộng sang mô hình **Google Gemini 1.5 Flash Cloud** để hỏi đáp tự do mọi chủ đề, bạn chỉ cần bấm vào biểu tượng 🔑 (chìa khóa) ở góc trên bên phải để dán API Key miễn phí từ Google AI Studio (`aistudio.google.com`)."
            }

            // 14. Phản hồi thông minh mặc định dựa trên dữ liệu thực tế
            else -> {
                val balanceStr = CurrencyFormatter.format(balance, currency)
                val incomeStr = CurrencyFormatter.format(income, currency)
                val expenseStr = CurrencyFormatter.format(expense, currency)

                "🤖 **Trợ lý Tài chính Financier**:\n" +
                "Tôi đã ghi nhận câu hỏi của bạn. Dưới đây là tóm tắt dữ liệu tài chính hiện tại:\n" +
                "• 💰 Số dư: $balanceStr\n" +
                "• 📈 Thu nhập: $incomeStr | 📉 Chi tiêu: $expenseStr\n\n" +
                "💡 **Bạn có thể thử hỏi tôi**:\n" +
                "1. *'Phân tích chi tiêu tháng này'*\n" +
                "2. *'Lời khuyên tiết kiệm theo quy tắc 50/30/20'*\n" +
                "3. *'Kiểm tra hóa đơn cần thanh toán'*\n" +
                "4. *'Tư vấn chiến lược đầu tư an toàn'*\n" +
                "5. *'Tìm cây ATM / ngân hàng gần nhất'*\n\n" +
                "✨ *Mẹo: Nhấn nút 🔑 ở góc trên để thêm Google Gemini API Key nếu bạn muốn trò chuyện mở rộng tự do!*"
            }
        }
    }
}
