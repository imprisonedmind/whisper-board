# Button Styling Guardrails (Walkie Talkie App)

Use this file whenever adding or editing buttons in `walkie-talkie/app`.

## Canonical Styles

- Primary action button style:
  - `@style/Widget.DictationIme.PrimaryButton`
  - Background: `@drawable/bg_primary_button`
  - Text color: `@color/on_primary`
  - For `AppCompatButton`, explicitly set both:
    - `android:backgroundTint="@null"`
    - `app:backgroundTint="@null"`

- Danger/destructive button style:
  - `@style/Widget.DictationIme.DangerButton`
  - Background: `@drawable/bg_danger_button_states`
  - Text color: white via `@color/text_on_danger_button` selector
  - For `AppCompatButton`, explicitly set both:
    - `android:backgroundTint="@null"`
    - `app:backgroundTint="@null"`
  - Do not override danger text color ad hoc

## Required Rules

1. For destructive actions (`Delete`, `Cancel deletion`, `Turn off`, etc.), use `Widget.DictationIme.DangerButton` directly in XML.
2. For dialog `AppCompatButton`s, include explicit background/tint attrs in XML even when style is set:
   - `android:background="@drawable/bg_danger_button_states"` (or primary drawable)
   - `android:backgroundTint="@null"`
   - `app:backgroundTint="@null"`
3. Do not declare destructive buttons as `PrimaryButton` and then patch with runtime overrides.
3. In dialogs, prefer `Button` over `AppCompatButton` for destructive actions if tint keeps overriding danger background.
4. If dialog tint still overrides despite XML attrs, apply runtime fallback before `show()`:
   - `button.backgroundTintList = null`
   - `button.setBackgroundResource(R.drawable.bg_danger_button_states)`
   - `button.setTextColor(Color.WHITE)`
5. Do not override destructive text color per-button unless applying the dialog fallback above.
6. Keep style ownership in XML by default; runtime re-skinning is fallback-only for dialog tint issues.

## Known Pitfall

- Wrong pattern (do not do by default):
  - `style="@style/Widget.DictationIme.PrimaryButton"` + `android:background="@drawable/bg_danger_button_states"` + custom text color
- Why this fails:
  - It drifts from the app-wide danger style and can mismatch typography/state behavior.
- Also fails in dialogs when support tint is left active:
  - If `app:backgroundTint` is not explicitly null on `AppCompatButton`, danger backgrounds can render as primary/accent color.

## Quick Verification Checklist

1. Build and install:
   - `GRADLE_USER_HOME=.gradle-local ./gradlew :app:assembleDebug` (inside `walkie-talkie/`)
   - `adb install -r walkie-talkie/app/build/outputs/apk/debug/app-debug.apk`
2. Open changed UI and capture proof screenshot:
   - Prefer direct capture (avoids truncated PNG pulls):
     - `adb exec-out screencap -p > /tmp/data-privacy-latest.png`
3. Visually compare destructive button with an existing known-good danger button on device.
