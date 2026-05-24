package com.financier.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import com.financier.app.databinding.DialogAddAccountBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AddAccountDialog(
    private val onSave: (
        name: String,
        type: String,
        currency: String,
        cardNumber: String?,
        cardExpiry: String?,
        cardPin: String?,
        isLinked: Boolean,
        walletType: String?
    ) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogAddAccountBinding? = null
    private val binding get() = _binding!!

    private val typeKeys = listOf("CASH", "BANK", "CREDIT_CARD", "E_WALLET")
    private val typeNames = listOf("💵 Tiền mặt", "🏦 Ngân hàng", "💳 Thẻ tín dụng", "📱 Ví điện tử")
    private val currencies = listOf("VND", "USD")
    private val bankNames = listOf("Vietcombank", "Techcombank", "BIDV", "Vietinbank", "Agribank", "MB Bank")
    private val walletNames = listOf("MoMo", "ZaloPay", "ShopeePay", "Viettel Money")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Type dropdown
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, typeNames)
        binding.dropdownType.setAdapter(typeAdapter)

        // Currency dropdowns
        val currencyAdapter1 = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, currencies)
        val currencyAdapter2 = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, currencies)
        val currencyAdapter3 = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, currencies)
        binding.dropdownCashCurrency.setAdapter(currencyAdapter1)
        binding.dropdownCashCurrency.setText("VND", false)
        binding.dropdownCardCurrency.setAdapter(currencyAdapter2)
        binding.dropdownCardCurrency.setText("VND", false)
        binding.dropdownWalletCurrency.setAdapter(currencyAdapter3)
        binding.dropdownWalletCurrency.setText("VND", false)

        // Banks dropdown
        val bankAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, bankNames)
        binding.dropdownBankSelect.setAdapter(bankAdapter)
        binding.dropdownBankSelect.setText("Vietcombank", false)

        // Wallets dropdown
        val walletAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, walletNames)
        binding.dropdownWalletSelect.setAdapter(walletAdapter)
        binding.dropdownWalletSelect.setText("MoMo", false)

        // Dynamic visibility logic
        binding.dropdownType.setOnItemClickListener { _, _, position, _ ->
            val key = typeKeys[position]
            binding.tilType.error = null
            showFieldsForType(key)
        }

        binding.btnSaveAccount.setOnClickListener {
            val selectedTypeString = binding.dropdownType.text.toString()
            val selectedTypeIndex = typeNames.indexOf(selectedTypeString)
            if (selectedTypeIndex < 0) {
                binding.tilType.error = "Chọn loại tài khoản"
                return@setOnClickListener
            }
            val key = typeKeys[selectedTypeIndex]

            when (key) {
                "CASH" -> {
                    val name = binding.etCashName.text.toString().trim()
                    val currency = binding.dropdownCashCurrency.text.toString()
                    if (name.isEmpty()) {
                        binding.tilCashName.error = "Nhập tên tài khoản"
                        return@setOnClickListener
                    }
                    onSave(name, key, currency, null, null, null, false, null)
                    dismiss()
                }
                "BANK" -> {
                    val selectedBank = binding.dropdownBankSelect.text.toString()
                    val shouldLink = binding.cbLinkBank.isChecked
                    if (shouldLink) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Liên kết tài khoản ngân hàng")
                            .setMessage("Bạn có đồng ý cho phép ứng dụng liên kết và đồng bộ giao dịch từ tài khoản ngân hàng $selectedBank không? (Chỉ demo, cam kết bảo mật)")
                            .setPositiveButton("Đồng ý") { _, _ ->
                                onSave("$selectedBank Bank", key, "VND", null, null, null, true, null)
                                dismiss()
                            }
                            .setNegativeButton("Hủy") { _, _ ->
                                // Don't dismiss or save, let user re-decide
                            }
                            .show()
                    } else {
                        // Ask to link since it is bank account
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Hỏi liên kết ngân hàng")
                            .setMessage("Tài khoản ngân hàng $selectedBank sẽ được thêm. Bạn có muốn liên kết tài khoản để đồng bộ tự động không?")
                            .setPositiveButton("Có, liên kết") { _, _ ->
                                onSave("$selectedBank Bank", key, "VND", null, null, null, true, null)
                                dismiss()
                            }
                            .setNegativeButton("Không, chỉ thêm thường") { _, _ ->
                                onSave("$selectedBank Bank", key, "VND", null, null, null, false, null)
                                dismiss()
                            }
                            .show()
                    }
                }
                "CREDIT_CARD" -> {
                    val name = binding.etCardName.text.toString().trim()
                    val number = binding.etCardNumber.text.toString().trim()
                    val expiry = binding.etCardExpiry.text.toString().trim()
                    val pin = binding.etCardPin.text.toString().trim()
                    val currency = binding.dropdownCardCurrency.text.toString()

                    if (name.isEmpty()) {
                        binding.tilCardName.error = "Nhập tên thẻ"
                        return@setOnClickListener
                    }
                    if (number.isEmpty()) {
                        binding.tilCardNumber.error = "Nhập số thẻ"
                        return@setOnClickListener
                    }
                    if (expiry.isEmpty()) {
                        binding.tilCardExpiry.error = "Nhập ngày hết hạn"
                        return@setOnClickListener
                    }
                    if (pin.isEmpty()) {
                        binding.tilCardPin.error = "Nhập mã PIN"
                        return@setOnClickListener
                    }

                    onSave(name, key, currency, number, expiry, pin, false, null)
                    dismiss()
                }
                "E_WALLET" -> {
                    val selectedWallet = binding.dropdownWalletSelect.text.toString()
                    val currency = binding.dropdownWalletCurrency.text.toString()
                    onSave("$selectedWallet Wallet", key, currency, null, null, null, false, selectedWallet)
                    dismiss()
                }
            }
        }
    }

    private fun showFieldsForType(key: String) {
        binding.layoutCashFields.visibility = if (key == "CASH") View.VISIBLE else View.GONE
        binding.layoutBankFields.visibility = if (key == "BANK") View.VISIBLE else View.GONE
        binding.layoutCreditFields.visibility = if (key == "CREDIT_CARD") View.VISIBLE else View.GONE
        binding.layoutWalletFields.visibility = if (key == "E_WALLET") View.VISIBLE else View.GONE
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
