package com.financier.app.ui.services

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.financier.app.data.news.FinancialNewsManager
import com.financier.app.data.news.NewsItem
import com.financier.app.databinding.FragmentServicesHubBinding
import com.financier.app.ui.news.ArticleReaderActivity
import com.financier.app.widget.FinanceWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        setupEnterpriseSection()
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

        // Quick Receive Money & My QR (Dedicated page)
        binding.btnQuickMyQr.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_receive_money)
        }

        // Quick history
        binding.btnQuickHistory.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_transactions)
        }

        // Refresh News
        binding.btnRefreshNews.setOnClickListener {
            AppAudio.playClick(requireContext())
            loadFinancialNews(forceRefresh = true)
        }

        // Read article web
        binding.btnReadArticleWeb.setOnClickListener {
            val news = currentNewsItem ?: return@setOnClickListener
            AppAudio.playClick(requireContext())
            ArticleReaderActivity.open(
                context = requireContext(),
                url = news.link,
                title = news.title,
                source = news.source
            )
        }

        // View all news feed
        binding.btnViewAllNews.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_news_feed)
        }

        // News card click -> direct web article reader
        binding.cardFinancialNews.setOnClickListener {
            val news = currentNewsItem ?: return@setOnClickListener
            AppAudio.playClick(requireContext())
            ArticleReaderActivity.open(
                context = requireContext(),
                url = news.link,
                title = news.title,
                source = news.source
            )
        }
    }

    private fun setupEnterpriseSection() {
        val isDawnAdmin = SessionManager.isDawnAdmin(requireContext())
        binding.layoutEnterpriseSection.visibility = if (isDawnAdmin) View.VISIBLE else View.GONE

        if (!isDawnAdmin) {
            return
        }

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
                    .setTitle("📋 Danh sách Hóa đơn Doanh nghiệp (@dawn)")
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
        // Open dedicated Bills Hub Page
        binding.btnViewAllBills.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_bills_hub)
        }

        // Each category tile opens BillsHub directly
        val billTiles = listOf(
            binding.itemBillElectric,
            binding.itemBillWater,
            binding.itemBillRent,
            binding.itemBillInternet,
            binding.itemPhoneTopup,
            binding.itemBillEducation,
            binding.itemBillShopping,
            binding.itemBillTransport
        )

        for (tile in billTiles) {
            tile.setOnClickListener {
                AppAudio.playClick(requireContext())
                findNavController().navigate(R.id.action_services_to_bills_hub)
            }
        }
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
        // Open dedicated Entertainment & Gamification Page
        binding.btnOpenEntertainment.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_entertainment_hub)
        }

        binding.cardGameWheel.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_entertainment_hub)
        }

        binding.cardGameTarot.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_entertainment_hub)
        }

        binding.cardGameCheckin.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigate(R.id.action_services_to_entertainment_hub)
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