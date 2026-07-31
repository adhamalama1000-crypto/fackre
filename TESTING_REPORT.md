# Testing Report — Warehouse Inventory

**Method:** Static code‑review verification of the complete source tree, plus dependency,
resource, navigation and DI consistency checks. **These results are code‑level
verifications, not device‑run QA**, because the build sandbox had no Android SDK and could
not reach Google's Maven repo (see README). Each row states exactly *how* it was verified so
you can reproduce the check on a device with the reproduction step given.

Legend: ✅ = verified by review · 📱 = confirm on device after `./gradlew assembleDebug`

---

## Core features

| # | Feature | Status | How it was verified / how to confirm on device |
|---|---------|--------|--------------------------------------------------|
| 1 | Login | ✅ / 📱 | `LoginViewModel` validates empty fields → `LoginError.EMPTY`; calls `UserRepository.login` which PBKDF2‑verifies the hash; success writes to `SessionManager`. Device: log in with `admin@warehouse.com` / `admin123`. |
| 2 | Admin permissions | ✅ / 📱 | `DashboardScreen` filters menu tiles by `adminOnly`; `ProductsScreen` shows add/edit/delete only when `isAdmin`. `isAdmin` derives from `SessionUser.role == ADMIN`. Device: log in as admin, confirm all tiles + product FAB visible. |
| 3 | Viewer permissions | ✅ / 📱 | Same guards hide Stock‑Out, Categories, Users tiles and all mutate actions for viewers. Device: create a Viewer user, log in, confirm read‑only. |
| 4 | Dashboard | ✅ / 📱 | `DashboardViewModel` combines 4 Room aggregate Flows (`observeTotalProducts`, `observeTotalQuantity`, `observeLowStockCount`, `observeUnitsToday`). Device: values update live as data changes. |
| 5 | Add Product | ✅ / 📱 | `ProductFormViewModel.save()` inserts a `ProductEntity`; validation blocks blank name / invalid quantity. Device: add a product, see it in Products & Inventory. |
| 6 | Edit Product | ✅ / 📱 | `load(productId)` populates the form; `save()` updates the existing row (copy). Device: tap a product, change a field, save. |
| 7 | Delete Product | ✅ / 📱 | `ProductsViewModel.delete()` removes the row and its stored image via `ImageStorage.deleteIfExists`. Confirm dialog guards it. |
| 8 | Camera capture | ✅ / 📱 | `ActivityResultContracts.TakePicture` + runtime CAMERA permission + `ImageStorage.createCaptureTarget` (FileProvider Uri). Device: tap "التقاط صورة", grant permission, shoot. |
| 9 | Gallery selection | ✅ / 📱 | `ActivityResultContracts.GetContent("image/*")` → `ImageStorage.copyFromUri` copies into app storage. |
| 10 | Product image storage | ✅ / 📱 | Images saved under `getExternalFilesDir("Pictures")/products`; only the path persists in Room; Coil loads from the `File`. |
| 11 | Categories | ✅ / 📱 | `CategoriesViewModel` add (dedup by unique name) / delete; product FK is `ON DELETE SET NULL`. |
| 12 | Inventory list | ✅ / 📱 | `InventoryScreen` renders `observeAllWithCategory` as image cards with low‑stock coloring. |
| 13 | Product search | ✅ / 📱 | `InventoryViewModel` combines query + category and `flatMapLatest` → `ProductDao.search` (LEFT JOIN, name/category LIKE). Device: type to filter. |
| 14 | Stock Out | ✅ / 📱 | `StockOutRepository.recordStockOut` runs in a Room transaction: `decreaseStock` (guarded UPDATE) then insert history. |
| 15 | DB persistence across restart | ✅ / 📱 | Room `warehouse.db` on disk; session in DataStore. Device: add data, kill app, reopen — data + login persist. |
| 16 | Navigation | ✅ | Every one of the 9 screens referenced by `AppNavHost` exists with a matching signature (verified programmatically). Session‑gated graph: Loading→spinner, LoggedOut→Login, LoggedIn→full graph. |
| 17 | Dark mode | ✅ / 📱 | `WarehouseTheme` selects dark scheme via `isSystemInDarkTheme()`; `values-night` theme + dynamic color on API 31+. |
| 18 | Light mode | ✅ / 📱 | Default light color scheme; verified all screens read `MaterialTheme.colorScheme`. |
| 19 | Arabic RTL | ✅ / 📱 | Default string resources are **Arabic** (shown on any locale); `MainActivity` forces `LayoutDirection.Rtl`. English available under `values-en`. |

## Edge cases

| Case | Status | Handling verified |
|------|--------|-------------------|
| Empty fields | ✅ | Login shows empty‑field error; product form flags blank name / invalid quantity; dialogs disable confirm until valid. |
| Invalid login | ✅ | `LoginResult.InvalidCredentials` → localized error; no session written. |
| Duplicate category / user email | ✅ | Unique indices + repository return `false`; UI shows "email exists" snackbar / keeps dialog open. |
| Large quantities | ✅ | Quantity stored as `Int` (max 2,147,483,647); numeric‑only input filter. |
| Negative stock attempt | ✅ | `decreaseStock` UPDATE has `WHERE quantity >= :amount`; 0 rows → `InsufficientStock`; whole op is transactional, so stock can never go negative. |
| Large images | ✅ | Images kept as files, not blobs, and loaded via Coil (down‑sampling) — DB stores only the path, so large photos don't bloat the DB or memory. |
| App restart | ✅ | Room + DataStore persistence (row 15). |
| Screen rotation | ✅ | UI state lives in ViewModels (`StateFlow`), survives configuration changes; transient form state kept in `rememberSaveable`‑friendly VM state. |

## Build / integrity checks performed

* ✅ 48 Kotlin files — all have package declarations, all braces balanced.
* ✅ All 9 NavHost destinations resolve to existing composables with matching parameters.
* ✅ All 71 referenced `R.string` keys exist in **both** the default (Arabic) and `values-en`
  resource sets; key sets are identical (76 each).
* ✅ Every Material icon reference (`Filled`, `Outlined`, `AutoMirrored.Filled`) has a
  matching import.
* ✅ Core cross‑cutting imports present everywhere used (`stringResource`, `hiltViewModel`,
  `collectAsStateWithLifecycle`, `rememberLauncherForActivityResult`,
  `SubcomposeAsyncImage`, `@HiltViewModel`, `viewModelScope`).
* ✅ Hilt graph consistent — single binding for `SessionManager` (constructor‑injected with
  `@ApplicationContext`); DB + 4 DAOs provided once.
* ✅ Gradle wrapper present and valid (`gradle-wrapper.jar` contains `GradleWrapperMain`,
  `gradlew` / `gradlew.bat` fetched from the official Gradle 8.10.2 tag).

## Not verifiable in this environment (run these on your machine)

* Actual compilation to bytecode and APK packaging (`./gradlew assembleDebug`).
* Runtime launch, live navigation, camera hardware, and emulator screenshots.

These require Android SDK + Google Maven access, which your local Android Studio has.
