package com.financier.app.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.financier.app.R
import com.financier.app.common.SessionManager
import com.financier.app.databinding.FragmentAiChatBinding

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
        observeData()
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
