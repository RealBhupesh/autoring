# Android platform constraints for an automation app (compileSdk 37 / targetSdk 37, minSdk 26)

Researched 2026-10-27 from developer.android.com (fetched live), AOSP source on android.googlesource.com (main branch), and web search for Play policy.
**support.google.com, source.android.com and developers.google.com could not be reached from this sandbox (DNS failure)**, so Play-policy statements below come from search-result snippets. Treat them as "verify in Play Console".

Legend: [V] verified on an official page fetched today · [AOSP] verified in AOSP source, not documented · [U] unverified / secondary source.

---

## 1. Foreground services (FGS)

### Types relevant to this app
| Type | Manifest perm | Prereq | Time limit | Notes |
|---|---|---|---|---|
| `mediaPlayback` | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | none | none | "Continue audio or video playback from the background." Cannot be started from `BOOT_COMPLETED` (target 35+). [V] |
| `shortService` | only `FOREGROUND_SERVICE` | none | ~3 min from `startForeground()`; `onTimeout(int)` then ANR if not stopped. Can't be sticky, can't start other FGS. Ignored if combined with other types. [V] | **Android 17: does NOT count as a valid state for background audio** (see §9). |
| `specialUse` | `FOREGROUND_SERVICE_SPECIAL_USE` | none | none | Must add `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="..."/>`; reviewed in Play Console. [V] |
| `systemExempted` | `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | app must be one of: demo mode, DO/PO, `ROLE_EMERGENCY`, device admin, **holds `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`**, VPN app; else `ForegroundServiceTypeNotAllowedException` | none | [V] Play-policy acceptability for a non-alarm automation app [U] — the doc says "Reserved for system applications and specific system integrations". |
| `dataSync` | `FOREGROUND_SERVICE_DATA_SYNC` | none | target 35+: **6 h per 24 h** (shared by all dataSync FGS), `onTimeout(int,int)`, a few seconds to `stopSelf()` else `RemoteServiceException`; reset when user brings app to foreground. Blocked from `BOOT_COMPLETED`. [V] | Only for data transfer use cases; wrong fit for automation. |
| `mediaProcessing` | `FOREGROUND_SERVICE_MEDIA_PROCESSING` | none | 6 h / 24 h (API 35+) [V] | n/a |

General: target 34+ must declare a type for every FGS + matching permission (`SecurityException` otherwise); passing an undeclared type → `IllegalArgumentException`. FGS notification needs `POST_NOTIFICATIONS` to be *visible* (API 33+) but FGS still runs. [V]
Sources: https://developer.android.com/develop/background-work/services/fgs/service-types , .../fgs/timeout , .../fgs/changes , https://developer.android.com/about/versions/15/behavior-changes-15

### Fit for the three use cases
- **(a) Ring alarm sound for a few minutes after a notification arrives** → `mediaPlayback` (audio playback is its documented purpose). `shortService` is NOT acceptable on Android 17 (background audio requires a non-shortService FGS, all apps). On target 37 the FGS must also have WIU capability *or* app holds exact-alarm permission and plays `USAGE_ALARM` (see §9). An FGS started from a NotificationListener callback does **not** get WIU [AOSP], so **hold `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` and use `AudioAttributes.USAGE_ALARM`**.
- **(b) Short macro with waits of a few minutes** → `shortService` only if total < ~3 min and no audio; otherwise `specialUse` (with justification) or schedule the continuation with `AlarmManager`/WorkManager instead of holding an FGS during waits. Exact alarm firing is itself an FGS-start exemption.
- **(c) Keep process alive for monitoring** → no type fits cleanly. Options: `specialUse` (Play review); `systemExempted` if exact-alarm permission held [Play acceptability U]. A bound, enabled NotificationListenerService already keeps the process at BOUND_FOREGROUND_SERVICE state while connected [AOSP], which is the usual way MacroDroid-style apps stay resident.

### Background-start restrictions (target 31+) [V]
Exemptions listed by docs: transition from visible state; app can start activities from background (BAL exemption); high-priority FCM; user interaction with notification/widget/bubble; **exact alarm firing for a user-requested action**; current IME; geofence/activity recognition; `BOOT_COMPLETED`/`LOCKED_BOOT_COMPLETED`/`MY_PACKAGE_REPLACED`; `TIMEZONE_CHANGED`/`TIME_CHANGED`/`LOCALE_CHANGED`; NFC `ACTION_TRANSACTION_DETECTED`; device owner/certain roles; CompanionDeviceManager + `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`; **user disabled battery optimization for the app**; `SYSTEM_ALERT_WINDOW` (target 35+: only while the app **has a visible `TYPE_APPLICATION_OVERLAY` window**).
Source: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

- **`BOOT_COMPLETED` (target 35+)**: cannot launch `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection`, `microphone` → `ForegroundServiceStartNotAllowedException`. `specialUse`/`shortService`/`systemExempted` not on the list. [V]
- **NotificationListenerService callback**: not mentioned in the docs. **AOSP**: listeners are bound by system_server with `BIND_AUTO_CREATE | BIND_FOREGROUND_SERVICE | BIND_NOT_PERCEPTIBLE | BIND_ALLOW_WHITELIST_MANAGEMENT` (`NotificationManagerService.NotificationListeners.getBindFlags()`), so the process is at `PROCESS_STATE_BOUND_FOREGROUND_SERVICE` with `PROCESS_CAPABILITY_BFSL`, and `ActiveServices.shouldAllowFgsStartForegroundNoBindingCheckLocked` allows FGS start (`uidBfsl && procstate <= BOUND_FOREGROUND_SERVICE`). → **FGS start from an NLS callback is allowed while the listener is connected [AOSP, undocumented — test on device; OEMs may differ]**. It does **not** grant while-in-use (`shouldAllowFgsWhileInUsePermissionLocked` has no BFGS reason) → no location/camera/mic, and no WIU for Android 17 audio.
- While-in-use (location/camera/mic) FGS must be started while visible, except: system component, widget interaction, notification interaction, PendingIntent from a visible app, DPC, VoiceInteractionService, `START_ACTIVITIES_FROM_BACKGROUND`. [V]
- Android 16 (all apps): jobs running while an FGS is running now obey job runtime quotas. [V]
- Android 17: no FGS-specific changes listed on the FGS changes page; background audio hardening is the relevant one (§9). [V]

## 2. Full-screen intents [V unless noted]
- Manifest: `<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT"/>` (normal protection; required since target 29).
- Android 14+: "apps that are allowed to use this permission are limited to those that provide calling and alarms only"; Play revokes the default grant for other apps (deadline 31 May 2024). Apps installed before the OS update keep it. Users can toggle it.
- Check: `NotificationManager.canUseFullScreenIntent()` (API 34). If false the notification "will show up as an expanded heads up notification on lockscreen".
- Request: `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` with data `package:<pkg>` (data URI required).
- Behaviour: device locked → full-screen activity over lockscreen; unlocked/in use → heads-up.
- Play: declare in Play Console App content as "core functionality: alarm/calling" to keep the default grant; otherwise must ask user [U — from search snippets of support.google.com/googleplay/android-developer/answer/13392821]. An automation app is unlikely to qualify → design for the permission being OFF and deep-link to settings.
Sources: https://developer.android.com/about/versions/14/behavior-changes-14 , NotificationManager & Settings reference pages.

