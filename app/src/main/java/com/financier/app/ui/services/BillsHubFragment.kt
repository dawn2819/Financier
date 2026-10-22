package com.financier.app.ui.services

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.financier.app.R
import com.financier.app.common.AppAudio
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.SessionManager
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.FinancialAccountEntity
import com.financier.app.data.local.entity.InvoiceEntity
import com.financier.app.data.local.entity.TransactionEntity
import com.financier.app.databinding.FragmentBillsHubBinding
import com.financier.app.widget.FinanceWidgetProvider
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BillsHubFragment : Fragment() {

    private var _binding: FragmentBillsHubBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillsHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigateUp()
        }

        setupQuickServiceCards()
        setupLookupButton()
        loadBills()
    }

    override fun onResume() {
        super.onResume()
        loadBills()
    }

    private fun setupQuickServiceCards() {
        binding.cardBillElectric.setOnClickListener {
            AppAudio.playClick(requireContext())
            showCategoryPayDialog("Tiền điện EVN", "bills", "Mã khách hàng EVN (vd: EVN-2024):", 450000.0, "EVN-2024")
        }

        binding.cardBillWater.setOnClickListener {
            AppAudio.playClick(requireContext())
            showCategoryPayDialog("Tiền nước sinh hoạt", "bills", "Mã danh bộ nước:", 125000.0, "WAT-102")
        }

        binding.cardBillRent.setOnClickListener {
            AppAudio.playClick(requireContext())
            showCategoryPayDialog("Tiền nhà / Phòng trọ", "housing", "Mã phòng / Hóa đơn thuê:", 3500000.0, "NHA-P302")
        }

        binding.cardBillInternet.setOnClickListener {
            AppAudio.playClick(requireContext())
            showCategoryPayDialog("Internet & Truyền hình", "bills", "Mã hợp đồng mạng:", 220000.0, "NET-5501")
        }

        binding.cardPhoneTopup.setOnClickListener {
            AppAudio.playClick(requireContext())
            showPhoneTopupDialog()
        }

        binding.cardBillEducation.setOnClickListener {
            AppAudio.playClick(requireContext())
            showCategoryPayDialog("Học phí & Khóa học", "education", "Mã sinh viên / học viên:", 1500000.0, "EDU-9812")
        }

        binding.cardBillShopping.setOnClickListener {
            AppAudio.playClick(requireContext())
            confirmAndPayDirect("Đi chợ & Siêu thị", "shopping", "HD-SHOP", 300000.0, null)
        }

        binding.cardBillTransport.setOnClickListener {
            AppAudio.playClick(requireContext())
            confirmAndPayDirect("Xăng xe & Đi lại", "transport", "HD-GAS", 100000.0, null)
        }
    }

    private fun setupLookupButton() {
        binding.btnLookupBill.setOnClickListener {
            AppAudio.playClick(requireContext())
            val code = binding.etBillCode.text?.toString()?.trim().orEmpty()
            if (code.isBlank()) {
                Toast.makeText(requireContext(), "Vui lòng nhập mã hóa đơn cần tra cứu", Toast.LENGTH_SHORT).show()
                binding.etBillCode.requestFocus()
                return@setOnClickListener
            }
            lookupAndPayBill(code)
        }
    }

    private fun loadBills() {
        val username = SessionManager.getUsername(requireContext()).ifEmpty { "dawn" }
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val allInvoices = try {
                db.invoiceDao().getAllInvoicesSync()
            } catch (_: Exception) {
                emptyList()
            }

            // Filter for current user or public bills
            val userInvoices = allInvoices.filter {
                it.targetUsername.equals("ALL", ignoreCase = true) ||
                it.targetUsername.equals(username, ignoreCase = true) ||
                it.creatorUsername.equals(username, ignoreCase = true)
            }

            val unpaid = userInvoices.filter { it.status == "UNPAID" }
            val paid = userInvoices.filter { it.status == "PAID" }

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                renderBills(unpaid, paid)
            }
        }
    }

    private fun renderBills(unpaid: List<InvoiceEntity>, paid: List<InvoiceEntity>) {
        binding.containerUnpaidBills.removeAllViews()
        binding.containerPaidBills.removeAllViews()

        binding.tvUnpaidCountBadge.text = "${unpaid.size} cần đóng"
        binding.tvNoUnpaidBills.visibility = if (unpaid.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(requireContext())

        // Render Unpaid Bills
        unpaid.forEach { inv ->
            val itemView = inflater.inflate(R.layout.item_bill_card, binding.containerUnpaidBills, false)
            bindBillView(itemView, inv, isUnpaid = true)
            binding.containerUnpaidBills.addView(itemView)
        }

        // Render Paid Bills
        paid.forEach { inv ->
            val itemView = inflater.inflate(R.layout.item_bill_card, binding.containerPaidBills, false)
            bindBillView(itemView, inv, isUnpaid = false)
            binding.containerPaidBills.addView(itemView)
        }
    }

    private fun bindBillView(itemView: View, invoice: InvoiceEntity, isUnpaid: Boolean) {
        val ivIcon = itemView.findViewById<ImageView>(R.id.iv_bill_icon)
        val tvTitle = itemView.findViewById<TextView>(R.id.tv_bill_title)
        val tvCodeAndCreator = itemView.findViewById<TextView>(R.id.tv_bill_code_and_creator)
        val tvStatus = itemView.findViewById<TextView>(R.id.tv_bill_status)
        val tvAmount = itemView.findViewById<TextView>(R.id.tv_bill_amount)
        val btnAction = itemView.findViewById<MaterialButton>(R.id.btn_pay_now)

        tvTitle.text = invoice.title
        tvCodeAndCreator.text = "Mã: ${invoice.billCode} · Đơn vị: @${invoice.creatorUsername}"
        tvAmount.text = CurrencyFormatter.format(invoice.amount, "VND")

        // Icon based on category
        val iconRes = when {
            invoice.category.contains("housing", true) || invoice.title.contains("nhà", true) -> R.drawable.ic_cat_housing
            invoice.title.contains("điện", true) || invoice.category.contains("electric", true) -> R.drawable.ic_cat_bills
            invoice.title.contains("nước", true) || invoice.category.contains("water", true) -> R.drawable.ic_cat_health
            invoice.title.contains("internet", true) -> R.drawable.ic_cat_entertainment
            invoice.category.contains("education", true) -> R.drawable.ic_cat_education
            else -> R.drawable.ic_cat_bills
        }
        ivIcon.setImageResource(iconRes)

        if (isUnpaid) {
            tvStatus.text = "Chưa đóng"
            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
            btnAction.text = "Thanh toán"
            btnAction.visibility = View.VISIBLE
            btnAction.setOnClickListener {
                AppAudio.playClick(requireContext())
                confirmAndPayDirect(invoice.title, invoice.category, invoice.billCode, invoice.amount, invoice)
            }
        } else {
            val dateStr = invoice.paidAtMs?.let {
                SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date(it))
            } ?: "Đã hoàn tất"
            tvStatus.text = "✓ Đã thanh toán"
            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.tertiary))
            tvCodeAndCreator.text = "Mã: ${invoice.billCode} · $dateStr"
            btnAction.text = "Xem chi tiết"
            btnAction.setOnClickListener {
                AppAudio.playClick(requireContext())
                AlertDialog.Builder(requireContext())
                    .setTitle("Chi tiết hóa đơn")
                    .setMessage(
                        "Dịch vụ: ${invoice.title}\n" +
                        "Mã hóa đơn: ${invoice.billCode}\n" +
                        "Số tiền: ${CurrencyFormatter.format(invoice.amount, "VND")}\n" +
                        "Đơn vị phát hành: @${invoice.creatorUsername}\n" +
                        "Trạng thái: Đã thanh toán thành công\n" +
                        "Thời gian: $dateStr"
                    )
                    .setPositiveButton("Đóng", null)
                    .show()
            }
        }
    }

    private fun showCategoryPayDialog(
        serviceName: String,
        category: String,
        hint: String,
        suggestedAmount: Double,
        defaultCode: String
    ) {
        val input = EditText(requireContext()).apply {
            this.hint = hint
            setText(defaultCode)
            setPadding(48, 36, 48, 36)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("⚡ Thanh toán $serviceName")
            .setView(input)
            .setPositiveButton("Tiếp tục") { _, _ ->
                val code = input.text.toString().trim().ifEmpty { defaultCode }
                lookupAndPayBill(code, serviceName, category, suggestedAmount)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun lookupAndPayBill(
        code: String,
        fallbackTitle: String = "Thanh toán hóa đơn",
        fallbackCategory: String = "bills",
        fallbackAmount: Double = 450000.0
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val invoice = db.invoiceDao().getInvoiceByCode(code)

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (invoice != null) {
                    if (invoice.status == "PAID") {
                        AlertDialog.Builder(requireContext())
                            .setTitle("ℹ️ Hóa đơn đã đóng")
                            .setMessage("Hóa đơn [${invoice.billCode}] '${invoice.title}' đã được thanh toán hoàn tất trước đó.")
                            .setPositiveButton("Đóng", null)
                            .show()
                        return@withContext
                    }
                    confirmAndPayDirect(invoice.title, invoice.category, invoice.billCode, invoice.amount, invoice)
                } else {
                    // Not in database, allow paying as generic bill
                    confirmAndPayDirect(fallbackTitle, fallbackCategory, code, fallbackAmount, null)
                }
            }
        }
    }

    private fun confirmAndPayDirect(
        title: String,
        category: String,
        code: String,
        amount: Double,
        invoice: InvoiceEntity?
    ) {
        val userId = SessionManager.getUserId(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val accounts = db.financialAccountDao().getAccountsByUserSync(userId)
            val primaryAccount = accounts.firstOrNull()

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (primaryAccount == null) {
                    Toast.makeText(requireContext(), "Vui lòng tạo tài khoản nguồn trước khi thanh toán", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val creatorStr = if (invoice != null) "\nĐơn vị phát hành: @${invoice.creatorUsername}" else ""
                AlertDialog.Builder(requireContext())
                    .setTitle("Xác nhận thanh toán")
                    .setMessage(
                        "Dịch vụ: $title\n" +
                        "Mã hóa đơn: $code$creatorStr\n" +
                        "Số tiền cước: ${CurrencyFormatter.format(amount, primaryAccount.currency)}\n" +
                        "Tài khoản trích: ${primaryAccount.name}\n" +
                        "Phí dịch vụ: Miễn phí (0 ₫)"
                    )
                    .setPositiveButton("Thanh toán ngay") { _, _ ->
                        executeBillPayment(primaryAccount, title, category, code, amount, invoice)
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
        }
    }

    private fun executeBillPayment(
        account: FinancialAccountEntity,
        title: String,
        category: String,
        code: String,
        amount: Double,
        invoice: InvoiceEntity?
    ) {
        val userId = SessionManager.getUserId(requireContext())
        val username = SessionManager.getUsername(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val timestamp = System.currentTimeMillis()

            // 1. Ghi chi phí cho user thanh toán
            db.transactionDao().insertTransaction(
                TransactionEntity(
                    userId = userId,
                    accountId = account.id,
                    amount = amount,
                    type = "EXPENSE",
                    category = category,
                    note = "Thanh toán $title ($code)",
                    dateMs = timestamp
                )
            )

            // 2. Cập nhật trạng thái hóa đơn doanh nghiệp thành PAID
            if (invoice != null) {
                db.invoiceDao().updateInvoice(
                    invoice.copy(
                        status = "PAID",
                        paidAtMs = timestamp,
                        paidByUserId = userId
                    )
                )

                // 3. Nếu doanh nghiệp tạo hóa đơn là user khác (vd @dawn), ghi nhận thu nhập cho doanh nghiệp
                if (invoice.creatorId != userId) {
                    val creatorAccounts = db.financialAccountDao().getAccountsByUserSync(invoice.creatorId)
                    if (creatorAccounts.isNotEmpty()) {
                        db.transactionDao().insertTransaction(
                            TransactionEntity(
                                userId = invoice.creatorId,
                                accountId = creatorAccounts.first().id,
                                amount = amount,
                                type = "INCOME",
                                category = "income",
                                note = "Thu cước hóa đơn $code từ @$username",
                                dateMs = timestamp
                            )
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                AppAudio.playClick(requireContext())
                FinanceWidgetProvider.updateAll(requireContext())
                loadBills()

                AlertDialog.Builder(requireContext())
                    .setTitle("🎉 Thanh toán thành công!")
                    .setMessage("Đã thanh toán hóa đơn $title với số tiền ${CurrencyFormatter.format(amount, account.currency)}.")
                    .setPositiveButton("Hoàn tất", null)
                    .show()
            }
        }
    }

    private fun showPhoneTopupDialog() {
        val phoneInput = EditText(requireContext()).apply {
            hint = "Nhập số điện thoại (vd: 0912345678)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(48, 36, 48, 36)
        }

        val denominations = arrayOf("50.000 ₫", "100.000 ₫", "200.000 ₫", "500.000 ₫")
        val amounts = doubleArrayOf(50000.0, 100000.0, 200000.0, 500000.0)

        AlertDialog.Builder(requireContext())
            .setTitle("📱 Nạp tiền điện thoại")
            .setView(phoneInput)
            .setItems(denominations) { _, which ->
                val phone = phoneInput.text.toString().trim().ifEmpty { "0988888888" }
                confirmAndPayDirect("Nạp ĐT $phone", "bills", phone, amounts[which], null)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
