# PriorityRing: progress log

The handoff document. A fresh session should read this first, then `README.md`.

## Status

| Milestone | State |
|---|---|
| M1 Skeleton | Done. Device results not received yet; the owner said to keep going. |
| M2 Core loop (listener, parsers, executor, AlertController, first blocks, basic builder) | **Done here; waiting for device results** |
| M3 Block library expansion | Next |
| M4 Product quality | — |
| M5 Hardening | — |

## Working setup

- Code is written and built in a **Claude Code cloud container** (repo `realbhupesh/autoring`, branch `claude/clever-turing-944n10`), not on the owner's computer.
- The owner's phone (iQOO Neo 10, vivo skin) can only be reached from the owner's own computer. Flow: build and test in the container → push → owner pulls, then runs `./gradlew installDebug` (or `adb install`) locally → owner reports results.
- Container toolchain: OpenJDK 21, Android SDK at `/opt/android-sdk` (platform `android-37.0`, build-tools 37.0.0, platform-tools). The owner accepted the SDK licence. `local.properties` (gitignored) points to it.
- There is no KVM in the container, so no emulator. UI is smoke-tested with **Robolectric** instead (`app/src/test`). Compose instrumented tests need the phone or an emulator on the owner's machine.
- Maven Central often answers HTTP 429 from the container. Simply rerun the build.
- **Still unknown:** the phone's exact Android version and OS skin version (Settings → About phone). The owner should report these with the M1 results.

## Decisions

Engineering decisions made so far; product decisions go to the owner.

- **D1: Gradle project at the repo root**, not `./priorityring`. A clean clone then builds with `./gradlew` directly. The owner was told and didn't object.
- **D2: Versions.** All were checked on 2026-10-27 against Google Maven, Maven Central and gradle.org:
  - Tooling: AGP 9.4.1, Gradle 9.8.2 (AGP 9.4 needs ≥ 9.6.0), Kotlin 2.4.21, KSP 2.3.12.
  - UI: Compose BOM 2026.09.00 (Material3 1.4.0, UI 1.12.1), Activity 1.13.0, Lifecycle 2.11.0, Navigation 2.10.2.
  - DI and data: Hilt 2.60.1, androidx.hilt 1.4.0, Room 2.8.5, DataStore 1.3.0, kotlinx-serialization 1.12.0, coroutines 1.11.0.
  - Tests: Robolectric 4.17, JUnit 5.x (6.1.3) for `:engine`, JUnit 4 for Android modules.
  - All versions live in `gradle/libs.versions.toml`.
- **D3: AGP 9 built-in Kotlin.** Android modules don't apply `org.jetbrains.kotlin.android`, since AGP 9 rejects it. Annotation processing uses **KSP** because kapt is incompatible with built-in Kotlin. Source: https://developer.android.com/build/migrate-to-built-in-kotlin
- **D4: SDK levels.** `compileSdk 37` (the maximum AGP 9.4 supports). `minSdk 26` (Android 8.0), which provides `java.time`, adaptive icons, notification channels and variable fonts natively.
- **D5: Macro storage is versioned JSON.** The envelope is `{"formatVersion":1,"macro":{…}}` in a Room `TEXT` column, written by `MacroJson`.
  - Blocks are referenced by string id plus a free-form config object, so new blocks never need a DB migration.
  - A custom tolerant serializer keeps **unknown node types** (written by a newer app version) verbatim and writes them back unchanged.
  - Unknown **block ids** survive as-is and display as "Missing block (id)".
  - Unreadable rows show as "Can't read this macro: …" instead of crashing.
- **D6: Block code placement.** Pure-logic blocks (text match, time window, days) live in `:engine` and are unit-tested on the JVM. Android blocks live in `:blocks` and join the registry through a Hilt `@IntoSet` multibinding in `BlocksModule`.
- **D7: `targetSdk` stays 36 (Android 16) for now.**
  - Android 17 restricts background audio and volume changes **for all apps**: they need a visible activity or a non-`shortService` foreground service.
  - Apps *targeting* 37 additionally need while-in-use capability. The exception is apps that hold the exact-alarm permission *and* play `USAGE_ALARM` audio.
  - We'll design M2 to satisfy the 37 rules, then raise `targetSdk` once it's verified on a device.
  - **Before publishing, verify Play's current target-API deadline in Play Console.** The research agent couldn't reach support.google.com.
  - Lint's `OldTargetApi` warning is silenced in `app/lint.xml` with a pointer here.
