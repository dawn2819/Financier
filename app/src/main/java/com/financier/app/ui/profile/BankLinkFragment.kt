package com.financier.app.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.financier.app.R
import com.financier.app.common.SessionManager
import com.financier.app.data.bank.BankSmsParser
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.BankSmsLogEntity
import com.financier.app.data.local.entity.TransactionEntity
import com.financier.app.databinding.FragmentBankLinkBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BankLinkFragment : Fragment() {

    private var _binding: FragmentBankLinkBinding? = null
    private val binding get() = _binding!!

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val receiveSmsGranted = permissions[Manifest.permission.RECEIVE_SMS] ?: false
        val readSmsGranted = permissions[Manifest.permission.READ_SMS] ?: false

        if (receiveSmsGranted && readSmsGranted) {
            updateUiState(true)
            Toast.makeText(requireContext(), "Quyền SMS đã được cấp thành công!", Toast.LENGTH_SHORT).show()
        } else {
            updateUiState(false)
            Toast.makeText(requireContext(), "Quyền SMS bị từ chối.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBankLinkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnGrantPermission.setOnClickListener {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
            )
        }

        binding.btnScanHistory.setOnClickListener {
            scanInboxSms()
        }

        checkSmsPermissions()
    }

    private fun checkSmsPermissions() {
        val receiveGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val readGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        updateUiState(receiveGranted && readGranted)
    }

    private fun updateUiState(isGranted: Boolean) {
        if (isGranted) {
            binding.tvStatusDesc.text = "Quyền giám sát SMS đang hoạt động. Ứng dụng sẽ tự động phát hiện biến động số dư từ tin nhắn các ngân hàng và thêm giao dịch mới."
            binding.tvStatusDesc.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            binding.btnGrantPermission.visibility = View.GONE
            binding.btnScanHistory.visibility = View.VISIBLE
        } else {
            binding.tvStatusDesc.text = getString(R.string.sms_permission_required)
            binding.tvStatusDesc.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
            binding.btnGrantPermission.visibility = View.VISIBLE
            binding.btnScanHistory.visibility = View.GONE
        }
    }

    private fun scanInboxSms() {
        val userId = SessionManager.getUserId(requireContext())
        if (userId == -1L) return

        binding.btnScanHistory.isEnabled = false
        binding.btnScanHistory.text = "Đang quét..."

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val smsLogDao = db.bankSmsLogDao()
            val transactionDao = db.transactionDao()
            val accountDao = db.financialAccountDao()

            var importedCount = 0

            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("body", "address", "date")
            val cursor: Cursor? = requireContext().contentResolver.query(
                uri, projection, null, null, "date DESC"
            )

            cursor?.use {
                val bodyIndex = it.getColumnIndexOrThrow("body")
                val addressIndex = it.getColumnIndexOrThrow("address")
                val dateIndex = it.getColumnIndexOrThrow("date")

                val accounts = accountDao.getAccountsByUserSync(userId)

                while (it.moveToNext()) {
                    val body = it.getString(bodyIndex) ?: continue
                    val sender = it.getString(addressIndex) ?: continue
                    val timestamp = it.getLong(dateIndex)

                    if (BankSmsParser.isBankSender(sender)) {
                        // Check if duplicate
                        if (smsLogDao.isSmsParsed(body, timestamp) == 0) {
                            val parsed = BankSmsParser.parse(sender, body)
                            if (parsed != null) {
                                // Find account
                                val bankAccount = accounts.find { acc ->
                                    acc.accountType == "BANK" || acc.name.contains(parsed.bankName, ignoreCase = true)
                                } ?: accounts.firstOrNull()

                                if (bankAccount != null) {
                                    val transaction = TransactionEntity(
                                        userId = userId,
                                        accountId = bankAccount.id,
                                        amount = parsed.amount,
                                        type = parsed.type,
                                        category = if (parsed.type == "INCOME") "income" else "others",
                                        note = "[${parsed.bankName}] ${parsed.note}",
                                        dateMs = parsed.dateMs
                                    )
                                    val txId = transactionDao.insertTransaction(transaction)

                                    smsLogDao.insertLog(
                                        BankSmsLogEntity(
                                            smsBody = body,
                                            sender = sender,
                                            timestamp = timestamp,
                                            transactionId = txId
                                        )
                                    )
                                    importedCount++
                                }
                            }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                binding.btnScanHistory.isEnabled = true
                binding.btnScanHistory.text = getString(R.string.scan_old_sms)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.scan_old_sms_success, importedCount),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
