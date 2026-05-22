package com.financier.app.data.bank

import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedBankTransaction(
    val amount: Double,
    val type: String, // "INCOME" | "EXPENSE"
    val note: String,
    val dateMs: Long,
    val bankName: String
)

object BankSmsParser {
    fun parse(sender: String, body: String): ParsedBankTransaction? {
        val cleanBody = body.replace("\n", " ").trim()
        val normalizedSender = sender.uppercase()

        // 1. Detect Bank
        val bankName = when {
            normalizedSender.contains("VCB") || normalizedSender.contains("VIETCOMBANK") -> "Vietcombank"
            normalizedSender.contains("TCB") || normalizedSender.contains("TECHCOMBANK") -> "Techcombank"
            normalizedSender.contains("MB") || normalizedSender.contains("MBBANK") -> "MB Bank"
            normalizedSender.contains("ACB") -> "ACB"
            normalizedSender.contains("VPB") || normalizedSender.contains("VPBANK") -> "VPBank"
            normalizedSender.contains("BIDV") -> "BIDV"
            normalizedSender.contains("AGR") || normalizedSender.contains("AGRIBANK") -> "Agribank"
            normalizedSender.contains("VTB") || normalizedSender.contains("VIETINBANK") -> "VietinBank"
            normalizedSender.contains("TPB") || normalizedSender.contains("TPBANK") -> "TPBank"
            else -> "Bank"
        }

        // 2. Extract amount and type
        // Vietnamese SMS alerts format: +150,000 VND or -50.000đ or GD +100,000VND
        val amountRegex = Regex("([+-])\\s*([\\d,\\.]+)\\s*(?:VND|vnd|Vnd|đ|d|\\$)?")
        val amountMatch = amountRegex.find(cleanBody) ?: return null

        val sign = amountMatch.groupValues[1]
        val amountStr = amountMatch.groupValues[2].replace(",", "").replace(".", "")
        val amount = amountStr.toDoubleOrNull() ?: return null
        val type = if (sign == "+") "INCOME" else "EXPENSE"

        // 3. Extract description
        val descKeywords = listOf("ND:", "GD:", "Noi dung:", "Noi dung GD:", "Mota:", "nội dung:")
        var note = ""
        for (keyword in descKeywords) {
            val idx = cleanBody.indexOf(keyword, ignoreCase = true)
            if (idx != -1) {
                note = cleanBody.substring(idx + keyword.length).trim()
                break
            }
        }

        if (note.isEmpty()) {
            note = "Giao dịch qua SMS ngân hàng $bankName"
        }

        // 4. Extract Date
        val dateRegex = Regex("(\\d{2}[/-]\\d{2}[/-]\\d{4}|\\d{2}[/-]\\d{2})")
        val dateMatch = dateRegex.find(cleanBody)
        var dateMs = System.currentTimeMillis()
        if (dateMatch != null) {
            val dateStr = dateMatch.groupValues[1]
            try {
                val format = if (dateStr.contains("/")) {
                    if (dateStr.length == 5) SimpleDateFormat("dd/MM", Locale.getDefault())
                    else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                } else {
                    if (dateStr.length == 5) SimpleDateFormat("dd-MM", Locale.getDefault())
                    else SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                }
                format.parse(dateStr)?.let {
                    dateMs = it.time
                    if (dateStr.length == 5) {
                        val cal = java.util.Calendar.getInstance()
                        val currentYear = cal.get(java.util.Calendar.YEAR)
                        cal.time = it
                        cal.set(java.util.Calendar.YEAR, currentYear)
                        dateMs = cal.timeInMillis
                    }
                }
            } catch (_: Exception) {}
        }

        return ParsedBankTransaction(amount, type, note, dateMs, bankName)
    }

    // Helper to check if a sender represents a bank
    fun isBankSender(sender: String): Boolean {
        val normalized = sender.uppercase()
        val bankKeywords = listOf(
            "VCB", "VIETCOMBANK", "TCB", "TECHCOMBANK", "MB", "MBBANK", "ACB", 
            "VPB", "VPBANK", "BIDV", "AGR", "AGRIBANK", "VTB", "VIETINBANK", "TPB", "TPBANK"
        )
        return bankKeywords.any { normalized.contains(it) }
    }
}
