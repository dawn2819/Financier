package com.financier.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.financier.app.common.LocaleHelper
import com.financier.app.common.SessionManager
import com.financier.app.databinding.ActivityMainBinding
import com.financier.app.ui.auth.LoginActivity

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

        // Ẩn bottom nav khi vào profile/accounts (optional)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.profileFragment, R.id.manageAccountsFragment -> {
                    // Keep nav visible — user can navigate back
                }
            }
        }
    }
}
