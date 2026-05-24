package com.financier.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.financier.app.BuildConfig
import com.financier.app.data.local.dao.*
import com.financier.app.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        UserEntity::class,
        FinancialAccountEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        AppSettingsEntity::class,
        BankSmsLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun financialAccountDao(): FinancialAccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun settingsDao(): SettingsDao
    abstract fun bankSmsLogDao(): BankSmsLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private var seeded = false

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = buildPassphrase(context)
                val factory = SupportFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "financier_db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance

                // Seed data sau khi INSTANCE đã được gán
                if (!seeded) {
                    seeded = true
                    CoroutineScope(Dispatchers.IO).launch {
                        seedIfEmpty(instance)
                    }
                }

                instance
            }
        }

        private suspend fun seedIfEmpty(db: AppDatabase) {
            try {
                val userCount = db.userDao().getUserCount()
                if (userCount > 0) return // Đã có data, skip

                // Tạo user mặc định "Dawn" (admin)
                val passwordHash = com.financier.app.common.SecurityUtils.hashPassword("dawn123")

                val dawnUser = UserEntity(
                    username = "dawn",
                    displayName = "Dawn",
                    passwordHash = passwordHash,
                    role = "admin",
                    avatarColor = "#78DC77"
                )
                val userId = db.userDao().insertUser(dawnUser)

                // Tạo settings mặc định
                val settings = AppSettingsEntity(
                    userId = userId,
                    language = "vi",
                    currency = "VND",
                    darkMode = true,
                    notificationsOn = true
                )
                db.settingsDao().insertSettings(settings)

                // Tạo tài khoản tài chính mặc định
                val cashAccount = FinancialAccountEntity(
                    userId = userId,
                    name = "Tiền mặt",
                    accountType = "CASH",
                    initialBalance = 0.0,
                    currency = "VND",
                    colorHex = "#78DC77",
                    icon = "account_balance_wallet"
                )
                val cashId = db.financialAccountDao().insertAccount(cashAccount)

                val bankAccount = FinancialAccountEntity(
                    userId = userId,
                    name = "Tài khoản ngân hàng",
                    accountType = "BANK",
                    initialBalance = 0.0,
                    currency = "VND",
                    colorHex = "#9ECAFF",
                    icon = "account_balance"
                )
                db.financialAccountDao().insertAccount(bankAccount)

                // Cập nhật selected account
                db.settingsDao().updateSettings(settings.copy(selectedAccountId = cashId))

                // Seed giao dịch mẫu
                val now = System.currentTimeMillis()
                val oneDayMs = 86400000L

                val sampleTransactions = listOf(
                    TransactionEntity(userId = userId, accountId = cashId, amount = 10_000_000.0, type = "INCOME", category = "income", note = "Lương tháng 5", dateMs = now - oneDayMs),
                    TransactionEntity(userId = userId, accountId = cashId, amount = 150_000.0, type = "EXPENSE", category = "food", note = "Cơm trưa", dateMs = now - 2 * oneDayMs),
                    TransactionEntity(userId = userId, accountId = cashId, amount = 50_000.0, type = "EXPENSE", category = "transport", note = "Grab", dateMs = now - 2 * oneDayMs),
                    TransactionEntity(userId = userId, accountId = cashId, amount = 500_000.0, type = "EXPENSE", category = "shopping", note = "Mua đồ", dateMs = now - 3 * oneDayMs),
                    TransactionEntity(userId = userId, accountId = cashId, amount = 200_000.0, type = "EXPENSE", category = "food", note = "Siêu thị", dateMs = now - 4 * oneDayMs)
                )
                sampleTransactions.forEach { db.transactionDao().insertTransaction(it) }

                android.util.Log.d("AppDatabase", "✅ Seed data thành công! User: dawn / dawn123")
            } catch (e: Exception) {
                android.util.Log.e("AppDatabase", "❌ Seed data lỗi: ${e.message}", e)
            }
        }

        private fun buildPassphrase(context: Context): ByteArray {
            val raw = "${BuildConfig.DB_SALT}_${context.packageName}"
            return SQLiteDatabase.getBytes(raw.toCharArray())
        }
    }
}
