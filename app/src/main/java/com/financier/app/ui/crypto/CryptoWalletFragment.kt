package com.financier.app.ui.crypto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.financier.app.R
import com.financier.app.databinding.FragmentCryptoWalletBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class CryptoWalletFragment : Fragment() {
    private var _binding: FragmentCryptoWalletBinding? = null
    private val binding get() = _binding!!
    private val preferences by lazy { requireContext().getSharedPreferences("crypto_watch_only", 0) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCryptoWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.cryptoAddress.setText(preferences.getString(KEY_ADDRESS, ""))
        binding.cryptoTokenContract.setText(preferences.getString(KEY_TOKEN, ""))
        binding.cryptoWatchButton.setOnClickListener { watchAddress() }
        binding.cryptoRefreshButton.setOnClickListener {
            loadMarket()
            val address = binding.cryptoAddress.text?.toString()?.trim().orEmpty()
            if (address.matches(ADDRESS_REGEX)) loadWallet(address, binding.cryptoTokenContract.text?.toString()?.trim().orEmpty())
        }
        loadMarket()
        preferences.getString(KEY_ADDRESS, null)?.let { address ->
            if (address.matches(ADDRESS_REGEX)) loadWallet(address, preferences.getString(KEY_TOKEN, "").orEmpty())
        }
    }

    private fun watchAddress() {
        val address = binding.cryptoAddress.text?.toString()?.trim().orEmpty()
        val token = binding.cryptoTokenContract.text?.toString()?.trim().orEmpty()
        if (!address.matches(ADDRESS_REGEX) || (token.isNotEmpty() && !token.matches(ADDRESS_REGEX))) {
            binding.cryptoWalletResult.text = getString(R.string.crypto_address_invalid)
            return
        }
        preferences.edit().putString(KEY_ADDRESS, address).putString(KEY_TOKEN, token).apply()
        loadWallet(address, token)
    }

    private fun loadMarket() {
        binding.cryptoMarketStatus.text = getString(R.string.crypto_loading)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { CryptoReadOnlyRepository.getMarketSnapshot() } }
            val current = _binding ?: return@launch
            result.onSuccess { snapshot ->
                val btc = snapshot.bitcoin
                val bnb = snapshot.bnb
                current.cryptoMarketStatus.text = listOf(
                    getString(R.string.crypto_btc_price, formatPrice(btc.usd), formatPrice(btc.vnd), formatChange(btc.change24h)),
                    getString(R.string.crypto_bnb_price, formatPrice(bnb.usd), formatPrice(bnb.vnd), formatChange(bnb.change24h))
                ).joinToString("\n")
            }.onFailure {
                current.cryptoMarketStatus.text = getString(R.string.crypto_price_error)
            }
        }
    }

    private fun loadWallet(address: String, token: String) {
        binding.cryptoWalletResult.text = getString(R.string.crypto_loading)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { CryptoReadOnlyRepository.getWalletSnapshot(address, token.ifBlank { null }) }
            }
            val current = _binding ?: return@launch
            result.onSuccess { snapshot ->
                val lines = mutableListOf(getString(R.string.crypto_wallet_balance, snapshot.bnb))
                if (snapshot.token != null) {
                    lines += getString(R.string.crypto_token_balance, snapshot.token, snapshot.tokenSymbol.orEmpty())
                }
                current.cryptoWalletResult.text = lines.joinToString("\n")
            }.onFailure {
                current.cryptoWalletResult.text = getString(R.string.crypto_wallet_error)
            }
        }
    }

    private fun formatPrice(value: String): String = value.toBigDecimalOrNull()?.let { amount ->
        NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = if (amount > java.math.BigDecimal("100")) 0 else 2 }.format(amount)
    } ?: value

    private fun formatChange(value: String): String = value.toBigDecimalOrNull()?.let { "${"%.2f".format(Locale.US, it)}%" } ?: value

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val KEY_ADDRESS = "public_address"
        private const val KEY_TOKEN = "token_contract"
        private val ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{40}$")
    }
}
