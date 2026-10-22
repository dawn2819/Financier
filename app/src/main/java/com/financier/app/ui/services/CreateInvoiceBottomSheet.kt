package com.financier.app.ui.services

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.financier.app.common.AppAudio
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.SessionManager
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.InvoiceEntity
import com.financier.app.databinding.BottomsheetCreateInvoiceBinding
import com.financier.app.widget.FinanceWidgetProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class CreateInvoiceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetCreateInvoiceBinding? = null
    private val binding get() = _binding!!

    private val categories = listOf(
        Pair("Tiền điện EVN", "bills"),
        Pair("Tiền nước sinh hoạt", "bills"),
        Pair("Tiền nhà / Chung cư / Phòng trọ", "housing"),
        Pair("Internet & Truyền hình", "bills"),
        Pair("Học phí & Khóa học", "education"),
        Pair("Dịch vụ doanh nghiệp khác", "services")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetCreateInvoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!SessionManager.isDawnAdmin(requireContext())) {
            Toast.makeText(requireContext(), "Chỉ tài khoản admin @dawn mới có quyền tạo mã hóa đơn!", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }
        setupViews()
    }

    private fun setupViews() {
        val currentUsername = SessionManager.getUsername(requireContext()).ifEmpty { "dawn" }
        binding.tvAdminBadge.text = "DOANH NGHIỆP: @${currentUsername.uppercase()}"

        // Setup Category Spinner
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categories.map { it.first }
        )
        binding.spinnerCategory.adapter = adapter

        // Random code button
        binding.btnRandomCode.setOnClickListener {
            val prefix = when (binding.spinnerCategory.selectedItemPosition) {
                0 -> "EVN"
                1 -> "WAT"
                2 -> "NHA"
                3 -> "NET"
                4 -> "EDU"
                else -> "HD"
            }
            val num = Random.nextInt(1000, 9999)
            binding.etBillCode.setText("$prefix-$num")
        }

        binding.btnCreateInvoice.setOnClickListener {
            validateAndCreateInvoice()
        }
    }

    private fun validateAndCreateInvoice() {
        val code = binding.etBillCode.text?.toString()?.trim()?.uppercase().orEmpty()
        if (code.isBlank()) {
            Toast.makeText(requireContext(), "Vui lòng nhập mã hóa đơn", Toast.LENGTH_SHORT).show()
            binding.etBillCode.requestFocus()
            return
        }

        val title = binding.etBillTitle.text?.toString()?.trim().orEmpty()
        if (title.isBlank()) {
            Toast.makeText(requireContext(), "Vui lòng nhập tên hóa đơn", Toast.LENGTH_SHORT).show()
            binding.etBillTitle.requestFocus()
            return
        }

        val amountStr = binding.etAmount.text?.toString()?.trim().orEmpty()
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        if (amount <= 0) {
            Toast.makeText(requireContext(), "Số tiền phải lớn hơn 0", Toast.LENGTH_SHORT).show()
            binding.etAmount.requestFocus()
            return
        }

        val targetUser = binding.etTargetUser.text?.toString()?.trim()?.ifEmpty { "ALL" } ?: "ALL"
        val selectedCat = categories[binding.spinnerCategory.selectedItemPosition].second

        val creatorId = SessionManager.getUserId(requireContext())
        val creatorUsername = SessionManager.getUsername(requireContext()).ifEmpty { "dawn" }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val existing = db.invoiceDao().getInvoiceByCode(code)
            if (existing != null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Mã hóa đơn '$code' đã tồn tại. Vui lòng chọn mã khác!", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val invoice = InvoiceEntity(
                creatorId = creatorId,
                creatorUsername = creatorUsername,
                billCode = code,
                targetUsername = targetUser,
                title = title,
                category = selectedCat,
                amount = amount,
                status = "UNPAID"
            )
            db.invoiceDao().insertInvoice(invoice)

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                AppAudio.playClick(requireContext())
                FinanceWidgetProvider.updateAll(requireContext())

                AlertDialog.Builder(requireContext())
                    .setTitle("✅ Phát hành hóa đơn thành công!")
                    .setMessage(
                        "Mã hóa đơn: $code\n" +
                        "Dịch vụ: $title\n" +
                        "Số tiền: ${CurrencyFormatter.format(amount, "VND")}\n" +
                        "Khách hàng: @$targetUser\n\n" +
                        "Người dùng có thể nhập mã '$code' trong mục Hóa đơn để thanh toán."
                    )
                    .setPositiveButton("Sao chép mã") { dialog, _ ->
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("BillCode", code)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "Đã sao chép mã: $code", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        dismiss()
                    }
                    .setNegativeButton("Đóng") { dialog, _ ->
                        dialog.dismiss()
                        dismiss()
                    }
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CreateInvoiceBottomSheet"
        fun newInstance() = CreateInvoiceBottomSheet()
    }
}
