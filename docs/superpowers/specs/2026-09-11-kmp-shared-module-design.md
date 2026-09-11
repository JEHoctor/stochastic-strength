# KMP shared module: move `data/` and `domain/` to `commonMain`

**Date:** 2026-09-11
**Status:** approved design, pending implementation plan

## Goal

First step toward an iOS build of the app via Compose Multiplatform. Introduce
a `shared` Kotlin Multiplatform module, move the platform-neutral `data/` and
`domain/` code into its `commonMain`, and prove it compiles and links for iOS
in CI — while the Android app stays green on every commit and the source tree
stays mergeable with upstream (`fowles/stochastic-strength`).

Phase 1 of a multi-phase port. This spec is Phase 1 only. Later phases move
the Compose UI into `shared`, add an `iosApp/` Xcode project, and replace the
Android-only services (rest-timer notification, location, Strava OAuth,
encrypted token storage) with iOS counterparts.

## Decisions (from brainstorming)

- **Compose Multiplatform**, not "KMP domain + SwiftUI UI" and not a rewrite.
  Sharing the UI is what keeps upstream changes mergeable rather than
  re-implemented by hand.
- **Two modules: `app` stays the Android application, `shared` is new.**
  Under AGP 9 the Kotlin Multiplatform plugin cannot coexist with
  `com.android.application` in one module, so a separate shared module is
  forced. Of the two ways to name things, keeping `app` as the application
  module wins for upstream merges: `app/build.gradle.kts` (where fowles bumps
  `versionCode` every release), the manifest, `res/`, signing, build types, and
  the `release`/`releaseLocal` source sets all stay exactly where they are.
- **Phase 1 does not touch `ui/`, `location/`, `notification/`, `MainActivity`,
  or `StochasticStrengthApp`.** Only `commonMain`-bound files move. Same
  packages, so no import changes in the files that stay.
- **Host (JVM) tests follow their code; device tests stay in `app`.**
  `domain/`+`data/` unit tests use `internal` symbols (`weightForExerciseTest`,
  `buildFrame`, ...) and `internal` is module-scoped, so they move to `shared`'s
  `androidHostTest`; `ui/` unit tests stay in `app`. Instrumented tests do
  **not** move: AGP 9.3's KMP library plugin has no assets pipeline for device
  tests (`mergeAndroidDeviceTestAssets` has zero source inputs), and Room's
  Android `MigrationTestHelper` loads schemas only from assets. `app` already
  has a working `androidTest` assets pipeline. The only `internal` symbols the
  device tests reference are the 11 `internal val MIGRATION_*`, which become
  public. Tests stay JUnit4 on the JVM — no migration to `commonTest`, no
  `kotlin-test`. If iOS-native verification of the belief engine is wanted
  later, it is a *new* small `commonTest` suite, not a migration of the
  existing 6.7k lines.
- **Phase 1 exit criterion:** Android CI green, plus a macOS CI job that links
  `shared` as an iOS simulator framework, plus one instrumented test run on an
  emulator. No Xcode project, nothing executes on iOS yet.
- **Minimize edits inside files fowles also edits.** Where a platform API must
  change, prefer a thin adapter in a *new* file so the upstream-facing file
  changes by an import line. Two such adapters in this phase: a `withTransaction`
  wrapper and an `org.json`-shaped JSON shim.
- **Moves and edits never share a commit.** The big move is a pure `git mv`
  commit so git (and fowles) read it as 100% renames.
- **No AI-tooling files change.** `CLAUDE.md`, `docs/superpowers/plans/`,
  `gradle.properties`, `.idea/` are untouched. This spec follows the repo's
  existing `docs/superpowers/specs/` convention.
- **Nothing runtime-visible changes on Android.** Same schema, same migrations,
  same backup format, same behavior. The one deliberate difference is the
  SQLite driver (see Room section); the database file format is identical.

## Out of scope

- Moving any UI, ViewModel, or Android service code.
- Adding the Compose Multiplatform plugin or any Compose dependency to `shared`.
- An `iosApp/` Xcode project, code signing, TestFlight.
- Converting `domain/strava/` (stays Android; consumed only by `App` and
  `ui/strava`). Phase 3.
- Converting `domain/history/HistoryRows.kt` (exposes `java.time` types as
  public API; consumed only by `ui/history`). Moves with the UI in Phase 2.
- Bumping any existing dependency version, including Room (2.8.4 already
  supports KMP). Room 3.0 is a separate decision.
- Branding. Name, icon, and bundle ID are Phase 2+ and will be isolated to a
  single config point when they arrive.
