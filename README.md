# Walkie Talkie Keyboard

Android dictation IME scaffold that uses the OpenAI Whisper API for transcription.

## What is implemented
- Native Kotlin IME service with hold-to-talk flow.
- In-memory PCM capture (`AudioRecord` @ 16k mono PCM16).
- Replaceable interfaces for model manager, capture, and ASR.
- Settings activity for permissions, IME settings, and API status.
- Unit test coverage for dictation state flow and commit behavior.

## Screenshot
Screenshots of the keyboard UI:

![Walkie Talkie Keyboard](docs/images/hero_image.jpg)

## Important setup notes
1. `gradle.properties` (not checked in):
   - Set `APP_MODE=dev` or `APP_MODE=prod`.
   - In `prod`, set `BACKEND_BASE_URL=...` here.
2. `gradle.properties.dev` (checked in):
   - Contains dev/open-source defaults such as `OPENAI_API_KEY`.
   - Loaded automatically when `APP_MODE` is `dev/open_source/oss`.
3. Build-time override precedence:
   - Environment variables
   - Gradle `-P...` properties
   - `gradle.properties.dev` (dev/open-source only)

## Main paths
- `app/src/main/java/com/walkietalkie/dictationime/ime/DictationImeService.kt`
- `app/src/main/java/com/walkietalkie/dictationime/ime/DictationController.kt`
- `app/src/main/java/com/walkietalkie/dictationime/asr/WhisperApiRecognizer.kt`
- `docs/architecture.md`
