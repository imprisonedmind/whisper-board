package com.walkietalkie.dictationime.config

import com.walkietalkie.dictationime.BuildConfig

object AppModeConfig {
    val appMode: String
        get() = BuildConfig.APP_MODE.trim().lowercase()

    val isOpenSourceMode: Boolean
        get() = appMode == "dev" || appMode == "open_source" || appMode == "oss"

    val backendFeaturesEnabled: Boolean
        get() = BuildConfig.ENABLE_BACKEND_FEATURES

    val isAuthRequired: Boolean
        get() = BuildConfig.REQUIRE_AUTH
}
