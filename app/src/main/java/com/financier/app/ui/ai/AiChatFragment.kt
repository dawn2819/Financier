package com.financier.app.ui.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.financier.app.R
import com.financier.app.common.SessionManager
import com.financier.app.databinding.FragmentAiChatBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AiChatFragment : Fragment() {

    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AiChatViewModel
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = SessionManager.getUserId(requireContext())
        viewModel = ViewModelProvider(
            this, AiChatViewModel.Factory(requireContext(), userId)
        )[AiChatViewModel::class.java]

        setupChat()
        setupQuickChips()
        setupAiSettings()
        observeData()
        updateModeStatus()
    }

    private fun setupChat() {
        chatAdapter = ChatAdapter()
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }

        // Send on button click
        binding.btnSend.setOnClickListener { sendMessage() }

        // Send on keyboard done
        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true }
            else false
        }
    }

    private fun setupQuickChips() {
        binding.chipAnalyze.setOnClickListener {
            binding.etInput.setText(getString(R.string.chip_analyze))
            sendMessage()
        }
        binding.chipSaving.setOnClickListener {
            binding.etInput.setText(getString(R.string.chip_saving))
            sendMessage()
        }
        binding.chipBills.setOnClickListener {
            binding.etInput.setText(getString(R.string.chip_bills))
            sendMessage()
        }
    }

    private fun setupAiSettings() {
        binding.btnAiSettings.setOnClickListener {
            showApiKeyDialog()
        }
    }

    private fun updateModeStatus() {
        if (_binding == null) return
        val hasKey = SessionManager.hasGeminiApiKey(requireContext())
        if (hasKey) {
            binding.tvAiModeStatus.text = "⚡ Gemini 1.5 Cloud (Trực tuyến)"
            binding.tvAiModeStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
        } else {
            binding.tvAiModeStatus.text = "🧠 Trợ lý tài chính On-Device"
            binding.tvAiModeStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
        }
    }

    private fun showApiKeyDialog() {
        val context = requireContext()
        val currentKey = SessionManager.getGeminiApiKey(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }

        val tvDesc = TextView(context).apply {
            text = "Financier tích hợp sẵn Trợ lý tài chính On-Device thông minh phân tích thu chi, số dư, dòng tiền và hóa đơn.\n\nĐể kích hoạt trí tuệ nhân tạo Gemini 1.5 Flash Cloud từ Google cho mọi câu hỏi tự do, hãy dán API Key của bạn từ Google AI Studio."
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
            setPadding(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
        }
        container.addView(tvDesc)

        val input = TextInputEditText(context).apply {
            hint = "Dán Google Gemini API Key (AIzaSy...)"
            setText(currentKey)
            setSingleLine(true)
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
        }
        val inputLayout = TextInputLayout(context).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(12f, 12f, 12f, 12f)
            addView(input)
        }
        container.addView(inputLayout)

        val btnOpenStudio = MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "🌐 Lấy API Key miễn phí tại Google AI Studio"
            textSize = 12f
            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Không thể mở trình duyệt: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        container.addView(btnOpenStudio)

        MaterialAlertDialogBuilder(context)
            .setTitle("Cấu hình Google Gemini AI")
            .setView(container)
            .setPositiveButton("Lưu Key") { _, _ ->
                val newKey = input.text?.toString()?.trim().orEmpty()
                SessionManager.saveGeminiApiKey(context, newKey)
                updateModeStatus()
                if (newKey.isNotBlank()) {
                    Toast.makeText(context, "Đã lưu Gemini API Key! Đã bật chế độ Gemini Cloud.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Đã xóa key. Trợ lý On-Device đang hoạt động.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Dùng On-Device") { _, _ ->
                SessionManager.saveGeminiApiKey(context, null)
                updateModeStatus()
                Toast.makeText(context, "Đã kích hoạt Trợ lý On-Device độc lập.", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Đóng", null)
            .show()
    }

    private fun sendMessage() {
        val text = binding.etInput.text.toString().trim()
        if (text.isEmpty()) return
        binding.etInput.setText("")
        binding.progressAi.visibility = View.VISIBLE
        viewModel.sendMessage(text)
    }

    private fun observeData() {
        viewModel.chatMessages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.submitList(messages.toMutableList())
            // Smooth scroll to bottom with bounds check
            if (messages.isNotEmpty()) {
                binding.rvChat.postDelayed({
                    binding.rvChat.smoothScrollToPosition(messages.size - 1)
                }, 100)
            }
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressAi.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnSend.isEnabled = !loading
            binding.btnSend.alpha = if (loading) 0.5f else 1f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
