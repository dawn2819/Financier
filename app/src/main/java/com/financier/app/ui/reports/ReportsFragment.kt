package com.financier.app.ui.reports

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.financier.app.R
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.SessionManager
import com.financier.app.databinding.FragmentReportsBinding
import com.financier.app.ui.transactions.TransactionAdapter
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportsFragment : Fragment() {
    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ReportsViewModel
    private lateinit var breakdownAdapter: CategoryBreakdownAdapter
    private var currentCurrency = "VND"

    private val createPdfLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            try {
                val outputStream = requireContext().contentResolver.openOutputStream(it)
                if (outputStream != null) {
                    val filterText = when (viewModel.filter.value ?: TimeFilter.MONTH) {
                        TimeFilter.DAY -> getString(R.string.filter_day)
                        TimeFilter.WEEK -> getString(R.string.filter_week)
                        TimeFilter.MONTH -> getString(R.string.filter_month)
                        TimeFilter.YEAR -> getString(R.string.filter_year)
                    }
                    val income = viewModel.totalIncome.value ?: 0.0
                    val expense = viewModel.totalExpense.value ?: 0.0
                    val categories = viewModel.categorySpending.value ?: emptyList()

                    ReportExportHelper.exportToPdf(
                        requireContext(),
                        outputStream,
                        filterText,
                        currentCurrency,
                        income,
                        expense,
                        categories
                    )
                    outputStream.close()
                    android.widget.Toast.makeText(requireContext(), "PDF exported successfully", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Failed to export PDF: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private val createCsvLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val income = viewModel.totalIncome.value ?: 0.0
            val expense = viewModel.totalExpense.value ?: 0.0
            val categories = viewModel.categorySpending.value.orEmpty()
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                ReportExportHelper.exportToCsv(
                    outputStream = outputStream,
                    filterText = currentFilterLabel(),
                    currency = currentCurrency,
                    totalIncome = income,
                    totalExpense = expense,
                    categories = categories
                )
            } ?: throw IllegalStateException("Unable to open the selected file")
            android.widget.Toast.makeText(requireContext(), R.string.csv_export_success, android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.csv_export_failed, e.message ?: "Unknown error"),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = SessionManager.getUserId(requireContext())
        viewModel = ViewModelProvider(this, ReportsViewModel.Factory(requireContext(), userId))[ReportsViewModel::class.java]

        setupCharts()
        setupFilters()
        setupBreakdownList()
        observeData()

        binding.btnExportPdf.setOnClickListener {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val filename = "financier_report_${sdf.format(Date())}.pdf"
            createPdfLauncher.launch(filename)
        }
        binding.btnExportCsv.setOnClickListener {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            createCsvLauncher.launch("financier_report_${sdf.format(Date())}.csv")
        }
    }

    private fun setupCharts() {
        // Pie Chart
        binding.pieChart.apply {
            setNoDataText(getString(R.string.reports_no_data))
            setNoDataTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
            setUsePercentValues(true)
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 55f
            transparentCircleRadius = 60f
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.TRANSPARENT)
            legend.isEnabled = false
            setEntryLabelColor(Color.TRANSPARENT)
            setBackgroundColor(Color.TRANSPARENT)
            setExtraOffsets(5f, 5f, 5f, 5f)
        }

        // Bar Chart
        binding.barChart.apply {
            setNoDataText(getString(R.string.reports_no_data))
            setNoDataTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
            description.isEnabled = false
            legend.isEnabled = true
            legend.textColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.on_surface_variant)
            legend.textSize = 10f
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            setBackgroundColor(Color.TRANSPARENT)
            axisRight.isEnabled = false
            axisLeft.apply {
                isEnabled = true
                setDrawAxisLine(false)
                setDrawGridLines(true)
                gridColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.outline_variant)
                textColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.on_surface_variant)
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        CurrencyFormatter.formatShort(value.toDouble(), currentCurrency)
                }
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.on_surface_variant)
                textSize = 10f
                granularity = 1f
            }
            setExtraOffsets(0f, 8f, 0f, 8f)
        }
    }

    private fun currentFilterLabel(): String = when (viewModel.filter.value ?: TimeFilter.MONTH) {
        TimeFilter.DAY -> getString(R.string.filter_day)
        TimeFilter.WEEK -> getString(R.string.filter_week)
        TimeFilter.MONTH -> getString(R.string.filter_month)
        TimeFilter.YEAR -> getString(R.string.filter_year)
    }

    private fun setupFilters() {
        binding.chipDay.setOnClickListener { viewModel.setFilter(TimeFilter.DAY) }
        binding.chipWeek.setOnClickListener { viewModel.setFilter(TimeFilter.WEEK) }
        binding.chipMonth.setOnClickListener { viewModel.setFilter(TimeFilter.MONTH) }
        binding.chipYear.setOnClickListener { viewModel.setFilter(TimeFilter.YEAR) }
    }

    private fun setupBreakdownList() {
        breakdownAdapter = CategoryBreakdownAdapter()
        binding.rvCategoryBreakdown.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategoryBreakdown.adapter = breakdownAdapter
    }

    private fun observeData() {
        viewModel.currency.observe(viewLifecycleOwner) { currency ->
            currentCurrency = currency
            breakdownAdapter.currency = currency
            renderNetCashFlow()
            viewModel.totalIncome.value?.let { income ->
                binding.tvTotalIncome.text = "+${CurrencyFormatter.format(income, currency)}"
            }
            viewModel.totalExpense.value?.let { expense ->
                binding.tvTotalExpense.text = "-${CurrencyFormatter.format(expense, currency)}"
            }
            viewModel.categorySpending.value?.let { items ->
                updatePieChart(items)
            }
        }

        viewModel.totalIncome.observe(viewLifecycleOwner) { income ->
            binding.tvTotalIncome.text = "+${CurrencyFormatter.format(income ?: 0.0, currentCurrency)}"
            renderNetCashFlow()
        }

        viewModel.totalExpense.observe(viewLifecycleOwner) { expense ->
            binding.tvTotalExpense.text = "-${CurrencyFormatter.format(expense ?: 0.0, currentCurrency)}"
            renderNetCashFlow()
        }

        viewModel.categorySpending.observe(viewLifecycleOwner) { items ->
            updatePieChart(items)
            breakdownAdapter.submitList(items)
        }

        viewModel.monthlyComparison.observe(viewLifecycleOwner) { data ->
            updateBarChart(data)
        }
    }

    private fun renderNetCashFlow() {
        if (_binding == null) return
        val net = (viewModel.totalIncome.value ?: 0.0) - (viewModel.totalExpense.value ?: 0.0)
        binding.tvNetCashFlow.text = if (net > 0.0) {
            "+${CurrencyFormatter.format(net, currentCurrency)}"
        } else {
            CurrencyFormatter.format(net, currentCurrency)
        }
        val color = if (net >= 0.0) R.color.finance_income else R.color.error
        binding.tvNetCashFlow.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), color))
    }

    private fun updatePieChart(items: List<CategorySpending>) {
        if (items.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.centerText = getString(R.string.reports_no_data)
            binding.pieChart.setCenterTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
            binding.pieChart.setCenterTextSize(14f)
            binding.pieChart.invalidate()
            return
        }

        val entries = items.map { PieEntry(it.percentage, it.category) }
        val colors = items.map { item ->
            val (_, _, colorHex) = TransactionAdapter.getCategoryInfo(requireContext(), item.category)
            try { Color.parseColor(colorHex) } catch (_: Exception) { Color.GRAY }
        }

        val dataSet = PieDataSet(entries, "").apply {
            setColors(colors)
            setDrawValues(false)
            valueFormatter = PercentFormatter(binding.pieChart)
            sliceSpace = 2f
        }

        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.centerText = CurrencyFormatter.formatShort(items.sumOf { it.amount }, currentCurrency)
            binding.pieChart.setCenterTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.on_surface))
        binding.pieChart.setCenterTextSize(16f)
        binding.pieChart.animateY(800)
        binding.pieChart.invalidate()
    }

    private fun updateBarChart(data: List<MonthlyComparison>) {
        if (data.isEmpty()) {
            binding.barChart.clear()
            binding.barChart.invalidate()
            return
        }

        val incomeEntries = data.mapIndexed { i, it -> BarEntry(i.toFloat(), it.income.toFloat()) }
        val expenseEntries = data.mapIndexed { i, it -> BarEntry(i.toFloat(), it.expense.toFloat()) }
        val labels = data.map { it.label }

        val incomeDataSet = BarDataSet(incomeEntries, "Thu nhập").apply {
            color = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.finance_income)
            setDrawValues(false)
        }
        val expenseDataSet = BarDataSet(expenseEntries, "Chi tiêu").apply {
            color = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.error)
            setDrawValues(false)
        }

        val barData = BarData(incomeDataSet, expenseDataSet).apply {
            barWidth = 0.35f
        }

        binding.barChart.apply {
            this.data = barData
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = labels.size
            xAxis.setCenterAxisLabels(true)
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = data.size.toFloat()
            groupBars(0f, 0.2f, 0.05f)
            animateY(800)
            invalidate()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadData()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