## 3. Background activity launch (BAL) [V]
Exemptions (docs list): visible window; current IME; PendingIntent sent by system (e.g. notification tap); **`SYSTEM_ALERT_WINDOW` granted**; `START_ACTIVITIES_FROM_BACKGROUND` (privileged); bound by a service with BAL permission; launcher/widget-initiated; core OS component. **Notification listener access is NOT a listed exemption.** Companion device not listed on this page.
PendingIntent rules:
- Target 34+: **sender** must opt in via `ActivityOptions.setPendingIntentBackgroundActivityStartMode(...)` when calling `send()`; binding app must use `BIND_ALLOW_ACTIVITY_STARTS`.
- Target 35+: **creator** no longer grants its BAL privileges by default; opt in with `setPendingIntentCreatorBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)` in the options bundle passed to `PendingIntent.getActivity(...)`. Also: can't bring a task to foreground unless creator/sender allows; non-visible windows don't count.
- API 36 adds `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` (recommended); reserve `..._ALLOWED` for true background launches (e.g. from a receiver).
- Target 37: BAL applies to `IntentSender` (`IntentSender.sendIntent()` needs sender opt-in; `ActivityResultLauncher<IntentSenderRequest>` affected). Behavior page tells apps to stop using `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` and use `ALLOW_IF_VISIBLE`. Opt-in task-hijack protection `android:allowCrossUidActivitySwitchFromBelow`.
- Blocked launches fail silently ("Background activity launch blocked!" in logcat). Android 16: `StrictMode.VmPolicy.Builder().detectBlockedBackgroundActivityLaunch()`.
- Alternatives: notification (tap = system-sent PI, allowed), full-screen intent (if granted), or `SYSTEM_ALERT_WINDOW` overlay (user-granted via `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`; also enables BAL).
Sources: https://developer.android.com/guide/components/activities/background-starts , behavior-changes-14/15/17.

