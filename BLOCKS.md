# Block catalogue

**Tiers:** 1 = works for normal apps, possibly after a user grant. 2 = needs Shizuku or an adb grant (planned add-on). 3 = impossible without root (listed in `LIMITATIONS.md`).

**Device status:** ⬜ not yet tested on the phone · ✅ works on iQOO Neo 10 · ⚠️ partial (see notes) · ❌ doesn't work.

**Where the code lives:** pure blocks are in `engine/.../blocks/core/` (unit-tested on the JVM); Android blocks are in `blocks/.../action/`, registered in `blocks/.../di/BlocksModule.kt`.

## Triggers

| Id | Name | Tier | Permissions | Android notes | Unit tests | Device |
|---|---|---|---|---|---|---|
| `trigger.notification_posted` | Notification received (any app or chosen apps; Gmail / WhatsApp parsed specially) | 1 | Notification access (special access) | Excludes group summaries and ongoing notifications by default. Android 15+ hides OTP notification content from listeners. Apps installed outside Play need "Allow restricted settings" before access can be granted (Android 13+). PriorityRing's own notifications never trigger macros. Outputs: `{sender} {text} {title} {subject} {conversation} {app} {package} {account} {is_group} {senders} {message_count}` | ✅ | ⬜ |

## Conditions

| Id | Name | Tier | Permissions | Android notes | Unit tests | Device |
|---|---|---|---|---|---|---|
| `condition.text_match` | Text matches (contains / exact / starts / ends / regex, any-of lines) | 1 | — | Pure logic. Sender names can be spoofed (the builder warns). Empty or missing variable → not met, with the reason. | ✅ | ⬜ |
| `condition.time_window` | Time window, crosses midnight, optional days | 1 | — | Pure logic. Uses the phone's time zone. For windows crossing midnight, days refer to the day the window starts. | ✅ | ⬜ |
| `condition.days` | Days of week | 1 | — | Pure logic | ✅ | ⬜ |

## Actions

| Id | Name | Tier | Permissions | Android notes | Unit tests | Device |
|---|---|---|---|---|---|---|
| `action.ring_alarm` | Ring alarm (sound, max duration, vibrate, full volume, snooze, full-screen) | 1 | Show notifications (runtime, 13+). Full-screen alerts optional (special access, 14+) | Alarm audio stream (`USAGE_ALARM`) in a `mediaPlayback` foreground service. Only one alarm at a time: a second request joins it. Without the full-screen permission the alarm is a heads-up notification with STOP / SNOOZE. Android 17 needs the foreground service for background audio. | ✅ state machine; block ⬜ (M3) | ⬜ |
| `action.vibrate` | Vibrate (pattern, N times or until stopped) | 1 | — (VIBRATE is install-time) | Alarm-usage vibration so it isn't dropped in the background. "Until stopped" joins the alert, so the same STOP ends it. Unsupported on phones without a vibrator. | ⬜ (M3) | ⬜ |
| `action.flash_torch` | Flash torch (steady / blink / strobe, max duration) | 1 | — (no CAMERA permission needed) | `CameraManager.setTorchMode`. Fails if the camera is in use. The torch goes off if our process dies, which the foreground service prevents. Joins the alert, so STOP turns it off. | ⬜ (M3) | ⬜ |
| `action.show_notification` | Show notification (title / text with variables, importance) | 1 | Show notifications (runtime, 13+) | Three channels (urgent / normal / quiet); the user can change each in system settings. | ⬜ (M3) | ⬜ |
| `control.wait` | Wait (1 s – 1 h) | 1 | — | Counts toward the macro's max run time. A wake lock in the foreground service keeps it on time with the screen off. | ✅ | ⬜ |
| `control.stop` | Stop macro | 1 | — | Ends the run successfully; later actions are skipped. | ✅ | ⬜ |

*Built into the engine (not blocks): **If** (conditions inside the action list) and **Repeat** (capped at 100). They run and are tested, but the basic builder shows them read-only until M3.*

*Coming in M3: the remaining v1 triggers, conditions and actions (see the build brief), each with its tier verified against current docs.*
