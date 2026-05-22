package com.financier.app.ui.dashboard

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.financier.app.R
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.SessionManager
import com.financier.app.databinding.FragmentDashboardBinding
import com.financier.app.ui.transactions.TransactionAdapter
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.util.Calendar

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: DashboardViewModel
    private lateinit var transactionAdapter: TransactionAdapter
    private var isFirstLoad = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = SessionManager.getUserId(requireContext())
        viewModel = ViewModelProvider(
            this, DashboardViewModel.Factory(requireContext(), userId)
        )[DashboardViewModel::class.java]

        setupGreeting()
        setupRecyclerView()
        setupChart()
        setupClickListeners()
        observeData()
    }

    private fun setupGreeting() {
        val name = SessionManager.getDisplayName(requireContext())
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> getString(R.string.good_morning, name)
            hour < 18 -> getString(R.string.good_afternoon, name)
            else -> getString(R.string.good_evening, name)
        }
        binding.tvGreeting.text = greeting
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(
            currency = "VND",
            onItemClick = { /* TODO: transaction detail */ },
            onEditClick = { /* unused on dashboard */ },
            onDeleteClick = { /* unused on dashboard */ }
        )
        binding.rvRecentTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupChart() {
        val chart = binding.chartTrend
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            setBackgroundColor(Color.TRANSPARENT)
            axisRight.isEnabled = false
            axisLeft.apply {
                isEnabled = false
                setDrawGridLines(false)
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#BECAB9")
                textSize = 10f
                granularity = 1f
            }
            setExtraOffsets(0f, 8f, 0f, 8f)
        }
    }

    private fun setupClickListeners() {
        binding.tvViewAll.setOnClickListener {
            findNavController().navigate(R.id.transactionsFragment)
        }

        binding.chipAccountSelector.setOnClickListener {
            // TODO: show account picker dialog
        }

        // Avatar click → navigate to Profile
        binding.ivAvatar.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }

        // Notification bell → refresh data with animation
        binding.btnNotification.setOnClickListener {
            // Spin animation on bell
            ObjectAnimator.ofFloat(binding.btnNotification, "rotation", 0f, 20f, -20f, 15f, -15f, 0f).apply {
                duration = 500
                start()
            }
            viewModel.loadData()
        }

        // Scan QR code
        binding.btnScanQr.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_qr_scan)
        }
    }

    private fun observeData() {
        val currency = "VND"

        viewModel.balance.observe(viewLifecycleOwner) { balance ->
            binding.tvBalance.text = CurrencyFormatter.format(balance ?: 0.0, currency)
            // Animate balance text on first load
            if (isFirstLoad) {
                animateCardEntry(binding.tvBalance.parent.parent as? View)
            }
        }

        viewModel.monthlyIncome.observe(viewLifecycleOwner) { income ->
            binding.tvIncome.text = "+${CurrencyFormatter.format(income ?: 0.0, currency)}"
        }

        viewModel.monthlyExpense.observe(viewLifecycleOwner) { expense ->
            binding.tvExpense.text = "-${CurrencyFormatter.format(expense ?: 0.0, currency)}"
        }

        viewModel.dailySpending.observe(viewLifecycleOwner) { daily ->
            binding.tvDailySpending.text = CurrencyFormatter.format(daily ?: 0.0, currency)
        }

        viewModel.recentTransactions.observe(viewLifecycleOwner) { transactions ->
            transactionAdapter.submitList(transactions) {
                binding.rvRecentTransactions.scheduleLayoutAnimation()
            }
            if (isFirstLoad) {
                isFirstLoad = false
            }
        }

        viewModel.weeklyTrend.observe(viewLifecycleOwner) { trendData ->
            updateChart(trendData)
        }

        viewModel.accountName.observe(viewLifecycleOwner) { name ->
            binding.chipAccountSelector.text = name ?: "Tài khoản"
        }
    }

    private fun animateCardEntry(view: View?) {
        view ?: return
        view.alpha = 0f
        view.translationY = 30f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun updateChart(data: List<Pair<String, Float>>) {
        if (data.isEmpty()) return

        val entries = data.mapIndexed { index, pair ->
            BarEntry(index.toFloat(), pair.second)
        }
        val labels = data.map { it.first }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#4CAF50")
            setDrawValues(false)
            highLightColor = Color.parseColor("#78DC77")
        }

        binding.chartTrend.apply {
            this.data = BarData(dataSet).apply {
                barWidth = 0.5f
            }
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = labels.size
            animateY(800)
            invalidate()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data khi quay lại Dashboard (sau khi thêm giao dịch)
        if (!isFirstLoad) {
            viewModel.loadData()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
