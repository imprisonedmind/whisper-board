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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class AppProfilesActivity : AppCompatActivity() {
    private lateinit var appsListContainer: LinearLayout
    private lateinit var appsEmptyText: TextView
    private var appPromptDialog: Dialog? = null
    private var loadedApps: List<AppListItem> = emptyList()
    private var installedApps: List<AppListItem> = emptyList()
    private var defaultPrompt: String = ""
    private var appPrompts: MutableMap<String, String> = mutableMapOf()
    private var appLastUsedAt: MutableMap<String, Long> = mutableMapOf()

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
            showPromptDialog(
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
                showPromptDialog(
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
}
