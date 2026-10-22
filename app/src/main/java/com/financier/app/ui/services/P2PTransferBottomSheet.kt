package com.financier.app.ui.services

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.financier.app.R
import com.financier.app.common.AppAudio
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.SecurityUtils
import com.financier.app.common.SessionManager
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.FinancialAccountEntity
import com.financier.app.data.local.entity.TransactionEntity
import com.financier.app.data.local.entity.UserEntity
import com.financier.app.databinding.BottomsheetP2pTransferBinding
import com.financier.app.widget.FinanceWidgetProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class P2PTransferBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetP2pTransferBinding? = null
    private val binding get() = _binding!!

    private var senderAccounts = listOf<FinancialAccountEntity>()
    private val accountBalances = mutableMapOf<Long, Double>()
    private var selectedAccount: FinancialAccountEntity? = null
    private var validatedRecipient: UserEntity? = null
    private var currentUser: UserEntity? = null
    private var recipientSearchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetP2pTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
        setupListeners()
        applyInitialArguments()
    }

    private fun applyInitialArguments() {
        arguments?.let { args ->
            val recipient = args.getString(ARG_RECIPIENT)
            val amount = args.getDouble(ARG_AMOUNT, 0.0)
            val note = args.getString(ARG_NOTE)

            if (!recipient.isNullOrBlank()) {
                binding.etRecipient.setText(recipient)
                debounceRecipientCheck(recipient)
            }
            if (amount > 0.0) {
                binding.etAmount.setText(amount.toLong().toString())
            }
            if (!note.isNullOrBlank()) {
                binding.etNote.setText(note)
            }
        }
    }

    private fun loadData() {
        val userId = SessionManager.getUserId(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            currentUser = db.userDao().getUserById(userId)
            val accounts = db.financialAccountDao().getAccountsByUserSync(userId)
            val allTx = db.transactionDao().getAllTransactionsSync(userId)

            val balances = mutableMapOf<Long, Double>()
            accounts.forEach { acc ->
                val txs = allTx.filter { it.accountId == acc.id }
                val net = txs.sumOf { tx -> if (tx.type == "INCOME") tx.amount else -tx.amount }
                balances[acc.id] = acc.initialBalance + net
            }

            withContext(Dispatchers.Main) {
                senderAccounts = accounts
                accountBalances.clear()
                accountBalances.putAll(balances)
                setupAccountSpinner()
            }
        }
    }

    private fun setupAccountSpinner() {
        if (!isAdded) return
        if (senderAccounts.isEmpty()) {
            binding.tvSourceBalance.text = "Bạn chưa có tài khoản nào để chuyển"
            binding.btnConfirmTransfer.isEnabled = false
            return
        }

        val items = senderAccounts.map { acc ->
            val bal = accountBalances[acc.id] ?: 0.0
            "${acc.name} (${CurrencyFormatter.format(bal, acc.currency)})"
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items)
        binding.spinnerSourceAccount.adapter = adapter
        binding.spinnerSourceAccount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedAccount = senderAccounts[position]
                val bal = accountBalances[selectedAccount?.id] ?: 0.0
                binding.tvSourceBalance.text = "Số dư khả dụng: ${CurrencyFormatter.format(bal, selectedAccount?.currency ?: "VND")}"
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (senderAccounts.isNotEmpty()) {
            selectedAccount = senderAccounts[0]
            val bal = accountBalances[selectedAccount?.id] ?: 0.0
            binding.tvSourceBalance.text = "Số dư khả dụng: ${CurrencyFormatter.format(bal, selectedAccount?.currency ?: "VND")}"
        }
    }

    private fun setupListeners() {
        // Quick amount chips
        binding.chip50k.setOnClickListener { binding.etAmount.setText("50000") }
        binding.chip100k.setOnClickListener { binding.etAmount.setText("100000") }
        binding.chip200k.setOnClickListener { binding.etAmount.setText("200000") }
        binding.chip500k.setOnClickListener { binding.etAmount.setText("500000") }

        // Recipient text watcher
        binding.etRecipient.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                debounceRecipientCheck(query)
            }
        })

        // Confirm transfer button
        binding.btnConfirmTransfer.setOnClickListener {
            validateAndProceed()
        }
    }

    private fun debounceRecipientCheck(query: String) {
        recipientSearchJob?.cancel()
        val cleanQuery = query.removePrefix("@").trim()
        if (cleanQuery.isBlank()) {
            validatedRecipient = null
            binding.tvRecipientFeedback.text = "Nhập tên tài khoản để kiểm tra"
            binding.tvRecipientFeedback.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
            return
        }

        recipientSearchJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(300) // 300ms debounce
            val db = AppDatabase.getDatabase(requireContext())
            val user = db.userDao().getUserByUsername(cleanQuery)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                val currentUserId = SessionManager.getUserId(requireContext())
                if (user == null) {
                    validatedRecipient = null
                    binding.tvRecipientFeedback.text = "⚠ Không tìm thấy người dùng '@$cleanQuery'"
                    binding.tvRecipientFeedback.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
                } else if (user.id == currentUserId) {
                    validatedRecipient = null
                    binding.tvRecipientFeedback.text = "⚠ Không thể tự chuyển tiền cho chính mình"
                    binding.tvRecipientFeedback.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
                } else {
                    validatedRecipient = user
                    binding.tvRecipientFeedback.text = "✓ Người nhận: ${user.displayName} (@${user.username})"
                    binding.tvRecipientFeedback.setTextColor(ContextCompat.getColor(requireContext(), R.color.tertiary))
                }
            }
        }
    }

    private fun validateAndProceed() {
        val recipient = validatedRecipient
        if (recipient == null) {
            Toast.makeText(requireContext(), getString(R.string.p2p_user_not_found), Toast.LENGTH_SHORT).show()
            binding.etRecipient.requestFocus()
            return
        }

        val account = selectedAccount
        if (account == null) {
            Toast.makeText(requireContext(), "Vui lòng chọn tài khoản nguồn", Toast.LENGTH_SHORT).show()
            return
        }

        val amountStr = binding.etAmount.text.toString().trim()
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        if (amount <= 0) {
            Toast.makeText(requireContext(), "Số tiền chuyển phải lớn hơn 0", Toast.LENGTH_SHORT).show()
            binding.etAmount.requestFocus()
            return
        }

        val available = accountBalances[account.id] ?: 0.0
        if (amount > available) {
            Toast.makeText(requireContext(), getString(R.string.p2p_insufficient_funds), Toast.LENGTH_SHORT).show()
            return
        }

        val note = binding.etNote.text.toString().trim().ifEmpty { "Chuyển tiền nội bộ" }

        authenticateAndExecute(recipient, account, amount, note)
    }

    private fun authenticateAndExecute(
        recipient: UserEntity,
        account: FinancialAccountEntity,
        amount: Double,
        note: String
    ) {
        val biometricManager = BiometricManager.from(requireContext())
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)

        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(requireContext())
            val biometricPrompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        executeDoubleEntryTransfer(recipient, account, amount, note)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                            showPasswordFallbackDialog(recipient, account, amount, note)
                        } else {
                            Toast.makeText(requireContext(), "Xác thực không thành công: $errString", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Xác thực chuyển tiền")
                .setSubtitle("Chuyển ${CurrencyFormatter.format(amount, account.currency)} tới @${recipient.username}")
                .setNegativeButtonText("Dùng mật khẩu")
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else {
            showPasswordFallbackDialog(recipient, account, amount, note)
        }
    }

    private fun showPasswordFallbackDialog(
        recipient: UserEntity,
        account: FinancialAccountEntity,
        amount: Double,
        note: String
    ) {
        val input = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Nhập mật khẩu tài khoản"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Xác nhận mật khẩu")
            .setMessage("Vui lòng nhập mật khẩu để xác nhận giao dịch chuyển ${CurrencyFormatter.format(amount, account.currency)}:")
            .setView(input)
            .setPositiveButton("Xác nhận") { _, _ ->
                val enteredPw = input.text.toString().trim()
                val user = currentUser
                if (user != null && SecurityUtils.verifyPassword(enteredPw, user.passwordHash)) {
                    executeDoubleEntryTransfer(recipient, account, amount, note)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.error_invalid_credentials), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun executeDoubleEntryTransfer(
        recipient: UserEntity,
        account: FinancialAccountEntity,
        amount: Double,
        note: String
    ) {
        val currentUserId = SessionManager.getUserId(requireContext())
        val sender = currentUser

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val timestamp = System.currentTimeMillis()

            // 1. Debit sender's account
            val senderTx = TransactionEntity(
                userId = currentUserId,
                accountId = account.id,
                amount = amount,
                type = "EXPENSE",
                category = "transfer",
                note = "Chuyển tiền tới @${recipient.username}: $note",
                dateMs = timestamp
            )
            db.transactionDao().insertTransaction(senderTx)

            // 2. Credit recipient's account
            val recipientAccounts = db.financialAccountDao().getAccountsByUserSync(recipient.id)
            val recipientAccountId = if (recipientAccounts.isNotEmpty()) {
                recipientAccounts.first().id
            } else {
                db.financialAccountDao().insertAccount(
                    FinancialAccountEntity(
                        userId = recipient.id,
                        name = "Ví chính",
                        accountType = "CASH",
                        initialBalance = 0.0,
                        currency = account.currency
                    )
                )
            }

            val recipientTx = TransactionEntity(
                userId = recipient.id,
                accountId = recipientAccountId,
                amount = amount,
                type = "INCOME",
                category = "transfer",
                note = "Nhận tiền từ @${sender?.username ?: "Financier"}: $note",
                dateMs = timestamp
            )
            db.transactionDao().insertTransaction(recipientTx)

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                AppAudio.playClick(requireContext())
                FinanceWidgetProvider.updateAll(requireContext())

                val txCode = "FN" + (timestamp % 100000000).toString()
                AlertDialog.Builder(requireContext())
                    .setTitle("🎉 Chuyển tiền thành công!")
                    .setMessage(
                        "Số tiền: ${CurrencyFormatter.format(amount, account.currency)}\n" +
                        "Người nhận: ${recipient.displayName} (@${recipient.username})\n" +
                        "Tài khoản nguồn: ${account.name}\n" +
                        "Mã giao dịch: $txCode\n" +
                        "Thời gian: ${java.text.SimpleDateFormat("HH:mm:ss dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))}\n" +
                        "Phí giao dịch: Miễn phí (0 ₫)"
                    )
                    .setPositiveButton("Hoàn tất") { dialog, _ ->
                        dialog.dismiss()
                        dismiss()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "P2PTransferBottomSheet"
        private const val ARG_RECIPIENT = "arg_recipient"
        private const val ARG_AMOUNT = "arg_amount"
        private const val ARG_NOTE = "arg_note"

        fun newInstance(
            initialRecipient: String? = null,
            initialAmount: Double? = null,
            initialNote: String? = null
        ): P2PTransferBottomSheet {
            return P2PTransferBottomSheet().apply {
                arguments = Bundle().apply {
                    if (initialRecipient != null) putString(ARG_RECIPIENT, initialRecipient)
                    if (initialAmount != null) putDouble(ARG_AMOUNT, initialAmount)
                    if (initialNote != null) putString(ARG_NOTE, initialNote)
                }
            }
        }
    }
}