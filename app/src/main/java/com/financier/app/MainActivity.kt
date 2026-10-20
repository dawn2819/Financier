package com.financier.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.financier.app.data.local.AppDatabase
import com.financier.app.common.LocaleHelper
import com.financier.app.common.AppAudio
import com.financier.app.common.SessionManager
import com.financier.app.databinding.ActivityMainBinding
import com.financier.app.ui.auth.LoginActivity
import com.financier.app.widget.FinanceWidgetProvider

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyCurrentLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestHighRefreshRate()

        // Kiểm tra session
        if (!SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val userId = SessionManager.getUserId(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val settings = db.settingsDao().getSettingsByUser(userId)
            val darkMode = settings?.darkMode ?: true
            withContext(Dispatchers.Main) {
                val targetMode = if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                    AppCompatDelegate.setDefaultNightMode(targetMode)
                }
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
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

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        var hasSeenDestination = false
        navController.addOnDestinationChangedListener { _, _, _ ->
            if (hasSeenDestination) AppAudio.playClick(this)
            hasSeenDestination = true
        }

        // Ẩn bottom nav khi vào profile/accounts (optional)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.profileFragment, R.id.manageAccountsFragment -> {
                    // Keep nav visible — user can navigate back
                }
            }
        }
    }

    private val inactivityHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        if (SessionManager.isLoggedIn(this)) {
            SessionManager.markBackgrounded(this)
            val intent = Intent(this, LoginActivity::class.java).apply {
                putExtra("require_authentication", true)
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetInactivityTimer()
    }

    private fun resetInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        if (SessionManager.isLoggedIn(this)) {
            inactivityHandler.postDelayed(inactivityRunnable, SessionManager.REAUTHENTICATION_TIMEOUT_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (SessionManager.isLoggedIn(this) && SessionManager.requiresReauthentication(this)) {
            startActivity(Intent(this, LoginActivity::class.java).putExtra("require_authentication", true))
            finish()
            return
        }
        SessionManager.clearBackgroundedAt(this)
        resetInactivityTimer()
        FinanceWidgetProvider.updateAll(this)
        AppAudio.startMusic(this)
    }

    override fun onPause() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        AppAudio.pauseMusic()
        super.onPause()
    }

    override fun onStop() {
        if (!isChangingConfigurations && SessionManager.isLoggedIn(this)) {
            SessionManager.markBackgrounded(this)
        }
        super.onStop()
    }

    override fun onDestroy() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        if (isFinishing) AppAudio.stopMusic()
        super.onDestroy()
    }
}