- Running migration tests on the host JVM via Room KMP's driver-based
  `MigrationTestHelper`. Possible and attractive (CI could verify migrations
  without a device); follow-up.

## Build configuration

### Modules

`settings.gradle.kts` gains `include(":shared")`. Nothing else in it changes;
the `google()` content filter already admits `com.android.*` and `androidx.*`.

### `shared/build.gradle.kts`

Plugins: `org.jetbrains.kotlin.multiplatform`,
`com.android.kotlin.multiplatform.library`, `com.google.devtools.ksp`,
`androidx.room` (owns `schemaDirectory`; replaces the `ksp { arg(...) }` block),
`org.jetbrains.kotlin.plugin.serialization`. **No Compose plugin.**

Targets:

```kotlin
kotlin {
    android {   // AGP 9.3 name; `androidLibrary { }` is deprecated
        namespace = "io.github.fowles.stochastic_strength.shared"
        compileSdk { version = release(37) { minorApiLevel = 1 } }   // same form as app; verified
        minSdk = 33
        withHostTestBuilder {}
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    iosArm64()
    iosSimulatorArm64()
    // no iosX64: GitHub's macOS runners are Apple Silicon
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "Shared"; isStatic = true }
    }
    // Migrations keep upstream's `db` parameter name over Room's `connection`;
    // silence the per-override named-argument warning that choice produces.
    compilerOptions { freeCompilerArgs.add("-Xwarning-level=PARAMETER_NAME_CHANGED_ON_OVERRIDE:disabled") }
}
```

Source sets and dependencies:

| Source set          | Dependencies |
|---------------------|--------------|
| `commonMain`        | `room-runtime`, `sqlite-bundled`, `kotlinx-coroutines-core`, `kotlinx-datetime`, `kotlinx-serialization-json` |
| `androidMain`       | (nothing beyond common) |
| `iosMain`           | (nothing beyond common) |
| `androidHostTest`   | `junit`, `org.json`, `kotlinx-coroutines-test` |

No device-test compilation in `shared` (see Decisions).

KSP for Room is per-target: `kspAndroid`, `kspIosArm64`, `kspIosSimulatorArm64`
each get `room-compiler`. `room { schemaDirectory("$projectDir/schemas") }`.

The `kotlinx-serialization-core` declaration and its comment about lifting the
runtime for `MigrationTestHelper` move here from `app` with the concern they
address.

### `app/build.gradle.kts`

Remove: the `ksp` plugin and `ksp { }` block; `room-runtime`, `room-ktx`,
`room-compiler`. Keep `room-testing` (androidTest) and the `androidTest`
schema-assets line, repointed from `$projectDir/schemas` to
`$rootDir/shared/schemas`. Add: `implementation(project(":shared"))`.
Everything else is untouched.

### Version catalog

Add plugin aliases `kotlin-multiplatform`, `android-kotlin-multiplatform-library`
(`version.ref = "agp"`), `room` (`version.ref = "room"`), `kotlin-serialization`;
add libraries `androidx-sqlite-bundled`, `kotlinx-datetime`,
`kotlinx-serialization-json`. No existing entry changes.

### Verified by a throwaway scaffold (AGP 9.3.2, Kotlin 2.4.10)

- The plugin combination configures and builds; `app` consumes `shared` via
  `implementation(project(":shared"))`.
- The target block is `kotlin { android { } }`; `androidLibrary { }` is deprecated.
  It accepts `compileSdk { version = release(37) { minorApiLevel = 1 } }`.
- `kotlin.time.Clock` and `kotlin.concurrent.Volatile` need no opt-in.
- An explicit import of a same-named declaration shadows the JVM default
  imports (`java.lang.System`, `kotlin.text.format`) — verified by a host test.
- `-Xwarning-level=PARAMETER_NAME_CHANGED_ON_OVERRIDE:disabled` is the
  non-deprecated form of the warning suppression.
- Task names: `:shared:testAndroidHostTest` (JUnit4 host tests),
  `:shared:compileCommonMainKotlinMetadata` (common-purity check),
  `:shared:linkDebugFrameworkIosSimulatorArm64` (iOS link; disabled on Linux).
- `compileCommonMainKotlinMetadata` fails on a stray `java.io.File` import with
  `Unresolved reference 'java'` — the local leak detector works.
- In `commonMain`, `Dispatchers.IO` needs `import kotlinx.coroutines.IO`.
- In `kotlinx-datetime` 0.8.0, use `format.format(date)`; the
  `date.format(format)` member form does not resolve.
