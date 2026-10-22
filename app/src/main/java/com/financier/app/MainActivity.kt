package com.financier.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.financier.app.common.AppAudio
import com.financier.app.common.AppThemePreferences
import com.financier.app.common.LocaleHelper
import com.financier.app.common.SessionManager
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.news.DailyNewsScheduler
import com.financier.app.databinding.ActivityMainBinding
import com.financier.app.ui.auth.LoginActivity
import com.financier.app.ui.news.ArticleReaderActivity
import com.financier.app.widget.FinanceWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyCurrentLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppThemePreferences.apply(this)

        requestHighRefreshRate()

        // Kiểm tra session và yêu cầu xác thực lại nếu chạy nền >= 3 phút
        if (!SessionManager.isLoggedIn(this) || SessionManager.requiresReauthentication(this)) {
            val intent = Intent(this, LoginActivity::class.java).apply {
                if (SessionManager.requiresReauthentication(this@MainActivity)) {
                    putExtra("require_authentication", true)
                }
                this@MainActivity.intent?.extras?.let { putExtras(it) }
            }
            startActivity(intent)
            finish()
            return
        }

        val userId = SessionManager.getUserId(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val settings = db.settingsDao().getSettingsByUser(userId)
            withContext(Dispatchers.Main) {
                AppThemePreferences.apply(this@MainActivity, settings?.darkMode)
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        DailyNewsScheduler.scheduleDaily8AM(this)
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

        handleNavigationIntent(intent)

        var hasSeenDestination = false
        navController.addOnDestinationChangedListener { _, _, _ ->
            if (hasSeenDestination) AppAudio.playClick(this)
            hasSeenDestination = true
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(targetIntent: Intent?) {
        val navTarget = targetIntent?.getStringExtra("navigate_to") ?: return
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        val navController = navHostFragment.navController

        when (navTarget) {
            "article_reader" -> {
                val url = targetIntent.getStringExtra("article_url")
                val title = targetIntent.getStringExtra("article_title")
                val source = targetIntent.getStringExtra("article_source")
                if (!url.isNullOrBlank()) {
                    ArticleReaderActivity.open(this, url, title.orEmpty().ifEmpty { "Tin tức" }, source.orEmpty().ifEmpty { "Tin tức" })
                } else {
                    navController.navigate(R.id.financialNewsFeedFragment)
                }
            }
            "news" -> {
                navController.navigate(R.id.financialNewsFeedFragment)
            }
            "bills" -> {
                navController.navigate(R.id.billsHubFragment)
            }
            "services" -> {
                navController.navigate(R.id.servicesHubFragment)
            }
            "receive_money" -> {
                navController.navigate(R.id.receiveMoneyFragment)
            }
            "entertainment" -> {
                navController.navigate(R.id.entertainmentHubFragment)
            }
        }
    }

    private val inactivityHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        if (SessionManager.isLoggedIn(this)) {
            SessionManager.markBackgrounded(this)
            val intent = Intent(this, LoginActivity::class.java).apply {
                putExtra("require_authentication", true)
                this@MainActivity.intent?.extras?.let { putExtras(it) }
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
            val intent = Intent(this, LoginActivity::class.java).apply {
                putExtra("require_authentication", true)
                this@MainActivity.intent?.extras?.let { putExtras(it) }
            }
            startActivity(intent)
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