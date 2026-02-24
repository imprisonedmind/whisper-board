package com.walkietalkie.dictationime.settings

import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
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
import com.walkietalkie.dictationime.auth.LoginEmailActivity
import com.walkietalkie.dictationime.model.DEFAULT_MODEL_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class AppProfilesActivity : AppCompatActivity() {
    private lateinit var appsListContainer: LinearLayout
    private lateinit var appsEmptyText: TextView
    private var appPromptDialog: Dialog? = null
    private var profileModeDialog: Dialog? = null
    private var loadedApps: List<AppListItem> = emptyList()
    private var installedApps: List<AppListItem> = emptyList()
    private var defaultPrompt: String = ""
    private var appPrompts: MutableMap<String, String> = mutableMapOf()
    private var appLastUsedAt: MutableMap<String, Long> = mutableMapOf()

    private enum class RecommendedPromptMode {
        WhisperExample,
        Instructional
    }

    private data class AppListItem(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthStore.isSignedIn(this)) {
            startActivity(Intent(this, LoginEmailActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_app_profiles)

        findViewById<ImageButton>(R.id.appProfilesBackButton).setOnClickListener { finish() }
        appsListContainer = findViewById(R.id.appProfilesListContainer)
        appsEmptyText = findViewById(R.id.appProfilesEmptyText)

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        if (loadedApps.isNotEmpty()) {
            loadApps()
        }
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val cachedProfiles = AppProfilesStore.getCachedProfiles(this@AppProfilesActivity)
            defaultPrompt = cachedProfiles.defaultPrompt
            appPrompts = cachedProfiles.appPrompts.toMutableMap()
            appLastUsedAt = cachedProfiles.appLastUsedAt.toMutableMap()
            loadedApps = mergeKnownAndInstalledApps(emptyList(), cachedProfiles.knownAppPackages)
            renderApps(loadedApps)

            val apps = queryLauncherApps()

            installedApps = apps
            AppProfilesStore.cacheKnownAppsLocal(
                this@AppProfilesActivity,
                installedApps.map { it.packageName }
            )
            val localMerged = AppProfilesStore.getCachedProfiles(this@AppProfilesActivity)
            loadedApps = mergeKnownAndInstalledApps(installedApps, localMerged.knownAppPackages)
            renderApps(loadedApps)

            runCatching {
                AppProfilesStore.registerDiscoveredApps(
                    this@AppProfilesActivity,
                    localMerged.knownAppPackages.toList()
                )
            }

            runCatching {
                AppProfilesStore.fetchProfiles(this@AppProfilesActivity)
            }.onSuccess { remote ->
                defaultPrompt = remote.defaultPrompt
                appPrompts = remote.appPrompts.toMutableMap()
                appLastUsedAt = remote.appLastUsedAt.toMutableMap()
                val latestLocalKnown = AppProfilesStore
                    .getCachedProfiles(this@AppProfilesActivity)
                    .knownAppPackages
                val mergedKnown = latestLocalKnown + remote.knownAppPackages + installedApps.map { it.packageName }
                AppProfilesStore.cacheKnownAppsLocal(this@AppProfilesActivity, mergedKnown)
                loadedApps = mergeKnownAndInstalledApps(installedApps, mergedKnown)
                renderApps(loadedApps)
            }.onFailure {
                Toast.makeText(
                    this@AppProfilesActivity,
                    R.string.app_profiles_sync_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun queryLauncherApps(): List<AppListItem> = withContext(Dispatchers.Default) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        toUniquePackageList(resolved, packageManager)
    }

    private fun mergeKnownAndInstalledApps(
        installed: List<AppListItem>,
        knownPackages: Set<String>
    ): List<AppListItem> {
        val byPackage = linkedMapOf<String, AppListItem>()
        installed.forEach { item ->
            byPackage[item.packageName] = item
        }
        knownPackages.forEach { packageName ->
            if (!byPackage.containsKey(packageName)) {
                byPackage[packageName] = AppListItem(
                    packageName = packageName,
                    label = packageName,
                    icon = ContextCompat.getDrawable(this, R.drawable.ic_briefcase)
                )
            }
        }
        return byPackage.values.sortedBy { it.label.lowercase(Locale.US) }
    }

    private fun toUniquePackageList(
        resolved: List<ResolveInfo>,
        packageManager: PackageManager
    ): List<AppListItem> {
        val byPackage = linkedMapOf<String, ResolveInfo>()
        resolved.forEach { info ->
            val packageName = info.activityInfo?.packageName ?: return@forEach
            if (packageName == this.packageName) return@forEach
            if (!byPackage.containsKey(packageName)) {
                byPackage[packageName] = info
            }
        }

        return byPackage.map { (packageName, info) ->
            val rawLabel = info.loadLabel(packageManager).toString().trim()
            val label = if (rawLabel.isNotBlank()) rawLabel else packageName
            AppListItem(
                packageName = packageName,
                label = label,
                icon = info.loadIcon(packageManager)
            )
        }.sortedBy { it.label.lowercase(Locale.US) }
    }

    private fun renderApps(apps: List<AppListItem>) {
        appsListContainer.removeAllViews()

        val inflater = LayoutInflater.from(this)
        val defaultRow = inflater.inflate(R.layout.item_app_profile_app, appsListContainer, false)
        defaultRow.findViewById<android.widget.ImageView>(R.id.appIconView)
            .setImageResource(R.drawable.ic_sparkle)
        defaultRow.findViewById<TextView>(R.id.appNameView).text = getString(R.string.app_profiles_default_name)
        defaultRow.findViewById<TextView>(R.id.appPackageView).text =
            getString(R.string.app_profiles_default_subtitle)
        val defaultEnabled = defaultPrompt.isNotBlank()
        defaultRow.findViewById<TextView>(R.id.appProfileStatusView).text =
            getString(if (defaultEnabled) R.string.app_profile_status_enabled else R.string.app_profile_status_disabled)
        defaultRow.setOnClickListener {
            showProfileModeDialog(
                packageName = null,
                profileName = getString(R.string.app_profiles_default_name),
                existingPrompt = defaultPrompt
            )
        }
        appsListContainer.addView(defaultRow)

        appsEmptyText.text = if (apps.isEmpty()) {
            getString(R.string.app_profiles_empty)
        } else {
            getString(R.string.app_profiles_count, apps.size)
        }

        val sortedApps = apps.sortedWith(
            compareByDescending<AppListItem> { appLastUsedAt[it.packageName] ?: 0L }
                .thenBy { it.label.lowercase(Locale.US) }
        )

        sortedApps.forEach { app ->
            val row = inflater.inflate(R.layout.item_app_profile_app, appsListContainer, false)
            row.findViewById<android.widget.ImageView>(R.id.appIconView).setImageDrawable(app.icon)
            row.findViewById<TextView>(R.id.appNameView).text = app.label
            row.findViewById<TextView>(R.id.appPackageView).text = app.packageName
            val enabled = appPrompts[app.packageName].isNullOrBlank().not()
            row.findViewById<TextView>(R.id.appProfileStatusView).text =
                getString(if (enabled) R.string.app_profile_status_enabled else R.string.app_profile_status_disabled)
            row.setOnClickListener {
                showProfileModeDialog(
                    packageName = app.packageName,
                    profileName = app.label,
                    existingPrompt = appPrompts[app.packageName].orEmpty()
                )
            }
            appsListContainer.addView(row)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appPromptDialog?.dismiss()
        appPromptDialog = null
        profileModeDialog?.dismiss()
        profileModeDialog = null
    }

    private fun showProfileModeDialog(packageName: String?, profileName: String, existingPrompt: String) {
        profileModeDialog?.dismiss()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_profile_mode_picker)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<TextView>(R.id.dialogModeTitle).text =
            getString(R.string.app_profiles_mode_title_format, profileName)

        dialog.findViewById<Button>(R.id.dialogModeCustomButton).setOnClickListener {
            dialog.dismiss()
            showPromptDialog(packageName, profileName, existingPrompt)
        }
        dialog.findViewById<Button>(R.id.dialogModeRecommendedButton).setOnClickListener {
            dialog.dismiss()
            val modelId = currentModelId()
            val mode = recommendedPromptModeForModel(modelId)
            val recommended = buildRecommendedPrompt(packageName, profileName, mode)
            showPromptDialog(packageName, profileName, recommended)
        }

        dialog.setOnDismissListener {
            if (profileModeDialog === dialog) profileModeDialog = null
        }
        profileModeDialog = dialog
        dialog.show()
    }

    private fun showPromptDialog(packageName: String?, profileName: String, existingPrompt: String) {
        appPromptDialog?.dismiss()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_app_profile_prompt)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val title = dialog.findViewById<TextView>(R.id.dialogTitle)
        val input = dialog.findViewById<EditText>(R.id.dialogPromptInput)
        val cancelButton = dialog.findViewById<Button>(R.id.dialogCancelButton)
        val submitButton = dialog.findViewById<Button>(R.id.dialogSubmitButton)

        title.text = getString(R.string.app_profiles_dialog_title_format, profileName)
        input.setText(existingPrompt)

        cancelButton.backgroundTintList = null
        cancelButton.setBackgroundResource(R.drawable.bg_danger_button_states)
        cancelButton.setTextColor(android.graphics.Color.WHITE)

        submitButton.backgroundTintList = null
        submitButton.setBackgroundResource(R.drawable.bg_primary_button)
        submitButton.setTextColor(ContextCompat.getColor(this, R.color.on_primary))

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        submitButton.setOnClickListener {
            val prompt = input.text?.toString()?.trim().orEmpty()
            submitButton.isEnabled = false
            cancelButton.isEnabled = false
            lifecycleScope.launch {
                runCatching {
                    if (packageName == null) {
                        AppProfilesStore.setDefaultPrompt(this@AppProfilesActivity, prompt)
                        defaultPrompt = prompt
                    } else {
                        AppProfilesStore.setAppPrompt(this@AppProfilesActivity, packageName, prompt)
                        if (prompt.isBlank()) {
                            appPrompts.remove(packageName)
                        } else {
                            appPrompts[packageName] = prompt
                        }
                    }
                }.onSuccess {
                    dialog.dismiss()
                    renderApps(loadedApps)
                }.onFailure {
                    submitButton.isEnabled = true
                    cancelButton.isEnabled = true
                    Toast.makeText(
                        this@AppProfilesActivity,
                        R.string.app_profiles_save_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        dialog.setOnDismissListener {
            if (appPromptDialog === dialog) appPromptDialog = null
        }
        appPromptDialog = dialog
        dialog.show()
    }

    private fun currentModelId(): String {
        return DEFAULT_MODEL_ID
    }

    private fun recommendedPromptModeForModel(modelId: String): RecommendedPromptMode {
        val normalized = modelId.trim().lowercase(Locale.US)
        return if (normalized.startsWith("whisper")) {
            RecommendedPromptMode.WhisperExample
        } else {
            RecommendedPromptMode.Instructional
        }
    }

    private fun buildRecommendedPrompt(
        packageName: String?,
        profileName: String,
        mode: RecommendedPromptMode
    ): String {
        return when (mode) {
            RecommendedPromptMode.WhisperExample ->
                buildWhisperRecommendedPrompt(packageName, profileName)
            RecommendedPromptMode.Instructional ->
                buildInstructionalRecommendedPrompt(packageName, profileName)
        }
    }

    private fun buildWhisperRecommendedPrompt(packageName: String?, profileName: String): String {
        if (packageName == null) {
            return "Hi team, quick update: I finished the draft and will share the final version by 3:00 PM. Please review and send feedback."
        }
        val key = packageName.lowercase(Locale.US)
        return when {
            key.contains("whatsapp") || key.contains("telegram") || key.contains("messages") ->
                "hey lol that was wild 😭 i cant believe it happened today. wanna chat later?"
            key.contains("slack") || key.contains("teams") || key.contains("discord") ->
                "Quick update: shipped auth fix, tests are green, and PR is ready for review."
            key.contains("gmail") || key.contains("outlook") ->
                "Hi Sarah,\n\nThanks for your message. I have attached the revised proposal and timeline.\n\nBest regards,\nLuke"
            key.contains("notion") || key.contains("docs") || key.contains("keep") ->
                "Meeting notes:\n- finalize onboarding flow\n- add analytics event for signup\n- QA by Thursday"
            key.contains("instagram") || key.contains("twitter") || key.contains("x") || key.contains("linkedin") ->
                "Shipping a cleaner voice-to-text workflow today. Faster edits, better accuracy, and less friction."
            else ->
                "Draft for $profileName: clear wording, natural sentence flow, and minimal filler words."
        }
    }

    private fun buildInstructionalRecommendedPrompt(packageName: String?, profileName: String): String {
        if (packageName == null) {
            return "Transcribe clearly with proper punctuation and concise wording. Keep a neutral professional tone and avoid emojis."
        }
        val key = packageName.lowercase(Locale.US)
        return when {
            key.contains("whatsapp") || key.contains("telegram") || key.contains("messages") ->
                "Transcribe as casual chat text: short sentences, lowercase, and natural texting style with light emoji use when relevant."
            key.contains("slack") || key.contains("teams") || key.contains("discord") ->
                "Transcribe for team chat: concise, readable, and action-oriented. Preserve technical terms and keep formatting clear."
            key.contains("gmail") || key.contains("outlook") ->
                "Transcribe as a polished email draft with correct punctuation, sentence casing, and clear paragraph breaks."
            key.contains("notion") || key.contains("docs") || key.contains("keep") ->
                "Transcribe as structured notes with clean sentence boundaries. Preserve headings, list items, and tasks if spoken."
            key.contains("instagram") || key.contains("twitter") || key.contains("x") || key.contains("linkedin") ->
                "Transcribe in a social post style: concise, engaging, and clear. Keep tone natural and avoid overlong sentences."
            else ->
                "Transcribe for $profileName with clear wording and natural tone. Keep punctuation correct and avoid filler words."
        }
    }
}
