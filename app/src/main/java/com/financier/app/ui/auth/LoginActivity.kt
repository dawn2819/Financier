package com.financier.app.ui.auth

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import androidx.appcompat.app.AppCompatDelegate
import com.financier.app.MainActivity
import com.financier.app.R
import com.financier.app.common.LocaleHelper
import com.financier.app.common.SecurityUtils
import com.financier.app.common.SessionManager
import com.financier.app.data.local.AppDatabase
import com.financier.app.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var db: AppDatabase

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.applyCurrentLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestHighRefreshRate()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        // Nếu đã đăng nhập trước đó thì kiểm tra khóa sinh trắc học và theme
        if (SessionManager.isLoggedIn(this)) {
            val userId = SessionManager.getUserId(this)
            val username = SessionManager.getUsername(this) ?: ""
            binding.etUsername.setText(username) // Prefill in case of biometric fallback
            
            lifecycleScope.launch(Dispatchers.IO) {
                val settings = db.settingsDao().getSettingsByUser(userId)
                val biometricEnabled = settings?.biometricEnabled ?: false
                val darkMode = settings?.darkMode ?: true
                withContext(Dispatchers.Main) {
                    val targetMode = if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                    if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                        AppCompatDelegate.setDefaultNightMode(targetMode)
                    }
                    if (biometricEnabled) {
                        showBiometricPrompt()
                    } else {
                        goToMain()
                    }
                }
            }
            return
        }

        setupListeners()
        playEntryAnimation()
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showError(getString(R.string.biometric_failed_fallback_pw))
                    binding.etPassword.requestFocus()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    goToMain()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    showError(getString(R.string.biometric_failed_fallback_pw))
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_negative_button))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun requestHighRefreshRate() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val display = this.display
                val modes = display?.supportedModes
                val maxRefreshRateMode = modes?.maxByOrNull { it.refreshRate }
                if (maxRefreshRateMode != null) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxRefreshRateMode.modeId
                    window.attributes = params
                }
            } else {
                val params = window.attributes
                params.preferredRefreshRate = 120f
                window.attributes = params
            }
        } catch (e: Exception) {
            // Avoid crash if display modes are not accessible
        }
    }

    private fun playEntryAnimation() {
        // Fade in + slide up the login card
        binding.root.post {
            val views = listOf(binding.tvAppName, binding.btnLogin)
            // Animate the entire scroll content with a fade-in
            binding.root.alpha = 0f
            ObjectAnimator.ofFloat(binding.root, "alpha", 0f, 1f).apply {
                duration = 600
                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

    private fun setupListeners() {
        // Enter trên password field → đăng nhập
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }

        binding.btnLogin.setOnClickListener { attemptLogin() }

        binding.tvRegister.setOnClickListener {
            // Hiện dialog/fragment đăng ký
            RegisterDialog().show(supportFragmentManager, "register")
        }
    }

    private fun attemptLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        // Validate
        if (username.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.error_empty_fields))
            shakeErrorView()
            return
        }

        setLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            val user = db.userDao().getUserByUsername(username)

            withContext(Dispatchers.Main) {
                setLoading(false)

                if (user != null && SecurityUtils.verifyPassword(password, user.passwordHash)) {
                    // Lưu session
                    SessionManager.saveSession(
                        context = this@LoginActivity,
                        userId = user.id,
                        username = user.username,
                        displayName = user.displayName,
                        role = user.role,
                        avatarColor = user.avatarColor,
                        avatarPath = user.avatarPath
                    )
                    // Áp dụng cài đặt từ settings user
                    applyUserSettings(user.id)
                    goToMain()
                } else {
                    showError(getString(R.string.error_invalid_credentials))
                    shakeErrorView()
                }
            }
        }
    }

    private suspend fun applyUserSettings(userId: Long) {
        val settings = withContext(Dispatchers.IO) {
            db.settingsDao().getSettingsByUser(userId)
        }
        settings?.let {
            withContext(Dispatchers.Main) {
                val targetMode = if (it.darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                    AppCompatDelegate.setDefaultNightMode(targetMode)
                }
            }
            LocaleHelper.setLocale(this, it.language)
        }
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    /** Shake animation cho error feedback */
    private fun shakeErrorView() {
        ObjectAnimator.ofFloat(binding.tvError, "translationX", 0f, 15f, -15f, 10f, -10f, 5f, -5f, 0f).apply {
            duration = 400
            start()
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.alpha = if (loading) 0.6f else 1f
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }
}
