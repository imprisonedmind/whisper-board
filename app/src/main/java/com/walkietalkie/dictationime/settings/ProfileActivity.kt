package com.walkietalkie.dictationime.settings

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.auth.AuthStore
import com.walkietalkie.dictationime.config.AppModeConfig
import com.walkietalkie.dictationime.auth.LoginEmailActivity
import com.walkietalkie.dictationime.model.TranscriptionModels
import com.walkietalkie.dictationime.openai.OpenAiConfig
import com.walkietalkie.dictationime.openai.OpenAiKeyStore
import android.app.Dialog
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    private lateinit var modelSubtitleText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AppModeConfig.isAuthRequired && !AuthStore.isSignedIn(this)) {
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
        modelSubtitleText = findViewById(R.id.modelSelectionSubtitle)
        renderSelectedModel()

        findViewById<LinearLayout>(R.id.deviceSettingsRow).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val dataPrivacyRow = findViewById<LinearLayout>(R.id.dataPrivacyRow)
        val appProfilesRow = findViewById<LinearLayout>(R.id.appProfilesRow)
        val transactionHistoryRow = findViewById<LinearLayout>(R.id.transactionHistoryRow)
        val creditStoreRow = findViewById<LinearLayout>(R.id.creditStoreRow)
        val openSourceApiKeyRow = findViewById<LinearLayout>(R.id.openSourceApiKeyRow)
        val profileIdentitySection = findViewById<LinearLayout>(R.id.profileIdentitySection)
        val dataPrivacyDivider = findViewById<View>(R.id.dataPrivacyDivider)
        val openSourceApiKeyDivider = findViewById<View>(R.id.openSourceApiKeyDivider)
        val appProfilesDivider = findViewById<View>(R.id.appProfilesDivider)
        val modelSelectionDivider = findViewById<View>(R.id.modelSelectionDivider)
        val transactionHistoryDivider = findViewById<View>(R.id.transactionHistoryDivider)
        val creditStoreDivider = findViewById<View>(R.id.creditStoreDivider)
        val logoutButton = findViewById<android.widget.Button>(R.id.logoutButton)

        if (AppModeConfig.isOpenSourceMode) {
            openSourceApiKeyRow.visibility = View.VISIBLE
            dataPrivacyDivider.visibility = View.VISIBLE
            openSourceApiKeyDivider.visibility = View.VISIBLE
            modelSelectionDivider.visibility = View.GONE
            openSourceApiKeyRow.setOnClickListener {
                showOpenSourceApiKeyDialog()
            }
        } else {
            openSourceApiKeyRow.visibility = View.GONE
            openSourceApiKeyDivider.visibility = View.GONE
            modelSelectionDivider.visibility = View.VISIBLE
        }

        if (AppModeConfig.backendFeaturesEnabled) {
            dataPrivacyRow.setOnClickListener {
                lifecycleScope.launch {
                    DataDeletionStatusDataCache.prefetch(this@ProfileActivity)
                }
                startActivity(Intent(this, DataPrivacyActivity::class.java))
            }

            appProfilesRow.setOnClickListener {
                startActivity(Intent(this, AppProfilesActivity::class.java))
            }

            transactionHistoryRow.setOnClickListener {
                lifecycleScope.launch {
                    TransactionHistoryDataCache.prefetch(this@ProfileActivity)
                }
                startActivity(Intent(this, TransactionHistoryActivity::class.java))
            }

            creditStoreRow.setOnClickListener {
                lifecycleScope.launch {
                    CreditStoreDataCache.prefetch(this@ProfileActivity)
                }
                startActivity(Intent(this, CreditStoreActivity::class.java))
            }
        } else {
            profileIdentitySection.visibility = View.GONE
            dataPrivacyRow.visibility = View.GONE
            if (!AppModeConfig.isOpenSourceMode) {
                dataPrivacyDivider.visibility = View.GONE
            }
            appProfilesRow.visibility = View.GONE
            appProfilesDivider.visibility = View.GONE
            transactionHistoryRow.visibility = View.GONE
            transactionHistoryDivider.visibility = View.GONE
            creditStoreRow.visibility = View.GONE
            creditStoreDivider.visibility = View.GONE
        }

        findViewById<LinearLayout>(R.id.modelSelectionRow).setOnClickListener {
            startActivity(Intent(this, ModelSelectionActivity::class.java))
        }

        logoutButton.setOnClickListener {
            AuthStore.clearSession(this)
            val intent = Intent(this, LoginEmailActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }
        if (!AppModeConfig.isAuthRequired) {
            logoutButton.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        renderSelectedModel()
        if (AppModeConfig.backendFeaturesEnabled) {
            lifecycleScope.launch {
                DataDeletionStatusDataCache.prefetch(this@ProfileActivity)
            }
        }
        lifecycleScope.launch {
            runCatching {
                TranscriptionModelStore.fetchRemote(this@ProfileActivity)
            }.onSuccess {
                renderSelectedModel()
            }
        }
    }

    private fun renderSelectedModel() {
        val modelId = SettingsStore.getSelectedModel(this)
        modelSubtitleText.text = TranscriptionModels.displayLabelFor(modelId)
    }

    private fun showOpenSourceApiKeyDialog() {
        if (!AppModeConfig.isOpenSourceMode) return

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_open_source_api_key)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val apiKeyInput = dialog.findViewById<EditText>(R.id.dialogApiKeyInput)
        val cancelButton = dialog.findViewById<Button>(R.id.dialogCancelButton)
        val submitButton = dialog.findViewById<Button>(R.id.dialogSubmitButton)

        cancelButton.backgroundTintList = null
        cancelButton.setBackgroundResource(R.drawable.bg_danger_button_states)
        cancelButton.setTextColor(android.graphics.Color.WHITE)

        submitButton.backgroundTintList = null
        submitButton.setBackgroundResource(R.drawable.bg_primary_button)
        submitButton.setTextColor(ContextCompat.getColor(this, R.color.on_primary))

        val resolvedKey = OpenAiConfig.apiKey(this)
        val maskedKey = maskApiKeyForDisplay(resolvedKey)
        if (resolvedKey.isNotBlank()) {
            apiKeyInput.setText(maskedKey)
            apiKeyInput.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && apiKeyInput.text.toString() == maskedKey) {
                    apiKeyInput.setText(resolvedKey)
                    apiKeyInput.setSelection(resolvedKey.length)
                }
            }
            apiKeyInput.setOnClickListener {
                if (apiKeyInput.text.toString() == maskedKey) {
                    apiKeyInput.setText(resolvedKey)
                    apiKeyInput.setSelection(resolvedKey.length)
                }
            }
        }
        cancelButton.setOnClickListener { dialog.dismiss() }
        submitButton.setOnClickListener {
            OpenAiKeyStore.setApiKey(this, apiKeyInput.text.toString())
            Toast.makeText(this, R.string.open_source_api_key_saved, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun maskApiKeyForDisplay(apiKey: String): String {
        if (apiKey.isBlank()) return ""
        val visibleChars = (apiKey.length + 1) / 2
        val hiddenChars = apiKey.length - visibleChars
        return apiKey.take(visibleChars) + "*".repeat(hiddenChars)
    }
}
