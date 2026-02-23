package com.walkietalkie.dictationime.settings

import android.content.Context
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.auth.AuthSessionManager
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object AppProfilesStore {
    private const val PREFS_NAME = "walkie_app_profiles"
    private const val KEY_DEFAULT_PROMPT = "default_prompt"
    private const val KEY_APP_PROFILES_JSON = "app_profiles_json"
    private const val KEY_KNOWN_APPS_JSON = "known_apps_json"
    private const val KEY_APP_LAST_USED_JSON = "app_last_used_json"
    private val client = OkHttpClient()

    data class ProfilesSnapshot(
        val defaultPrompt: String,
        val appPrompts: Map<String, String>,
        val knownAppPackages: Set<String>,
        val appLastUsedAt: Map<String, Long>
    )

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getDefaultPrompt(context: Context): String {
        return prefs(context).getString(KEY_DEFAULT_PROMPT, "").orEmpty()
    }

    fun getAppPrompt(context: Context, packageName: String): String {
        return getCachedProfiles(context).appPrompts[packageName].orEmpty()
    }

    fun getCachedProfiles(context: Context): ProfilesSnapshot {
        val defaultPrompt = getDefaultPrompt(context)
        val promptRaw = prefs(context).getString(KEY_APP_PROFILES_JSON, null).orEmpty()
        val prompts = runCatching {
            val result = mutableMapOf<String, String>()
            val json = JSONObject(promptRaw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key, "").trim()
                if (value.isNotBlank()) {
                    result[key] = value
                }
            }
            result.toMap()
        }.getOrDefault(emptyMap())
        val knownRaw = prefs(context).getString(KEY_KNOWN_APPS_JSON, null).orEmpty()
        val known = runCatching {
            val array = JSONArray(knownRaw)
            buildSet {
                for (i in 0 until array.length()) {
                    val pkg = array.optString(i, "").trim()
                    if (pkg.isNotBlank()) add(pkg)
                }
            }
        }.getOrDefault(emptySet())
        val lastUsedRaw = prefs(context).getString(KEY_APP_LAST_USED_JSON, null).orEmpty()
        val lastUsed = runCatching {
            val result = mutableMapOf<String, Long>()
            val json = JSONObject(lastUsedRaw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optLong(key, 0L)
                if (value > 0L) {
                    result[key] = value
                }
            }
            result.toMap()
        }.getOrDefault(emptyMap())
        return ProfilesSnapshot(
            defaultPrompt = defaultPrompt,
            appPrompts = prompts,
            knownAppPackages = known,
            appLastUsedAt = lastUsed
        )
    }

    suspend fun fetchProfiles(context: Context): ProfilesSnapshot {
        val token = AuthSessionManager.getValidAccessToken(context)
            ?: throw IOException("Login required")
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) throw IOException("Backend URL missing")

        val request = Request.Builder()
            .url("$baseUrl/profiles")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Profiles fetch failed: ${response.code}")
                }
                val json = JSONObject(body)
                val defaultPrompt = json.optString("defaultPrompt", "").trim()
                val profilesArray = json.optJSONArray("appProfiles") ?: JSONArray()
                val appPrompts = mutableMapOf<String, String>()
                val knownPackages = mutableSetOf<String>()
                val appLastUsedAt = mutableMapOf<String, Long>()
                for (i in 0 until profilesArray.length()) {
                    val item = profilesArray.optJSONObject(i) ?: continue
                    val packageName = item.optString("packageName", "").trim()
                    val prompt = item.optString("prompt", "").trim()
                    val lastUsedAt = item.optLong("lastUsedAt", 0L)
                    if (packageName.isNotBlank()) {
                        knownPackages.add(packageName)
                        if (lastUsedAt > 0L) {
                            appLastUsedAt[packageName] = lastUsedAt
                        }
                        if (prompt.isNotBlank()) {
                            appPrompts[packageName] = prompt
                        }
                    }
                }
                val snapshot = ProfilesSnapshot(
                    defaultPrompt = defaultPrompt,
                    appPrompts = appPrompts,
                    knownAppPackages = knownPackages,
                    appLastUsedAt = appLastUsedAt
                )
                cacheProfiles(context, snapshot)
                snapshot
            }
        }
    }

    suspend fun setDefaultPrompt(context: Context, prompt: String) {
        val cleaned = prompt.trim()
        putProfile(context, routePath = "default", prompt = cleaned)
        val cached = getCachedProfiles(context)
        cacheProfiles(
            context,
            ProfilesSnapshot(
                defaultPrompt = cleaned,
                appPrompts = cached.appPrompts,
                knownAppPackages = cached.knownAppPackages,
                appLastUsedAt = cached.appLastUsedAt
            )
        )
    }

    suspend fun setAppPrompt(context: Context, packageName: String, prompt: String) {
        val cleaned = prompt.trim()
        putProfile(
            context,
            routePath = "apps/$packageName",
            prompt = cleaned
        )
        val cached = getCachedProfiles(context)
        val next = cached.appPrompts.toMutableMap()
        if (cleaned.isBlank()) {
            next.remove(packageName)
        } else {
            next[packageName] = cleaned
        }
        cacheProfiles(
            context,
            ProfilesSnapshot(
                defaultPrompt = cached.defaultPrompt,
                appPrompts = next,
                knownAppPackages = cached.knownAppPackages + packageName,
                appLastUsedAt = cached.appLastUsedAt
            )
        )
    }

    suspend fun registerDiscoveredApps(context: Context, packageNames: List<String>) {
        val cleaned = packageNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleaned.isEmpty()) return
        cacheKnownAppsLocal(context, cleaned)

        val token = AuthSessionManager.getValidAccessToken(context)
            ?: throw IOException("Login required")
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) throw IOException("Backend URL missing")

        val requestBody = JSONObject()
            .put("packageNames", JSONArray(cleaned))
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/profiles/apps/discovered")
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Profile discovery sync failed: ${response.code}")
                }
            }
        }
    }

    fun cacheKnownAppsLocal(context: Context, packageNames: Collection<String>) {
        val cleaned = packageNames.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (cleaned.isEmpty()) return
        val cached = getCachedProfiles(context)
        val mergedKnown = (cached.knownAppPackages + cleaned).toSet()
        cacheProfiles(
            context,
            ProfilesSnapshot(
                defaultPrompt = cached.defaultPrompt,
                appPrompts = cached.appPrompts,
                knownAppPackages = mergedKnown,
                appLastUsedAt = cached.appLastUsedAt
            )
        )
    }

    suspend fun touchAppProfile(context: Context, packageName: String, touchedAtMs: Long = System.currentTimeMillis()) {
        val cleaned = packageName.trim()
        if (cleaned.isBlank()) return
        cacheKnownAppsLocal(context, listOf(cleaned))
        val cached = getCachedProfiles(context)
        cacheProfiles(
            context,
            ProfilesSnapshot(
                defaultPrompt = cached.defaultPrompt,
                appPrompts = cached.appPrompts,
                knownAppPackages = cached.knownAppPackages + cleaned,
                appLastUsedAt = cached.appLastUsedAt + (cleaned to touchedAtMs)
            )
        )

        val token = AuthSessionManager.getValidAccessToken(context)
            ?: throw IOException("Login required")
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) throw IOException("Backend URL missing")
        val request = Request.Builder()
            .url("$baseUrl/profiles/apps/$cleaned/touch")
            .header("Authorization", "Bearer $token")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Profile touch failed: ${response.code}")
                }
            }
        }
    }

    private suspend fun putProfile(context: Context, routePath: String, prompt: String) {
        val token = AuthSessionManager.getValidAccessToken(context)
            ?: throw IOException("Login required")
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) throw IOException("Backend URL missing")

        val requestBody = JSONObject()
            .put("prompt", prompt)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/profiles/$routePath")
            .header("Authorization", "Bearer $token")
            .put(requestBody)
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Profiles update failed: ${response.code}")
                }
            }
        }
    }

    private fun cacheProfiles(context: Context, snapshot: ProfilesSnapshot) {
        val profilesJson = JSONObject().apply {
            snapshot.appPrompts.forEach { (packageName, prompt) ->
                put(packageName, prompt)
            }
        }
        val knownApps = JSONArray().apply {
            snapshot.knownAppPackages.sorted().forEach { packageName ->
                put(packageName)
            }
        }
        val lastUsedJson = JSONObject().apply {
            snapshot.appLastUsedAt.forEach { (packageName, ts) ->
                put(packageName, ts)
            }
        }
        prefs(context).edit()
            .putString(KEY_DEFAULT_PROMPT, snapshot.defaultPrompt)
            .putString(KEY_APP_PROFILES_JSON, profilesJson.toString())
            .putString(KEY_KNOWN_APPS_JSON, knownApps.toString())
            .putString(KEY_APP_LAST_USED_JSON, lastUsedJson.toString())
            .apply()
    }
}
