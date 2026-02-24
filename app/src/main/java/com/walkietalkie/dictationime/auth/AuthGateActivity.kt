package com.walkietalkie.dictationime.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.config.AppModeConfig
import com.walkietalkie.dictationime.settings.MainActivity
import kotlinx.coroutines.launch

class AuthGateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val target = if (!AppModeConfig.isAuthRequired) {
                MainActivity::class.java
            } else if (AuthStore.isSignedIn(this@AuthGateActivity)) {
                val token = AuthSessionManager.getValidAccessToken(this@AuthGateActivity)
                if (token.isNullOrBlank()) {
                    AuthStore.clearSession(this@AuthGateActivity)
                    LoginEmailActivity::class.java
                } else {
                    MainActivity::class.java
                }
            } else {
                LoginEmailActivity::class.java
            }

            startActivity(Intent(this@AuthGateActivity, target))
            finish()
        }
    }
}