- Pinned versions: `kotlinx-datetime` 0.8.0, `kotlinx-serialization-json`
  1.11.0 (matches the existing `-core`), `androidx.sqlite:sqlite-bundled` 2.6.2
  (the version Room 2.8.4 depends on), `androidx.room` Gradle plugin 2.8.4.

## Room → Room KMP

**Driver.** `BundledSQLiteDriver` on every platform including Android. Using
framework SQLite on Android and bundled on iOS would put different SQLite
versions under a replay engine whose contract is determinism. Existing Android
database files open unchanged. Setting a driver is also what switches Room's
migration API to `SQLiteConnection`, which `commonMain` requires.

**Migrations — minimal diff.** All 18 keep the parameter *name* `db` and change
only its type:

```kotlin
override fun migrate(db: SQLiteConnection) {
    db.execSQL("...")   // unchanged: androidx.sqlite.execSQL is an extension
}
```

Diff: 18 signature lines plus two imports, and the word `internal` removed
from the 11 `internal val MIGRATION_*` declarations so `app`'s instrumented
tests can still reference them across the module boundary. The 56
`db.execSQL(...)` calls do not change. A future upstream `MIGRATION_20_21`
conflicts on exactly its signature line; its body applies clean.

Room's Android `Migration` keeps both overloads. The default
`migrate(SQLiteConnection)` delegates to `migrate(SupportSQLiteDatabase)` only
when handed an `androidx.sqlite.driver.SupportSQLiteConnection`; the reverse
direction throws `NotImplementedError`. Two instrumented tests
(`Migration12To13Test`, `Migration15To16Test`) call `MIGRATION_X_Y.migrate(db)`
by hand with a `SupportSQLiteDatabase` — that still compiles after the change
but fails at runtime. Each gets a one-line wrap:
`migrate(SupportSQLiteConnection(db))`. `Migration19To20Test` goes through
`MigrationTestHelper` and needs no edit.

**Transactions — adapter, not rewrite.** New file `data/RoomTransactions.kt` in
`commonMain`:

```kotlin
suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
```

`WorkoutRepository` and `BackupManager` change their import from
`androidx.room.withTransaction` to this one. All 8 `db.withTransaction { }`
call sites are byte-identical. DAO calls inside the block join the transaction
via the coroutine context, as they did via the executor before.

**Construction split.** `AppDatabase.kt` keeps `@Database`, the DAO accessors,
and the companion with all 18 migrations, plus one new companion function:

```kotlin
fun configure(builder: RoomDatabase.Builder<AppDatabase>): RoomDatabase.Builder<AppDatabase> =
    builder
        .addMigrations(/* all 18 */)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
```

The `Context`-dependent trio — `getInstance`, `reset`, `buildDatabase` (~20
lines, `synchronized`, `context.deleteDatabase`) — becomes extension functions
on `AppDatabase.Companion` in `shared/src/androidMain/.../data/AppDatabase.android.kt`,
with the `INSTANCE` holder and lock alongside. Both call sites in
`StochasticStrengthApp` (`AppDatabase.getInstance(this, applicationScope)`,
`AppDatabase.reset(...)`) stay byte-identical. The `scope` parameter is threaded
through unchanged even though the builder does not use it; preserving the
signature is cheaper than an upstream-visible API change. The iOS counterpart
lands in Phase 2/3.

**`@ConstructedBy`.** `@ConstructedBy(AppDatabaseConstructor::class)` on the
class and `expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>`
in a new `commonMain` file let iOS build the database without reflection;
Room's KSP generates the `actual`. An `expect` cannot exist in a
non-multiplatform module, so this is the one edit that happens *after* the
move. Android's class-based `Room.databaseBuilder(context, AppDatabase::class.java, ...)`
keeps working alongside it.

**Schemas.** `app/schemas/` → `shared/schemas/` by `git mv`. Room keys schema
files by the database's fully-qualified class name, which is unchanged, so the
19 JSON files stay valid for `MigrationTest` and for version-bump validation.

**The honest gap.** Room is the one Phase 1 change with runtime behavior CI
cannot exercise: unit tests use fakes; migration, DAO, and repository tests are
instrumented. CI guarantees they compile, not that they pass. See the merge gate.

## Other in-place edits

