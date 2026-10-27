# Roadmap

## v1 (milestones M1–M5)

These are tracked in `PROGRESS.md`.

## After v1

- **Tier 2 via Shizuku.** Elevated actions such as Wi-Fi and Bluetooth toggles, and `WRITE_SECURE_SETTINGS`-based settings, offered as an opt-in add-on.
- **More triggers.** Calendar events, location / geofence, SMS and call triggers (note Android 17's three-hour OTP SMS hold), NFC tags.
- **Tasker / Locale plugin compatibility**, which opens up hundreds of existing third-party plugins.
- **Camera actions**, only where Android allows them with the app in the foreground.

## Play Store readiness

- A notification-listener permission declaration and justification in Play Console.
- A privacy policy, and a Data safety form ("no data collected or shared", with the user-configured HTTP action explained).
- Restricted permissions: SMS and call log are allowed only for default handlers or approved exceptions. Messaging blocks may stay sideload-only.
- `USE_FULL_SCREEN_INTENT` policy (calling and alarm apps). Expect the fallback notification on Play builds.
- `SCHEDULE_EXACT_ALARM` justification. `USE_EXACT_ALARM` is not eligible.
- Foreground service type declarations, with a video of each use.
- Check Play's current target-API requirement (see PROGRESS D7).
