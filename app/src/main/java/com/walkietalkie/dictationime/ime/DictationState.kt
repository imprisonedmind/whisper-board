package com.walkietalkie.dictationime.ime

enum class DictationError {
    PermissionDenied,
    ConfigurationMissing,
    AudioCaptureFailed,
    TranscriptionFailed,
    TooShort,
    NoSpeechDetected,
    Unknown
}

sealed class DictationState {
    data object Idle : DictationState()
    data object Recording : DictationState()
    data object Transcribing : DictationState()
    data class Error(val reason: DictationError) : DictationState()
}
