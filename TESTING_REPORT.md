# Verification Report — راصد · Rasid v2.0

**What was actually done, and what was not.**

- ✅ **The project compiles.** `assembleDebug` succeeded in CI in ~4 min and produced a
  31.1 MB `app-debug.apk`.
- ✅ **All 66 unit tests pass.** `testDebugUnitTest` reported no failures.
- ✅ **The tree was verified programmatically** before any compile was possible — the checks
  are listed below, along with the six real defects they and CI found.
- ❌ **No emulator or device was used.** There are no screenshots, and no instrumented tests.
  Camera, SAF pickers, notification delivery and PDF Arabic shaping remain device-only.

Verified by the `Android CI` workflow on GitHub's runners:

| Run | Commit | assembleDebug | Tests |
|---|---|---|---|
| [32270748862](https://github.com/adhamalama1000-crypto/fackre/actions/runs/32270748862) | `376c2c9` | ✅ SUCCESS | ❌ 59/66 — 7 failed |
| [32272117923](https://github.com/adhamalama1000-crypto/fackre/actions/runs/32272117923) | `be1a5f9` | ✅ SUCCESS | ✅ 66/66 |

The authoring environment could not build this project at all: its egress policy denies
`dl.google.com`, the only host serving the Android Gradle Plugin, the AndroidX/Compose/Room
artifacts and the SDK (AGP 8.7.2 and `androidx.core:core-ktx` both 404 on Maven Central, and
`maven.google.com` 301-redirects every artifact to the denied host). `./gradlew assembleDebug`
was run there and failed immediately on plugin resolution — which is why the CI workflow was
added, and why CI is the authority on build status.

---

## 1. Automated static verification

Scripted checks run over 101 main-source and 7 test-source Kotlin files.

| Check | Result |
|---|---|
| Every `R.string`, `R.drawable`, `R.color`, `R.style` reference resolves to a declared resource | ✅ 0 unresolved |
| Arabic (`values/`) and English (`values-en/`) string key sets are identical | ✅ 346 keys each, no difference |
| Every `NavHost` call site matches its screen composable's signature (no unknown or missing parameter) | ✅ 41 composables, 0 mismatches |
| Every `viewModel.x` / `viewModel::x` reference from a screen exists on that screen's ViewModel | ✅ 20 ViewModels, 0 missing members |
| Every repository / DAO / manager / checker method called from a ViewModel exists on that type | ✅ 25 types checked, 0 missing |
| Every internal (`com.warehouse.inventory.*`) import resolves to a declared symbol | ✅ 0 unresolved |
| Braces, parens and brackets balance in every file | ✅ balanced |
| Test sources' imports and structure | ✅ 0 unresolved |
| Hilt graph: database + 9 DAOs provided exactly once; every `@Inject` constructor dependency is provided or constructor-injectable | ✅ reviewed in `di/DatabaseModule.kt` |
| Room: 9 entities declared on `@Database`, 9 DAO accessors, `version = 2`, `exportSchema = true` with a `room.schemaLocation` KSP argument | ✅ consistent |

The AR/EN parity check is the one that guards a stated requirement directly: identical key sets
mean no Arabic screen can fall back to an English string through a missing key.

### Defects found and fixed

1. **`LabeledDropdown`'s `label` / `secondaryLabel` were plain lambdas**, but
   `AdjustmentScreen` passed `{ stringResource(AdjustmentReasonLabels.stringRes(it)) }` into
   one. Calling a `@Composable` function from a non-composable function type does not compile.
   Both parameters are now `@Composable`, and the two safe-call/`let` invocation sites inside
   the component were rewritten as explicit conditionals.
2. **`ReportsViewModel` had no `shareIntent`**, which `ReportsScreen` calls to open the share
   sheet after an export. Added as a passthrough to `ExportManager`, keeping the FileProvider
   authority out of the UI layer.
3. **Logout never wrote its audit entry.** `AuditAction.LOGOUT` existed and was never
   recorded. `RootViewModel.logout()` now records it *before* clearing the session, because
   once the session is gone the acting user cannot be resolved.
4. **`MainActivity` never called `setTheme(R.style.Theme_Rasid_Main)`**, despite
   `themes.xml` documenting that it did. The branded cold-start drawable stayed as the window
   background behind every screen — a full-screen overdraw visible through any translucent
   content.

A fifth was caught while reviewing the newly written restore flow: clearing the session inside
`confirmRestore()` flipped the app to its signed-out state immediately, which swapped the
navigation graph and destroyed the settings screen **before the restart dialog could render** —
leaving the user on a login screen backed by a closed database. The sign-out now happens in
`finishRestore()`, ordered immediately before the process is replaced.

A sixth came from CI rather than static analysis, and is the clearest argument for having it:
`gradlew` was committed with mode `100644`, so `./gradlew` failed with `Permission denied` on
any fresh clone. Nothing short of running the build would have caught that.

CI also reported three deprecations, two of which were real RTL bugs rather than warnings:
`Icons.Filled.Assignment` (audit log) and `Icons.Filled.FactCheck` (dashboard tile) have
`AutoMirrored` variants, and the non-mirrored glyphs point the wrong way in an Arabic layout.
Both now use the mirrored variants.

---

## 2. Unit tests — 66 written, 66 passing

In `app/src/test/java/com/warehouse/inventory/`. They run on the JVM via Robolectric so Room
executes against **real SQLite**: the negative-stock guarantee is a guarded
`UPDATE … WHERE quantity >= :amount`, and only a real database proves it holds.

The first CI run failed 7 of these, all in `MigrationV1ToV2Test`, all at `RoomOpenHelper:93` —
Room's post-migration schema validation. The cause was in the test: its hand-written v1 DDL
gave `categories` a `createdAt` column that `CategoryEntity` does not declare, so Room found an
extra column and rejected the whole schema, failing every test in the class on a table the
migration never touches. Diffing the migration's DDL against all nine entities confirmed the
migration itself is correct — column sets match and every declared index is created — so real
v1→v2 upgrades were never affected. Fixed in `be1a5f9`; the suite has been green since.

### `StockOperationsTest` — 17 tests

| Behaviour asserted | Why it matters |
|---|---|
| Stock-in `50 + 20 = 70`, in both the warehouse row and the cached product total | The core arithmetic, and that the cache cannot drift |
| Stock-in writes a history row with `previous=50`, `new=70`, `difference=+20`, invoice number and acting user | "Never silent" |
| Stock-in rejects quantity `0` and `-5`, and future dates | Validation |
| Stock-out `50 − 20 = 30` | Core arithmetic |
| Stock-out of 11 from 10 → `InsufficientStock`, **nothing changed and nothing logged** | Negative stock impossible; failure unwinds the transaction |
| Stock-out down to exactly `0` succeeds | Off-by-one in the guard |
| Stock-out from a warehouse holding none is refused even though other warehouses have stock | Per-warehouse levels, not a global total |
| Adjustment `100 → 97` records `difference = −3`, reason `DAMAGED`, magnitude `3` | The requirement's worked example |
| Adjustment `40 → 45` records `+5` | Upward correction |
| Adjustment to the same quantity → `NoChange`, no history row | No empty rows |
| Adjustment to a negative quantity refused | Validation |
| Transfer of 3 from A(10) → B(0) leaves A=7, B=3, **total unchanged at 10** | Transfers relocate, never create |
| Transfer writes two legs sharing one `transferGroupId`, each naming the other warehouse | Pairable in reports |
| Transfer of 5 from A(2) → `InsufficientStock` with **B still 0** and no history | Atomicity — the case that would corrupt stock if the legs were not one transaction |
| Transfer to the same warehouse → `SameWarehouse` | Validation |
| A sequence (in 100, out 30, transfer 20, adjust to 18) leaves A=50, B=18, total=68, 5 history rows all with non-null before/after | End-to-end consistency |

### `RolePermissionTest` — 10 tests

Stock-in, stock-out, adjustment, transfer and product creation are each refused for a viewer;
a signed-out caller is refused; refusals are written to the audit log with the operation name
and the acting user; a viewer reading the audit log receives an empty list even though rows
exist; and — the important one — **demoting a user's row while their session is live
immediately revokes access**, proving the role is read from the `users` table and not from the
session file. A final test confirms the same call succeeds as an admin, so the refusals are
authorisation and not a broken fixture.

### `ProductValidationTest` — 11 tests

Duplicate barcode and duplicate SKU each rejected with the correct field named; a product
re-saved with its *own* unchanged codes does not collide with itself; three products with no
codes coexist (blanks stored as `NULL`, since two products sharing `""` would collide under the
unique index); blank name rejected; lookup by barcode, by SKU, and with surrounding whitespace;
unknown and blank codes return null; an opening balance is written as stock **and** as a
`STOCK_IN` row; a zero opening balance writes no row; **editing a product ignores a changed
quantity** (that has to be an adjustment, with a reason); deleting a product preserves its
history with the name intact and the id nulled.

### `MigrationV1ToV2Test` — 7 tests

A schema-v1 database is built with raw DDL — holding a category, two products and two
stock-outs, one of them orphaned — then opened through Room. Room re-reads the schema with
PRAGMA after migrating and throws on any mismatch, so **the open succeeding is itself the
schema assertion**. Then: product quantities preserved and new columns defaulted (`unit =
PIECE`, codes null); one default warehouse created holding all pre-existing stock; stock-out
rows folded into `inventory_transactions` with date, employee, notes and original `syncId`
carried over and `previousQuantity`/`newQuantity` left null; the orphaned row surviving with a
null `productId` and its name intact; `stock_out` dropped; generated `syncId` matching the
RFC-4122 v4 shape.

The v1 schema is hand-written rather than loaded via `MigrationTestHelper` because no
`schemas/1.json` was ever exported — v1 shipped with `fallbackToDestructiveMigration()`.

### `BackupRestoreTest` — 9 tests

Round trip: back up, diverge the data, restore, confirm the backup's rows are back and the
divergence is gone. Rejections: random bytes, a zip with no manifest, a manifest naming
another app, a newer schema version, and a database whose SHA-256 disagrees with the manifest.
A rejected restore leaves the live database untouched. And a **zip-slip** entry
(`images/../../../files/marker.txt`) cannot write outside the staging directory.

### `DateUtilsTest` — 12 tests

Business-date bounds (today valid, end-of-today valid, future rejected, epoch-zero rejected),
`todayRange` half-open by exactly one day, `lastDaysRange` inclusive at both ends, `monthRange`
covering the calendar month, Latin-digit formatting, and the ISO/month keys matching the
SQLite `strftime` patterns the activity reports group by. Plain JUnit — no Robolectric — so
this part of the suite is fast.

---

## 3. Not verified — please check on a device

| Area | What to confirm |
|---|---|
| ~~Compilation~~ | Done — `assembleDebug` passes in CI, including KSP, Hilt and the Compose compiler. |
| ~~Test run~~ | Done — 66/66 pass in CI. |
| Camera / scanner | Real barcode and QR labels, permission grant and denial, and the "code not found" path. Cannot be exercised without hardware. |
| PDF Arabic shaping | The platform PDF writer handles the text; complex ligatures should be eyeballed in a real reader. |
| CSV in Excel | Written UTF-8 with a BOM; confirm Arabic columns open correctly. |
| SAF backup / restore | The system file picker, and the self-restart after restore. |
| Notifications | Runtime `POST_NOTIFICATIONS` grant on API 33+, and that a product notifies once per low-stock episode rather than on every movement. |
| RTL layout | Every screen, particularly the dropdowns, date picker and the auto-mirrored back arrow. |
| Dark mode | All screens, plus the appearance setting persisting across restart. |
| Rotation | Form state survives configuration change (state lives in ViewModels). |
| Migration on real data | Install v1, add products and stock-outs, then upgrade. The test covers the logic; a real upgrade covers the file. |

---

## 4. Requirement coverage

| # | Requirement | Status |
|---|---|---|
| 1 | Stock-In / receiving | Implemented + tested |
| 2 | Barcode / QR scanner | Implemented; camera not exercisable here |
| 3 | Stock adjustment with reasons | Implemented + tested |
| 4 | Complete inventory history with filters | Implemented |
| 5 | Audit log, admin-only | Implemented + tested |
| 6 | Backup and restore with validation | Implemented + tested |
| 7 | Low-stock notifications, no duplicates | Implemented; delivery not exercisable here |
| 8 | Suppliers | Implemented |
| 9 | Employees (separate from users) | Implemented |
| 10 | SKU / barcode uniqueness | Implemented + tested |
| 11 | Product units shown with quantities | Implemented |
| 12 | Multiple warehouses | Implemented + tested |
| 13 | Stock transfer, atomic, no negatives | Implemented + tested |
| 14 | Reports with filters | Implemented |
| 15 | CSV and PDF export | Implemented; Arabic rendering needs a device check |
| 16 | Dashboard redesign | Implemented |
| 17 | Product cost / inventory value, hidden from viewers | Implemented |
| 18 | Material 3 UI/UX, RTL, states, dialogs | Implemented |
| 19 | App name (5 options, one chosen) | راصد / Rasid — see README |
| 20 | Logo and brand identity | Original vector assets; see README |
| 21 | Splash screen | Implemented |
| 22 | Login redesign with validation | Implemented |
| 23 | Security: hashing, permissions below the UI | Implemented + tested |
| 24 | Room architecture, FKs, indexes, transactions, sync fields | Implemented + tested |
| 25 | Data validation with Arabic messages | Implemented + tested |
| 26 | Project inspection and tests | Static checks above; **66 tests written and passing in CI**, compile verified |
