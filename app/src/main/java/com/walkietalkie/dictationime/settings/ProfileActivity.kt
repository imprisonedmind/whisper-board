package com.walkietalkie.dictationime.settings

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.auth.AuthStore
import com.walkietalkie.dictationime.auth.LoginEmailActivity
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthStore.isSignedIn(this)) {
            startActivity(Intent(this, LoginEmailActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_profile)

        findViewById<ImageButton>(R.id.profileBackButton).setOnClickListener { finish() }
        val email = AuthStore.getEmail(this)
        if (!email.isNullOrBlank()) {
            findViewById<TextView>(R.id.profileName).text = email
        }
        val verificationStatus = if (!email.isNullOrBlank()) {
            getString(R.string.profile_verified_status)
        } else {
            getString(R.string.profile_unverified_status)
        }
        findViewById<TextView>(R.id.profileVerificationStatus).text = verificationStatus

        findViewById<LinearLayout>(R.id.deviceSettingsRow).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.creditStoreRow).setOnClickListener {
            lifecycleScope.launch {
                CreditStoreDataCache.prefetch(this@ProfileActivity)
            }
            startActivity(Intent(this, CreditStoreActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.logoutButton).setOnClickListener {
            AuthStore.clearSession(this)
            val intent = Intent(this, LoginEmailActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }
    }
}
