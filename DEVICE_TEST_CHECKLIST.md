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

## M2: The core loop (VIP alarm end to end)

Install the new build (`git pull && ./gradlew installDebug`). Your M1 macros and settings are kept.

**Setup**

1. **Grant notification access.** Home shows red *NEEDS ATTENTION* → tap **Grant access** → turn on PriorityRing → come back. Home should say **HEALTHY** with a moving pulse.
   - If the switch is greyed out or blocked: Android 13+ blocks this for apps installed by `adb`/APK until you open **Settings → Apps → PriorityRing → ⋮ (top right) → Allow restricted settings**, then try again. Tell me the exact wording vivo uses.
2. **Allow notifications** when asked (needed for the alarm's STOP button). Tell me whether Settings → Permissions shows *Full-screen alerts* as **On** or **Off** on your phone (Android 14+ usually defaults to Off for non-alarm apps).
3. **Test alarm.** On Home tap **Test alarm (30 s)**. It should ring on the *alarm* sound and vibrate; a red ALARM banner appears on Home. Tap **STOP**: sound and vibration must stop **instantly** (same moment).

**Build the VIP macro** (Macros → **New macro**)

4. Name: `VIP email`. **Add trigger → Notification received** → tap **+ Gmail** → Done.
   **Add condition → Text matches**: Check variable `{sender}`, How *contains*, Text = the **exact display name** of someone who can email you right now (a friend / your other account) → Done.
   **Add condition → Time window** → leave 09:00–23:00 (or widen it to cover now) → Done.
   **Add action → Ring alarm** (defaults: up to 2 min, vibrate, full volume) → Done. **Add action → Flash torch** → Done. **Save**.
   - The card reads like “WHEN Notification from Gmail · IF {sender} contains “…” and Between 09:00 and 23:00 · THEN Ring default alarm for up to 2 min and vibrate → Flash torch…”.

**The checks from the brief**

5. **Real VIP email rings.** Have that person email you. Within a few seconds: alarm sound + vibration + torch blinking + alarm notification (full screen if allowed). Then **Activity** shows the run as *RAN*; open it and screenshot the steps.
6. **Non-VIP doesn't ring.** An email from anyone else → no alarm; Activity shows *NOT MET* with the sender's name in the reason.
7. **Duplicate doesn't re-ring.** While the first email's notification is still in the shade, stop the alarm, then pull down the shade / let Gmail refresh. No second alarm. (Gmail re-posting the same notification shows as *SKIPPED — Duplicate* in Activity, or not at all.) A *second, new* email from the VIP should ring again.
8. **Stop is instant** from each place: the notification's **STOP**, the full-screen **STOP**, and Home's banner. Sound, vibration and torch must all stop together.
9. **Snooze works.** Tap **Snooze**: everything goes quiet, the notification says “Snoozed until …”. After 5 min it rings again. Snooze 3 times → the 4th time there's no Snooze button (only STOP). While snoozed, a new VIP email should ring **immediately**.
10. **Locked screen.** Lock the phone, send the VIP email. Does the alarm screen appear over the lock screen (full-screen) or only a heads-up notification? Can you STOP without unlocking?
11. **Idle 30+ min.** Lock the phone, leave it untouched (screen off, not charging) for 30–60 min, then send the VIP email. Does it ring? If not, tell me what Home → Monitoring and Settings → Diagnostics say afterwards ("Last notification received" time, listener connected?).

**Extra checks (quick)**

12. **Silent mode & DND.** Put the phone on silent → Test alarm: does it ring? Turn on Do Not Disturb → Test alarm: does it ring? (Android allows alarms in DND by default, but vivo may differ — this is exactly what I need to know.)
13. **Simulator.** Settings → Diagnostics → **Event simulator** → Gmail email, sender = your VIP name → Send. Alarm rings; Activity shows a run marked *Simulated*. Turn off “New notification each time”, send twice: the second is *SKIPPED — Duplicate*.
14. **WhatsApp.** Make a second macro: Notification received from **WhatsApp**, condition `{text}` contains `urgent`, action **Show notification** (title `Urgent from {sender}`). Have someone WhatsApp you "urgent test". You should get a PriorityRing notification titled with their name. Also try in a **group**: the title should show the person, not the group name.
15. **Inspector (helps me fix parsers).** Settings → Diagnostics → **Inspector**: open the newest Gmail and WhatsApp entries, tap **Copy redacted dump** and paste them to me. Redacted means names and text are reduced to first letters, e.g. "R•••• S•••••".
16. **adb test (optional, from your computer):**
    ```bash
    adb shell am broadcast -a com.priorityring.debug.SIMULATE \
      -n com.priorityring.app.debug/com.priorityring.app.debug.DebugEventReceiver \
      --es preset gmail --es sender "Rahul Sharma" --es text "Quarterly report" --es key t1
    adb logcat -s PriorityRing   # shows which macros started / were skipped
    ```
    Run the same command twice with the same `key`: the second must be *Duplicate*.
17. **After a reboot**, without opening the app, send the VIP email. Does it ring? (Monitoring should resume when Android rebinds the listener.)
