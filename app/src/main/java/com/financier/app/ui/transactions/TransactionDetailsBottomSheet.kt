package com.financier.app.ui.transactions

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.financier.app.R
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.DateFormatter
import com.financier.app.data.local.AppDatabase
import com.financier.app.databinding.BottomsheetTransactionDetailsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetTransactionDetailsBinding? = null
    private val binding get() = _binding!!
    private var transactionId: Long = -1L

    companion object {
        private const val ARG_TX_ID = "transaction_id"

        fun newInstance(transactionId: Long): TransactionDetailsBottomSheet {
            val args = Bundle().apply {
                putLong(ARG_TX_ID, transactionId)
            }
            return TransactionDetailsBottomSheet().apply {
                arguments = args
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transactionId = arguments?.getLong(ARG_TX_ID) ?: -1L
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetTransactionDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnDetailClose.setOnClickListener {
            dismiss()
        }

        loadData()
    }

    private fun loadData() {
        if (transactionId == -1L) {
            dismiss()
            return
        }

        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = withContext(Dispatchers.Main) {
                com.financier.app.common.SessionManager.getUserId(requireContext())
            }
            val txEntity = db.transactionDao().getAllTransactionsSync(userId).firstOrNull { it.id == transactionId }
            if (txEntity == null) {
                withContext(Dispatchers.Main) { dismiss() }
                return@launch
            }

            val account = db.financialAccountDao().getAccountById(txEntity.accountId)
            val settings = db.settingsDao().getSettingsByUser(userId)
            val targetCurrency = settings?.currency ?: "VND"

            withContext(Dispatchers.Main) {
                bindDetails(txEntity, account?.name ?: "Tài khoản", account?.currency ?: "VND", targetCurrency)
            }
        }
    }

    private fun bindDetails(
        tx: com.financier.app.data.local.entity.TransactionEntity,
        accountName: String,
        accountCurrency: String,
        targetCurrency: String
    ) {
        val isIncome = tx.type == "INCOME"
        val prefix = if (isIncome) "+" else "-"
        val color = if (isIncome) Color.parseColor("#78DC77") else Color.parseColor("#FFB4AB")

        // Format amount in account's original currency
        binding.tvDetailAmount.text = "$prefix${CurrencyFormatter.format(tx.amount, accountCurrency)}"
        binding.tvDetailAmount.setTextColor(color)

        // If target currency differs from account's original currency, display converted amount
        if (targetCurrency != accountCurrency) {
            val converted = CurrencyFormatter.convert(tx.amount, accountCurrency, targetCurrency)
            binding.tvDetailConvertedAmount.visibility = View.VISIBLE
            binding.tvDetailConvertedAmount.text = "(= $prefix${CurrencyFormatter.format(converted, targetCurrency)})"
        } else {
            binding.tvDetailConvertedAmount.visibility = View.GONE
        }

        // Bind other fields
        val (_, categoryName, _) = TransactionAdapter.getCategoryInfo(requireContext(), tx.category)
        binding.tvDetailCategory.text = categoryName
        binding.tvDetailNote.text = tx.note.ifEmpty { "Không có" }
        binding.tvDetailAccount.text = accountName
        binding.tvDetailTime.text = "${DateFormatter.formatTime(tx.dateMs)} • ${DateFormatter.formatDate(tx.dateMs)}"

        // SMS Badge
        binding.layoutDetailSms.visibility = if (tx.isSmsSynced) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