## 4. Exact alarms [V]
- `SCHEDULE_EXACT_ALARM` (API 31+ target): special access, user/system-revocable. On Android 14+, **denied by default** for fresh installs targeting 33+ (except calendar/alarm-clock apps, platform/privileged, **apps on the power allowlist**, `SYSTEM_WELLBEING`). Kept on OS upgrade; denied after backup-restore.
- `USE_EXACT_ALARM` (target 33+): normal, auto-granted, not revocable; "only intended for use by apps that rely on exact alarms for their core functionality". Declare only one per device: `SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"` + `USE_EXACT_ALARM`. Play: restricted, alarm-clock/timer/calendar core functionality only, Play Console declaration [U, search snippets]. → automation app should use **`SCHEDULE_EXACT_ALARM`**.
- APIs requiring it: `setExact`, `setExactAndAllowWhileIdle`, `setAlarmClock` (SecurityException). `setExact(..., OnAlarmListener, Handler)` doesn't need it.
- Check `AlarmManager.canScheduleExactAlarms()`; request `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` with `package:` URI (returns RESULT_OK if granted). Listen for `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. **Revocation kills the app and cancels all future exact alarms.**
- Exact alarms are exempt from FGS start restrictions ("allowed to start a foreground service even if the app is in the background").
- Bonus: holding it unlocks `systemExempted` FGS and the Android 17 `USAGE_ALARM` audio waiver.
- No Android 16/17 alarm changes found.
Sources: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms , https://developer.android.com/develop/background-work/services/alarms/schedule , Manifest.permission ref.

## 5. DND / notification policy
- `ACCESS_NOTIFICATION_POLICY` is a normal "marker" permission (not on managed profiles); real grant is user special access: `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` ("Notification Policy (DND / Modes)"); check `NotificationManager.isNotificationPolicyAccessGranted()`; broadcast `ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED`. [V]
- **Target 35+**: apps "cannot modify the global interruption filter". `setInterruptionFilter()` instead activates/deactivates an **implicit `AutomaticZenRule` owned by the app**; `setNotificationPolicy()` creates/updates that rule's ZenPolicy. Most-restrictive-wins across rules. Consequence: `setInterruptionFilter(INTERRUPTION_FILTER_ALL)` turns off only *your* rule — **it cannot turn off DND the user or another app turned on**. Exceptions: e.g. companion device managers. [V]
- Preferred API: own `AutomaticZenRule` via `addAutomaticZenRule()` + `setAutomaticZenRuleState(id, Condition)`; user manual override may suppress the change for that cycle. [V]
- `USAGE_ALARM` in silent mode: AOSP default ringer-mode-affected streams are RING, NOTIFICATION, SYSTEM, SYSTEM_ENFORCED (`AudioService`) → **alarm stream not muted by silent/vibrate ringer mode** [AOSP; not stated on developer.android.com]. In DND, AOSP `default_zen_mode_config.xml` has `alarms="true"` → alarms allowed by default, but the user (or a stricter mode) can block them; "Alarms only"/"Total silence" filters differ [AOSP/U]. Alarm stream volume can be 0.
Sources: NotificationManager ref, https://developer.android.com/about/versions/15/behavior-changes-15 .

## 6. NotificationListenerService
- Manifest: service with `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"`, intent-filter action `android.service.notification.NotificationListenerService`; optional meta-data `android.service.notification.default_filter_types` / `disabled_filter_types` (e.g. `conversations|alerting`, `ongoing|silent`). [V]
- Wait for `onListenerConnected()` before any call. After `onListenerDisconnected()` only `requestRebind(ComponentName)` is safe. `requestRebind` "will fail for listeners that have not been granted the permission by the user". `requestUnbind()` (API 24) / static `requestUnbind(ComponentName)` (API 34). Callbacks on main thread (N+). Listeners ignored in work profile; not on low-RAM ≤ Q. [V]
- Grant: user special access. Check `NotificationManager.isNotificationListenerAccessGranted(ComponentName)`; request via `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` or (API 30+) `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` with `EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME`. [V]
- **Android 15 (all apps) OTP redaction**: "Android will stop untrusted apps that implement a NotificationListenerService from reading unredacted content from notifications where an OTP has been detected. Trusted apps such as companion device manager associations are exempt." [V] What is redacted exactly (text/extras replaced with placeholder) is not specified on the page [U: detection done by Android System Intelligence, false positives reported]. Exemption permission `RECEIVE_SENSITIVE_NOTIFICATIONS` is `signature|preinstalled|knownSigner|role` → **not obtainable by a normal app** (reference page shows "Added in version 36.1"). [V]
- Sideloaded installs: Android 13+ "Restricted settings" blocks enabling notification-listener/accessibility for apps installed outside session-based installers until user picks "Allow restricted settings" in App info; Android 15 Enhanced Confirmation Mode tightens this [U, press sources]. Play installs are unaffected.
- RemoteInput reply: find `Notification.Action` with `getRemoteInputs()`, fill a `Bundle`, `RemoteInput.addResultsToIntent(inputs, intent, bundle)`, then `action.actionIntent.send(context, 0, intent)`. Standard pattern [U — not re-verified today]; reply PIs are normally broadcast/service PIs so BAL doesn't apply.
- Android 16/17: no listener-specific behaviour changes found on behavior pages. Android 17 "Restricted message access" (most apps can't access E2E-encrypted messages) appears on the 17 summary page but detail wasn't found on the all-apps page [U].
Source: https://developer.android.com/reference/android/service/notification/NotificationListenerService , https://developer.android.com/about/versions/15/behavior-changes-all

## 7. WRITE_SETTINGS, Wi-Fi, Bluetooth [V]
- `WRITE_SETTINGS`: `signature|preinstalled|appop|pre23`; target 23+ user grants via `Settings.ACTION_MANAGE_WRITE_SETTINGS` (`package:` URI); check `Settings.System.canWrite(context)`. Then `Settings.System.putInt(SCREEN_BRIGHTNESS / SCREEN_BRIGHTNESS_MODE)`.
- `WifiManager.setWifiEnabled()` deprecated API 29: target ≥ Q "will always fail and return false"; exempt only DO/PO/system. → use `Settings.Panel.ACTION_WIFI` / `ACTION_INTERNET_CONNECTIVITY` panel (user taps).
- `BluetoothAdapter.enable()/disable()` deprecated API 33: target ≥ T "will always fail and return false"; exempt DO/PO/system. → `BluetoothAdapter.ACTION_REQUEST_ENABLE` (dialog; needs BLUETOOTH_CONNECT on 31+) — no programmatic off.
Sources: Manifest.permission, WifiManager, BluetoothAdapter reference pages.

## 8. Usage access (app-launched trigger) [V]
- `<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>` (`signature|privileged|development|appop|retailDemo`); user grants in `Settings.ACTION_USAGE_ACCESS_SETTINGS`. Check via `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, uid, pkg) == MODE_ALLOWED` (standard pattern).
- `UsageStatsManager.queryEvents(begin,end)` + `UsageEvents.Event.ACTIVITY_RESUMED` (API 29; `MOVE_TO_FOREGROUND` deprecated). Polling-based — no push callback; poll while screen on (needs a running component).
- Play: usage access is in "Permissions and APIs that access sensitive information" policy [U — page unreachable].
Source: https://developer.android.com/reference/android/app/usage/UsageStatsManager

## 9. Android 17 (API 37) and 16 (API 36) changes that matter
### Android 17
- **Background audio hardening (ALL apps on 17)**: playback (`AudioTrack.write`, AAudio, OpenSL, ExoPlayer/media3), `requestAudioFocus`, and volume/ringer APIs (`setStreamVolume`, `adjustStreamVolume`, `setStreamMute`, **`setRingerMode`**) require a **visible activity or a non-`shortService` FGS**. Playback/volume fail *silently*; focus returns `AUDIOFOCUS_REQUEST_FAILED`.
  **Target 37 additionally**: the FGS must have **while-in-use** capability (started while visible, or from notification/widget/media-key interaction, etc.) — **waived if app holds exact-alarm permission AND the stream is `USAGE_ALARM`**. Test: `adb shell cmd audio set-enable-hardening enable|disable|throw`; logcat `AudioHardening`. Source: https://developer.android.com/about/versions/17/changes/bg-audio [V]
  → Implication: "set volume"/"ringer mode" macro actions run from background triggers will silently no-op unless an FGS (non-short) is running; on target 37 they need WIU too (no alarm waiver for ringer/media volume changes except alarm-stream). `setInterruptionFilter` is unaffected (NotificationManager).
- BAL extended to `IntentSender`; use `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`. [V]
- `ACCESS_LOCAL_NETWORK` runtime permission for LAN access (target 37). [V]
- SMS OTP: OTP SMS withheld 3 h from non-intended apps (WebOTP: all apps; standard SMS: target 37); `SMS_RECEIVED` withheld. Affects "SMS received" triggers. [V]
- RemoteViews/notification custom-view bitmap memory ceiling (target 37) → `IllegalArgumentException` for widgets. [V]
- Large screens ignore orientation/resizability (opt-out removed for 37). [V]
- App memory limits (all apps), "Optimized delivery of self-broadcasts" (self-broadcasts no longer unfreeze/raise priority of cached process — listed on summary page; detail not fetched [U]).
- Implicit URI grants for `ACTION_SEND`/`IMAGE_CAPTURE` going away in Android 18 — add grant flags explicitly. [V]
- No changes to alarms, FGS types, notification listeners found. [V for listed pages]
Sources: https://developer.android.com/about/versions/17/behavior-changes-17 , .../17/behavior-changes-all , .../17/summary
### Android 16
- All apps: job runtime quotas now apply to jobs started while top or running alongside an FGS; `STOP_REASON_TIMEOUT_ABANDONED`; `setImportantWhileForeground` ignored; ordered-broadcast priority only within a process; intent-redirection hardening (`removeLaunchSecurityProtection()`). [V]
- Target 36: `scheduleAtFixedRate` runs at most one missed execution; Safer Intents opt-in (`android:intentMatchingFlags="enforceIntentFilter"`); edge-to-edge opt-out removed; predictive back on by default (`onBackPressed` not called); large-screen orientation ignored (opt-out until 37); local network permission opt-in phase. [V]
- Notification lock-screen redaction of sensitive notifications in higher-risk situations [U, press].
Sources: https://developer.android.com/about/versions/16/behavior-changes-all , .../16/behavior-changes-16

## 10. POST_NOTIFICATIONS and torch [V unless noted]
- `POST_NOTIFICATIONS`: runtime (dangerous) permission API 33+, required for non-exempt notifications incl. FGS notifications (FGS runs but notification hidden from shade, shown in Task Manager). Request with `ActivityCompat.requestPermissions` / `rememberLauncherForActivityResult(RequestPermission())`; check `NotificationManagerCompat.areNotificationsEnabled()` or `checkSelfPermission`. On minSdk 26–32 no runtime request.
- `CameraManager.setTorchMode(cameraId, enabled)` (API 23): **no `CAMERA` permission** listed (only `openCamera` etc. require it). Throws `CameraAccessException` (CAMERA_IN_USE, MAX_CAMERAS_IN_USE, CAMERA_DISCONNECTED). Not exclusive: other apps can turn it off, and **"If the latest application that turned on the torch mode exits, the torch mode will be turned off"** → keep the process alive while torch is on. Background restriction: none documented; works from a service in practice [U]. `turnOnTorchWithStrengthLevel` API 33.
Source: https://developer.android.com/reference/android/hardware/camera2/CameraManager

## 11. Play Store policy [U — support.google.com unreachable; from search snippets]
- Notification listener: covered by "Permissions and APIs that Access Sensitive Information" (answer/16558241). Use must be tied to core functionality, disclosed; prominent disclosure + consent before sending user to the settings screen; Play Protect treats NOTIFICATION_LISTENER as high-risk for sideloaded apps.
- FGS: target 34+ must declare each FGS type in Play Console > App content (with description/video for some); `specialUse` subtype reviewed. [V that declaration exists — from developer.android.com]
- Full-screen intent: declare alarm/calling core functionality or lose default grant.
- `USE_EXACT_ALARM`: restricted to alarm/timer/calendar core functionality; declaration required. Use `SCHEDULE_EXACT_ALARM` otherwise.
- SMS / Call Log groups (`READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `READ_CALL_LOG`, `PROCESS_OUTGOING_CALLS`, ...): only default SMS/Phone/Assistant handlers, or approved exceptions via Permissions Declaration Form. "Task automation" was added as an exception category in 2019 (Tasker approved) — current wording unverified; reviews are strict ("permissions do not match core functionality"). Jan 27 2027: phone-call account verification no longer a READ_CALL_LOG use case. Android 17 SMS OTP delay applies on top.
- Accessibility API (if used for automation): separate strict policy (`isAccessibilityTool` etc.) [U].
