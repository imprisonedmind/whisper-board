# Walkie Talkie Keyboard

Android dictation IME scaffold that uses the OpenAI Whisper API for transcription.

## What is implemented
- Native Kotlin IME service with hold-to-talk flow.
- In-memory PCM capture (`AudioRecord` @ 16k mono PCM16).
- Replaceable interfaces for model manager, capture, and ASR.
- Settings activity for permissions, IME settings, and API status.
- Unit test coverage for dictation state flow and commit behavior.

## Important setup notes
1. Provide an OpenAI API key for builds via a Gradle property:
   - `OPENAI_API_KEY` (required)
   - `OPENAI_BASE_URL` (optional, defaults to `https://api.openai.com/v1`)
2. Example for local development:
   - Add to `~/.gradle/gradle.properties` or pass `-POPENAI_API_KEY=...` when invoking Gradle.

## Main paths
- `app/src/main/java/com/walkietalkie/dictationime/ime/DictationImeService.kt`
- `app/src/main/java/com/walkietalkie/dictationime/ime/DictationController.kt`
- `app/src/main/java/com/walkietalkie/dictationime/asr/WhisperApiRecognizer.kt`
- `docs/architecture.md`
