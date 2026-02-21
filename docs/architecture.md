# Architecture

## Overview
Walkie Talkie Keyboard is a native Android Input Method Editor (IME) focused on dictation
via the OpenAI Whisper API.

Core flow:
1. User hold-presses mic in `KeyboardView`.
2. `DictationImeService` invokes `DictationController.startRecording()`.
3. `AndroidAudioCapture` captures PCM16 mono 16kHz audio in memory.
4. On release, `DictationController.stopAndTranscribe()` runs `WhisperApiRecognizer.transcribe()`.
5. Recognized text is committed with `InputConnection.commitText()`.

## Modules
- `ime/`: IME service, UI view, controller, dictation state.
- `audio/`: capture abstraction and Android implementation.
- `model/`: model selection + API readiness checks.
- `asr/`: recognizer contract and API-backed implementation.

## Privacy
- No raw audio is written to disk.
- No transcript history is persisted.
- Audio is sent to OpenAI for transcription over the network.

## Replaceability
`SpeechRecognizer`, `AudioCapture`, and `ModelManager` are stable contracts. Any alternative recognizer/runtime can be swapped by replacing `WhisperApiRecognizer` without changing IME logic.