**`BackupJson` — `org.json` is in `android.jar`, not the common stdlib.**
Resolved with a thin shim, not a rewrite. New `json/` package in `commonMain`
providing `JSONObject`, `JSONArray`, `JSONException` over
`kotlinx.serialization.json`, implementing exactly the 16 methods `BackupJson`
uses: `JSONObject()`, `JSONObject(String)`, `put`, `getLong`, `getInt`,
`getDouble`, `getString`, `getBoolean`, `getJSONObject`, `getJSONArray`,
`optString`, `optInt`, `optBoolean`, `optJSONArray`, `isNull`, `toString(indent)`;
`JSONArray()`, `put`, `length`, `getJSONObject`, `getString`. About 100 lines.
`BackupJson.kt` changes three import lines and nothing else.

Round-trip fidelity is pinned by a host test: a populated `WorkoutBackup` is
serialized by the shim and parsed by `org.json`, and vice versa, and the parsed
backups must be equal. Byte output may differ (`org.json` prints `100.0` as
`100`; pretty-print whitespace differs). That is acceptable for a format whose
only consumer is its own parser, but the plan must check whether the existing
instrumented `BackupJsonTest` asserts on exact strings before assuming so.
`StravaJsonBuilder` and `StravaApiClient` use the same subset and reuse the
shim in Phase 3.

**`PrescriptionTrace` — one `SimpleDateFormat("MMM d", Locale.US)`.** Becomes a
`kotlinx-datetime` `LocalDate.Format` with `MonthNames.ENGLISH_ABBREVIATED` and
an unpadded day, evaluated in `TimeZone.currentSystemDefault()` (the zone
`SimpleDateFormat` used implicitly). Output is identical.

**`BacktestData` (test tree) — one line.** `File("src/test/resources/backtest")`
is module-relative; it becomes `src/androidHostTest/resources/backtest`. The
`.gitignore` entry moves with it. `org.json` stays as a host-test dependency for
parsing `history.json`.

**JVM-only stdlib calls — no import to grep for.** A scan of the 80 files
for JVM stdlib members that are not in the common stdlib found three kinds:

| Usage | Sites | Files |
|-------|-------|-------|
| `"%.1f".format(x)` (`kotlin.text.format`, JVM-only) | 9 | `WeightFormatter` (3), `PrescriptionTrace` (6) |
| `System.currentTimeMillis()` (`java.lang.System`) | 8 | `WorkoutPlanner` (1), `WorkoutRepository` (4), `BackupManager` (1), `ExerciseProgressionSeriesBuilder` (1) |
| `@Volatile` (`kotlin.jvm.Volatile`, default-imported on JVM) | 1 | `DerivedStateStore` |

Same treatment as the JSON shim — **the call sites stay byte-identical; each
file gains one import line**, because an explicit import shadows a JVM default
import:

- `text/Format.kt` in `commonMain`: `expect fun String.format(vararg args: Any?): String`.
  The `androidMain` actual delegates to `java.lang.String.format(this, *args)`,
  so Android behavior (including its locale-dependence) is unchanged. The
  `iosMain` actual delegates to a common `internal fun formatPrintfSubset(...)`
  that implements `%s`, `%d`, and `%.Nf` (half-up rounding, `.` separator),
  pinned by a host test against `java.lang.String.format(Locale.US, ...)` for
  every pattern the two files use. `WeightFormatter` and `PrescriptionTrace`
  add `import io.github.fowles.stochastic_strength.text.format`.
- `time/System.kt` in `commonMain`: `object System { fun currentTimeMillis(): Long }`
  over `kotlin.time.Clock.System`. The four files add
  `import io.github.fowles.stochastic_strength.time.System`. Shadowing a name
  as familiar as `System` is deliberate: it is what keeps eight call sites in
  `WorkoutRepository` and friends — files fowles edits constantly — untouched,
  and a future upstream `System.nanoTime()` fails loudly in `commonMain`
  rather than silently.
- `DerivedStateStore` adds `import kotlin.concurrent.Volatile` (a typealias to
  `kotlin.jvm.Volatile` on the JVM; no behavior change).

`compileCommonMainKotlinMetadata` is the ground truth. If it finds a usage this
scan missed, it gets the same treatment: an import-line shim where one is
possible, otherwise the smallest possible edit, and the spec's file list is
updated.

**Two instrumented tests — one line each.** `Migration12To13Test` and
`Migration15To16Test` wrap the `SupportSQLiteDatabase` they hand to
`migrate(...)` in `SupportSQLiteConnection(...)` (see Room section). They stay
in `app/src/androidTest`.

**Nothing else in the moved files changes.**

## File map

Every path keeps the package prefix `io/github/fowles/stochastic_strength/`;
no `package` or `import` line changes as a result of the move.

