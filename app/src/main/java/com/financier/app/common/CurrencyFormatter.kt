package com.financier.app.common

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {

    fun convert(amount: Double, from: String, to: String): Double {
        if (from == to) return amount
        return if (from == "USD" && to == "VND") amount * 25000.0
        else if (from == "VND" && to == "USD") amount / 25000.0
        else amount
    }

    fun format(amount: Double, currency: String): String {
        return when (currency) {
            "VND" -> {
                val formatter = NumberFormat.getNumberInstance(Locale("vi", "VN"))
                formatter.maximumFractionDigits = 0
                "${formatter.format(amount)} ₫"
            }
            "USD" -> {
                val formatter = NumberFormat.getCurrencyInstance(Locale.US)
                formatter.format(amount)
            }
            else -> "${amount.toLong()} ₫"
        }
    }

    fun formatShort(amount: Double, currency: String): String {
        return when {
            amount >= 1_000_000_000 -> "${String.format("%.1f", amount / 1_000_000_000)}T ${getCurrencySymbol(currency)}"
            amount >= 1_000_000 -> "${String.format("%.1f", amount / 1_000_000)}Tr ${getCurrencySymbol(currency)}"
            amount >= 1_000 -> "${String.format("%.1f", amount / 1_000)}K ${getCurrencySymbol(currency)}"
            else -> format(amount, currency)
        }
    }

    fun getCurrencySymbol(currency: String): String = when (currency) {
        "VND" -> "₫"
        "USD" -> "$"
        else -> "₫"
    }

    /** Format raw number with thousands separators (no currency symbol) */
    fun formatRaw(amount: Long): String {
        val formatter = NumberFormat.getNumberInstance(Locale("vi", "VN"))
        formatter.maximumFractionDigits = 0
        return formatter.format(amount)
    }
}
