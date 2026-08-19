# راصد · Rasid — Warehouse Inventory Management (Android)

An Arabic-first (RTL), Material 3 warehouse inventory system for Android. It covers the full
stock lifecycle — **receiving, issuing, adjustments and warehouse-to-warehouse transfers** —
with a complete transaction history, an audit trail, barcode scanning, exportable reports and
local backup/restore.

Built with **Kotlin, Jetpack Compose, MVVM, Hilt, Room**, CameraX + ML Kit, DataStore and Coil.

---

## Contents

- [Build and run](#build-and-run)
- [Default login](#default-login)
- [Features](#features)
- [Roles and permissions](#roles-and-permissions)
- [Branding](#branding)
- [Architecture](#architecture)
- [Database and migrations](#database-and-migrations)
- [Testing](#testing)
- [Project layout](#project-layout)
- [Dependencies](#dependencies)
- [Extending to Firebase](#extending-to-firebase-later)
- [Known limitations](#known-limitations)

---

## Build and run

### Requirements

| | |
|---|---|
| Android Studio | Ladybug (2024.2) or newer |
| JDK | 17 (bundled with recent Android Studio) |
| Android SDK | Platform 35 (Studio offers to install it on first open) |
| Minimum device | Android 7.0 (API 24) |
| Target / compile SDK | 35 |
| App version | `versionName 2.0` / `versionCode 2` |

### Producing the APK

```bash
# from the project root
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
```

Release build (unsigned — configure a signing config before installing on a device):

```bash
./gradlew assembleRelease      # → app/build/outputs/apk/release/app-release-unsigned.apk
```

Or open the folder in Android Studio, let it sync (it downloads the SDK and dependencies), and
press **Run**. Studio writes `local.properties` with your `sdk.dir` automatically.

Install the debug build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Running the tests

```bash
./gradlew testDebugUnitTest    # 66 unit tests; HTML report under app/build/reports/tests/
```

> **Build status.** The project **compiles and all 66 unit tests pass**, verified by the
> `Android CI` workflow on GitHub's runners
> ([run 32272117923](https://github.com/adhamalama1000-crypto/fackre/actions/runs/32272117923),
> commit `be1a5f9`): `assembleDebug` succeeded in about 4 minutes and `testDebugUnitTest`
> reported no failures. Every push and pull request rebuilds and re-tests, and each run
> uploads the debug APK as a downloadable `app-debug-apk` artifact — that is the quickest way
> to get an installable build without a local Android SDK.
>
> The authoring environment itself cannot build this project: its egress policy denies
> `dl.google.com`, which is the only host serving the Android Gradle Plugin, the
> AndroidX/Compose/Room artifacts and the SDK (none are published to Maven Central). That is
> why CI exists, and why CI — not this file — is the authority on build status.

---

## Default login

Seeded on first launch, along with seven Arabic starter categories and one default warehouse
(`المخزن الرئيسي` / code `MAIN`).

| Field | Value |
|---|---|
| Email | `admin@warehouse.com` |
| Password | `admin123` |
| Role | Admin |

Create further users from **Dashboard → المستخدمون**. Change the seeded password before real
use.

---

## Features

### Stock operations

All four run inside a single Room transaction and are recorded in the unified history. Stock
can never go negative, and no path changes a quantity without writing a transaction row.

- **Stock-In / receiving** — product, quantity, supplier, employee, warehouse, date, invoice /
  shipment number, notes. `new = current + received`.
- **Stock-Out / issuing** — product, quantity, employee, warehouse, date, notes. Refused when
  it would exceed what the warehouse holds.
- **Stock Adjustment** — for when the physical count disagrees with the system. Records
  previous quantity, new quantity, signed difference, and a required reason (damaged, lost,
  counting error, manual correction, other).
- **Stock Transfer** — moves stock between warehouses as two paired legs (`TRANSFER_OUT` /
  `TRANSFER_IN`) sharing one `transferGroupId`. Atomic: the destination is never credited if
  the source debit fails.

### Inventory transaction history

One history covering every movement type, replacing the old stock-out-only log. Each row shows
the type, product, quantity or signed difference, employee, warehouse, date, notes, and the
stock level before and after. Filterable by type, product, employee, warehouse, supplier,
category and date range, with free-text search across product, SKU, employee, supplier,
invoice number, warehouse and notes.

### Barcode / QR scanning

CameraX preview with ML Kit barcode recognition — the model is **bundled in the APK**, so
scanning works with no network. Products carry a barcode and a SKU, both uniquely validated.
The scanner is reachable from the dashboard (scan → open the product) and from the stock-in,
stock-out, adjustment and transfer forms (scan → product selected → enter quantity → confirm).
An unrecognised code produces a clear Arabic message and leaves the camera open for the next
label. Lookup matches barcode first, then falls back to SKU.

### Reports and export

Current inventory, low stock, stock-in, stock-out, adjustments, inventory movement, top issued
products, and daily / monthly activity. Filterable by date period, warehouse, category,
product, employee and transaction type. Every report exports to **CSV** or **PDF**; files are
written to `cacheDir/exports`, shared through the existing `FileProvider`, and pruned after a
day. Arabic text is written UTF-8 with a BOM so Excel opens it correctly.

### Audit log

Records logins and logouts, product / category / user / supplier / employee / warehouse
creates, edits and deletes, all four stock operations, backup and restore, and **every refused
privileged attempt**. Each entry carries the acting user, action, target, timestamp and
details. Reading it is admin-only, enforced in the repository — a viewer gets an empty stream,
not a hidden button.

### Backup and restore

A single `.zip` containing the database and every product photo, saved wherever the user likes
through the system file picker (no storage permission needed). A candidate file is validated
before anything is touched — it must carry a manifest naming this app, a format and schema
version this build understands, a database whose SHA-256 matches the manifest, and a real
SQLite header. It is extracted to a staging directory first and only swapped in once every
check passes, and the archive cannot write outside staging (zip-slip is blocked by keeping
only each entry's basename). Restore requires an explicit confirmation, clears the session,
and restarts the process.

### Low-stock handling

A product is low when `quantity <= lowStockThreshold`. Notifications fire on the *transition*
into low stock and stay quiet while it remains low no matter how many further movements it
sees; the warning is cancelled once the product is restocked, so a later dip notifies again.
Low-stock products are also listed on the dashboard and available as a report and an inventory
filter.

### Reference data

- **Suppliers** — name, phone, email, address, notes. Selectable on stock-in.
- **Employees** — name, employee code, phone, department, notes, active flag. Selectable on
  every stock operation. Kept separate from app *users*: an employee is someone stock is
  issued to, a user is someone who signs in.
- **Warehouses** — name, code, location, notes, default flag. Products and stock are held per
  warehouse, with transfers between them.

### Product identification and costing

Product id, SKU, barcode, name, category, unit of measure (piece, box, carton, pack, meter,
kilogram, liter), and optional purchase cost / selling price. Units appear wherever a quantity
is shown ("25 قطعة"). SKU and barcode are uniquely indexed; blanks are stored as `NULL` so any
number of products may have neither. Inventory value is computed from purchase cost and is
**never shown to viewers** — the ViewModel emits `null` for them, so the SUM query does not
even run.

### Dashboard

Total products, total quantity, low-stock count, stock-in today, stock-out today, warehouse
count, and (admin only) inventory value. Quick actions for stock-in, stock-out, scan, add
product, inventory and reports; a low-stock preview; recent activity; and a grid of every
section, filtered by role.

### Other

Splash screen, light/dark/system appearance choice, PBKDF2 password hashing with a per-user
salt, Arabic-first RTL throughout, and Material 3 empty / loading / error states with
confirmation dialogs and snackbars.

---

## Roles and permissions

| | Admin | Viewer |
|---|---|---|
| Dashboard, inventory, search, history, reports | ✅ | ✅ |
| Product / category create, edit, delete | ✅ | — |
| Stock-in, stock-out, adjustment, transfer | ✅ | — |
| Suppliers, employees, warehouses | ✅ | — |
| Users | ✅ | — |
| Audit log | ✅ | — |
| Backup / restore | ✅ | — |
| Purchase cost, selling price, inventory value | ✅ | — |

Enforcement is **not** the hidden buttons. `PermissionChecker` sits below the ViewModels and:

1. re-reads the role from the `users` table by id rather than trusting the cached session —
   the session lives in a DataStore file that is writable on a rooted device, so a role taken
   from it would be a client-side claim;
2. records every rejection as `PERMISSION_DENIED` in the audit log.

A viewer reaching a privileged repository call by any route gets `NotAuthorized` back. There
are tests for each of these paths.

---

## Branding

**Name: راصد (Rasid)** — Arabic for *observer* / *monitor*, the one who keeps watch and keeps
records. Two syllables, pronounceable in both languages, reads as a real product name in
Arabic and transliterates cleanly to English.

It was chosen over four alternatives: **مخزون / Makhzoon** (accurate but generic — it just
means "inventory"), **جرد / Jard** (means "stocktake" specifically, too narrow for a system
that also receives and transfers), **مستودع / Mustawda** (means "warehouse", four syllables,
awkward as an app name), and **رصيد / Raseed** (means "balance"; too close to banking, and a
letter away from Rasid, which would confuse both). Rasid won because it names what the app
*does for the user* — watching stock — rather than the object it manages, which leaves room
for the product to grow beyond a single warehouse.

The **logo** is an isometric cube on a rounded navy plate: the cube reads as a carton or a
stock unit, its three visible faces suggest depth and organisation, and the amber top face is
the one the eye lands on. It is a pure vector drawn on a 96-unit grid, so it stays sharp at
launcher, splash and login sizes, and it is original — no existing company's mark is used or
referenced. The palette is deep navy `#0B4A8F` → `#002F5F` with an amber accent `#F5A524`,
picked for legibility on a cheap phone screen under fluorescent light rather than for
vibrancy. Full Material 3 role sets are defined for light and dark.

Assets: `ic_brand_mark.xml` (in-app, with plate), `ic_launcher_foreground.xml` /
`ic_launcher_background.xml` / `ic_launcher_monochrome.xml` (adaptive launcher icon, themed
icon included), `splash_window_background.xml` (cold-start layer). The name appears in the app
label, splash, login, dashboard header and the About section.

`applicationId` is deliberately **unchanged** at `com.warehouse.inventory`: it is the app's
permanent identity on the device and in Play, and changing it would orphan every existing
install's database instead of upgrading it. Only the user-visible name changed.

---

## Architecture

```
UI (Jetpack Compose screens)        ← state via StateFlow / collectAsStateWithLifecycle
  └─ ViewModel (MVVM, @HiltViewModel)
       └─ Repository (@Singleton)   ← PermissionChecker guards privileged operations here
            └─ Room DAO  ──►  Room database (SQLite)
       └─ SessionManager / AppPreferences (DataStore)
```

- **DI** — Hilt (`SingletonComponent`) provides the database, nine DAOs and the repositories.
- **Concurrency** — coroutines and Flow; every stock mutation runs in `db.withTransaction`.
- **Navigation** — a single session-gated `NavHost`. Three top-level states rather than
  routes: loading shows the splash, signed-out gets a graph containing only login, signed-in
  gets the full graph. Because the signed-in graph has no login route, back can never walk
  into a stale authenticated screen, and signing out swaps the whole graph.
- **Scanner result passing** — the scanner is a full destination, not an embedded camera. It
  writes the code onto the *previous* back stack entry's `SavedStateHandle` and pops itself;
  the calling ViewModel observes that key and clears it once consumed, which is what lets a
  user scan repeatedly without leaving a form.
- **Images** — stored as files under `getExternalFilesDir("Pictures")/products`, captured via
  `FileProvider`; only the path is persisted, and Coil down-samples on load.
- **Enum labels** — enums are stored by their English `name` and mapped to Arabic string
  resources in `util/Labels.kt`, with exhaustive `when` blocks so adding a constant without a
  label is a compile error. This is what keeps `STOCK_IN` out of the Arabic UI.

---

## Database and migrations

Room database `warehouse.db`, **schema version 2**, `exportSchema = true` (JSON written to
`app/schemas/` at build time).

### Entities

| Table | Purpose |
|---|---|
| `users` | App accounts. PBKDF2 hash + per-user salt. |
| `categories` | Product categories. Unique name. |
| `products` | Catalogue + cached total quantity, SKU, barcode, unit, cost, price. |
| `suppliers` | Unique name. |
| `employees` | Unique code when set. |
| `warehouses` | Unique code, one default. |
| `product_stock` | Per-warehouse stock level. Unique `(productId, warehouseId)`. |
| `inventory_transactions` | Unified history: stock-in, stock-out, adjustment, transfer legs. |
| `audit_logs` | System action trail. |

`products.quantity` is a **maintained cache** of `SUM(product_stock.quantity)`. Only
`InventoryRepository` writes it, and only in the same transaction as the matching
`product_stock` row, so the two cannot drift. A test asserts they stay in step across a
sequence of mixed operations.

Foreign keys use `ON DELETE SET NULL` toward history and `CASCADE` for `product_stock`, and
history rows denormalise the product, warehouse, supplier and employee **names**. Deleting a
product therefore removes the product but preserves what moved — the row keeps the name and
nulls the id.

Every table carries `syncId`, `updatedAt` and `synced`.

### Migration 1 → 2

`fallbackToDestructiveMigration()` was **removed**. It was previously set, which meant a
schema change would silently erase a warehouse's entire stock history — unacceptable for real
data. Every schema change now ships a real migration, and the DDL in `Migrations.kt` is
written to match Room's generated schema column for column (types, order, `IF NOT EXISTS`,
Room's `index_<table>_<columns>` naming, `ON UPDATE NO ACTION` on every foreign key), because
Room re-reads the schema with PRAGMA after migrating and throws if anything differs.

What v1 → v2 does:

1. Creates `suppliers`, `employees`, `warehouses`, `product_stock`,
   `inventory_transactions` and `audit_logs` with their indexes.
2. Adds `sku`, `barcode`, `unit` (NOT NULL with a `'PIECE'` default, which is why the entity
   also declares `@ColumnInfo(defaultValue = "PIECE")`), `purchaseCost` and `sellingPrice` to
   `products`, plus unique indexes on `sku` and `barcode`.
3. **Preserves existing data:** creates one default warehouse (`المخزن الرئيسي` / `MAIN`) and
   moves every product's current quantity into it as a `product_stock` row.
4. Copies every `stock_out` row into `inventory_transactions` as a `STOCK_OUT`, carrying the
   original `syncId`, date, employee and notes. `previousQuantity` / `newQuantity` are left
   `NULL` because v1 never recorded them — the UI shows "—" rather than inventing numbers —
   and `productId` is nulled where the referenced product has since been deleted (v1 had no
   foreign key there).
5. Drops `stock_out`.

Rows the migration creates get a `syncId` generated in SQL in the same RFC-4122 v4 shape
`UUID.randomUUID().toString()` produces, so a future sync layer needs no special case for
them. `MigrationV1ToV2Test` covers all of this.

---

## Testing

**66 unit tests** in `app/src/test/`, run with `./gradlew testDebugUnitTest`. They execute on
the JVM through Robolectric so Room runs against **real SQLite** — the guarded
`WHERE quantity >= :amount` UPDATEs that prevent negative stock are the whole point, and
mocked DAOs would prove nothing about them.

| Suite | Covers |
|---|---|
| `StockOperationsTest` | All four operations; before/after/difference history rows; refusal to exceed stock; issue-to-exactly-zero; future dates; a failed transfer crediting nothing; warehouse rows and cached total staying in step across mixed operations. |
| `RolePermissionTest` | Every stock operation refused for a viewer and for a signed-out caller; product creation refused; denials written to the audit log; role re-read from the database after a live demotion; viewer reading the audit log gets nothing; admin succeeds (so the refusals are not a broken fixture). |
| `ProductValidationTest` | Duplicate SKU and barcode rejected with the right field named; a product keeps its own codes when edited; many products with no codes; blank name rejected; lookup by barcode and by SKU including whitespace; opening balance written as stock *and* history; editing cannot move stock; deleting preserves history. |
| `MigrationV1ToV2Test` | A hand-built v1 database migrated, then opened through Room (which validates the schema by PRAGMA and throws on any mismatch); quantities preserved; default warehouse owns pre-existing stock; stock-out history folded in; orphan-product row survives with a null id; `stock_out` dropped; generated `syncId` well-formed. |
| `BackupRestoreTest` | Backup/restore round trip; rejection of non-zip, missing manifest, foreign app, newer schema and checksum mismatch; a rejected restore leaving current data untouched; a zip-slip entry unable to escape staging. |
| `DateUtilsTest` | Business-date bounds, today / last-N-days / month ranges, Latin-digit formatting, report grouping keys. Plain JUnit, no Robolectric. |

The v1 schema is built with raw DDL rather than Room's `MigrationTestHelper`, because that
helper loads a historical schema from `schemas/1.json` and no v1 schema was ever exported —
v1 shipped with destructive migration. Hand-writing the old DDL is what makes the upgrade path
testable at all.

### Static verification performed here

Since no compiler could be run, the tree was checked programmatically:

- Every `R.string` / `R.drawable` / `R.color` / `R.style` reference resolves; the Arabic and
  English string sets are identical (**346 keys each**), so no English label can leak into the
  Arabic UI through a missing key.
- Every `NavHost` call site matches its screen's signature — no unknown or missing parameter.
- Every `viewModel.x` reference from a screen exists on that screen's ViewModel.
- Every repository / DAO / manager method called from a ViewModel exists on that type.
- Every internal import resolves to a declared symbol; braces and parens balance in all 101
  main and 7 test files.

This found four real defects, now fixed: `LabeledDropdown`'s label lambdas were not
`@Composable` although `AdjustmentScreen` called `stringResource` inside one (a compile
error); `ReportsViewModel` was missing the `shareIntent` passthrough `ReportsScreen` calls;
logout never wrote its audit entry; and `MainActivity` never switched off the splash window
background despite `themes.xml` documenting that it did.

**Static analysis is not a compile.** Type inference, Compose compiler restrictions, KSP code
generation and Hilt graph resolution can only be confirmed by building. Please run
`./gradlew assembleDebug testDebugUnitTest` and treat anything it reports as the real result.

### Manual checks worth doing on a device

1. Log in as admin; create a warehouse, a supplier and an employee.
2. Add a product with a barcode and an opening balance; confirm it appears in inventory with
   its unit.
3. Scan the barcode from the dashboard — the product should open.
4. Receive 20, issue 5, adjust to a lower count with a reason, transfer some to the second
   warehouse. Check the history shows five rows with correct before/after values.
5. Try to issue more than is on hand — expect an Arabic refusal and no change.
6. Export a report to CSV and PDF; open both and check the Arabic renders.
7. Create a viewer user, sign in as them, and confirm the admin sections and the cost fields
   are absent.
8. Back up, add a product, restore, and confirm the added product is gone and the app restarts
   to the login screen.
9. Toggle appearance between light, dark and system.
10. Confirm every screen lays out right-to-left with the back arrow pointing correctly.

---

## Project layout

```
app/src/main/java/com/warehouse/inventory/
├─ data/local/            Room database, 9 entities, 9 DAOs, converters, migrations
├─ data/repository/       Product / Inventory / Category / User / Supplier / Employee /
│                         Warehouse / AuditLog repositories, PermissionChecker, results
├─ data/session/          SessionManager, AppPreferences (theme, notification state)
├─ di/                    Hilt module
├─ util/                  PasswordHasher, DateUtils, ImageStorage, Labels, CsvWriter,
│                         PdfReportWriter, ExportManager, DatabaseBackupManager,
│                         LowStockNotifier
├─ ui/theme/              Rasid palette, typography, shapes, transaction colours
├─ ui/common/             Shared top bar, states, dialogs, dropdown, date field, pills
├─ ui/navigation/         Route, NavKeys, RootViewModel, ScannerViewModel, AppNavHost
└─ ui/…                   splash, auth, dashboard, products, inventory, stock (in/out/
                          adjustment/transfer), history, categories, suppliers, employees,
                          warehouses, users, audit, reports, scanner, settings
app/src/test/java/…       6 test suites + TestEnvironment fixture
```

---

## Dependencies

Existing stack kept as-is (AGP 8.7.2 · Kotlin 2.0.21 · KSP 2.0.21-1.0.25 · Hilt 2.52 · Room
2.6.1 · Compose BOM 2024.10.01 / Material 3 1.3.0 · Navigation 2.8.4 · Coil 2.7.0 · DataStore
1.1.1 · Gradle 8.10.2).

Added for the new features:

| Dependency | Why |
|---|---|
| `androidx.camera:camera-{core,camera2,lifecycle,view}` 1.3.4 | Camera preview and frame analysis for the scanner. |
| `com.google.mlkit:barcode-scanning` 17.3.0 | Barcode / QR recognition, **model bundled** so scanning works offline — warehouses are frequently on a captive network, and a scanner that silently fails until it can reach Play services is worse than a larger install. |
| `org.robolectric:robolectric` 4.13 *(test)* | Runs Room against real SQLite on the JVM. |
| `androidx.room:room-testing` *(test)* | Migration test support. |
| `androidx.test:core`, `androidx.test.ext:junit` *(test)* | `ApplicationProvider`, test runner. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` *(test)* | `runTest`. |
| `app.cash.turbine:turbine` 1.1.0 *(test)* | Flow assertions. |

No dependency was added for CSV or PDF: `CsvWriter` is a small writer of our own and
`PdfReportWriter` uses the platform's `android.graphics.pdf.PdfDocument`, which keeps Arabic
shaping in the hands of the system text stack and avoids pulling in a PDF library.

New permissions: `CAMERA` (scanner) and `POST_NOTIFICATIONS` (low-stock alerts, requested at
runtime on API 33+). Camera is declared `required="false"` so a camera-less device can still
install and use everything except the scanner.

---

## Extending to Firebase (later)

Every entity exposes `syncId` (remote id), `updatedAt` (last-write-wins clock) and `synced`
(dirty flag). A sync worker can push rows where `synced = 0`, pull remote changes newer than
the local `updatedAt`, then flip `synced = 1`. `inventory_transactions` rows are append-only,
which makes them safe to replicate without conflict resolution. No local schema change is
needed to add this.

---

## Known limitations

Stated plainly rather than left to be discovered:

- **The build runs only in CI, not in the authoring environment**, which cannot reach
  `dl.google.com`. This is not a limitation of the project — `assembleDebug` and all 66 tests
  pass on GitHub's runners and will pass in Android Studio — but it does mean CI is where
  build results come from. See the note under [Build and run](#build-and-run).
- **No instrumented (on-device) tests.** The 66 tests are JVM/Robolectric unit tests. Camera
  scanning, the SAF pickers, notification delivery and PDF Arabic shaping still need a real
  device; see the manual checklist below.
- **No device screenshots.** The SVGs in `docs/screenshots/` are design mockups of the
  **version 1.0** UI and are now outdated — they predate the Rasid branding, the redesigned
  dashboard and every new screen. They are kept only as history.
- **PDF export uses the platform PDF writer.** Arabic shaping and RTL line breaking come from
  the system text stack; complex ligatures should be spot-checked on a device. CSV is UTF-8
  with a BOM so Excel opens Arabic correctly.
- **Reports are computed on demand**, not cached. On a very large history (hundreds of
  thousands of rows) generation will be noticeably slower; the queries are indexed on `date`,
  `type`, `productId`, `warehouseId`, `supplierId` and `employeeId`, but no pagination or
  pre-aggregation exists yet.
- **Backup is local only.** There is no scheduled or cloud backup; the user chooses when to
  create one and where it goes.
- **Restore requires a process restart**, which the app performs itself after confirmation.
  This is deliberate: the Room instance in the Hilt graph is closed and its file replaced
  underneath it, and only a fresh process is guaranteed to open the new file cleanly.
- **No multi-device sync yet** — the schema is prepared for it (above) but no sync layer is
  implemented.
- **Inventory value uses purchase cost only** and applies no currency formatting or FIFO/
  weighted-average costing; it is `SUM(quantity × purchaseCost)`.
