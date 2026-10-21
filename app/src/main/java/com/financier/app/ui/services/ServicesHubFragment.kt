package com.financier.app.ui.services

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.financier.app.data.news.FinancialNewsManager
import com.financier.app.data.news.NewsItem
import com.financier.app.databinding.FragmentServicesHubBinding
import com.financier.app.widget.FinanceWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class ServicesHubFragment : Fragment() {

    private var _binding: FragmentServicesHubBinding? = null
    private val binding get() = _binding!!

    private var currentNewsItem: NewsItem? = null
    private var userAccounts = listOf<FinancialAccountEntity>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServicesHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserAccounts()
        loadFinancialNews()
        setupQuickActions()
        setupEnterpriseSection()
        setupPopularServices()
        setupLivingBills()
        setupFinancialServices()
        setupGamification()
        setupSearchFilter()
    }

    override fun onResume() {
        super.onResume()
        updateInvoiceBadge()
    }

    private fun loadUserAccounts() {
        val userId = SessionManager.getUserId(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val accounts = db.financialAccountDao().getAccountsByUserSync(userId)
            withContext(Dispatchers.Main) {
                userAccounts = accounts
            }
        }
    }

    private fun loadFinancialNews(forceRefresh: Boolean = false) {
        lifecycleScope.launch {
            if (forceRefresh) {
                binding.tvNewsTitle.text = "Đang cập nhật bản tin 8:00 AM..."
            }
            val newsList = FinancialNewsManager.getNews(requireContext(), forceRefresh)
            val topNews = newsList.firstOrNull() ?: FinancialNewsManager.getCachedHeadlineSync(requireContext())
            currentNewsItem = topNews

            binding.tvNewsBadge.text = "8:00 AM · ${topNews.source.uppercase()}"
            binding.tvNewsTitle.text = topNews.title
            binding.tvNewsDescription.text = topNews.description

            if (forceRefresh) {
                Toast.makeText(requireContext(), "Đã cập nhật bản tin mới nhất từ ${topNews.source}", Toast.LENGTH_SHORT).show()
                FinanceWidgetProvider.updateAll(requireContext())
            }
        }
    }

    private fun setupQuickActions() {
        // Quick transfer
        binding.btnQuickTransfer.setOnClickListener {
            AppAudio.playClick(requireContext())
            P2PTransferBottomSheet.newInstance().show(childFragmentManager, P2PTransferBottomSheet.TAG)
        }

        // Quick scan QR
        binding.btnQuickScanQr.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_qr)
        }

        // Quick my QR
        binding.btnQuickMyQr.setOnClickListener {
            AppAudio.playClick(requireContext())
            showMyQrDialog()
        }

        // Quick history
        binding.btnQuickHistory.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_transactions)
        }

        // News banner click
        binding.btnRefreshNews.setOnClickListener {
            AppAudio.playClick(requireContext())
            loadFinancialNews(forceRefresh = true)
        }

        binding.cardFinancialNews.setOnClickListener {
            val news = currentNewsItem ?: return@setOnClickListener
            AppAudio.playClick(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle("📰 ${news.source}")
                .setMessage("${news.title}\n\n${news.description}\n\nThời gian: ${news.pubDate}")
                .setPositiveButton("Đọc bài viết trên web") { _, _ ->
                    if (news.link.isNotBlank()) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(news.link))
                            startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(requireContext(), "Không thể mở trình duyệt", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Đóng", null)
                .show()
        }
    }

    private fun setupEnterpriseSection() {
        binding.btnCreateInvoice.setOnClickListener {
            AppAudio.playClick(requireContext())
            CreateInvoiceBottomSheet.newInstance().show(childFragmentManager, CreateInvoiceBottomSheet.TAG)
        }

        binding.btnViewInvoices.setOnClickListener {
            AppAudio.playClick(requireContext())
            showInvoiceListDialog()
        }

        updateInvoiceBadge()
    }

    private fun updateInvoiceBadge() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val count = db.invoiceDao().getInvoiceCount()
            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    binding.tvInvoiceCountBadge.text = "$count hóa đơn"
                }
            }
        }
    }

    private fun showInvoiceListDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val invoices = db.invoiceDao().getAllInvoicesSync()

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (invoices.isEmpty()) {
                    Toast.makeText(requireContext(), "Chưa có hóa đơn nào được tạo", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val items = invoices.map { inv ->
                    val statusIcon = if (inv.status == "PAID") "✓ ĐÃ THANH TOÁN" else "⏳ CHƯA THANH TOÁN"
                    val formatted = CurrencyFormatter.format(inv.amount, "VND")
                    "[${inv.billCode}] ${inv.title}\n• $formatted · Khách: @${inv.targetUsername}\n• Trạng thái: $statusIcon"
                }.toTypedArray()

                AlertDialog.Builder(requireContext())
                    .setTitle("📋 Danh sách Hóa đơn Doanh nghiệp")
                    .setItems(items) { _, which ->
                        val selected = invoices[which]
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("BillCode", selected.billCode)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "Đã sao chép mã: ${selected.billCode}", Toast.LENGTH_SHORT).show()
                    }
                    .setPositiveButton("Tạo mới") { _, _ ->
                        CreateInvoiceBottomSheet.newInstance().show(childFragmentManager, CreateInvoiceBottomSheet.TAG)
                    }
                    .setNegativeButton("Đóng", null)
                    .show()
            }
        }
    }

    private fun setupPopularServices() {
        binding.itemServiceP2p.setOnClickListener {
            AppAudio.playClick(requireContext())
            P2PTransferBottomSheet.newInstance().show(childFragmentManager, P2PTransferBottomSheet.TAG)
        }

        binding.itemServiceCrypto.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_crypto)
        }

        binding.itemServiceNearby.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_nearby)
        }

        binding.itemServiceBudget.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_budget)
        }
    }

    private fun setupLivingBills() {
        binding.itemBillElectric.setOnClickListener {
            AppAudio.playClick(requireContext())
            showBillPaymentDialog("Tiền điện EVN", "bills", "Mã khách hàng EVN (vd: EVN-2024):", 450000.0)
        }

        binding.itemBillWater.setOnClickListener {
            AppAudio.playClick(requireContext())
            showBillPaymentDialog("Tiền nước sinh hoạt", "bills", "Mã danh bộ nước (vd: WAT-102):", 125000.0)
        }

        binding.itemBillRent.setOnClickListener {
            AppAudio.playClick(requireContext())
            showBillPaymentDialog("Tiền nhà / Chung cư", "housing", "Số căn hộ / phòng trọ (vd: NHA-P302):", 3500000.0)
        }

        binding.itemBillInternet.setOnClickListener {
            AppAudio.playClick(requireContext())
            showBillPaymentDialog("Internet & Truyền hình", "bills", "Mã hợp đồng mạng:", 220000.0)
        }

        binding.itemPhoneTopup.setOnClickListener {
            AppAudio.playClick(requireContext())
            showPhoneTopupDialog()
        }

        binding.itemBillEducation.setOnClickListener {
            AppAudio.playClick(requireContext())
            showBillPaymentDialog("Học phí & Dịch vụ công", "education", "Mã sinh viên / học viên:", 1500000.0)
        }

        binding.itemBillShopping.setOnClickListener {
            AppAudio.playClick(requireContext())
            showQuickExpenseDialog("Đi chợ & Siêu thị", "shopping", 300000.0)
        }

        binding.itemBillTransport.setOnClickListener {
            AppAudio.playClick(requireContext())
            showQuickExpenseDialog("Xăng xe & Đi lại", "transport", 100000.0)
        }
    }

    private fun showBillPaymentDialog(
        serviceName: String,
        category: String,
        inputHint: String,
        suggestedAmount: Double
    ) {
        val input = EditText(requireContext()).apply {
            hint = inputHint
            setPadding(48, 36, 48, 36)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("⚡ Thanh toán: $serviceName")
            .setMessage("Nhập mã hóa đơn do doanh nghiệp phát hành để tra cứu:")
            .setView(input)
            .setPositiveButton("Tra cứu & Thanh toán") { _, _ ->
                val code = input.text.toString().trim().ifEmpty { "EVN-2024" }
                confirmAndPayBill(serviceName, category, code, suggestedAmount)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun confirmAndPayBill(
        serviceName: String,
        category: String,
        customerCode: String,
        amount: Double
    ) {
        val userId = SessionManager.getUserId(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val accounts = db.financialAccountDao().getAccountsByUserSync(userId)
            val primaryAccount = accounts.firstOrNull()

            // Check if this matches a registered enterprise invoice!
            val invoice = db.invoiceDao().getInvoiceByCode(customerCode)

            withContext(Dispatchers.Main) {
                if (primaryAccount == null) {
                    Toast.makeText(requireContext(), "Vui lòng tạo tài khoản nguồn trước khi thanh toán", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                if (invoice != null && invoice.status == "PAID") {
                    AlertDialog.Builder(requireContext())
                        .setTitle("ℹ️ Hóa đơn đã thanh toán")
                        .setMessage("Hóa đơn [$customerCode] '${invoice.title}' đã được thanh toán hoàn tất trước đó!")
                        .setPositiveButton("Đóng", null)
                        .show()
                    return@withContext
                }

                val finalTitle = invoice?.title ?: serviceName
                val finalCategory = invoice?.category ?: category
                val finalAmount = invoice?.amount ?: amount
                val creatorInfo = if (invoice != null) "\nĐơn vị phát hành: @${invoice.creatorUsername}" else ""

                AlertDialog.Builder(requireContext())
                    .setTitle("Xác nhận thanh toán")
                    .setMessage(
                        "Dịch vụ: $finalTitle\n" +
                        "Mã hóa đơn: $customerCode$creatorInfo\n" +
                        "Số tiền cước: ${CurrencyFormatter.format(finalAmount, primaryAccount.currency)}\n" +
                        "Tài khoản trích: ${primaryAccount.name}\n" +
                        "Phí dịch vụ: Miễn phí (0 ₫)"
                    )
                    .setPositiveButton("Thanh toán ngay") { _, _ ->
                        executeBillPayment(primaryAccount, finalTitle, finalCategory, customerCode, finalAmount, invoice)
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
        }
    }

    private fun executeBillPayment(
        account: FinancialAccountEntity,
        serviceName: String,
        category: String,
        customerCode: String,
        amount: Double,
        invoice: InvoiceEntity?
    ) {
        val userId = SessionManager.getUserId(requireContext())
        val username = SessionManager.getUsername(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val timestamp = System.currentTimeMillis()

            // 1. Ghi chi phí cho user thanh toán
            val tx = TransactionEntity(
                userId = userId,
                accountId = account.id,
                amount = amount,
                type = "EXPENSE",
                category = category,
                note = "Thanh toán $serviceName ($customerCode)",
                dateMs = timestamp
            )
            db.transactionDao().insertTransaction(tx)

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
                        val creatorAcc = creatorAccounts.first()
                        db.transactionDao().insertTransaction(
                            TransactionEntity(
                                userId = invoice.creatorId,
                                accountId = creatorAcc.id,
                                amount = amount,
                                type = "INCOME",
                                category = "income",
                                note = "Thu cước hóa đơn $customerCode từ @$username",
                                dateMs = timestamp
                            )
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {
                AppAudio.playClick(requireContext())
                updateInvoiceBadge()
                FinanceWidgetProvider.updateAll(requireContext())

                AlertDialog.Builder(requireContext())
                    .setTitle("🎉 Thanh toán thành công!")
                    .setMessage("Đã thanh toán thành công hóa đơn $serviceName với số tiền ${CurrencyFormatter.format(amount, account.currency)}.")
                    .setPositiveButton("Đóng", null)
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
                confirmAndPayBill("Nạp ĐT $phone", "bills", phone, amounts[which])
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showQuickExpenseDialog(title: String, category: String, amount: Double) {
        confirmAndPayBill(title, category, "Hóa đơn ngày hôm nay", amount)
    }

    private fun setupFinancialServices() {
        binding.cardServiceLoan.setOnClickListener {
            AppAudio.playClick(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle("💳 Ví Trả Sau Financier PayLater")
                .setMessage(
                    "• Hạn mức khả dụng: 20.000.000 ₫\n" +
                    "• Lãi suất: 0% trong 45 ngày đầu\n" +
                    "• Miễn phí thường niên\n" +
                    "• Thanh toán hóa đơn, mua sắm trước - trả sau linh hoạt."
                )
                .setPositiveButton("Kích hoạt ngay") { _, _ ->
                    AppAudio.playClick(requireContext())
                    Toast.makeText(requireContext(), "Kích hoạt Ví Trả Sau thành công! Hạn mức 20.000.000 ₫ đã sẵn sàng.", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Đóng", null)
                .show()
        }

        binding.cardServiceSavings.setOnClickListener {
            AppAudio.playClick(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle("📈 Tiết Kiệm Tích Lũy Financier")
                .setMessage(
                    "• Lãi suất sinh lời: 6.5% / năm\n" +
                    "• Trả lãi tự động vào tài khoản mỗi ngày lúc 8:00 AM\n" +
                    "• Rút tiền gốc & lãi linh hoạt bất kỳ lúc nào không mất phí."
                )
                .setPositiveButton("Mở sổ tiết kiệm") { _, _ ->
                    AppAudio.playClick(requireContext())
                    Toast.makeText(requireContext(), "Đã đăng ký tài khoản sinh lời tự động!", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Đóng", null)
                .show()
        }
    }

    private fun setupGamification() {
        // Lucky Wheel
        binding.cardGameWheel.setOnClickListener {
            AppAudio.playClick(requireContext())
            val rewards = listOf(
                "🎉 Trúng 10.000 ₫ tiền mặt!",
                "🎟 Voucher giảm 30.000 ₫ tiền điện EVN!",
                "🪙 Nhận 100 Điểm Thưởng Financier!",
                "🍀 Chúc bạn may mắn lần sau!",
                "🎁 Voucher hoàn tiền 50% thanh toán Internet!"
            )
            val won = rewards.random()
            AlertDialog.Builder(requireContext())
                .setTitle("🎡 Vòng Quay May Mắn")
                .setMessage("Vòng quay đang quay... ✨\n\nKết quả: $won")
                .setPositiveButton("Nhận thưởng") { _, _ ->
                    AppAudio.playClick(requireContext())
                }
                .show()
        }

        // Tarot
        binding.cardGameTarot.setOnClickListener {
            AppAudio.playClick(requireContext())
            val tarotCards = listOf(
                "🌟 The Sun - Vận may tài lộc rực rỡ hôm nay! Các dự định chi tiêu đều thuận lợi.",
                "🪙 The Empress - Thu hút dòng tiền thịnh vượng. Hãy dành một phần tích lũy cho mục tiêu dài hạn.",
                "⚖️ Justice - Cân bằng thu chi tuyệt đối. Tránh mua sắm bốc đồng hôm nay.",
                "🚀 The Chariot - Kế hoạch đầu tư tiến triển mạnh mẽ. Dòng tiền kinh doanh sinh lời tốt.",
                "💎 The Star - Hy vọng và cơ hội mới. Bạn sẽ nhận được một khoản tiền thưởng bất ngờ!"
            )
            val card = tarotCards.random()
            AlertDialog.Builder(requireContext())
                .setTitle("🔮 Bói Bài Tarot Tài Chính")
                .setMessage("Lá bài của bạn hôm nay:\n\n$card")
                .setPositiveButton("Cảm ơn vũ trụ", null)
                .show()
        }

        // Daily Check-in
        binding.cardGameCheckin.setOnClickListener {
            AppAudio.playClick(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle("🎁 Điểm Danh Hàng Ngày")
                .setMessage("Chuỗi điểm danh: Ngày 3 / 7 liên tiếp! ✨\n\nPhần thưởng hôm nay: +5.000 ₫ xu tích lũy.")
                .setPositiveButton("Tuyệt vời", null)
                .show()
        }
    }

    private fun showMyQrDialog() {
        val userId = SessionManager.getUserId(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val user = db.userDao().getUserById(userId)
            val username = user?.username ?: "financier_user"
            val displayName = user?.displayName ?: "Người dùng"

            withContext(Dispatchers.Main) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Mã QR nhận tiền cá nhân")
                    .setMessage(
                        "Tên: $displayName\n" +
                        "Username: @$username\n\n" +
                        "Người khác có thể nhập username @$username trong mục 'Chuyển tiền nội bộ' để gửi tiền cho bạn tức thì 24/7 không mất phí!"
                    )
                    .setPositiveButton("Sao chép Username") { _, _ ->
                        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Username", username)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "Đã sao chép @$username", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Đóng", null)
                    .show()
            }
        }
    }

    private fun setupSearchFilter() {
        binding.etSearchServices.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase().orEmpty()
                filterTiles(q)
            }
        })
    }

    private fun filterTiles(query: String) {
        val matchesP2p = query.isEmpty() || "chuyen tien transfer p2p".contains(query)
        val matchesCrypto = query.isEmpty() || "crypto vi tien ao bitcoin web3".contains(query)
        val matchesNearby = query.isEmpty() || "atm dia chi chi nhanh ngan hang".contains(query)
        val matchesBudget = query.isEmpty() || "ngan sach budget quan ly".contains(query)

        val matchesElectric = query.isEmpty() || "dien evn hoa don".contains(query)
        val matchesWater = query.isEmpty() || "nuoc sinh hoat hoa don".contains(query)
        val matchesRent = query.isEmpty() || "nha phong tro chung cu thue".contains(query)
        val matchesInternet = query.isEmpty() || "internet truyen hinh wifi mang".contains(query)
        val matchesPhone = query.isEmpty() || "dien thoai nap tien the cao topup".contains(query)
        val matchesEdu = query.isEmpty() || "hoc phi dich vu cong".contains(query)
        val matchesShop = query.isEmpty() || "di cho sieu thi mua sam shopping".contains(query)
        val matchesTransport = query.isEmpty() || "xang xe di lai grab bike car".contains(query)

        binding.itemServiceP2p.visibility = if (matchesP2p) View.VISIBLE else View.GONE
        binding.itemServiceCrypto.visibility = if (matchesCrypto) View.VISIBLE else View.GONE
        binding.itemServiceNearby.visibility = if (matchesNearby) View.VISIBLE else View.GONE
        binding.itemServiceBudget.visibility = if (matchesBudget) View.VISIBLE else View.GONE

        binding.itemBillElectric.visibility = if (matchesElectric) View.VISIBLE else View.GONE
        binding.itemBillWater.visibility = if (matchesWater) View.VISIBLE else View.GONE
        binding.itemBillRent.visibility = if (matchesRent) View.VISIBLE else View.GONE
        binding.itemBillInternet.visibility = if (matchesInternet) View.VISIBLE else View.GONE
        binding.itemPhoneTopup.visibility = if (matchesPhone) View.VISIBLE else View.GONE
        binding.itemBillEducation.visibility = if (matchesEdu) View.VISIBLE else View.GONE
        binding.itemBillShopping.visibility = if (matchesShop) View.VISIBLE else View.GONE
        binding.itemBillTransport.visibility = if (matchesTransport) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