- **D8: Navigation.** Navigation-Compose 2.10 with type-safe `@Serializable` routes, which is stable and well documented. Navigation 3 was not adopted: the latest artifacts listed on Google Maven were alphas.
- **D9: Icons.** `material-icons-core` (frozen at 1.7.8) covers M1. Block icons are string keys (`BlockMeta.icon`) that the UI maps to vectors. Switching to custom or Material Symbols vectors later only touches that mapping.
- **D10: No cloud backup and no device transfer** (`allowBackup=false` plus exclude-all rules). Privacy first: the backup path is Export, which writes a JSON file the user controls.
- **D11: Debug builds use `applicationId` `com.priorityring.app.debug`**, so a future release build can sit alongside them. The release id `com.priorityring.app` can still be changed before the first Play upload.
- **D12: Bundled fonts (SIL OFL)** as variable TTFs: Inter for body, Space Grotesk for headings, JetBrains Mono for labels. Licences are in `/licenses`.
- **D14: One foreground service, `RunnerService`, with types `mediaPlayback|specialUse`.**
  - It's started (by `ForegroundCoordinator`) whenever a macro is running or an alert is active, and stops itself when both are idle.
  - On Android 14+ it always starts with **both** types. Upgrading the type later, from the background, is the risky path, and losing the alarm is the worst failure.
  - It holds a bounded partial wake lock, so waits and snooze timers fire with the screen off.
  - If Android refuses the start (`ForegroundServiceStartNotAllowedException`), the run still continues. The failure is shown on Home and in the alarm action's trace.
  - **Revisit before Play:** `specialUse` needs Play Console justification, and declaring `mediaPlayback` while no sound plays may be questioned. Alternatives: `shortService` for short runs, exact alarms for long waits.
- **D15: The alarm is owned by `AndroidAlertController`, driven by the pure `AlertMachine`.**
  - The state is persisted synchronously (SharedPreferences `commit`), so STOP works after process death.
  - Outputs (sound, vibration, torch) are *reconciled* against the state rather than toggled per event, which makes stop atomic.
  - Ring alarm, Vibrate "until stopped" and Flash torch all *join* the one alert, so a single STOP ends everything.
  - Snooze uses a coroutine timer inside the foreground service, with no exact-alarm permission needed. If the process dies while snoozed, the alert re-evaluates on the next start.
- **D16: The notification listener ignores PriorityRing's own notifications** (loop prevention), plus an origin chain on events for macro→macro loops (M3).
- **D17: Notification parsing is pure** (`engine/notification`), working on a `RawNotification` that `NotificationExtractor` (Android) builds field by field.
  - A failure reading one extras field never loses the whole notification.
  - The dedupe key is notification key + FNV-1a hash of the *content*, so silent re-posts are duplicates but a new message in the same chat isn't.
