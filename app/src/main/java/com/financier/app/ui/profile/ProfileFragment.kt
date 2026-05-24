package com.financier.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.financier.app.R
import com.financier.app.common.LocaleHelper
import com.financier.app.common.SessionManager
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.AppSettingsEntity
import com.financier.app.databinding.FragmentProfileBinding
import com.financier.app.ui.auth.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var currentSettings: AppSettingsEntity? = null
    private var isLoadingSettings = true // Prevent toggle listeners firing during load

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleAvatarSelected(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userId = SessionManager.getUserId(requireContext())

        // Load user info
        binding.tvDisplayName.text = SessionManager.getDisplayName(requireContext())
        binding.tvUsername.text = "@${SessionManager.getUsername(requireContext())}"

        binding.ivProfileAvatar.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        loadAvatar()

        // Load settings (sets isLoadingSettings = false when done)
        loadSettings(userId)

        // Language switcher
        binding.rowLanguage.setOnClickListener { showLanguageDialog() }

        // Currency switcher
        binding.rowCurrency.setOnClickListener { showCurrencyDialog() }

        // Manage accounts — navigate to ManageAccountsFragment
        binding.rowManageAccounts.setOnClickListener {
            findNavController().navigate(R.id.manageAccountsFragment)
        }

        // Bank SMS Link
        binding.rowBankLink.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_bank_link)
        }

        // Sign out
        binding.btnSignOut.setOnClickListener {
            SessionManager.clearSession(requireContext())
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        // Notifications toggle
        binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
            if (!isLoadingSettings) saveSettings { it.copy(notificationsOn = checked) }
        }

        // Dark mode toggle
        binding.switchDarkMode.setOnCheckedChangeListener { _, checked ->
            if (!isLoadingSettings) {
                saveSettings { it.copy(darkMode = checked) }
                AppCompatDelegate.setDefaultNightMode(
                    if (checked) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        }

        // Biometric toggle
        binding.switchBiometric.setOnCheckedChangeListener { _, checked ->
            if (!isLoadingSettings) saveSettings { it.copy(biometricEnabled = checked) }
        }
    }

    private fun loadSettings(userId: Long) {
        isLoadingSettings = true
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val settings = db.settingsDao().getSettingsByUser(userId)
            withContext(Dispatchers.Main) {
                currentSettings = settings
                settings?.let {
                    binding.switchNotifications.isChecked = it.notificationsOn
                    binding.switchDarkMode.isChecked = it.darkMode
                    binding.switchBiometric.isChecked = it.biometricEnabled
                    binding.tvCurrency.text = it.currency
                    binding.tvLanguage.text = if (it.language == "vi")
                        getString(R.string.lang_vietnamese)
                    else
                        getString(R.string.lang_english)
                }
                isLoadingSettings = false
            }
        }
    }

    private fun showLanguageDialog() {
        val options = arrayOf(getString(R.string.lang_vietnamese), getString(R.string.lang_english))
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.language_label))
            .setItems(options) { _, which ->
                val lang = if (which == 0) "vi" else "en"
                saveSettings { it.copy(language = lang) }
                LocaleHelper.setLocale(requireContext(), lang)
                requireActivity().recreate()
            }.show()
    }

    private fun showCurrencyDialog() {
        val options = arrayOf("VND (₫)", "USD ($)")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.currency_label))
            .setItems(options) { _, which ->
                val currency = if (which == 0) "VND" else "USD"
                binding.tvCurrency.text = currency
                saveSettings { it.copy(currency = currency) }
            }.show()
    }

    private fun saveSettings(update: (AppSettingsEntity) -> AppSettingsEntity) {
        val userId = SessionManager.getUserId(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val settings = db.settingsDao().getSettingsByUser(userId)
                ?: AppSettingsEntity(userId = userId)
            db.settingsDao().insertSettings(update(settings))
        }
    }

    private fun loadAvatar() {
        val path = SessionManager.getAvatarPath(requireContext())
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                binding.ivProfileAvatar.setImageURI(Uri.fromFile(file))
            } else {
                binding.ivProfileAvatar.setImageResource(R.drawable.ic_default_avatar)
            }
        } else {
            binding.ivProfileAvatar.setImageResource(R.drawable.ic_default_avatar)
        }
    }

    private fun handleAvatarSelected(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = requireContext()
                val userId = SessionManager.getUserId(context)
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val avatarFile = File(context.filesDir, "avatar_${userId}.jpg")
                    val outputStream = FileOutputStream(avatarFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()

                    val absolutePath = avatarFile.absolutePath
                    SessionManager.saveAvatarPath(context, absolutePath)

                    val db = AppDatabase.getDatabase(context)
                    val user = db.userDao().getUserById(userId)
                    if (user != null) {
                        db.userDao().updateUser(user.copy(avatarPath = absolutePath))
                    }

                    withContext(Dispatchers.Main) {
                        loadAvatar()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
