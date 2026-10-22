package com.financier.app.ui.services

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.financier.app.common.AppAudio
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.SessionManager
import com.financier.app.data.local.AppDatabase
import com.financier.app.databinding.FragmentReceiveMoneyBinding
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class ReceiveMoneyFragment : Fragment() {

    private var _binding: FragmentReceiveMoneyBinding? = null
    private val binding get() = _binding!!

    private var currentUsername: String = ""
    private var currentDisplayName: String = ""
    private var currentUserId: Long = 0L
    private var qrGenerateJob: Job? = null
    private var lastGeneratedBitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReceiveMoneyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigateUp()
        }

        setupUserData()
        setupAmountChips()
        setupInputWatchers()
        setupActionButtons()
    }

    private fun setupUserData() {
        currentUserId = SessionManager.getUserId(requireContext())
        currentUsername = SessionManager.getUsername(requireContext()).ifEmpty { "dawn" }

        binding.tvUsernameBadge.text = "@$currentUsername"

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val user = db.userDao().getUserById(currentUserId)
            val accounts = db.financialAccountDao().getAccountsByUserSync(currentUserId)
            val primary = accounts.firstOrNull()

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                currentDisplayName = user?.displayName ?: currentUsername
                binding.tvDisplayName.text = currentDisplayName

                val accName = primary?.name ?: "Ví chính"
                val accNumber = "FN-${1000 + currentUserId}"
                binding.tvAccountInfo.text = "$accName · Tài khoản số: $accNumber"

                generateQrCode()
            }
        }
    }

    private fun setupAmountChips() {
        binding.chip50k.setOnClickListener {
            AppAudio.playClick(requireContext())
            binding.etAmount.setText("50000")
        }
        binding.chip100k.setOnClickListener {
            AppAudio.playClick(requireContext())
            binding.etAmount.setText("100000")
        }
        binding.chip200k.setOnClickListener {
            AppAudio.playClick(requireContext())
            binding.etAmount.setText("200000")
        }
        binding.chip500k.setOnClickListener {
            AppAudio.playClick(requireContext())
            binding.etAmount.setText("500000")
        }
        binding.chipClear.setOnClickListener {
            AppAudio.playClick(requireContext())
            binding.etAmount.setText("")
            binding.etMessage.setText("")
        }
    }

    private fun setupInputWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                qrGenerateJob?.cancel()
                qrGenerateJob = lifecycleScope.launch {
                    delay(300)
                    generateQrCode()
                }
            }
        }

        binding.etAmount.addTextChangedListener(watcher)
        binding.etMessage.addTextChangedListener(watcher)
    }

    private fun generateQrCode() {
        val amountStr = binding.etAmount.text?.toString()?.trim().orEmpty()
        val note = binding.etMessage.text?.toString()?.trim().orEmpty()
        val amount = amountStr.toDoubleOrNull()

        if (amount != null && amount > 0) {
            binding.tvQrAmountDisplay.text = CurrencyFormatter.format(amount, "VND")
        } else {
            binding.tvQrAmountDisplay.text = "Số tiền bất kỳ"
        }

        // Build QR content payload
        val encodedNote = try {
            URLEncoder.encode(note, "UTF-8")
        } catch (_: Exception) {
            note
        }
        val qrContent = buildString {
            append("financier://transfer?to=").append(currentUsername)
            if (amount != null && amount > 0) {
                append("&amount=").append(amount.toLong())
            }
            if (encodedNote.isNotBlank()) {
                append("&note=").append(encodedNote)
            }
        }

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val encoder = BarcodeEncoder()
                val bitmap = encoder.encodeBitmap(qrContent, BarcodeFormat.QR_CODE, 600, 600)
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    lastGeneratedBitmap = bitmap
                    binding.ivQrCode.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupActionButtons() {
        binding.btnCopyInfo.setOnClickListener {
            AppAudio.playClick(requireContext())
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Financier Account", "@$currentUsername (FN-${1000 + currentUserId})")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Đã sao chép tài khoản: @$currentUsername", Toast.LENGTH_SHORT).show()
        }

        binding.btnShareQr.setOnClickListener {
            AppAudio.playClick(requireContext())
            val amountStr = binding.etAmount.text?.toString()?.trim().orEmpty()
            val amount = amountStr.toDoubleOrNull()
            val amountText = if (amount != null && amount > 0) " số tiền ${CurrencyFormatter.format(amount, "VND")}" else ""

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Chuyển tiền tới tài khoản Financier của $currentDisplayName (@$currentUsername)$amountText qua mã tài khoản FN-${1000 + currentUserId}"
                )
            }
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ thông tin nhận tiền"))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}