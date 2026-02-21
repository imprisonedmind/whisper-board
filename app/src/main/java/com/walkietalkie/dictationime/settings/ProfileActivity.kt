package com.walkietalkie.dictationime.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.walkietalkie.dictationime.R

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        findViewById<ImageButton>(R.id.profileBackButton).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.deviceSettingsRow).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.creditStoreRow).setOnClickListener {
            startActivity(Intent(this, CreditStoreActivity::class.java))
        }
    }
}
