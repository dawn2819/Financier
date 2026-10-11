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
        "food" to "#E9789A",
        "transport" to "#659CEB",
        "shopping" to "#E7A14B",
        "health" to "#E66D78",
        "entertainment" to "#9B78D4",
        "housing" to "#A97965",
        "education" to "#42AEB8",
        "gym" to "#CF5D98",
        "bills" to "#DA7759",
        "travel" to "#4E9EC9",
        "pets" to "#77A95A",
        "income" to "#188B76",
        "salary" to "#188B76",
        "others" to "#788397"
    )
}
