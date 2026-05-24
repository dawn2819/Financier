package com.financier.app.ui.budget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.SessionManager
import com.financier.app.databinding.FragmentBudgetBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class BudgetFragment : Fragment() {
    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: BudgetViewModel
    private lateinit var adapter: BudgetAdapter
    private var currentCurrency = "VND"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = SessionManager.getUserId(requireContext())
        viewModel = ViewModelProvider(this, BudgetViewModel.Factory(requireContext(), userId))[BudgetViewModel::class.java]

        setupRecyclerView()
        setupClickListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = BudgetAdapter(onDeleteClick = { item ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xóa ngân sách")
                .setMessage("Bạn có muốn xóa ngân sách \"${item.budget.category}\"?")
                .setPositiveButton("Xóa") { _, _ -> viewModel.deleteBudget(item.budget) }
                .setNegativeButton("Hủy", null)
                .show()
        })
        binding.rvBudgets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBudgets.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnPrevMonth.setOnClickListener { viewModel.previousMonth() }
        binding.btnNextMonth.setOnClickListener { viewModel.nextMonth() }

        binding.btnAddBudget.setOnClickListener {
            AddBudgetDialog { category, limit ->
                viewModel.addBudget(category, limit)
            }.show(parentFragmentManager, "add_budget")
        }
    }

    private fun observeData() {
        viewModel.monthLabel.observe(viewLifecycleOwner) { label ->
            binding.tvMonthLabel.text = label
        }

        viewModel.currency.observe(viewLifecycleOwner) { currency ->
            currentCurrency = currency
            adapter.currency = currency
            viewModel.totalBudget.value?.let { total ->
                binding.tvTotalBudget.text = CurrencyFormatter.format(total, currency)
            }
            viewModel.totalSpent.value?.let { spent ->
                binding.tvSpent.text = CurrencyFormatter.format(spent, currency)
                val total = viewModel.totalBudget.value ?: 0.0
                val remaining = total - spent
                binding.tvRemaining.text = CurrencyFormatter.format(remaining.coerceAtLeast(0.0), currency)
            }
        }

        viewModel.totalBudget.observe(viewLifecycleOwner) { total ->
            binding.tvTotalBudget.text = CurrencyFormatter.format(total ?: 0.0, currentCurrency)
        }

        viewModel.totalSpent.observe(viewLifecycleOwner) { spent ->
            binding.tvSpent.text = CurrencyFormatter.format(spent ?: 0.0, currentCurrency)
            val total = viewModel.totalBudget.value ?: 0.0
            val remaining = total - (spent ?: 0.0)
            binding.tvRemaining.text = CurrencyFormatter.format(remaining.coerceAtLeast(0.0), currentCurrency)
        }

        viewModel.overallPercentage.observe(viewLifecycleOwner) { pct ->
            binding.progressOverall.progress = pct ?: 0
        }

        viewModel.budgetItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.rvBudgets.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadBudgets()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
