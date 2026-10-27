# What PriorityRing can't do (and why)

Android protects users by limiting what apps can do in the background. PriorityRing never fakes a feature: if Android doesn't allow something, it's listed here. This is a first draft; it grows with each milestone and every item is checked against current Android documentation (sources in `docs/android-api-notes.md`).

## Not possible without root (Tier 3)

- **Turning mobile data or airplane mode on or off.** Only system apps can do this.
- **Silently taking photos or recording in the background.** Camera and microphone access needs the app visible (or a visible foreground notification), by design.
- **Turning Wi-Fi or Bluetooth on or off directly.** Since Android 10 (Wi-Fi) and Android 13 (Bluetooth), these calls do nothing for normal apps. The closest alternative is opening the system Wi-Fi or Bluetooth panel for you to tap. With Shizuku (Tier 2, planned) this may become possible.

## Possible, with caveats

- **Notifications are the only signal.** PriorityRing reacts to notifications other apps post. If Gmail doesn't notify you about an email (because of its own notification settings or a muted label), nothing can trigger.
- **Sender names come from the notification**, not a verified address. Gmail usually shows a display name, and anyone can set their display name to "Rahul Sharma". Don't use sender matching for anything security-sensitive.
- **Grouped notifications** ("3 new messages") may hide individual senders. PriorityRing reads every detail Android exposes, but some apps don't expose them.
- **One-time codes are hidden** (Android 15+). Android hides the text of notifications it thinks contain a one-time password from apps like PriorityRing.
- **Opening apps or links from the background.** Android blocks apps from popping up screens on their own. When a macro wants to open something while you're not using PriorityRing, it posts a notification you tap instead, and the run history says so.
- **Full-screen alarm screens.** Since Android 14, only calling and alarm apps get this automatically. If it isn't allowed, the alarm appears as a large notification with STOP and SNOOZE buttons.
- **Do Not Disturb** (Android 15+). PriorityRing can turn on DND through its own rule, but it can't turn off DND that you or another app turned on.
- **Background restrictions on vivo / iQOO.** The OS may stop background apps to save battery. PriorityRing detects this and shows a red warning on Home, but you may need to allow background activity and autostart in the phone's settings.
