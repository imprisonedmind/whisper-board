package com.walkietalkie.dictationime.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.auth.AuthStore
import com.walkietalkie.dictationime.auth.LoginEmailActivity

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

        findViewById<LinearLayout>(R.id.deviceSettingsRow).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.creditStoreRow).setOnClickListener {
            startActivity(Intent(this, CreditStoreActivity::class.java))
        }
    }
}
