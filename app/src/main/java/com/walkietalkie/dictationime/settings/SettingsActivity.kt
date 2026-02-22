package com.walkietalkie.dictationime.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.openai.OpenAiConfig
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var apiStatusText: TextView
    private lateinit var outOfCreditsSwitch: SwitchCompat
    private lateinit var openSourceSwitch: SwitchCompat

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updatePermissionButtonState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        apiStatusText = findViewById(R.id.apiStatusText)
        outOfCreditsSwitch = findViewById(R.id.outOfCreditsSwitch)
        openSourceSwitch = findViewById(R.id.openSourceSwitch)
        val grantPermissionButton: Button = findViewById(R.id.grantPermissionButton)
        val openImeSettingsButton: Button = findViewById(R.id.openImeSettingsButton)
        val openAppSettingsButton: Button = findViewById(R.id.openAppSettingsButton)

        findViewById<ImageButton>(R.id.settingsBackButton).setOnClickListener { finish() }

        grantPermissionButton.setOnClickListener {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        openImeSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        openAppSettingsButton.setOnClickListener {
            val appSettingsIntent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(appSettingsIntent)
        }

        outOfCreditsSwitch.isChecked = SettingsStore.isOutOfCreditsMode(this)
        outOfCreditsSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setOutOfCreditsMode(this, isChecked)
        }

        openSourceSwitch.isChecked = SettingsStore.isOpenSourceMode(this)
        openSourceSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setOpenSourceMode(this, isChecked)
            OpenAiConfig.setOpenSourceOverride(isChecked)
            updateApiStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtonState()
        openSourceSwitch.isChecked = SettingsStore.isOpenSourceMode(this)
        OpenAiConfig.setOpenSourceOverride(openSourceSwitch.isChecked)
        lifecycleScope.launch {
            updateApiStatus()
        }
    }

    private fun updatePermissionButtonState() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val button: Button = findViewById(R.id.grantPermissionButton)
        button.isEnabled = !granted
    }

    private fun updateApiStatus() {
        val display = if (OpenAiConfig.isConfigured()) {
            getString(R.string.api_ready)
        } else {
            getString(R.string.api_missing)
        }
        apiStatusText.text = getString(R.string.api_status, display)
    }
}
