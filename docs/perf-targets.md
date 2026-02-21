# Performance Targets

## Device Baseline
- Android 10+
- Mid-range CPU class
- Stable Wi-Fi or strong LTE connection

## Startup + Runtime Goals
- End-to-end latency (mic release -> commit):
  - p50 <= 3.0s for a 3-second utterance on Wi-Fi
  - p90 <= 6.0s for a 3-second utterance on Wi-Fi
- API error rate: <= 2% over 50 consecutive dictations
- Memory peak during transcribe: <= 120MB

## Measurement Notes
- Latency measured from mic release to `commitText` call.
- Run at least 10 short dictations in a quiet environment.
- Log median and p90 for warm runs after the first successful request.

## Guardrails
- Audio remains in memory only until upload.
- Any API error should fail quickly and return to recoverable idle state.