- **D18: Privacy.** `TracePrivacy` strips content variables (`text`, `title`, `subject`, …) from stored traces unless "Store notification text" is on, and masks them wherever a condition quoted them in a reason. Sender and app stay visible. Content equal to a kept value (Gmail's title = sender) isn't masked.
- **D19: Debug-only `adb` receiver** (`app/src/debug`), protected by the `DUMP` permission, so only the shell can use it. It doesn't exist in release builds.
- **D13: Red text on dark uses `#FF5A4F`.** `#E1261C` on `#090909` has only about 4.3:1 contrast. `#E1261C` remains for fills, icons and the pulse, and white on it reaches about 4.7:1.

## Design input for M2 (from the docs research, `docs/android-api-notes.md`)

- **Ringing:**
  - Play audio with `USAGE_ALARM` from a `mediaPlayback` foreground service, never `shortService` (it times out after ~3 min and fails Android 17's audio rule).
  - Request `SCHEDULE_EXACT_ALARM`: it satisfies Android 17's target-37 exemption for alarm audio and allows foreground-service starts from exact alarms (snooze re-ring).
- **Starting the foreground service from the notification listener:** AOSP binds listeners as `BIND_FOREGROUND_SERVICE`, so a start from a listener callback should be allowed. This is not documented; **must be verified on the vivo**.
- **Full-screen intent:** on Android 14+ it's auto-granted only to calling and alarm apps. Assume it's off; fall back to a high-priority heads-up notification with STOP / SNOOZE actions. Check `canUseFullScreenIntent()`.
- **Background activity starts** ("launch app", "open URL"):
  - Notification access is **not** an exemption.
  - A notification the user taps is the compliant path.
  - The block must report "posted a tap-to-open notification" instead of claiming it opened anything.
- **DND (targetSdk 35+):** `setInterruptionFilter` now controls an app-owned implicit rule and can't turn off DND that the user set. Use an `AutomaticZenRule` per macro.
- **Silent mode / DND and alarms:** AOSP says the alarm stream ignores silent and vibrate modes and is allowed in DND by default, but users can change that. This goes on the device checklist for the vivo.
- **Android 15+ OTP redaction:** notification listeners receive redacted text for notifications Android classifies as containing OTPs. Document this in LIMITATIONS.

## Done (M2)

- **Engine:**
  - `MacroEngine`, which does matching, event-level dedupe, pause, loop prevention, conditions (AND/OR/NOT, all evaluated for the trace), skip-if-running / cooldown / rate limit (atomic), timeouts, failure policy, If / Repeat (capped) / Stop / Wait, stop and stop all.
  - `RunTrace` with step-by-step records, `TracePrivacy`, `AlertMachine` plus the `AlertController` contract.
  - Notification model and parsers (Gmail single and summary, WhatsApp MessagingStyle 1:1 / group / own message / fallback / summary, generic), and the notification-posted trigger.
  - 90 unit tests.
- **Blocks:**
  - `NotificationExtractor`, the redacted `NotificationDump`, notification channels, device built-in `{battery}`, and Vibration / Torch helpers.
  - Actions: ring alarm, vibrate, flash torch, show notification, plus wait / stop in core.
- **App runtime:**
  - `NotificationMonitorService` (connection tracking, `requestRebind` on disconnect), `EventDispatcher` (ordered queue), `RunHistory` (privacy filter, last-run, retention pruning) and `MonitorHealth`.
  - `ForegroundCoordinator` plus `RunnerService`.
  - `AndroidAlertController` with `AlarmSound` (alarm stream, focus, optional full volume restored after), `AlertNotifier` (heads-up / full-screen, STOP / SNOOZE), `AlertActionReceiver` and `AlarmActivity` (over the lock screen, without dismissing the keyguard).
  - Debug `adb` receiver.
- **App UI:**
  - Schema-driven builder: WHEN / IF / THEN, a searchable block library with tier and availability, config forms for all 13 field types, a variable picker, a live sentence, reordering with up/down, NOT, failure policy, per-macro settings, validation, discard and delete confirmation.
  - Activity filters and run detail. Home: health states (granted / connected / stalled / foreground blocked), alarm banner, running macros with Stop / Stop all, test alarm, last event.
  - Settings Diagnostics, the event simulator and the notification inspector.
- **Tests:** 90 engine, 3 DataStore and 5 Robolectric (all tabs, master switch, builder creates and saves a macro, validation messages, simulated Gmail through the whole pipeline to the Activity detail). Lint is clean.

## Done (M1)

- Gradle multi-module project (`:engine`, `:blocks`, `:data`, `:app`) with a version catalog and Gradle 9.8.2 wrapper.
- `:engine` (pure Kotlin):
  - Macro model (triggers, AND/OR/NOT condition tree, action list with Step / If / Repeat) and block contracts (`BlockMeta`, `ConfigField` with 13 field types, `BlockConfig`, Trigger/Condition/Action interfaces, `Availability`, `BlockRegistry`).
  - Variables (`{name}` templates, built-ins `{time}` `{date}` `{weekday}` `{timestamp}`), `TriggerEvent`, and `MacroJson` (versioned storage, import/export).
  - Three core condition blocks: text match, time window (crosses midnight), days of week.
- `:data`:
  - Room DB v1 (`macros`, `runs`) with the exported schema in `data/schemas`, and DataStore settings (master switch, retention, store-text, theme, onboarding flag).
  - Repositories that turn unreadable rows into placeholders.
- `:blocks`: `AndroidDeviceCapabilities` (checks and settings-screen intents for 7 kinds of access) and the block catalogue / Hilt multibinding.
- `:app`:
  - Theme (palette, fonts, 8 dp spacing, small radii, reduce-motion flag), Canvas dot-matrix heading, monitoring pulse.
  - Bottom navigation with Home (health card, master switch, counts, recent runs), Macros (cards, toggle, duplicate, delete with confirmation, temporary "Add sample"), Activity (runs grouped by day) and Settings (live permission status with fix buttons, retention, store-text toggle, delete logs / everything, theme).
- Tests:
  - `:engine` 40 unit tests: JSON round trip, unknown blocks and nodes, corrupt input, import/export, variables, the three conditions including midnight crossing, config defaults and validation, availability, registry.
  - `:data` 3 DataStore tests.
  - `:app` 3 Robolectric smoke tests (launch, all tabs, master switch, sample macro with missing blocks).
- Lint is clean in all modules.

## Next (M3)

- Remaining v1 triggers: notification removed, time / schedule (exact alarms), charging, battery level, headphones / Bluetooth, Wi-Fi, screen on / unlocked, app launched (usage access), manual (button, widget, Quick Settings tile).
- Remaining conditions and actions from the brief, each verified against current docs. Speak text, volume, ringer, DND (AutomaticZenRule), brightness, launch app / URL via tap-to-open notification, intents, HTTP (adds INTERNET), notification reply / dismiss / press action, media, clipboard, wallpaper, and SMS / call (sideload-only).
- Builder editing for If / Repeat, plus Run another macro and Log blocks.
- Templates gallery, import / export UI (the engine side already exists), undo delete.
- Unit tests for every block's config and summary, including the M2 Android actions.

## Known issues / notes

- **Nothing in M2 has run on a real phone yet.** All verification is unit tests plus Robolectric. The M2 section of `DEVICE_TEST_CHECKLIST.md` is the real test.
- **Android 17 race:** a "Ring alarm" that is the macro's first action can start the sound a few milliseconds before the foreground service is up. That's fine on Android ≤ 16. On Android 17 the first fraction of a second might be blocked. Fix if device tests show it: await the service in `AlertController.request`.
- **Background start of the foreground service from the listener** is allowed per AOSP source but undocumented, and vivo may differ. If it fails, Home shows it.
- The basic builder shows nested groups / If / Repeat read-only (M3). Drag-to-reorder is up/down buttons for now (M4).
- `RecentNotifications` (inspector) is in memory only and lost when the process restarts. This is by design (privacy).
- Small icons for notifications use platform drawables. Custom monochrome icons come in M4.
- No undo for delete yet (M3/M4). Light theme hasn't had a design pass (M4). No Compose instrumented tests yet (they need a device; M4).
- The draft PR wasn't created: the repo has no base branch. The owner was asked whether to create `main`.

## Sources

See `docs/android-api-notes.md` for the full research notes with per-claim tags (`[V]` = verified on developer.android.com, `[AOSP]` = platform source, `[U]` = unverified). The key pages:

- https://developer.android.com/build/releases/gradle-plugin (AGP 9.4 compatibility: Gradle ≥ 9.6.0, JDK 17, max API 37)
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://developer.android.com/about/versions/17/changes/bg-audio
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/guide/components/activities/background-starts
- https://developer.android.com/develop/background-work/services/alarms/schedule
- https://developer.android.com/reference/android/app/NotificationManager#setInterruptionFilter(int)
