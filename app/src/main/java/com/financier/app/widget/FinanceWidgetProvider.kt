package com.financier.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.financier.app.MainActivity
import com.financier.app.R
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.DateFormatter
import com.financier.app.common.SessionManager
import com.financier.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

class FinanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val content = loadWidgetContent(context)
                widgetIds.forEach { widgetId ->
                    manager.updateAppWidget(widgetId, buildViews(context, content))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private data class WidgetContent(val balance: String, val monthSummary: String)

    private suspend fun loadWidgetContent(context: Context): WidgetContent {
        val userId = SessionManager.getUserId(context)
        if (!SessionManager.isLoggedIn(context) || userId < 0) {
            return WidgetContent("—", context.getString(R.string.finance_widget_open_app))
        }

        return try {
            val db = AppDatabase.getDatabase(context)
            val settings = db.settingsDao().getSettingsByUser(userId)
            val currency = settings?.currency ?: "VND"
            val accounts = db.financialAccountDao().getAccountsByUserSync(userId)
            val selectedId = settings?.selectedAccountId?.takeIf { id -> accounts.any { it.id == id } } ?: -1L
            val selectedAccounts = if (selectedId == -1L) accounts else accounts.filter { it.id == selectedId }
            val accountCurrencies = accounts.associate { it.id to it.currency }
            val allTransactions = db.transactionDao().getAllTransactionsSync(userId)
                .filter { selectedId == -1L || it.accountId == selectedId }

            val initial = selectedAccounts.sumOf {
                CurrencyFormatter.convert(it.initialBalance, it.currency, currency)
            }
            val net = allTransactions.sumOf { tx ->
                val amount = CurrencyFormatter.convert(tx.amount, accountCurrencies[tx.accountId] ?: "VND", currency)
                if (tx.type == "INCOME") amount else -amount
            }
            val now = Calendar.getInstance()
            val month = now.get(Calendar.MONTH) + 1
            val year = now.get(Calendar.YEAR)
            val from = DateFormatter.getStartOfMonth(month, year)
            val to = DateFormatter.getEndOfMonth(month, year)
            val monthTransactions = allTransactions.filter { it.dateMs in from..to }
            val expense = monthTransactions.filter { it.type == "EXPENSE" }.sumOf { tx ->
                CurrencyFormatter.convert(tx.amount, accountCurrencies[tx.accountId] ?: "VND", currency)
            }
            val bills = monthTransactions.filter { it.type == "EXPENSE" && it.category.equals("bills", true) }
                .sumOf { tx ->
                    CurrencyFormatter.convert(tx.amount, accountCurrencies[tx.accountId] ?: "VND", currency)
                }
            val summary = context.getString(
                R.string.finance_widget_month_summary,
                CurrencyFormatter.formatShort(expense, currency),
                CurrencyFormatter.formatShort(bills, currency)
            )
            WidgetContent(CurrencyFormatter.formatShort(initial + net, currency), summary)
        } catch (_: Exception) {
            WidgetContent("—", context.getString(R.string.finance_widget_refresh_error))
        }
    }

    private fun buildViews(context: Context, content: WidgetContent): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_finance).apply {
            setTextViewText(R.id.widget_balance, content.balance)
            setTextViewText(R.id.widget_month_summary, content.monthSummary)
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 7201, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        return views
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FinanceWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, FinanceWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
