package com.financier.app.ui.auth

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        // Nếu đã đăng nhập trước đó thì skip login
        if (SessionManager.isLoggedIn(this)) {
            goToMain()
            return
        }

        setupListeners()
        playEntryAnimation()
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
                        avatarColor = user.avatarColor
                    )
                    // Áp dụng ngôn ngữ từ settings user
                    applyUserLanguage(user.id)
                    goToMain()
                } else {
                    showError(getString(R.string.error_invalid_credentials))
                    shakeErrorView()
                }
            }
        }
    }

    private suspend fun applyUserLanguage(userId: Long) {
        val settings = withContext(Dispatchers.IO) {
            db.settingsDao().getSettingsByUser(userId)
        }
        settings?.let {
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
