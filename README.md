# PriorityRing

**Make sure what matters reaches you, and make your phone react the way you want.**

PriorityRing is an Android automation engine in the spirit of MacroDroid and Tasker. You build *macros* from blocks:

```
WHEN  [one or more triggers]          any one fires the macro
IF    [conditions, AND / OR / NOT]    all evaluated with recorded reasons
THEN  [ordered actions]               with Wait / If / Repeat / Stop control blocks
```

"Ring when my boss emails" is not a feature. It's a macro anyone can assemble from generic blocks.

> **Status: Milestone 2 (core loop).** Notification monitoring (Gmail / WhatsApp / generic parsers), the engine with traces, the alarm (stop / snooze / full-screen), a basic schema-driven builder, Activity traces, an event simulator and a notification inspector are implemented and pass the automated tests, but have **not yet been verified on a real phone** (see `DEVICE_TEST_CHECKLIST.md`). The full block library arrives in M3. See `PROGRESS.md`.

## Build and install

Requirements: **JDK 17+** (21 works) and the **Android SDK** with platform 37 and build-tools 37.0.0. The easiest way to get both is Android Studio. If you're only using the command line, create `local.properties` containing `sdk.dir=/path/to/Android/sdk`.

```bash
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # build + install on the connected phone
adb install -r app/build/outputs/apk/debug/app-debug.apk   # same thing, manually

./gradlew test                 # all JVM tests (engine, data, Robolectric UI smoke tests)
./gradlew :engine:test         # just the engine, takes seconds
./gradlew lintDebug            # Android lint
```

The debug app's id is `com.priorityring.app.debug`. To uninstall it: `adb uninstall com.priorityring.app.debug`.

**Phone setup for `adb`:** go to Settings → About phone and tap the software version (or build number) 7 times to unlock Developer options. In Developer options, turn on *USB debugging*, and on vivo also *USB debugging (Security settings)*. Connect the phone, run `adb devices`, and accept the prompt on the phone.

## Architecture

```
 system events ─────────────┐
 (notifications, charger,   │   :blocks / :app (Android)
  time, tile, widget …)     ▼
                     ┌──────────────┐      ┌──────────────────────────────────────────┐
                     │ event sources│─────▶│ TriggerEvent (type, variables, dedupeKey) │
                     └──────────────┘      └──────────────────┬───────────────────────┘
                                                              ▼
   ┌─────────────────────────────── :engine (pure Kotlin, no Android) ─────────────────────┐
   │  match triggers → dedupe / cooldown / rate limit → evaluate condition tree →          │
   │  run action list (Step / If / Repeat / Wait …) → RunTrace (every step + reason)        │
   │  BlockRegistry · BlockMeta + ConfigField schema · BlockConfig · Variables · MacroJson │
   └───────────────┬───────────────────────────────────────────────────────────┬──────────┘
                   ▼                                                           ▼
       ActionBlock implementations                                  :data (Room + DataStore)
       (:blocks; Android work behind                                 macros as versioned JSON,
        interfaces) → AlertController                                run traces, settings
                   ▼
              :app — Compose UI (Home · Macros · Activity · Settings), services, Hilt DI
```

| Module | Contents | Android? |
|---|---|---|
| `:engine` | Models, block contracts, config schema, variables, JSON, core pure blocks, executor (M2), traces | **No.** Unit-tested on the JVM |
| `:blocks` | Android triggers/conditions/actions, capability checks, block catalogue | Yes |
| `:data` | Room DB, DataStore settings, repositories | Yes |
| `:app` | Compose UI, theme, navigation, services, DI wiring | Yes |

Key ideas:

- **A block is a self-contained object.** Its `BlockMeta` declares id, name, category, icon key, tier, config fields, output variables, requirements and SDK bounds. It also implements `summarize()` (plain English) plus one of `match()` / `evaluate()` / `execute()`.
- **The builder renders forms from `ConfigField`s.** No block has custom UI.
- **Macros are stored as JSON** (`block id + config`), so new blocks never need DB migrations. Unknown blocks show as *Missing block* and are preserved.

## How to add a new block

> M1 note: the full worked example, with an action block added without UI or DB changes, will be proven here in M3. The contract is already final.

