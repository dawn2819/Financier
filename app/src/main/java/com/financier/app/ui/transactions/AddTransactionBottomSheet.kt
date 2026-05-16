package com.financier.app.ui.transactions

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.financier.app.R
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.SessionManager
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.TransactionEntity
import com.financier.app.databinding.BottomsheetAddTransactionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddTransactionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetAddTransactionBinding? = null
    private val binding get() = _binding!!

    private var selectedType = "EXPENSE" // or "INCOME"
    private var selectedCategory = "others"
    private var selectedDateMs = System.currentTimeMillis()
    private var currentAmountStr = "0"
    private var selectedAccountId = -1L
    
    /** If editing an existing transaction, set this before showing */
    var editTransaction: TransactionEntity? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetAddTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToggle()
        setupNumpad()
        setupCategoryGrid()
        setupDatePicker()
        setupSaveButton()
        loadDefaultAccount()
        updateDateDisplay()
        
        // If editing, pre-fill fields
        editTransaction?.let { tx ->
            currentAmountStr = if (tx.amount == tx.amount.toLong().toDouble()) {
                tx.amount.toLong().toString()
            } else {
                tx.amount.toString()
            }
            updateAmountDisplay()
            setType(tx.type)
            selectedCategory = tx.category
            selectedDateMs = tx.dateMs
            selectedAccountId = tx.accountId
            binding.etNote.setText(tx.note)
            updateDateDisplay()
            binding.btnSave.text = "Cập nhật"
        }
    }

    private fun loadDefaultAccount() {
        if (editTransaction != null) return // Skip for edit mode
        val userId = SessionManager.getUserId(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val settings = db.settingsDao().getSettingsByUser(userId)
            selectedAccountId = settings?.selectedAccountId ?: -1L
            if (selectedAccountId == -1L) {
                val accounts = db.financialAccountDao().getAccountsByUserSync(userId)
                selectedAccountId = accounts.firstOrNull()?.id ?: -1L
            }
        }
    }

    private fun setupToggle() {
        binding.btnExpense.setOnClickListener { setType("EXPENSE") }
        binding.btnIncome.setOnClickListener { setType("INCOME") }
        setType("EXPENSE")
    }

    private fun setType(type: String) {
        selectedType = type
        val isExpense = type == "EXPENSE"
        binding.btnExpense.isSelected = isExpense
        binding.btnIncome.isSelected = !isExpense

        // Update toggle button styles
        if (isExpense) {
            binding.btnExpense.setBackgroundColor(resources.getColor(R.color.primary, null))
            binding.btnExpense.setTextColor(resources.getColor(R.color.on_primary, null))
            binding.btnIncome.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnIncome.setTextColor(resources.getColor(R.color.on_surface_variant, null))
        } else {
            binding.btnIncome.setBackgroundColor(resources.getColor(R.color.primary, null))
            binding.btnIncome.setTextColor(resources.getColor(R.color.on_primary, null))
            binding.btnExpense.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnExpense.setTextColor(resources.getColor(R.color.on_surface_variant, null))
        }
    }

    private fun setupNumpad() {
        val numButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6, binding.btn7,
            binding.btn8, binding.btn9
        )

        numButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener { appendDigit(index.toString()) }
        }
        binding.btnDot.setOnClickListener {
            if (!currentAmountStr.contains(".")) appendDigit(".")
        }
        binding.btnBackspace.setOnClickListener { deleteLastDigit() }
        updateAmountDisplay()
    }

    private fun appendDigit(digit: String) {
        if (currentAmountStr == "0" && digit != ".") {
            currentAmountStr = digit
        } else if (currentAmountStr.length < 15) {
            currentAmountStr += digit
        }
        updateAmountDisplay()
    }

    private fun deleteLastDigit() {
        currentAmountStr = if (currentAmountStr.length <= 1) "0"
        else currentAmountStr.dropLast(1)
        updateAmountDisplay()
    }

    private fun updateAmountDisplay() {
        // Format with thousands separator for readability
        val amount = currentAmountStr.toDoubleOrNull() ?: 0.0
        if (currentAmountStr.contains(".") || amount == 0.0) {
            binding.tvAmount.text = currentAmountStr
        } else {
            binding.tvAmount.text = CurrencyFormatter.formatRaw(amount.toLong())
        }
    }

    private fun setupCategoryGrid() {
        val categories = listOf(
            binding.btnCatFood, binding.btnCatTransport, binding.btnCatShopping,
            binding.btnCatHealth, binding.btnCatEntertain, binding.btnCatHousing,
            binding.btnCatEdu, binding.btnCatGym, binding.btnCatBills,
            binding.btnCatTravel, binding.btnCatPets, binding.btnCatOthers
        )
        val categoryKeys = listOf(
            "food", "transport", "shopping", "health", "entertainment",
            "housing", "education", "gym", "bills", "travel", "pets", "others"
        )

        categories.forEachIndexed { index, btn ->
            btn.setOnClickListener {
                selectedCategory = categoryKeys[index]
                highlightSelectedCategory(btn, categories)
            }
        }

        // Default select food or the edit transaction's category
        val defaultIdx = categoryKeys.indexOf(editTransaction?.category ?: "food").coerceAtLeast(0)
        highlightSelectedCategory(categories[defaultIdx], categories)
        selectedCategory = categoryKeys[defaultIdx]
    }

    private fun highlightSelectedCategory(
        selected: com.google.android.material.button.MaterialButton,
        all: List<com.google.android.material.button.MaterialButton>
    ) {
        all.forEach { btn ->
            if (btn == selected) {
                btn.backgroundTintList = resources.getColorStateList(R.color.primary_container, null)
            } else {
                btn.backgroundTintList = resources.getColorStateList(R.color.surface_container_highest, null)
            }
        }
    }

    private fun setupDatePicker() {
        updateDateDisplay()
        binding.btnDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMs }
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val picked = Calendar.getInstance()
                    picked.set(year, month, day)
                    selectedDateMs = picked.timeInMillis
                    updateDateDisplay()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun updateDateDisplay() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        binding.tvDate.text = sdf.format(Date(selectedDateMs))
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val amount = currentAmountStr.toDoubleOrNull() ?: 0.0
            if (amount <= 0) {
                Toast.makeText(context, "Vui lòng nhập số tiền", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedAccountId == -1L) {
                Toast.makeText(context, "Chưa có tài khoản!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = SessionManager.getUserId(requireContext())
            val note = binding.etNote.text.toString()

            val transaction = TransactionEntity(
                id = editTransaction?.id ?: 0,
                userId = userId,
                accountId = selectedAccountId,
                amount = amount,
                type = selectedType,
                category = if (selectedType == "INCOME") "income" else selectedCategory,
                note = note,
                dateMs = selectedDateMs
            )

            lifecycleScope.launch(Dispatchers.IO) {
                val dao = AppDatabase.getDatabase(requireContext()).transactionDao()
                if (editTransaction != null) {
                    dao.updateTransaction(transaction)
                } else {
                    dao.insertTransaction(transaction)
                }

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val msg = if (editTransaction != null) "Đã cập nhật!" else "Đã lưu giao dịch!"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
