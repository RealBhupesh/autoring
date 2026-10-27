# PriorityRing

**Make sure what matters reaches you, and make your phone react the way you want.**

PriorityRing is an Android automation engine in the spirit of MacroDroid and Tasker. You build *macros* from blocks:

```
WHEN  [one or more triggers]          any one fires the macro
IF    [conditions, AND / OR / NOT]    all evaluated with recorded reasons
THEN  [ordered actions]               with Wait / If / Repeat / Stop control blocks
```

"Ring when my boss emails" is not a feature. It's a macro anyone can assemble from generic blocks.

> **Status: Milestone 1 (skeleton).** The engine contracts, storage, theme and screens exist. Notification monitoring, alarms and the macro builder arrive in M2. See `PROGRESS.md`.

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

| Permission / access | Why | Since |
|---|---|---|
| `POST_NOTIFICATIONS` | Show alerts and the alarm notification (runtime permission on Android 13+) | M1 (declared) |
| Notification access (listener) | Read other apps' notifications so they can trigger macros | M2 |
| Others (exact alarms, full-screen intent, DND access, modify settings, usage access, foreground service types) | Added with the blocks that need them, each documented here | M2+ |

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
