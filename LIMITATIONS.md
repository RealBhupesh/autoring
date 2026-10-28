# What PriorityRing can't do (and why)

Android protects users by limiting what apps can do in the background. PriorityRing never fakes a feature: if Android doesn't allow something, it's listed here. Sources for each point are in `docs/android-api-notes.md`.

## Not possible without root (Tier 3)

- **Turning mobile data or airplane mode on or off.** Only system apps can do this.
- **Silently taking photos or recording in the background.** Camera and microphone need the app visible (or a visible foreground notification), by design.
- **Turning Wi-Fi or Bluetooth on or off directly.** Since Android 10 (Wi-Fi) and Android 13 (Bluetooth), these calls do nothing for normal apps. The closest alternative is opening the system Wi-Fi or Bluetooth panel for you to tap. With Shizuku (Tier 2, planned) this may become possible.

## Possible, with caveats

- **Notifications are the only signal.** PriorityRing reacts to notifications other apps post. If Gmail doesn't notify you about an email (its own notification settings, a muted label, "high priority only"…), nothing can trigger.
- **Sender names come from the notification**, not a verified address. Gmail shows a display name, and anyone can set theirs to "Rahul Sharma". Don't use sender matching for anything security-sensitive.
- **Grouped notifications** ("3 new messages") may hide individual senders. PriorityRing reads every detail Android exposes, but some apps don't expose them. By default, group summaries don't trigger macros, because the individual notifications already did.
- **One-time codes are hidden** (Android 15+). Android hides the text of notifications it thinks contain a one-time password, so a condition on `{text}` won't see it.
- **Sideloaded installs need an extra step.** On Android 13+, apps installed from an APK or by `adb` can't be given notification access until you tap "Allow restricted settings" in the app's info page. Play Store installs don't have this step.
- **PriorityRing's own notifications never trigger macros.** This prevents loops (a macro showing a notification that triggers itself).
- **Opening apps or links from the background** *(from M3)*. Android blocks apps from popping up screens on their own. When a macro wants to open something while you're not using PriorityRing, it posts a notification you tap instead, and the run history says so.
- **Full-screen alarm screens.** Since Android 14, only calling and alarm-clock apps get this automatically. If it isn't allowed, the alarm appears as a large notification with STOP and SNOOZE buttons. You can allow it in Settings → Permissions → Full-screen alerts.
- **Alarm sound in silent mode / Do Not Disturb.** The alarm plays on the alarm volume, which on stock Android isn't silenced by silent mode and is allowed through DND by default. You (or a stricter DND mode) can block alarms, and phone makers can change this. If the alarm volume is 0 and "Ring at full volume" is off, it will be silent.
- **Do Not Disturb** *(from M3)*. On Android 15+, PriorityRing can turn on DND through its own rule, but can't turn off DND that you or another app turned on.
- **Background restrictions on vivo / iQOO.** The OS may stop background apps to save battery. If it disconnects PriorityRing from your notifications, Home turns red and offers "Reconnect". You may need to allow background activity / autostart for PriorityRing in the phone's settings.
- **Running macros don't survive a restart.** If Android kills PriorityRing mid-macro (or the phone reboots), that run isn't resumed. Your macros are kept, and monitoring resumes when Android reconnects the listener. An alarm that was ringing resumes (or clears, if its time is up) the next time PriorityRing starts.
