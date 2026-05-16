package com.financier.app.ui.budget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.financier.app.R
import com.financier.app.databinding.DialogAddBudgetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddBudgetDialog(
    private val onSave: (category: String, limit: Double) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogAddBudgetBinding? = null
    private val binding get() = _binding!!

    private val categoryKeys = listOf(
        "food", "transport", "shopping", "health", "entertainment",
        "housing", "education", "gym", "bills", "travel", "pets", "others"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup category dropdown
        val categoryNames = categoryKeys.map { key ->
            getCategoryDisplayName(key)
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryNames)
        binding.dropdownCategory.setAdapter(adapter)

        binding.btnSaveBudget.setOnClickListener {
            val selectedIndex = categoryNames.indexOf(binding.dropdownCategory.text.toString())
            val amount = binding.etAmount.text.toString().toDoubleOrNull()

            if (selectedIndex < 0) {
                binding.tilCategory.error = "Chọn danh mục"
                return@setOnClickListener
            }
            if (amount == null || amount <= 0) {
                binding.tilAmount.error = "Nhập số tiền hợp lệ"
                return@setOnClickListener
            }

            onSave(categoryKeys[selectedIndex], amount)
            dismiss()
        }
    }

    private fun getCategoryDisplayName(key: String): String {
        val ctx = requireContext()
        return when (key) {
            "food" -> ctx.getString(R.string.cat_food)
            "transport" -> ctx.getString(R.string.cat_transport)
            "shopping" -> ctx.getString(R.string.cat_shopping)
            "health" -> ctx.getString(R.string.cat_health)
            "entertainment" -> ctx.getString(R.string.cat_entertainment)
            "housing" -> ctx.getString(R.string.cat_housing)
            "education" -> ctx.getString(R.string.cat_education)
            "gym" -> ctx.getString(R.string.cat_gym)
            "bills" -> ctx.getString(R.string.cat_bills)
            "travel" -> ctx.getString(R.string.cat_travel)
            "pets" -> ctx.getString(R.string.cat_pets)
            else -> ctx.getString(R.string.cat_others)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
