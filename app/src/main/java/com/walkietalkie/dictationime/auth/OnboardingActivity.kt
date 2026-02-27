package com.walkietalkie.dictationime.auth

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.model.TranscriptionModels
import com.walkietalkie.dictationime.settings.TranscriptionModelStore
import com.walkietalkie.dictationime.settings.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale

class OnboardingActivity : AppCompatActivity() {
    private enum class Step { COUNTRY, MODEL, MICROPHONE, KEYBOARD }

    private data class CountryOption(
        val code: String,
        val name: String
    )

    private val httpClient = OkHttpClient()
    private val countryOptions by lazy {
        Locale.getISOCountries()
            .map { code ->
                val locale = Locale("", code)
                CountryOption(
                    code = code.uppercase(Locale.US),
                    name = locale.getDisplayCountry(Locale.getDefault()).ifBlank { code }
                )
            }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private lateinit var stepCount: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var countryRow: View
    private lateinit var countryValue: TextView
    private lateinit var modelCard: View
    private lateinit var modelListContainer: LinearLayout
    private lateinit var primaryButton: Button
    private lateinit var secondaryButton: Button
    private lateinit var progress: View

    private var step = Step.COUNTRY
    private var selectedCountryCode: String? = null
    private var selectedModelId: String = ""

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            renderStep()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthStore.isSignedIn(this)) {
            startActivity(Intent(this, LoginEmailActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_onboarding)

        stepCount = findViewById(R.id.onboardingStepCount)
        title = findViewById(R.id.onboardingTitle)
        body = findViewById(R.id.onboardingBody)
        countryRow = findViewById(R.id.countryPickerRow)
        countryValue = findViewById(R.id.countryPickerValue)
        modelCard = findViewById(R.id.onboardingModelCard)
        modelListContainer = findViewById(R.id.onboardingModelListContainer)
        primaryButton = findViewById(R.id.onboardingPrimaryButton)
        secondaryButton = findViewById(R.id.onboardingSecondaryButton)
        progress = findViewById(R.id.onboardingProgress)

        selectedCountryCode = inferDefaultCountry()
        selectedModelId = TranscriptionModelStore.getCached(this)
        step = parseStepExtra(intent.getStringExtra(EXTRA_START_STEP))
        countryRow.setOnClickListener { showCountryPicker() }
        primaryButton.setOnClickListener { onPrimaryTapped() }
        secondaryButton.setOnClickListener { onSecondaryTapped() }
        lifecycleScope.launch {
            runCatching {
                TranscriptionModelStore.fetchRemote(this@OnboardingActivity)
            }.onSuccess { modelId ->
                selectedModelId = modelId
                if (step == Step.MODEL) {
                    renderModelOptions()
                }
            }
        }

        renderStep()
    }

    override fun onResume() {
        super.onResume()
        if (step != Step.COUNTRY) {
            renderStep()
        }
    }

    private fun renderStep() {
        when (step) {
            Step.COUNTRY -> {
                stepCount.text = getString(R.string.onboarding_step_count, 1, 4)
                title.text = getString(R.string.onboarding_country_title)
                body.text = getString(R.string.onboarding_country_body)
                countryRow.visibility = View.VISIBLE
                modelCard.visibility = View.GONE
                countryValue.text = selectedCountryCode ?: getString(R.string.onboarding_country_select)
                primaryButton.text = getString(R.string.onboarding_continue)
                secondaryButton.text = getString(R.string.onboarding_country_skip)
            }
            Step.MODEL -> {
                stepCount.text = getString(R.string.onboarding_step_count, 2, 4)
                title.text = getString(R.string.onboarding_model_title)
                body.text = getString(R.string.onboarding_model_body)
                countryRow.visibility = View.GONE
                modelCard.visibility = View.VISIBLE
                renderModelOptions()
                primaryButton.text = getString(R.string.onboarding_continue)
                secondaryButton.text = getString(R.string.onboarding_model_keep_current)
            }
            Step.MICROPHONE -> {
                val micGranted = OnboardingRequirements.isMicrophoneGranted(this)
                stepCount.text = getString(R.string.onboarding_step_count, 3, 4)
                title.text = getString(R.string.onboarding_mic_title)
                body.text = getString(R.string.onboarding_mic_body)
                countryRow.visibility = View.GONE
                modelCard.visibility = View.GONE
                primaryButton.text = if (micGranted) {
                    getString(R.string.onboarding_continue)
                } else {
                    getString(R.string.grant_mic_permission)
                }
                secondaryButton.text = getString(R.string.open_app_settings)
            }
            Step.KEYBOARD -> {
                val enabled = OnboardingRequirements.isKeyboardEnabled(this)
                stepCount.text = getString(R.string.onboarding_step_count, 4, 4)
                title.text = getString(R.string.onboarding_keyboard_title)
                body.text = getString(R.string.onboarding_keyboard_body)
                countryRow.visibility = View.GONE
                modelCard.visibility = View.GONE
                primaryButton.text = if (enabled) {
                    getString(R.string.onboarding_finish)
                } else {
                    getString(R.string.open_input_method_settings)
                }
                secondaryButton.text = getString(R.string.onboarding_keyboard_refresh)
            }
        }
    }

    private fun onPrimaryTapped() {
        when (step) {
            Step.COUNTRY -> submitCountryAndContinue()
            Step.MODEL -> {
                step = Step.MICROPHONE
                renderStep()
            }
            Step.MICROPHONE -> {
                if (OnboardingRequirements.isMicrophoneGranted(this)) {
                    step = Step.KEYBOARD
                    renderStep()
                } else {
                    requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            Step.KEYBOARD -> {
                if (OnboardingRequirements.isKeyboardEnabled(this)) {
                    completeOnboarding()
                } else {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
            }
        }
    }

    private fun onSecondaryTapped() {
        when (step) {
            Step.COUNTRY -> skipCountryAndContinue()
            Step.MODEL -> {
                step = Step.MICROPHONE
                renderStep()
            }
            Step.MICROPHONE -> {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                })
            }
            Step.KEYBOARD -> renderStep()
        }
    }

    private fun showCountryPicker() {
        val labels = countryOptions.map { "${it.name} (${it.code})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.onboarding_country_picker_title)
            .setItems(labels) { _, index ->
                selectedCountryCode = countryOptions[index].code
                countryValue.text = selectedCountryCode
            }
            .show()
    }

    private fun submitCountryAndContinue() {
        val code = selectedCountryCode ?: inferDefaultCountry()
        if (code == null) {
            Toast.makeText(this, R.string.onboarding_country_select, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            setLoading(true)
            val ok = updateOnboardingStatus(countryCode = code, countrySkipped = false, completed = null)
            setLoading(false)
            if (ok) {
                step = Step.MODEL
                renderStep()
            }
        }
    }

    private fun skipCountryAndContinue() {
        lifecycleScope.launch {
            setLoading(true)
            val ok = updateOnboardingStatus(countryCode = null, countrySkipped = true, completed = null)
            setLoading(false)
            if (ok) {
                step = Step.MODEL
                renderStep()
            }
        }
    }

    private fun renderModelOptions() {
        modelListContainer.removeAllViews()
        val inflater = layoutInflater
        TranscriptionModels.options.forEachIndexed { index, option ->
            val row = inflater.inflate(R.layout.item_model_option, modelListContainer, false)
            row.findViewById<TextView>(R.id.modelOptionTitle).text = option.label
            row.findViewById<TextView>(R.id.modelOptionSubtitle).text = option.subtitle
            val selected = option.id == selectedModelId
            row.findViewById<TextView>(R.id.modelOptionStatus).text = getString(
                if (selected) R.string.model_option_selected else R.string.model_option_available
            )
            row.setOnClickListener {
                if (selectedModelId == option.id) return@setOnClickListener
                val previous = selectedModelId
                selectedModelId = option.id
                renderModelOptions()
                lifecycleScope.launch {
                    runCatching {
                        TranscriptionModelStore.setSelectedModel(this@OnboardingActivity, option.id)
                    }.onFailure {
                        selectedModelId = previous
                        renderModelOptions()
                        Toast.makeText(
                            this@OnboardingActivity,
                            R.string.model_selection_save_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            modelListContainer.addView(row)
            if (index < TranscriptionModels.options.lastIndex) {
                modelListContainer.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(R.dimen.onboarding_model_divider_height)
                    )
                    setBackgroundColor(resources.getColor(R.color.home_border_subtle, theme))
                })
            }
        }
    }

    private fun completeOnboarding() {
        if (!OnboardingRequirements.isMicrophoneGranted(this)) {
            Toast.makeText(this, R.string.ime_permission_denied, Toast.LENGTH_SHORT).show()
            step = Step.MICROPHONE
            renderStep()
            return
        }
        if (!OnboardingRequirements.isKeyboardEnabled(this)) {
            Toast.makeText(this, R.string.onboarding_keyboard_required, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            setLoading(true)
            val ok = updateOnboardingStatus(countryCode = null, countrySkipped = null, completed = true)
            setLoading(false)
            if (ok) {
                startActivity(
                    Intent(this@OnboardingActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }
        }
    }

    private fun inferDefaultCountry(): String? {
        val code = Locale.getDefault().country.uppercase(Locale.US)
        if (code.length != 2) return null
        return if (countryOptions.any { it.code == code }) code else null
    }

    private suspend fun updateOnboardingStatus(
        countryCode: String?,
        countrySkipped: Boolean?,
        completed: Boolean?
    ): Boolean {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return false
        val token = AuthSessionManager.getValidAccessToken(this)
        if (token.isNullOrBlank()) {
            AuthStore.clearSession(this)
            startActivity(Intent(this, LoginEmailActivity::class.java))
            finish()
            return false
        }

        return try {
            val payload = JSONObject()
            if (countryCode != null) payload.put("countryCode", countryCode)
            if (countryCode == null && countrySkipped == true) payload.put("countryCode", JSONObject.NULL)
            if (countrySkipped != null) payload.put("countrySkipped", countrySkipped)
            if (completed != null) payload.put("completed", completed)

            val request = Request.Builder()
                .url("$baseUrl/profiles/onboarding")
                .header("Authorization", "Bearer $token")
                .header("content-type", "application/json")
                .put(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Failed onboarding update")
                    }
                }
            }
            true
        } catch (_: Exception) {
            Toast.makeText(this, R.string.onboarding_update_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun setLoading(loading: Boolean) {
        primaryButton.isEnabled = !loading
        secondaryButton.isEnabled = !loading
        countryRow.isEnabled = !loading
        setModelCardEnabled(!loading)
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun setModelCardEnabled(enabled: Boolean) {
        for (index in 0 until modelListContainer.childCount) {
            modelListContainer.getChildAt(index).isEnabled = enabled
        }
        modelCard.isEnabled = enabled
    }

    private fun parseStepExtra(rawStep: String?): Step {
        return when (rawStep) {
            STEP_MODEL -> Step.MODEL
            STEP_MICROPHONE -> Step.MICROPHONE
            STEP_KEYBOARD -> Step.KEYBOARD
            else -> Step.COUNTRY
        }
    }

    companion object {
        const val EXTRA_START_STEP = "extra_start_step"
        const val STEP_COUNTRY = "country"
        const val STEP_MODEL = "model"
        const val STEP_MICROPHONE = "microphone"
        const val STEP_KEYBOARD = "keyboard"
    }
}
