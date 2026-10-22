package com.financier.app.ui.auth

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
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

        setupListeners()

        // Nếu đã đăng nhập trước đó thì kiểm tra khóa sinh trắc học và theme
        if (SessionManager.isLoggedIn(this)) {
            val userId = SessionManager.getUserId(this)
            val username = SessionManager.getUsername(this)
            binding.etUsername.setText(username) // Prefill in case of biometric fallback
            val requiresAuthentication = intent.getBooleanExtra("require_authentication", false) ||
                SessionManager.requiresReauthentication(this)
            
            lifecycleScope.launch(Dispatchers.IO) {
                val settings = db.settingsDao().getSettingsByUser(userId)
                val biometricEnabled = settings?.biometricEnabled ?: false
                val darkMode = settings?.darkMode ?: true
                withContext(Dispatchers.Main) {
                    val targetMode = if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                    if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                        AppCompatDelegate.setDefaultNightMode(targetMode)
                    }
                    // Chỉ yêu cầu xác thực lại khi đã quá 3 phút không sử dụng app (chạy nền)
                    val shouldAuthenticate = requiresAuthentication
                    if (!shouldAuthenticate) {
                        SessionManager.clearBackgroundedAt(this@LoginActivity)
                        goToMain()
                    } else {
                        // Đã quá 3 phút: yêu cầu xác thực lại bằng mk hoặc sinh trắc học
                        binding.tvError.visibility = View.GONE
                        binding.tvLoginSubtitle.text = getString(R.string.reauthentication_password_prompt)
                        playEntryAnimation()

                        if (biometricEnabled && canAuthenticateBiometric()) {
                            showBiometricPrompt()
                        } else {
                            // Tắt sinh trắc học hoặc không hỗ trợ: yêu cầu nhập mật khẩu bình thường (không báo lỗi đỏ)
                            binding.etPassword.post {
                                binding.etPassword.requestFocus()
                                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.showSoftInput(binding.etPassword, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                    }
                }
            }
            return
        }

        playEntryAnimation()
    }

    private fun canAuthenticateBiometric(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Nếu người dùng chủ động chọn "Dùng Mật Khẩu" hoặc hủy, không hiện thông báo lỗi đỏ
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        showError(getString(R.string.biometric_failed_fallback_pw))
                    }
                    binding.etPassword.post {
                        binding.etPassword.requestFocus()
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showSoftInput(binding.etPassword, InputMethodManager.SHOW_IMPLICIT)
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    SessionManager.clearBackgroundedAt(this@LoginActivity)
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
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (_: IllegalArgumentException) {
            binding.etPassword.post {
                binding.etPassword.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.etPassword, InputMethodManager.SHOW_IMPLICIT)
            }
        } catch (_: SecurityException) {
            binding.etPassword.post {
                binding.etPassword.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.etPassword, InputMethodManager.SHOW_IMPLICIT)
            }
        }
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
        // Clear error text when user starts typing
        binding.etUsername.doAfterTextChanged { binding.tvError.visibility = View.GONE }
        binding.etPassword.doAfterTextChanged { binding.tvError.visibility = View.GONE }

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

    fun onRegisterSuccess(registeredUsername: String) {
        binding.etUsername.setText(registeredUsername)
        binding.etPassword.setText("")
        binding.tvError.visibility = View.GONE
        binding.etPassword.post {
            binding.etPassword.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.etPassword, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun attemptLogin() {
        val username = binding.etUsername.text.toString().trim().lowercase()
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
                ?: if (SessionManager.isLoggedIn(this@LoginActivity)) {
                    db.userDao().getUserById(SessionManager.getUserId(this@LoginActivity))
                } else null

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
                    SessionManager.clearBackgroundedAt(this@LoginActivity)
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
        SessionManager.clearBackgroundedAt(this)
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            this@LoginActivity.intent?.extras?.let { putExtras(it) }
        }
        startActivity(mainIntent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }
}
