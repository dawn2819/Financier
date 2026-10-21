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
import com.financier.app.data.news.FinancialNewsManager
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

    private data class WidgetContent(
        val billsSummary: String,
        val newsSource: String,
        val newsHeadline: String,
        val syncTimeBadge: String
    )

    private suspend fun loadWidgetContent(context: Context): WidgetContent {
        val userId = SessionManager.getUserId(context)
        val headline = try {
            val list = FinancialNewsManager.getNews(context, forceRefresh = false)
            list.firstOrNull() ?: FinancialNewsManager.getCachedHeadlineSync(context)
        } catch (_: Exception) {
            FinancialNewsManager.getCachedHeadlineSync(context)
        }

        val newsSource = "TIN TÀI CHÍNH 8:00 AM · ${headline.source.uppercase()}"
        val newsHeadline = headline.title

        if (!SessionManager.isLoggedIn(context) || userId < 0) {
            return WidgetContent(
                billsSummary = "⚡ Điện EVN · 🏠 Tiền nhà (Mở app để đồng bộ)",
                newsSource = newsSource,
                newsHeadline = newsHeadline,
                syncTimeBadge = "8:00 AM"
            )
        }

        return try {
            val db = AppDatabase.getDatabase(context)
            val settings = db.settingsDao().getSettingsByUser(userId)
            val currency = settings?.currency ?: "VND"

            val accounts = db.financialAccountDao().getAccountsByUserSync(userId)
            val accountCurrencies = accounts.associate { it.id to it.currency }

            val now = Calendar.getInstance()
            val month = now.get(Calendar.MONTH) + 1
            val year = now.get(Calendar.YEAR)
            val from = DateFormatter.getStartOfMonth(month, year)
            val to = DateFormatter.getEndOfMonth(month, year)

            val monthTransactions = db.transactionDao().getTransactionsInRangeSync(userId, from, to)

            // Look for bill/housing/utility transactions
            val billTransactions = monthTransactions.filter { tx ->
                tx.type == "EXPENSE" && (
                    tx.category.equals("bills", ignoreCase = true) ||
                    tx.category.equals("housing", ignoreCase = true) ||
                    tx.note.contains("điện", ignoreCase = true) ||
                    tx.note.contains("nước", ignoreCase = true) ||
                    tx.note.contains("nhà", ignoreCase = true) ||
                    tx.note.contains("internet", ignoreCase = true) ||
                    tx.note.contains("evn", ignoreCase = true)
                )
            }

            val username = SessionManager.getUsername(context)
            val unpaidInvoices = try {
                db.invoiceDao().getUnpaidInvoicesForUserSync(username)
            } catch (_: Exception) {
                emptyList()
            }

            val billsSummary = if (unpaidInvoices.isNotEmpty()) {
                val items = unpaidInvoices.take(2).map { inv ->
                    val icon = when {
                        inv.category.contains("housing", true) || inv.title.contains("nhà", true) -> "🏠"
                        inv.title.contains("điện", true) || inv.category.contains("electric", true) -> "⚡"
                        inv.title.contains("nước", true) || inv.category.contains("water", true) -> "💧"
                        inv.title.contains("internet", true) -> "🌐"
                        else -> "📄"
                    }
                    val amountStr = CurrencyFormatter.formatShort(
                        CurrencyFormatter.convert(inv.amount, "VND", currency),
                        currency
                    )
                    "$icon ${inv.billCode}: $amountStr"
                }
                items.joinToString(" · ")
            } else if (billTransactions.isNotEmpty()) {
                val items = billTransactions.take(2).map { tx ->
                    val icon = when {
                        tx.note.contains("điện", true) || tx.category.equals("bills", true) -> "⚡"
                        tx.note.contains("nước", true) -> "💧"
                        tx.note.contains("nhà", true) -> "🏠"
                        tx.note.contains("internet", true) -> "🌐"
                        else -> "📄"
                    }
                    val label = if (tx.note.isNotBlank()) tx.note else "Hóa đơn"
                    val amountStr = CurrencyFormatter.formatShort(
                        CurrencyFormatter.convert(tx.amount, accountCurrencies[tx.accountId] ?: "VND", currency),
                        currency
                    )
                    "$icon $label: $amountStr"
                }
                items.joinToString(" · ")
            } else {
                "⚡ Điện EVN: 450k · 🏠 Tiền nhà: 3.5Tr (Dự kiến)"
            }

            WidgetContent(
                billsSummary = billsSummary,
                newsSource = newsSource,
                newsHeadline = newsHeadline,
                syncTimeBadge = "8:00 AM"
            )
        } catch (_: Exception) {
            WidgetContent(
                billsSummary = "⚡ Điện EVN · 🏠 Tiền nhà (Chạm để xem)",
                newsSource = newsSource,
                newsHeadline = newsHeadline,
                syncTimeBadge = "8:00 AM"
            )
        }
    }

    private fun buildViews(context: Context, content: WidgetContent): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_finance).apply {
            setTextViewText(R.id.widget_sync_badge, content.syncTimeBadge)
            setTextViewText(R.id.widget_bills_content, content.billsSummary)
            setTextViewText(R.id.widget_news_source, content.newsSource)
            setTextViewText(R.id.widget_news_headline, content.newsHeadline)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "services")
        }
        val pending = PendingIntent.getActivity(
            context,
            7201,
            openIntent,
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
