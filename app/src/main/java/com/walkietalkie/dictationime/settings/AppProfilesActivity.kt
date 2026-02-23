package com.walkietalkie.dictationime.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

    private fun loadApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.Default) {
                val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolved = packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                toUniquePackageList(resolved, packageManager)
            }

            renderApps(apps)
        }
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

        if (apps.isEmpty()) {
            appsEmptyText.text = getString(R.string.app_profiles_empty)
            return
        }

        appsEmptyText.text = getString(R.string.app_profiles_count, apps.size)
        val inflater = LayoutInflater.from(this)
        apps.forEach { app ->
            val row = inflater.inflate(R.layout.item_app_profile_app, appsListContainer, false)
            row.findViewById<android.widget.ImageView>(R.id.appIconView).setImageDrawable(app.icon)
            row.findViewById<TextView>(R.id.appNameView).text = app.label
            row.findViewById<TextView>(R.id.appPackageView).text = app.packageName
            appsListContainer.addView(row)
        }
    }
}