1. Create one file, for example `blocks/src/main/kotlin/com/priorityring/blocks/action/MyAction.kt`:

   ```kotlin
   class MyAction @Inject constructor(@ApplicationContext private val ctx: Context) : ActionBlock {
       override val meta = BlockMeta(
           id = "action.my_action",                 // stable forever: it's stored in users' macros
           kind = BlockKind.ACTION,
           name = "Do my thing",
           description = "One sentence for the library.",
           category = BlockCategory.DEVICE,
           icon = "bolt",
           fields = listOf(ConfigField.Text("message", "Message", variables = true, required = true)),
       )
       override fun summarize(config: BlockConfig) = "Do my thing with “${config.string("message")}”"
       override suspend fun execute(config: BlockConfig, context: ActionContext): ActionResult {
           val text = context.render(config.string("message"))   // substitutes {variables}
           // … do the work …
           return ActionResult.Success("Did it")
       }
   }
   ```

2. Register it with one line in `blocks/src/main/kotlin/com/priorityring/blocks/di/BlocksModule.kt`:

   ```kotlin
   @Binds @IntoSet abstract fun myAction(b: MyAction): Block
   ```

   A pure-Kotlin block (no Android APIs) goes in `:engine`'s `CoreBlocks.all` list instead.

3. Add a unit test for its config defaults and summary, and add a line to `BLOCKS.md` and `DEVICE_TEST_CHECKLIST.md`.

## Permissions

Permissions are requested only when a macro uses a block that needs them, always with an explanation first.

| Permission / access | Kind | Why |
|---|---|---|
| Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`) | Special access, user grants in system settings | Read other apps' notifications so they can trigger macros. |
| `POST_NOTIFICATIONS` | Runtime (Android 13+) | Show the alarm notification (with STOP / SNOOZE), "Show notification" actions, and the foreground-service notification. |
| `USE_FULL_SCREEN_INTENT` | Install-time; user-revocable special access on Android 14+ | Show the alarm screen over the lock screen. Without it, a heads-up notification is used. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_SPECIAL_USE` | Install-time | Keep running while a macro runs (`specialUse`) and play the alarm sound in the background (`mediaPlayback`). |
| `WAKE_LOCK` | Install-time | Keep the CPU awake (bounded) so waits and snooze timers fire on time with the screen off. |
| `VIBRATE` | Install-time | Vibrate action and alarm vibration. |
| Package visibility `<queries>` for launcher apps | Manifest declaration | List apps in the app picker and show app names. |

Not used: no internet permission yet (it arrives only with the user-configured HTTP action in M3), no location, contacts, SMS or call permissions, and no cloud backup.

## Testing without waiting for real notifications

- **In the app:** Settings → Diagnostics → **Event simulator** sends a Gmail / WhatsApp / custom notification through the real parsers and engine (runs are marked *Simulated*).
- **From your computer (debug builds only):**

  ```bash
  adb shell am broadcast -a com.priorityring.debug.SIMULATE \
    -n com.priorityring.app.debug/com.priorityring.app.debug.DebugEventReceiver \
    --es preset gmail --es sender "Rahul Sharma" --es text "Quarterly report" --es key t1
  # preset: gmail | whatsapp | whatsapp_group | custom   extras: sender, text, group, package, key
  adb logcat -s PriorityRing
  ```

  The receiver only exists in debug builds and only accepts broadcasts from the shell (it requires the `DUMP` permission). Reusing a `key` with the same text tests duplicate handling.
- **A real Android notification** (posted by the shell app `com.android.shell`, so use a trigger with "any app"). This is the AOSP `cmd notification` tool; OEM builds may differ:

  ```bash
  adb shell cmd notification post -S messaging --conversation "Family" --message "Rahul:urgent call me" -t "Family" test1 "urgent call me"
  ```
- **Inspector:** Settings → Diagnostics → **Inspector** shows how recent real notifications were parsed, with a redacted dump of their extras to share for debugging.

## Privacy

Everything stays on the device. No analytics, no account, no cloud backup. The only network use is an HTTP/webhook action **that you configure yourself**. By default, run history stores the time, trigger, app, parsed sender, macro and outcome, but not notification text (that's opt-in and truncated). History is kept for 1, 7 or 30 days (default 7).

## Documents

- `PROGRESS.md`: decisions, status, next steps, known issues.
- `BLOCKS.md`: every block, its tier, permissions and test status.
- `LIMITATIONS.md`: what Android doesn't allow, in plain language.
- `DEVICE_TEST_CHECKLIST.md`: what to test on a real phone.
- `ROADMAP.md`: what comes after v1.
- `docs/android-api-notes.md`: research notes on version-specific Android behaviour, with sources.

## Licences

Bundled fonts (Inter, Space Grotesk, JetBrains Mono) are under the SIL Open Font License 1.1; see `/licenses`.
