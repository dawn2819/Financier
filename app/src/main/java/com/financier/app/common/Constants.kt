package com.financier.app.common

object Constants {
    // Gemini API
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"
    const val GEMINI_MODEL = "gemini-1.5-flash"

    // Categories
    val EXPENSE_CATEGORIES = listOf(
        "food", "transport", "shopping", "health",
        "entertainment", "housing", "education", "gym",
        "bills", "travel", "pets", "others"
    )
    val INCOME_CATEGORIES = listOf(
        "salary", "freelance", "investment", "gift", "others"
    )

    // Account types
    val ACCOUNT_TYPES = listOf("CASH", "BANK", "EWALLET", "SAVINGS")

    // Colors for categories
    val CATEGORY_COLORS = mapOf(
        "food" to "#4CAF50",
        "transport" to "#2196F3",
        "shopping" to "#FF9800",
        "health" to "#F44336",
        "entertainment" to "#9C27B0",
        "housing" to "#795548",
        "education" to "#00BCD4",
        "gym" to "#E91E63",
        "bills" to "#FF5722",
        "travel" to "#03A9F4",
        "pets" to "#8BC34A",
        "income" to "#78DC77",
        "salary" to "#78DC77",
        "others" to "#607D8B"
    )
}
