package com.financier.app.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.financier.app.common.SessionManager
import com.financier.app.data.local.entity.FinancialAccountEntity
import com.financier.app.databinding.FragmentTransactionsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TransactionsFragment : Fragment() {
    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: TransactionsViewModel
    private lateinit var adapter: TransactionAdapter
    private var selectedPeriod = "ALL"
    private var selectedAccountId = -1L
    private var availableAccounts: List<FinancialAccountEntity> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userId = SessionManager.getUserId(requireContext())
        viewModel = ViewModelProvider(this, TransactionsViewModel.Factory(requireContext(), userId))[TransactionsViewModel::class.java]

        val filterType = arguments?.getString("filterType") ?: "ALL"
        viewModel.setFilterType(filterType)

        adapter = TransactionAdapter("VND",
            onItemClick = { tx ->
                TransactionDetailsBottomSheet.newInstance(tx.id).show(parentFragmentManager, "tx_detail")
            },
            onEditClick = { tx ->
                // Open bottom sheet in edit mode
                val sheet = AddTransactionBottomSheet()
                sheet.editTransaction = tx
                sheet.show(parentFragmentManager, "edit_tx")
            },
            onDeleteClick = { tx ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Xóa giao dịch")
                    .setMessage("Bạn có chắc muốn xóa giao dịch này?")
                    .setPositiveButton("Xóa") { _, _ -> viewModel.deleteTransaction(tx) }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
        )
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter

        binding.etSearch.addTextChangedListener { text ->
            viewModel.search(text.toString())
        }
        setupAdvancedFilters()

        viewModel.currency.observe(viewLifecycleOwner) { targetCurrency ->
            adapter.updateDisplayCurrency(targetCurrency)
        }

        viewModel.accountCurrencyMap.observe(viewLifecycleOwner) { map ->
            adapter.updateAccountCurrencyMap(map)
        }

        viewModel.accounts.observe(viewLifecycleOwner) { accounts ->
            availableAccounts = accounts.orEmpty()
            if (selectedAccountId != -1L && availableAccounts.none { it.id == selectedAccountId }) {
                selectedAccountId = -1L
                viewModel.setFilterAccount(-1L)
            }
            updateAccountFilterLabel()
        }

        viewModel.transactions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fab.setOnClickListener {
            AddTransactionBottomSheet().show(parentFragmentManager, "add_tx")
        }
    }

    private fun setupAdvancedFilters() {
        binding.btnFilterPeriod.setOnClickListener {
            val periods = listOf("ALL", "MONTH", "30_DAYS")
            val labels = arrayOf(
                getString(com.financier.app.R.string.filter_all_time),
                getString(com.financier.app.R.string.filter_this_month),
                getString(com.financier.app.R.string.filter_last_30_days)
            )
            val checked = periods.indexOf(selectedPeriod).coerceAtLeast(0)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(com.financier.app.R.string.filter_period)
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    selectedPeriod = periods[which]
                    binding.btnFilterPeriod.text = labels[which]
                    viewModel.setFilterPeriod(selectedPeriod)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        binding.btnFilterAccount.setOnClickListener { showAccountFilterDialog() }
    }

    private fun showAccountFilterDialog() {
        val allAccountsLabel = getString(com.financier.app.R.string.filter_all_accounts)
        val labels = arrayOf(allAccountsLabel, *availableAccounts.map { it.name }.toTypedArray())
        val selectedIndex = if (selectedAccountId == -1L) 0
            else availableAccounts.indexOfFirst { it.id == selectedAccountId }.let { if (it < 0) 0 else it + 1 }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(com.financier.app.R.string.filter_select_account)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                selectedAccountId = if (which == 0) -1L else availableAccounts[which - 1].id
                viewModel.setFilterAccount(selectedAccountId)
                updateAccountFilterLabel()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateAccountFilterLabel() {
        binding.btnFilterAccount.text = if (selectedAccountId == -1L) {
            getString(com.financier.app.R.string.filter_all_accounts)
        } else {
            availableAccounts.firstOrNull { it.id == selectedAccountId }?.name
                ?: getString(com.financier.app.R.string.filter_all_accounts)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
