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
import com.financier.app.databinding.FragmentTransactionsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TransactionsFragment : Fragment() {
    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: TransactionsViewModel
    private lateinit var adapter: TransactionAdapter

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

        viewModel.currency.observe(viewLifecycleOwner) { targetCurrency ->
            adapter.updateDisplayCurrency(targetCurrency)
        }

        viewModel.accountCurrencyMap.observe(viewLifecycleOwner) { map ->
            adapter.updateAccountCurrencyMap(map)
        }

        viewModel.transactions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fab.setOnClickListener {
            AddTransactionBottomSheet().show(parentFragmentManager, "add_tx")
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
