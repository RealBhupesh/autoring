# Block catalogue

**Tiers:** 1 = works for normal apps, possibly after a user grant. 2 = needs Shizuku or an adb grant (planned add-on). 3 = impossible without root (listed in `LIMITATIONS.md`).

**Device status:** ⬜ not yet tested on the phone · ✅ works on iQOO Neo 10 · ⚠️ partial (see notes) · ❌ doesn't work.

## Conditions

| Id | Name | Tier | Permissions | Android notes | Unit tests | Device |
|---|---|---|---|---|---|---|
| `condition.text_match` | Text matches (contains / exact / starts / ends / regex, any-of lines) | 1 | — | Pure logic. Sender names can be spoofed (a warning is shown in the builder). | ✅ | ⬜ |
| `condition.time_window` | Time window, crosses midnight, optional days | 1 | — | Pure logic. Uses the phone's local time zone. | ✅ | ⬜ |
| `condition.days` | Days of week | 1 | — | Pure logic | ✅ | ⬜ |

*Triggers and actions arrive from M2. The planned v1 list, with researched tiers, is in the build brief and `docs/android-api-notes.md`. Each block is added here as it's implemented.*
