package com.financier.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.financier.app.databinding.DialogAddAccountBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddAccountDialog(
    private val onSave: (name: String, type: String, currency: String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogAddAccountBinding? = null
    private val binding get() = _binding!!

    private val typeKeys = listOf("CASH", "BANK", "CREDIT_CARD", "E_WALLET")
    private val typeNames = listOf("💵 Tiền mặt", "🏦 Ngân hàng", "💳 Thẻ tín dụng", "📱 Ví điện tử")
    private val currencies = listOf("VND", "USD")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Type dropdown
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, typeNames)
        binding.dropdownType.setAdapter(typeAdapter)

        // Currency dropdown
        val currencyAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, currencies)
        binding.dropdownCurrency.setAdapter(currencyAdapter)
        binding.dropdownCurrency.setText("VND", false) // Default

        binding.btnSaveAccount.setOnClickListener {
            val name = binding.etAccountName.text.toString().trim()
            val selectedTypeIndex = typeNames.indexOf(binding.dropdownType.text.toString())
            val currency = binding.dropdownCurrency.text.toString()

            if (name.isEmpty()) {
                binding.etAccountName.error = "Nhập tên tài khoản"
                return@setOnClickListener
            }
            if (selectedTypeIndex < 0) {
                binding.tilType.error = "Chọn loại tài khoản"
                return@setOnClickListener
            }

            onSave(name, typeKeys[selectedTypeIndex], currency)
            dismiss()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
