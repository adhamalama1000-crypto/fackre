# مخزن الجرد — Warehouse Inventory (Android)

An Arabic‑first (RTL), Material 3 warehouse inventory management app for Android.
It replaces manual stock counting: you do a **one‑time initial count**, then every
**Stock‑Out** operation decrements the stock and is recorded in a history log.

Built with **Kotlin, Jetpack Compose, MVVM, Hilt, Room**, dark/light theming and full
right‑to‑left (RTL) Arabic UI.

---

## ⚠️ About APKs & screenshots in this delivery

This project was assembled in a sandbox that has **no Android SDK** and **blocks Google's
Maven repository** (`dl.google.com` / `maven.google.com` return HTTP 403). A real Gradle
Android build therefore could **not** be executed there, which means:

* I could **not** produce compiled `app-debug.apk` / `app-release.apk` binaries in that
  environment, and
* I could **not** run an emulator to capture real device screenshots.

Everything else is complete and ready. The images in `docs/screenshots/` are **design
mockups** of each screen (clearly labeled), not device captures. **Building the APK on your
machine takes one command** — see below — because your Android Studio *can* reach Google's
Maven and download the SDK. The `TESTING_REPORT.md` documents verification done by
**static code review** of every requested feature; run the steps yourself to confirm on a
device.

---

## Requirements

* **Android Studio** Ladybug (2024.2) or newer
* **JDK 17** (bundled with recent Android Studio)
* **Android SDK Platform 35** (Android Studio will offer to install it on first open)
* Min device: **Android 7.0 (API 24)** or newer

## Build & Run (the one command that produces the APK)

```bash
# from the project root
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
```

Release build (unsigned; configure signing to install on device):

```bash
./gradlew assembleRelease        # → app/build/outputs/apk/release/app-release-unsigned.apk
```

Or simply **open the folder in Android Studio → let it sync (it downloads the SDK &
dependencies) → press ▶ Run**. Android Studio also generates `local.properties` with your
`sdk.dir` automatically; you do not need to create it by hand.

Install the debug APK on a phone:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Default login (seeded on first launch)

| Field    | Value                 |
|----------|-----------------------|
| Email    | `admin@warehouse.com` |
| Password | `admin123`            |
| Role     | Admin                 |

The database also seeds 7 Arabic starter categories. Create additional users (Admin or
Viewer) from **Dashboard → Users**.

## Roles & permissions

* **Admin** — full access: add/edit/delete products, stock‑out, manage users & categories.
* **Viewer** — read‑only: dashboard, inventory list and search. Admin‑only actions (add
  product FAB, edit/delete, stock‑out, users, categories) are hidden and the menu tiles are
  filtered out.

## Features

* Email/password login (PBKDF2‑hashed passwords, per‑user salt — no plaintext storage)
* Dashboard with live stats (total products, total quantity, low‑stock count, units shipped
  today) and quick‑nav tiles
* Products: photo (camera **or** gallery), name, category, quantity, warehouse location,
  notes, per‑product low‑stock threshold
* Unlimited categories
* Inventory grid with search (by name/category) and category filter chips
* Stock‑Out: quantity, employee, date, notes — **never allows negative stock** (enforced
  atomically at the SQL/transaction level)
* Stock‑out history log
* Material 3 dynamic color (Android 12+), light & dark themes
* Full Arabic RTL layout

## Architecture

```
UI (Jetpack Compose screens)         ← state via StateFlow / collectAsStateWithLifecycle
  └─ ViewModel (MVVM, @HiltViewModel)
       └─ Repository (@Singleton)
            └─ Room DAO  ──►  Room Database (SQLite)
       └─ SessionManager (DataStore) for the signed‑in user
```

* **DI:** Hilt (`SingletonComponent`) provides the database, DAOs and repositories.
* **Persistence:** Room (`warehouse.db`). Entities: `User`, `Category`, `Product`,
  `StockOut`. All tables carry `syncId / updatedAt / synced` columns so a future **Firebase
  sync** layer can be added without a schema migration.
* **Concurrency:** Kotlin coroutines + Flow; stock‑out runs inside a Room transaction.
* **Images:** stored as files in app‑specific external storage
  (`getExternalFilesDir("Pictures")/products`), captured via a `FileProvider`; only the file
  path is kept in the DB. Loaded with Coil.

## Project layout

```
app/src/main/java/com/warehouse/inventory/
├─ data/local/        Room DB, entities, DAOs, type converters
├─ data/repository/   User / Category / Product / StockOut repositories
├─ data/session/      SessionManager (DataStore)
├─ di/                Hilt module
├─ util/              PasswordHasher, DateUtils, ImageStorage
├─ ui/theme/          Material 3 colors, typography, theme
├─ ui/navigation/     Route, RootViewModel, AppNavHost (session‑gated)
└─ ui/…               auth, dashboard, products, inventory, stockout,
                      categories, users, history  (screen + ViewModel each)
```

## Tech versions

AGP 8.7.2 · Kotlin 2.0.21 · KSP 2.0.21‑1.0.25 · Hilt 2.52 · Room 2.6.1 ·
Compose BOM 2024.10.01 (Material 3 1.3.0) · Gradle 8.10.2 · compileSdk 35 · minSdk 24.

## Notes for extending to Firebase (later)

Each entity already exposes `syncId` (remote id), `updatedAt` (last‑write‑wins clock) and
`synced` (dirty flag). A sync worker can push rows where `synced = 0` and pull remote
changes newer than the local `updatedAt`, then flip `synced = 1`. No local schema change is
required to add this.
