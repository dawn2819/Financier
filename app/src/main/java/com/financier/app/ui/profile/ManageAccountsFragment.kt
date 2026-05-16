package com.financier.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.financier.app.common.SessionManager
import com.financier.app.databinding.FragmentManageAccountsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ManageAccountsFragment : Fragment() {
    private var _binding: FragmentManageAccountsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ManageAccountsViewModel
    private lateinit var adapter: AccountAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentManageAccountsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = SessionManager.getUserId(requireContext())
        viewModel = ViewModelProvider(this, ManageAccountsViewModel.Factory(requireContext(), userId))[ManageAccountsViewModel::class.java]

        setupRecyclerView()

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.fabAddAccount.setOnClickListener {
            AddAccountDialog { name, type, currency ->
                val colors = listOf("#78DC77", "#9ECAFF", "#FFB4AB", "#FFD700", "#E040FB", "#00BCD4")
                val color = colors.random()
                viewModel.addAccount(name, type, currency, color)
            }.show(parentFragmentManager, "add_account")
        }

        viewModel.accounts.observe(viewLifecycleOwner) { accounts ->
            adapter.submitList(accounts)
            binding.tvEmpty.visibility = if (accounts.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupRecyclerView() {
        adapter = AccountAdapter(onDeleteClick = { account ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xóa tài khoản")
                .setMessage("Xóa \"${account.name}\"? Tất cả giao dịch liên quan sẽ bị mất.")
                .setPositiveButton("Xóa") { _, _ -> viewModel.deleteAccount(account) }
                .setNegativeButton("Hủy", null)
                .show()
        })
        binding.rvAccounts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAccounts.adapter = adapter
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
