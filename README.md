# PillPantry (Kotlin / Jetpack Compose)

A native, Android-only app for tracking groceries and vitamins, built with
Kotlin and Jetpack Compose. **All data is stored on the phone** (Room /
SQLite) — no account, no Firebase, no network, no sync. Everything is added
by hand; there is no barcode scanner.

## What's included

- `MainActivity.kt` — bottom nav (Pantry / Shopping List), notification
  permission request, launch-time portion-decay catch-up
- `ui/pantry/PantryScreen.kt` — groceries list (blue rows, tap the cart icon
  to add/remove an item from your shopping list) and vitamins list (orange
  rows, `#FFA85D`) with a "Take Dose" button. The floating **+** button adds
  a grocery (Groceries tab) or a vitamin (Vitamins tab) by hand. Each row has
  an edit (pencil) icon for its settings. Swipe a row **right to restock** or
  **left to delete** (with confirmation) — the swipe needs a deliberate,
  mostly-full drag. Tapping "Take Dose" twice in one day asks for
  confirmation. Two icons next to the tabs export/import a JSON backup.
- `ui/shoppinglist/ShoppingListScreen.kt` — flagged groceries (blue), low
  vitamins (orange), and one-off custom items (gray-blue) you can add and
  delete here.
- `data/Models.kt`, `data/AppDatabase.kt` — Room entities, DAO and database
- `data/PillPantryRepository.kt` — all reads/writes, daily decay, backup
- `notifications/NotificationHelper.kt` — local notifications
- `work/` — `PortionDecayWorker` (~3am Chicago) and `PillReminderWorker`
  (~9pm Chicago), both best-effort and fully offline

## Build the APK on GitHub

1. Push this folder to a GitHub repo on the `main` branch.
2. `.github/workflows/android-build.yml` builds a debug APK — no secrets or
   config files needed anymore.
3. When the **Actions** run finishes, download the `pillpantry-debug-apk`
   artifact, copy the APK to your phone and install it.

## Your data lives only on the phone

Uninstalling the app, clearing its storage, or losing the phone erases the
data. Use the **export** icon in Pantry now and then and keep the JSON file
somewhere safe (Drive, email to yourself, etc.).

### Moving over data from the old Firebase/scanner version

In the **old** app, tap export and save the JSON. Install the new app and tap
import. Items are matched by name (case-insensitive) and existing ones are
skipped, so it is safe to repeat. Old backups containing barcodes still work.

## Behavior notes

- **Auto-add to shopping list**: groceries when `portions <= threshold`,
  vitamins when `pills <= threshold` (after Take Dose, restock or edit).
- **Restock**: swipe right. Adds `portionsPerUnit` portions per unit;
  `portionsPerUnit` is computed as `portions / quantity` at creation and can
  be changed with the edit icon.
- **Daily portion decay**: on every launch (and from the background worker),
  each grocery loses 1 portion per 3am America/Chicago boundary since its
  last update. The **Check/apply portion decay** button runs the same
  catch-up on demand and reports what changed.
- **Evening reminder**: ~9pm Chicago, only if some vitamin hasn't been taken.
- **Dark mode** follows the system setting.
