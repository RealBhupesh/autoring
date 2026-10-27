# Device test checklist

Things only a real phone can verify. Each milestone adds a section. Report each item as ✅ / ❌ plus what you saw, and add screenshots where something looks wrong.

Test device: iQOO Neo 10. Please report the **Android version** and the **OS skin version** (Settings → About phone).

## M1: Skeleton

Install from the repo root on your computer, with the phone connected and `adb devices` showing it:

```bash
git pull
./gradlew installDebug          # or: adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. **Install and launch.** The app named *PriorityRing* (icon: a white ring with a red dot) installs, opens on a dark screen with a dotted "PRIORITYRING" heading, and doesn't crash. Did the iQOO show any "install via USB" security prompt?
2. **Home: health card.** It shows a red *NEEDS ATTENTION*: "Notification access is off…" with a red **Grant access** button. Tap it.
   - Expected: the system *Notification access* screen opens.
   - PriorityRing is **not** listed there yet; that arrives in M2.
   - Tell me what the screen is called on vivo, and whether it opened at all.
3. **Master switch.** Tap the *Master switch* row. You should feel a haptic tick, the card should change to grey *PAUSED*, and the subtitle should read "Everything is paused". Swipe the app away from Recents, reopen it, and check the switch remembered its state. Then turn it back on.
4. **Macros: storage.** Go to Macros and tap **Add sample**. A card "VIP email alarm" appears with:
   - WHEN: `Missing block (trigger.notification_posted)`
   - IF: `{sender} contains “Rahul Sharma” and Between 09:00 and 23:00`
   - THEN: `Missing block (action.ring_alarm) → Missing block (action.flash_torch)`
   - a red line: "Uses blocks this version doesn't have."

   That's expected: those blocks come in M2, and this proves unknown blocks don't crash anything.
5. **Persistence across a force stop.** Toggle the sample off, open ⋮ → **Duplicate**, then ⋮ → **Delete** on the copy and confirm. Then go to Settings → Apps → PriorityRing → **Force stop**, reopen the app, and check the remaining macro and its on/off state are still there.
6. **Settings: permissions.** Every row shows *On* or *Off*. For each *Off* row, tap **Open** and note whether the right system screen appears:
   - *Show notifications*: the Android 13+ system dialog. Allow it, then check the row turns *On*.
   - *Modify system settings*: grant it, come back, and check the row turns *On*.
   - *Alarms & reminders*, *Do Not Disturb access*, *Usage access* and *Full-screen alerts*: just tell me whether each screen opens. You don't need to grant them.
7. **Privacy controls.** Change retention to 30 days, leave the app, come back, and check it's still 30 days. Tap **Delete everything** → Delete, and check the Macros list is empty.
8. **Theme.** Switch between Light, Dark and System. The whole app should change immediately with no unreadable text. Screenshot anything that looks off.
9. **Large text.** Set Settings → Display → Font size (and Display size) to the maximum. Check that the Home and Settings screens are still usable and that the dot-matrix heading wraps instead of being cut off.
10. **TalkBack** (optional). Turn on TalkBack. The dotted heading should be read as "PriorityRing, heading", and the master switch as "Master switch, on/off, switch".
11. **Battery settings (information only).** Go to Settings → Apps → PriorityRing → Battery, and tell me which options vivo shows (e.g. "Allow background activity", "High background power consumption", "Autostart"). M2 needs these names for the guidance screen.
