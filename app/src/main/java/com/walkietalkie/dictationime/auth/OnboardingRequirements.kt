package com.walkietalkie.dictationime.auth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

object OnboardingRequirements {
    fun isMicrophoneGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun isKeyboardEnabled(context: Context): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        return imm.enabledInputMethodList.any { it.packageName == context.packageName }
    }

    fun missingStep(context: Context): String? {
        if (!isMicrophoneGranted(context)) return OnboardingActivity.STEP_MICROPHONE
        if (!isKeyboardEnabled(context)) return OnboardingActivity.STEP_KEYBOARD
        return null
    }
}