**`shared/src/commonMain/kotlin/` — 80 files by `git mv`, 9 edited, 5 new**

- `data/` — all 33: `AppDatabase.kt` *(edited)*, `Converters.kt`, `dao/` (9),
  `model/` (21), `seed/ExerciseLibrary.kt`.
- `domain/` — 47 of 52: all except `strava/` (4) and `history/HistoryRows.kt`.
  Edited, import lines only: `WorkoutRepository.kt` (2), `backup/BackupManager.kt`
  (2), `backup/BackupJson.kt` (3), `WeightFormatter.kt` (1), `WorkoutPlanner.kt`
  (1), `progression/ExerciseProgressionSeriesBuilder.kt` (1),
  `derived/DerivedStateStore.kt` (1). Edited beyond imports:
  `belief/PrescriptionTrace.kt` (imports plus the 3-line date format).
- New: `data/AppDatabaseConstructor.kt`, `data/RoomTransactions.kt`,
  `json/Json.kt` (the `org.json`-shaped shim), `text/Format.kt`,
  `time/System.kt`.

**`shared/src/androidMain/kotlin/` — 2 new files:** `data/AppDatabase.android.kt`,
`text/Format.android.kt`.

**`shared/src/iosMain/kotlin/` — 1 new file:** `text/Format.ios.kt`.

**`shared/src/androidHostTest/kotlin/` — 66 files by `git mv`, 1 edited, 1 new**

- `data/` (4), `domain/` (62, including the 14-file `backtest/` tree).
  `backtest/BacktestData.kt` gets its path fix.
- New: `json/JsonTest.kt` (the `org.json` round-trip test),
  `text/FormatTest.kt` (the printf-subset fidelity test).
- `shared/src/androidHostTest/resources/backtest/` — gitignored fixture dir.

**`shared/schemas/` — 19 JSON files by `git mv`.**

**`app/` — untouched apart from the build file:** `MainActivity`,
`StochasticStrengthApp`, `ui/` (66), `location/` (2), `notification/` (2),
`domain/strava/` (4), `domain/history/HistoryRows.kt`; `res/`, the manifest,
`src/release`, `src/releaseLocal`; 9 unit tests (`ui/` × 8, `HistoryRowsTest`);
all 20 instrumented tests (2 edited by one line each, see above).

`domain.strava` and `domain.history` temporarily straddle two modules. Kotlin
permits it; both resolve in later phases.

## Verification

### Local, on every step

1. `:app:testDebugUnitTest` plus `:shared:testAndroidHostTest`. The same 378
   tests, now split across the two modules; the total must not change.
2. `:app:lintDebug`, `:app:assembleDebug`.
3. **`:shared:compileCommonMainKotlinMetadata` — the local leak detector.** It
   compiles `commonMain` against only the common stdlib, so an accidental
   `android.*` or `java.*` import fails on Linux in seconds rather than on a
   macOS runner later. The plan's first step includes a deliberate negative
   test: add a `java.io.File` import to a `commonMain` file, confirm the task
   fails, remove it.
4. `:app:compileDebugAndroidTestKotlin` — no device needed; proves the
   instrumented tests still resolve the now-public `MIGRATION_*` vals across
   the module boundary.
5. iOS targets are declared and skipped on Linux with a warning. Expected.

### CI

`android.yml` gains steps 3 and 4 and the shared host tests. Stays on
`ubuntu-latest`.

New `ios.yml` on `macos-latest`: JDK 21; `setup-gradle` with caching; a
`~/.konan` cache keyed on the Kotlin version (the Kotlin/Native toolchain is a
several-hundred-MB download); one invocation,
`:shared:linkDebugFrameworkIosSimulatorArm64`. Linking, not just compiling,
also proves the framework declaration is sound. `iosArm64` is not compiled in
Phase 1: it would double Kotlin/Native time to catch nothing the simulator
build doesn't, and it is exercised the first time an app is built in Phase 2.
Same triggers and concurrency group as `android.yml`; 60-minute timeout.

`android.yml` also gains, **in its own commit**, a guard step that fails if
`app/src/main/java/.../domain` or `.../data` contains any `.kt` file outside the
known Android-only set (`domain/strava/`, `domain/history/HistoryRows.kt`).
Purpose: after `git merge upstream/main`, a file fowles *adds* under `domain/`
lands in `app/`, compiles fine there, and is silently absent from `commonMain`.
The guard makes that a red check on the merge commit. The allowlist shrinks as
later phases move the remaining files. It touches no source and no upstream
file; in a merged-upstream world it becomes the lint rule "domain and data
code lives in `shared`."

