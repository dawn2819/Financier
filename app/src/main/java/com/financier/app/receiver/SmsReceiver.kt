package com.financier.app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.financier.app.R
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.SessionManager
import com.financier.app.data.bank.BankSmsParser
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.BankSmsLogEntity
import com.financier.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val sender = message.displayOriginatingAddress ?: continue
                val body = message.displayMessageBody ?: continue
                val timestamp = message.timestampMillis

                if (BankSmsParser.isBankSender(sender)) {
                    processBankSms(context, sender, body, timestamp)
                }
            }
        }
    }

    private fun processBankSms(context: Context, sender: String, body: String, timestamp: Long) {
        val parsed = BankSmsParser.parse(sender, body) ?: return
        val userId = SessionManager.getUserId(context)
        if (userId == -1L) return // Not logged in

        val db = AppDatabase.getDatabase(context)
        val smsLogDao = db.bankSmsLogDao()
        val transactionDao = db.transactionDao()
        val accountDao = db.financialAccountDao()

        CoroutineScope(Dispatchers.IO).launch {
            // Check duplicate
            if (smsLogDao.isSmsParsed(body, timestamp) > 0) return@launch

            // Get bank account or first account
            val accounts = accountDao.getAccountsByUserSync(userId)
            val bankAccount = accounts.find { it.accountType == "BANK" || it.name.contains(parsed.bankName, ignoreCase = true) }
                ?: accounts.firstOrNull()
                ?: return@launch

            // Insert transaction
            val transaction = TransactionEntity(
                userId = userId,
                accountId = bankAccount.id,
                amount = parsed.amount,
                type = parsed.type,
                category = if (parsed.type == "INCOME") "income" else "others",
                note = "[${parsed.bankName}] ${parsed.note}",
                dateMs = parsed.dateMs,
                isSmsSynced = true
            )
            val txId = transactionDao.insertTransaction(transaction)

            // Log SMS
            smsLogDao.insertLog(
                BankSmsLogEntity(
                    smsBody = body,
                    sender = sender,
                    timestamp = timestamp,
                    transactionId = txId
                )
            )

            // Show Notification
            showNotification(context, parsed.bankName, parsed.amount, parsed.type)
        }
    }

    private fun showNotification(context: Context, bankName: String, amount: Double, type: String) {
        val channelId = "bank_sync_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Bank Sync Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val amountStr = CurrencyFormatter.format(amount, "VND")
        val contentText = if (type == "INCOME") {
            "Tự động thêm giao dịch thu nhập từ $bankName: +$amountStr"
        } else {
            "Tự động thêm giao dịch chi tiêu từ $bankName: -$amountStr"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_nav_reports)
            .setContentTitle("Đã đồng bộ ngân hàng")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
