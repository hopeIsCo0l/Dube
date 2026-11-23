# Dube Debter · ዱቤ ደብተር

An Android credit ledger for keeping track of customers, purchases on credit, payments, and remaining balances. Built with Kotlin, Jetpack Compose, and Material 3.

The app stores its ledger locally and does not require an account or a backend. Customers can have multiple named tabs, each containing items and payment records.

## Start here

- [Using the app](#using-the-app)
- [How balances and credit work](#how-balances-and-credit-work)
- [Exporting reports](#exporting-reports)
- [Build and run](#build-and-run)
- [Tests and manual checks](#tests-and-manual-checks)
- [Code map](#code-map)
- [Storage and sharing](#storage-and-sharing)
- [Current limitations](#current-limitations)

## Using the app

### 1. Register a customer

From **Customers**, choose **Register a customer**. A name is required; phone number and notes are optional. Saving opens the new customer's page.

Search existing customers by name or phone number. The registration button stays accessible below the list.

### 2. Open a tab and add purchases

On the customer's page, choose **Open a tab** and give it a name, such as “Weekly groceries.” Then choose **Add item**.

Each item has:

- A name.
- A quantity greater than zero.
- A unit price of zero or more.

Quantity and unit-price inputs accept up to two decimal places. Totals use decimal arithmetic. Tap an item to edit or delete it; deletion asks for confirmation. Closed tabs must be reopened before their items can be changed.

### 3. Record a payment

Open a tab containing items and choose **Record payment** in its balance card.

Enter the amount actually received. The dialog shows the resulting balance and any credit that will remain before you save. A partial payment leaves the tab open; full payment closes it automatically. The tab stays visible after saving, with a confirmation message.

Existing customer credit can also settle a balance. Enter **0** only when available credit covers the balance; this applies credit without creating a new cash receipt.

### 4. Find details and history

- **Open tabs** shows active tabs; **All tabs** also includes closed tabs.
- **Contact details and notes**, **Tab details**, and tab payment records expand when needed.
- **Payment history** on the customer page includes receipts across both open and closed tabs.
- Opening and payment dates are displayed in Gregorian and Ethiopian calendars.
- An overdue tab exposes reminder actions when a phone number is available. Calling opens the dialer; SMS opens a draft in a messaging app.
- The **⋮ More options** menu contains export, language selection, the quick guide, and the feature tour.

Language choices are English, Amharic, Silt'e, and Gurage. Some newer interface labels fall back to English.

## How balances and credit work

The financial model lives in [PaymentLedger.kt](app/src/main/java/com/sook/dubedebter/PaymentLedger.kt) and uses `BigDecimal`.

| Term | Meaning |
| --- | --- |
| Item total | Quantity × unit price |
| Tab total | Sum of the tab's item totals |
| Applied payments | Money or existing credit already allocated to a tab |
| Tab balance | Tab total minus applied payments, with a minimum of zero |
| Customer balance | Sum of that customer's tab balances |
| Available credit | Unallocated money available for that customer's future purchases |
| Recorded receipts | Actual payment records, separate from credit allocations |

**Example:** A tab totals 100. Receiving 120 applies 100 to the tab, closes it, and leaves 20 of customer credit. Adding an item worth 35 to a new tab applies that 20 credit, leaving a balance of 15.

Important rules:

- Payments apply to the selected tab; they do not automatically pay every outstanding tab.
- Excess payment becomes customer credit.
- Editing items recalculates allocations. Any excess already applied to the edited tab returns to credit.
- Reopening a tab does not erase its payments.
- Available credit is shown separately. Do not subtract it again from a balance that already reflects an allocation.
- Older stored data can contain applied payments without individual receipt records. Applied-payment totals and recorded-receipt totals are therefore not always equal.
- Payment IDs prevent the same recorded payment from being applied twice.

The ledger has no currency-selection or conversion feature. Reports preserve the entered amounts; the SMS reminder template currently labels amounts as ETB.

## Exporting reports

Export scope follows the screen you are on:

1. From **Customers**, open **⋮ → Export whole ledger**.
2. From a customer's screen, open **⋮ → Export this customer**.
3. Choose **Excel workbook (.xlsx)** or **Images (.png)**.
4. Choose **Share report**, then select a destination in Android's share chooser.

Reports include open and closed tabs regardless of the current tab filter. Exporting a customer includes only that customer's records. Export is disabled when there are no customers.

### Excel

The workbook contains five sheets:

| Sheet | Contents |
| --- | --- |
| Summary | Scope, generation time, counts, totals, and accounting notes |
| Customers | Names, IDs, phone numbers, notes, balances, credit, and receipt totals |
| Tabs | Customer references, tab names, opening dates, status, and totals |
| Items | Customer/tab references, item names, quantities, prices, and totals |
| Payments | Customer/tab references, receipt IDs, timestamps, and received amounts |

Header rows are frozen, and detail sheets have filters. Names, phone numbers, notes, and other user text are stored as literal text, not formulas. Numbers exceeding Excel's 15-digit precision are stored as text to preserve their exact value.

The writer rejects values that exceed Excel's cell-text or row limits instead of silently truncating them.

### Images

Reports are rendered as numbered 1200 × 1600 PNG pages. Long text wraps onto subsequent pages, and empty detail sections are omitted. The renderer uses one bitmap at a time.

Image exports are limited to **100 pages**. For larger ledgers, choose Excel or export customers individually.

### Export behavior

- Preparing a report does not alter ledger records.
- Preparation can be cancelled by dismissing the dialog.
- Report headings are currently in English.
- Report timestamps use Gregorian dates with a time-zone offset.
- Reports include customer contact details and notes.
- Files are shared through Android's chooser; saving a permanent copy depends on the destination app you select.
- Exports are reports, **not restorable backups**. There is no report-import feature.

## Build and run

### Project configuration

These values describe the checked-in project, rather than a recommendation to upgrade dependencies.

| Setting | Value |
| --- | --- |
| Application ID | `com.sook.dubedebter` |
| Minimum Android API | 23 |
| Compile / target API | 37 / 37 |
| Android Gradle Plugin | 9.5.2 |
| Gradle wrapper | 9.6.0 |
| Gradle daemon toolchain | Java 25 |
| Java source / target compatibility | 11 |
| Kotlin Compose plugin | 2.2.10 |
| Compose BOM | 2026.02.01 |

Sources of truth: [app build configuration](app/build.gradle.kts), [version catalog](gradle/libs.versions.toml), [Gradle wrapper](gradle/wrapper/gradle-wrapper.properties), and [daemon JVM configuration](gradle/gradle-daemon-jvm.properties).

### Android Studio

1. Open the repository root in Android Studio with support for the configured AGP version.
2. Install Android SDK Platform 37 through SDK Manager.
3. Allow Gradle sync to complete. The daemon configuration requests Java 25; provide a compatible JDK or allow toolchain provisioning.
4. Select the `app` run configuration.
5. Run on an emulator or device with API 23 or newer.

Initial setup needs network access to download Gradle, dependencies, and any missing toolchain components. No Firebase configuration, API key, or backend credentials are needed.

### Command line

Run commands from the repository root:

```bash
# Build a debug APK
./gradlew :app:assembleDebug

# Install on a connected device or running emulator
./gradlew :app:installDebug
```

On Windows, use `gradlew.bat` in place of `./gradlew`.

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Use the checked-in wrapper rather than a separately installed Gradle version. Configure the Android SDK path through Android Studio or your local `local.properties`; do not commit machine-specific paths.

## Tests and manual checks

```bash
# Local JVM tests
./gradlew :app:testDebugUnitTest

# Device tests: requires a connected emulator or device
./gradlew :app:connectedDebugAndroidTest

# Android lint
./gradlew :app:lintDebug
```

Test locations:

- [JVM tests](app/src/test/java/com/sook/dubedebter): payment allocation, excess credit, item edits, payment history, tab visibility, Ethiopian date conversion, and workbook structure/data handling.
- [Device tests](app/src/androidTest/java/com/sook/dubedebter): customer search, payment flow, expandable details, export scope, image pagination, cancellation, and restricted report-file access.
- [Compose previews](app/src/main/java/com/sook/dubedebter/LedgerFlowPreview.kt): tab layout at normal and larger text sizes.

Useful manual regression checks:

1. Register a customer and confirm their page opens.
2. Add items, record a partial payment, then settle the remainder.
3. Verify that the paid tab stays visible and appears under **All tabs**.
4. Overpay a tab and verify that remaining credit applies to a future purchase.
5. Edit an item after applying credit and verify both balance and credit.
6. Navigate back to verify that search and scroll state are retained.
7. Export one customer and the whole ledger in both formats.
8. Cancel report preparation and reopen the export dialog.
9. Check long names, long notes, large text, and non-Latin customer names.
10. Restart the app and confirm the ledger is retained.

## Code map

Production Kotlin files are in [app/src/main/java/com/sook/dubedebter](app/src/main/java/com/sook/dubedebter).

| File | Responsibility |
| --- | --- |
| [MainActivity.kt](app/src/main/java/com/sook/dubedebter/MainActivity.kt) | App entry point, locale setup, screen selection, JSON persistence, and editing/payment dialogs |
| [PaymentLedger.kt](app/src/main/java/com/sook/dubedebter/PaymentLedger.kt) | Models and pure balance, credit, payment, and item-update logic |
| [LedgerScreens.kt](app/src/main/java/com/sook/dubedebter/LedgerScreens.kt) | Customer list, customer tabs, tab detail, and expandable information |
| [LedgerAppMenu.kt](app/src/main/java/com/sook/dubedebter/LedgerAppMenu.kt) | Contextual export, language, and help menu |
| [EthiopianDate.kt](app/src/main/java/com/sook/dubedebter/EthiopianDate.kt) | Ethiopian calendar conversion |
| [LedgerReport.kt](app/src/main/java/com/sook/dubedebter/LedgerReport.kt) | Shared report snapshot and sheet definitions |
| [ExcelReportWriter.kt](app/src/main/java/com/sook/dubedebter/ExcelReportWriter.kt) | Dependency-free OOXML workbook generation |
| [ImageReportWriter.kt](app/src/main/java/com/sook/dubedebter/ImageReportWriter.kt) | Paginated bitmap reports |
| [ExportReportAction.kt](app/src/main/java/com/sook/dubedebter/ExportReportAction.kt) | Export dialog, background generation, cancellation, and sharing |
| [OnboardingScreen.kt](app/src/main/java/com/sook/dubedebter/OnboardingScreen.kt) / [CoachMarkOverlay.kt](app/src/main/java/com/sook/dubedebter/CoachMarkOverlay.kt) | First-run introduction and optional feature tour |
| [ui/theme](app/src/main/java/com/sook/dubedebter/ui/theme) | Material theme, colors, and typography |

This is a single-module Compose application. State and persistence are currently coordinated in `MainActivity.kt`; there is no Room database, repository layer, or ViewModel-based navigation architecture.

When changing financial behavior, keep calculations in `PaymentLedger.kt` and extend its tests. When changing report contents, update `LedgerReport.kt` so both export formats use the same data definitions. Preserve existing JSON compatibility when modifying storage.

## Storage and sharing

- Customer records are serialized as JSON in the app-private `customer_ledger` SharedPreferences file, under the `customers` key.
- Language and onboarding preferences use the same preferences file.
- Saves commit the updated ledger before replacing the displayed in-memory data.
- There is no app-level cloud synchronization or sign-in.
- Android backup is enabled in the manifest. [Backup rules](app/src/main/res/xml/backup_rules.xml) and [data-extraction rules](app/src/main/res/xml/data_extraction_rules.xml) are still template configurations; the project does not implement its own backup/restore workflow.
- Generated reports use `cacheDir/reports/<session-id>/`. During a later export, report sessions older than seven days are eligible for cleanup. Android can also clear cache files.
- Failed or cancelled export sessions are deleted.
- The non-exported FileProvider exposes only the reports cache directory and grants temporary read access to selected report URIs. See [export_paths.xml](app/src/main/res/xml/export_paths.xml).
- The app manifest declares no Internet or broad storage permissions. Dialing and SMS composition are delegated to other apps.

Clearing app storage removes the local ledger. Keep important reports outside the app's cache, while remembering that they cannot currently be imported back into the app.

## Current limitations

- **Overdue threshold is still configured for testing.** `THIRTY_DAYS_MS` in [PaymentLedger.kt](app/src/main/java/com/sook/dubedebter/PaymentLedger.kt) is currently **one day**, while the interface says **30 days**. Align the calculation and wording before relying on overdue reminders in production.
- New flow/export labels are not fully translated; report headings are English.
- There is no report import, app-managed backup restore, or multi-device synchronization.
- Storage rewrites the complete JSON ledger on each save. Very large ledgers may require a more scalable persistence design.
- Image exports stop at 100 pages; Excel exports enforce workbook format limits.
- Release signing is not configured in the checked-in app build script.
- Currency is not stored per account or transaction; reports do not perform currency conversion.