### Phase 1 merge gate — all three

1. `android.yml` green on the final commit.
2. `ios.yml` green on the final commit.
3. **One `:app:connectedAndroidTest` run on an emulator on the dev VM** (which
   has `/dev/kvm`, 12 cores, 15 GB) — the command `CLAUDE.md` already
   documents. The only way to execute migration, DAO, and repository tests
   against the bundled driver, and the only thing that catches the
   `NotImplementedError` class of mistake. Standing up a headless emulator is
   its own plan step; it serves every later phase.

### Not verified in Phase 1

Nothing runs on iOS. Compiling and linking prove portability; first execution
on an iOS simulator is Phase 2's opening move.

## Commits

Six commits, each with Android CI green:

1. **`build: add shared KMP module`** — settings, catalog, root build file,
   `shared/build.gradle.kts`; the four adapters (`withTransaction` wrapper, JSON
   shim, `String.format` expect/actual, `System` object) and their host tests
   as the module's first content;
   `app → shared` dependency; `android.yml` additions; new `ios.yml`. Includes
   the negative test of `compileCommonMainKotlinMetadata`. `ios.yml` lands
   here, first, so the macOS plumbing is debugged against a tiny module.
2. **`data: convert Room to KMP APIs in place`** — still in `app/`. Migration
   signatures and visibility, driver, `configure()`, the builder trio extracted
   into a sibling file, the two `withTransaction` import swaps, the two
   one-line instrumented-test wraps.
3. **`domain: swap JVM-only calls for the shared shims`** — import-line
   changes in seven files (`BackupJson`, `WeightFormatter`, `PrescriptionTrace`,
   `WorkoutPlanner`, `WorkoutRepository`, `BackupManager`,
   `ExerciseProgressionSeriesBuilder`, `DerivedStateStore`) plus
   `PrescriptionTrace`'s date format. Still in `app/`, still on the JVM, so
   behavior is provably unchanged: the Android actuals delegate to the same
   JVM calls.
4. **`refactor: move data/ and domain/ into shared`** — `git mv` of 80 + 66
   files and 19 schemas, plus only the build-file wiring needed to stay green
   (Room and KSP leave `app/`; the androidTest schema-assets line is repointed). **No content edits.** If Room's KSP requires
   `@ConstructedBy` for native targets rather than warning, `ios.yml` is red on
   this commit only; Android is green regardless.
5. **`data: wire AppDatabase for iOS construction`** — `@ConstructedBy`, the
   `expect object`, the `BacktestData` path, `.gitignore`.
6. **`ci: guard against shared-layer code stranded in app`** — the five-line
   step. Separate so it can be cherry-picked out of any upstream PR.

Then the emulator run, then merge to `main`.

## Living with upstream

Add `upstream = https://github.com/fowles/stochastic-strength` and merge
`upstream/main` into `main` periodically. The dev VM clone has full history
(not shallow), so merges have a proper base. Git's rename detection is
content-based with a 50% similarity default; the moved files are ≥99%
identical to their origins, so upstream edits to *existing* `domain/`/`data/`
files follow them into `shared/` automatically.

Predictable hotspots:

- **`app/build.gradle.kts`** — `versionCode` bumps merge clean (untouched
  lines). A bump to a dependency removed from `app` conflicts trivially; take
  theirs into `shared`'s catalog entry.
- **`AppDatabase.kt`** — a new migration conflicts on its signature line only.
- **New upstream files under `domain/` or `data/`** — land in `app/`, must be
  moved by hand. The guard step (commit 6) makes this loud.

## Testing summary

| What | How | Where |
|------|-----|-------|
| Belief engine, planner, policy, replay | existing JUnit4 host tests, unchanged | `shared` host tests |
| UI helpers, `HistoryRows` | existing JUnit4 host tests, unchanged | `app` unit tests |
| JSON shim fidelity | new round-trip test vs `org.json` | `shared` host tests |
| printf-subset fidelity | new test vs `java.lang.String.format` | `shared` host tests |
| `commonMain` purity | `compileCommonMainKotlinMetadata` | local + `android.yml` |
| iOS portability | `linkDebugFrameworkIosSimulatorArm64` | `ios.yml` |
| Room migrations, DAOs, repository | existing instrumented tests in `app` (2 one-line edits) | `:app:connectedAndroidTest` on emulator, merge gate |
| Upstream drift | guard step | `android.yml` |
