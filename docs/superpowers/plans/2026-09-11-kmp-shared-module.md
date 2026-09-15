# KMP Shared Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `shared` Kotlin Multiplatform module, move the platform-neutral `data/` and `domain/` code into its `commonMain`, and prove it links as an iOS simulator framework in CI — with the Android app green on every commit.

**Architecture:** `app` stays the Android application module; a new `shared` module (`org.jetbrains.kotlin.multiplatform` + `com.android.kotlin.multiplatform.library`, targets `android`, `iosArm64`, `iosSimulatorArm64`) receives 80 source files and 63 host-test files by `git mv`. Platform APIs the moved code uses (`org.json`, `String.format`, `System.currentTimeMillis`, Room's `withTransaction`) are replaced by same-named shims in new files so that call sites stay byte-identical and each upstream-facing file changes by import lines only. Room moves to its KMP APIs (`BundledSQLiteDriver`, `SQLiteConnection` migrations, `@ConstructedBy`).

**Tech Stack:** Kotlin 2.4.10, AGP 9.3.2, Room 2.8.4 (KMP), `androidx.sqlite:sqlite-bundled` 2.6.2, `kotlinx-datetime` 0.8.0, `kotlinx-serialization-json` 1.11.0, KSP 2.3.10, JUnit4, GitHub Actions (`ubuntu-latest`, `macos-latest`).

**Spec:** `docs/superpowers/specs/2026-09-11-kmp-shared-module-design.md` — read it first; every task below argues from it.

## Global Constraints

- Android CI (`.github/workflows/android.yml`) must be green after **every** task. Run the local equivalent before each commit.
- **Moves and edits never share a commit.** Task 9 is `git mv` plus build wiring only.
- **No existing dependency version changes.** Room stays 2.8.4.
- **Do not touch** `CLAUDE.md`, `docs/superpowers/plans/` (other than this file), existing specs, `gradle.properties`, `.idea/`.
- **Nothing runtime-visible changes on Android.** Same schema (v20), same migrations, same backup format, same behavior.
- Package prefix `io.github.fowles.stochastic_strength` is preserved for every moved file; no `package` or `import` line changes as a result of a move.
- `ui/`, `location/`, `notification/`, `MainActivity`, `StochasticStrengthApp`, `domain/strava/`, `domain/history/HistoryRows.kt`, all of `app/src/androidTest`, and 12 unit tests stay in `app`.
- Shims live in new files; upstream-facing files change by import lines only (exceptions, all in the spec: `AppDatabase.kt`, `PrescriptionTrace.kt`'s date format, two one-line instrumented-test wraps).
- Verified task names: `:shared:testAndroidHostTest`, `:shared:compileCommonMainKotlinMetadata`, `:shared:linkDebugFrameworkIosSimulatorArm64`, `:app:compileDebugAndroidTestKotlin`.
- Every commit message ends with both trailers, per `~/Projects/CLAUDE.md`:
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>` then
  `Claude-Session: https://claude.ai/code/session_01UscjZNxDbAXotuM4Y1ojLh`.
  Commit author is the human (global git identity); never set a Claude identity.
- The spec's six commits map to tasks as: commit 1 = Tasks 1–6; commit 2 = Task 7; commit 3 = Task 8; commit 4 = Task 9; commit 5 = Task 10; commit 6 = Task 11. Task 12 is the merge gate.
- Environment: `export ANDROID_HOME=$HOME/Android/Sdk` (set in `~/.bashrc` on the dev VM). All `./gradlew` commands run from the repo root.

---

## File Structure

**Created**

| Path | Responsibility |
|------|----------------|
| `shared/build.gradle.kts` | The multiplatform module: targets, source sets, Room KSP per target |
| `shared/src/commonMain/kotlin/.../data/RoomTransactions.kt` | `withTransaction` adapter over `useWriterConnection` |
| `shared/src/commonMain/kotlin/.../data/AppDatabaseConstructor.kt` | `expect object` for `@ConstructedBy` (Task 10) |
| `shared/src/commonMain/kotlin/.../json/Json.kt` | `org.json`-shaped `JSONObject`/`JSONArray`/`JSONException` over kotlinx.serialization |
| `shared/src/commonMain/kotlin/.../text/Format.kt` | `expect fun String.format(...)` + common printf-subset implementation |
| `shared/src/commonMain/kotlin/.../time/System.kt` | `object System { fun currentTimeMillis() }` over `kotlin.time.Clock` |
| `shared/src/androidMain/kotlin/.../text/Format.android.kt` | `actual` delegating to `java.lang.String.format` |
| `shared/src/androidMain/kotlin/.../data/AppDatabase.android.kt` | `getInstance`/`reset`/`buildDatabase` as `Companion` extensions (created in `app` in Task 7, moved in Task 9) |
| `shared/src/iosMain/kotlin/.../text/Format.ios.kt` | `actual` delegating to the printf subset |
| `shared/src/androidHostTest/kotlin/.../json/JsonTest.kt` | Round-trip fidelity vs `org.json` |
| `shared/src/androidHostTest/kotlin/.../text/FormatTest.kt` | printf-subset fidelity vs `java.lang.String.format` |
| `shared/src/androidHostTest/kotlin/.../time/SystemTest.kt` | Clock sanity |
| `app/src/test/kotlin/.../domain/backup/BackupJsonRoundTripTest.kt` | Full `WorkoutBackup` round trip through the shim (Task 8; moves in Task 9) |
| `.github/workflows/ios.yml` | macOS job linking the iOS simulator framework |

**Modified**

| Path | Change |
|------|--------|
| `settings.gradle.kts` | `include(":shared")` |
| `build.gradle.kts` (root) | four `apply false` plugin aliases |
| `gradle/libs.versions.toml` | 2 versions, 3 libraries, 4 plugin aliases added |
| `app/build.gradle.kts` | `implementation(project(":shared"))` (Task 1); Room/KSP removal and schema-assets repoint (Task 9) |
| `app/src/main/.../data/AppDatabase.kt` | migrations → `SQLiteConnection`; `internal` removed; builder trio out; `configure()` in (Task 7); `@ConstructedBy` (Task 10) |
| 8 `domain/` files | import lines (Task 8); see Task 8 for the exact list |
| 2 instrumented migration tests | one-line `SupportSQLiteConnection` wrap (Task 7) |
| `app/src/test/.../backtest/BacktestData.kt` | one path line (Task 10) |
| `.gitignore` | backtest fixture path (Task 10) |
| `.github/workflows/android.yml` | shared tasks + guard step (Tasks 6, 11) |

`...` = `io/github/fowles/stochastic_strength`.

---

### Task 1: Build scaffolding — the empty `shared` module

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `settings.gradle.kts`
- Create: `shared/build.gradle.kts`
- Modify: `app/build.gradle.kts` (one line)

**Interfaces:**
- Produces: Gradle project `:shared` with source sets `commonMain`, `androidMain`, `iosMain`, `androidHostTest`; `app` depends on it.

- [ ] **Step 1: Add versions, libraries, and plugin aliases to the catalog**

Edit `gradle/libs.versions.toml`. Under `[versions]`, after `reorderable = "2.4.0"`, add:

```toml
kotlinxDatetime = "0.8.0"
sqlite = "2.6.2"
```

Under `[libraries]`, after the `kotlinx-serialization-core` line, add:

```toml
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }
androidx-sqlite-bundled = { group = "androidx.sqlite", name = "sqlite-bundled", version.ref = "sqlite" }
```

Under `[plugins]`, after the `ksp` line, add:

```toml
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
android-kotlin-multiplatform-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 2: Register the plugins at the root**

Edit `build.gradle.kts` (root) so the `plugins` block reads:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.room) apply false
}
```

- [ ] **Step 3: Include the module**

Edit `settings.gradle.kts`: after `include(":app")` add `include(":shared")`.

- [ ] **Step 4: Write `shared/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "io.github.fowles.stochastic_strength.shared"
        compileSdk { version = release(37) { minorApiLevel = 1 } }
        minSdk = 33
        withHostTestBuilder {}
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    iosArm64()
    iosSimulatorArm64()
    // No iosX64: GitHub's macOS runners are Apple Silicon.
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "Shared"; isStatic = true }
    }
    // Migrations keep upstream's `db` parameter name over Room's `connection`;
    // silence the per-override named-argument warning that choice produces.
    compilerOptions { freeCompilerArgs.add("-Xwarning-level=PARAMETER_NAME_CHANGED_ON_OVERRIDE:disabled") }

    sourceSets {
        commonMain.dependencies {
            // api: AppDatabase's public surface is Room's (RoomDatabase supertype, Builder in configure()),
            // and app calls DAO methods on it, so app needs Room on its compile classpath too.
            api(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            // Not used directly. Room 2.8.4's MigrationTestHelper needs kotlinx-serialization >= 1.8.1, but
            // lifecycle 2.11 pulls 1.7.3 into the app runtime and Gradle's consistent resolution then pins
            // the androidTest classpath to that. Declaring it here lifts the app runtime to what Room needs.
            implementation(libs.kotlinx.serialization.core)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.json)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
```

- [ ] **Step 5: Make `app` depend on `shared`**

Edit `app/build.gradle.kts`: in `dependencies { }`, immediately before `implementation(platform(libs.androidx.compose.bom))`, add:

```kotlin
    implementation(project(":shared"))
```

- [ ] **Step 6: Verify the module configures and the app still builds**

Run: `./gradlew :shared:tasks --all --console=plain 2>&1 | grep -E "^(testAndroidHostTest|compileCommonMainKotlinMetadata|linkDebugFrameworkIosSimulatorArm64) "`
Expected: all three task names listed.

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`. (378 tests still run in `app`; nothing has moved yet.)

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts settings.gradle.kts shared/build.gradle.kts app/build.gradle.kts
git commit -m "build: add empty shared KMP module and make app depend on it

Kotlin Multiplatform + com.android.kotlin.multiplatform.library (the AGP 9
pairing; the KMP plugin no longer coexists with com.android.application in one
module). Targets android, iosArm64, iosSimulatorArm64; Room KSP per target;
no Compose yet. No sources move in this commit.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UscjZNxDbAXotuM4Y1ojLh"
```

---

### Task 2: `withTransaction` adapter

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/data/RoomTransactions.kt`

**Interfaces:**
- Produces: `suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R` in package `io.github.fowles.stochastic_strength.data`. Task 8 swaps `WorkoutRepository` and `BackupManager` to import it.

This adapter is three lines delegating to Room; a host test would need a Room database on the JVM, which the Android host-test classpath cannot build without Robolectric. It is verified by `compileCommonMainKotlinMetadata` here and executed by `BackupManagerTest` / `WorkoutRepositoryTest` at the Task 12 emulator gate.

- [ ] **Step 1: Write the adapter**

```kotlin
package io.github.fowles.stochastic_strength.data

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Same shape as Android's `androidx.room.withTransaction`, which is not available in common
 * code. DAO calls inside [block] join the transaction through the coroutine context, as they
 * joined the executor-backed transaction before. Import this instead of the Android one.
 */
suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
```

- [ ] **Step 2: Verify it compiles as common code**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata --console=plain 2>&1 | grep -E "^e: |BUILD"`
Expected: `BUILD SUCCESSFUL`, no `e:` lines.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/data/RoomTransactions.kt
git commit -m "shared: withTransaction adapter over useWriterConnection

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UscjZNxDbAXotuM4Y1ojLh"
```

---

### Task 3: `org.json`-shaped JSON shim

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/json/Json.kt`
- Test: `shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/json/JsonTest.kt`

**Interfaces:**
- Produces, in package `io.github.fowles.stochastic_strength.json`:
  - `class JSONException(message: String, cause: Throwable? = null) : Exception`
  - `class JSONObject` with `constructor()`, `constructor(source: String)`, `put(key: String, value: Any?): JSONObject`, `has`, `isNull`, `getLong`, `getInt`, `getDouble`, `getString`, `getBoolean`, `getJSONObject`, `getJSONArray`, `optString(key, default = "")`, `optInt(key, default)`, `optBoolean(key, default)`, `optJSONArray(key): JSONArray?`, `toString()`, `toString(indentFactor: Int)`, `companion object { val NULL: Any }`
  - `class JSONArray` with `constructor()`, `put(value: Any?): JSONArray`, `length()`, `getJSONObject(index)`, `getString(index)`, `toString()`
- Task 8 swaps `BackupJson.kt`'s three `org.json` imports to these.

- [ ] **Step 1: Write the failing round-trip test**

`shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/json/JsonTest.kt`:

```kotlin
package io.github.fowles.stochastic_strength.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shim must round-trip with the real org.json in both directions for the subset BackupJson
 * uses. Byte-level output may differ (org.json prints 5.0 as 5); parsed values must not.
 */
class JsonTest {

    private fun shimSample(): JSONObject = JSONObject()
        .put("str", "hello")
        .put("long", 1234567890123L)
        .put("int", 42)
        .put("double", 2.5)
        .put("whole", 100.0)
        .put("bool", true)
        .put("nil", JSONObject.NULL)
        .put("nested", JSONObject().put("k", "v"))
        .put("strings", JSONArray().put("A").put("B"))
        .put("objects", JSONArray().put(JSONObject().put("n", 1)).put(JSONObject().put("n", 2)))

    @Test
    fun shimOutputParsesWithOrgJson() {
        for (text in listOf(shimSample().toString(), shimSample().toString(2))) {
            val o = org.json.JSONObject(text)
            assertEquals("hello", o.getString("str"))
            assertEquals(1234567890123L, o.getLong("long"))
            assertEquals(42, o.getInt("int"))
            assertEquals(2.5, o.getDouble("double"), 0.0)
            assertEquals(100.0, o.getDouble("whole"), 0.0)
            assertTrue(o.getBoolean("bool"))
            assertTrue(o.isNull("nil"))
            assertEquals("v", o.getJSONObject("nested").getString("k"))
            assertEquals(listOf("A", "B"), (0 until o.getJSONArray("strings").length()).map { o.getJSONArray("strings").getString(it) })
            assertEquals(2, o.getJSONArray("objects").getJSONObject(1).getInt("n"))
        }
    }

    @Test
    fun orgJsonOutputParsesWithShim() {
        val text = org.json.JSONObject()
            .put("str", "hello").put("long", 1234567890123L).put("int", 42)
            .put("double", 2.5).put("whole", 100.0).put("bool", true)
            .put("nil", org.json.JSONObject.NULL)
            .put("nested", org.json.JSONObject().put("k", "v"))
            .put("strings", org.json.JSONArray().put("A").put("B"))
            .put("objects", org.json.JSONArray().put(org.json.JSONObject().put("n", 1)).put(org.json.JSONObject().put("n", 2)))
            .toString(2)
        val o = JSONObject(text)
        assertEquals("hello", o.getString("str"))
        assertEquals(1234567890123L, o.getLong("long"))
        assertEquals(42, o.getInt("int"))
        assertEquals(2.5, o.getDouble("double"), 0.0)
        assertEquals(100.0, o.getDouble("whole"), 0.0)   // org.json wrote this as `100`
        assertTrue(o.getBoolean("bool"))
        assertTrue(o.isNull("nil"))
        assertEquals("v", o.getJSONObject("nested").getString("k"))
        val strings = o.getJSONArray("strings")
        assertEquals(listOf("A", "B"), (0 until strings.length()).map { strings.getString(it) })
        assertEquals(2, o.getJSONArray("objects").getJSONObject(1).getInt("n"))
    }

    @Test
    fun optAndIsNullSemanticsMatchOrgJson() {
        val o = JSONObject("""{"present":"x","nil":null,"num":7,"flag":false,"arr":[1]}""")
        assertTrue(o.isNull("nil"))
        assertTrue(o.isNull("missing"))
        assertFalse(o.isNull("present"))
        assertEquals("x", o.optString("present"))
        assertEquals("", o.optString("missing"))
        assertEquals("", o.optString("nil"))
        assertEquals(7, o.optInt("num", -1))
        assertEquals(-1, o.optInt("missing", -1))
        assertEquals(false, o.optBoolean("flag", true))
        assertEquals(true, o.optBoolean("missing", true))
        assertEquals(1, o.optJSONArray("arr")!!.length())
        assertNull(o.optJSONArray("missing"))
        assertTrue(o.has("present"))
        assertFalse(o.has("missing"))
    }

    @Test
    fun putNullRemovesKeyLikeOrgJson() {
        val o = JSONObject().put("a", 1).put("a", null)
        assertFalse(o.has("a"))
        assertEquals("{}", o.toString())
    }

    @Test
    fun getOnMissingOrWrongTypeThrowsJSONException() {
        val o = JSONObject("""{"s":"text","n":null}""")
        assertThrows(JSONException::class.java) { o.getLong("missing") }
        assertThrows(JSONException::class.java) { o.getLong("s") }
        assertThrows(JSONException::class.java) { o.getString("n") }
        assertThrows(JSONException::class.java) { o.getJSONArray("s") }
        assertThrows(JSONException::class.java) { o.getJSONObject("missing") }
    }

    @Test
    fun malformedInputThrowsJSONException() {
        assertThrows(JSONException::class.java) { JSONObject("not json") }
        assertThrows(JSONException::class.java) { JSONObject("[1,2]") }
    }

    @Test
    fun prettyPrintUsesTwoSpaceIndentAndCompactHasNone() {
        val o = JSONObject().put("a", JSONObject().put("b", 1))
        assertEquals("""{"a":{"b":1}}""", o.toString())
        assertEquals("{\n  \"a\": {\n    \"b\": 1\n  }\n}", o.toString(2))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --console=plain 2>&1 | grep -E "^e: |BUILD" | head -5`
Expected: `BUILD FAILED` with `e:` lines about unresolved `JSONObject`, `JSONArray`, `JSONException`.

- [ ] **Step 3: Write the shim**

`shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/json/Json.kt`:

```kotlin
package io.github.fowles.stochastic_strength.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A deliberately small facade with org.json's shape over kotlinx.serialization's JSON tree.
 *
 * `org.json` lives in `android.jar`, not the common stdlib. Rather than rewrite `BackupJson`
 * against a different API, this implements exactly the subset it uses, so that file changes
 * by three import lines. Number output differs from org.json in one visible way: org.json
 * prints 100.0 as `100`; this prints `100.0`. Both parsers read both.
 */
class JSONException(message: String, cause: Throwable? = null) : Exception(message, cause)

private val compactJson = Json
private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

private fun toElement(value: Any): JsonElement = when (value) {
    is JsonElement -> value
    is JSONObject -> value.toJsonElement()
    is JSONArray -> value.toJsonElement()
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    else -> JsonPrimitive(value.toString())
}

private fun JsonElement.primitiveOrThrow(where: String): JsonPrimitive =
    this as? JsonPrimitive ?: throw JSONException("$where is not a primitive")

private fun JsonPrimitive.longOrThrow(where: String): Long =
    if (this is JsonNull) throw JSONException("$where is null")
    else content.toLongOrNull() ?: content.toDoubleOrNull()?.toLong() ?: throw JSONException("$where is not a number: $content")

private fun JsonPrimitive.doubleOrThrow(where: String): Double =
    if (this is JsonNull) throw JSONException("$where is null")
    else content.toDoubleOrNull() ?: throw JSONException("$where is not a number: $content")

private fun JsonPrimitive.booleanOrThrow(where: String): Boolean =
    if (this is JsonNull) throw JSONException("$where is null")
    else content.toBooleanStrictOrNull() ?: throw JSONException("$where is not a boolean: $content")

private fun JsonPrimitive.stringOrThrow(where: String): String =
    if (this is JsonNull) throw JSONException("$where is null") else content

class JSONObject {
    private val map = LinkedHashMap<String, JsonElement>()

    constructor()

    constructor(source: String) {
        val element = try {
            compactJson.parseToJsonElement(source)
        } catch (e: Exception) {
            throw JSONException("Malformed JSON: ${e.message}", e)
        }
        val obj = element as? JsonObject ?: throw JSONException("Top-level value is not an object")
        map.putAll(obj)
    }

    internal constructor(obj: JsonObject) { map.putAll(obj) }

    /** Like org.json: `null` removes the key; use [NULL] to store an explicit JSON null. */
    fun put(key: String, value: Any?): JSONObject {
        if (value == null) map.remove(key) else map[key] = toElement(value)
        return this
    }

    fun has(key: String): Boolean = map.containsKey(key)
    fun isNull(key: String): Boolean = map[key].let { it == null || it is JsonNull }

    private fun element(key: String): JsonElement = map[key] ?: throw JSONException("No value for $key")

    fun getLong(key: String): Long = element(key).primitiveOrThrow(key).longOrThrow(key)
    fun getInt(key: String): Int = getLong(key).toInt()
    fun getDouble(key: String): Double = element(key).primitiveOrThrow(key).doubleOrThrow(key)
    fun getBoolean(key: String): Boolean = element(key).primitiveOrThrow(key).booleanOrThrow(key)
    fun getString(key: String): String = element(key).primitiveOrThrow(key).stringOrThrow(key)
    fun getJSONObject(key: String): JSONObject =
        JSONObject(element(key) as? JsonObject ?: throw JSONException("$key is not an object"))
    fun getJSONArray(key: String): JSONArray =
        JSONArray(element(key) as? JsonArray ?: throw JSONException("$key is not an array"))

    fun optString(key: String, default: String = ""): String =
        (map[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: default
    fun optInt(key: String, default: Int): Int =
        (map[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
            ?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }?.toInt() ?: default
    fun optBoolean(key: String, default: Boolean): Boolean =
        (map[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.toBooleanStrictOrNull() ?: default
    fun optJSONArray(key: String): JSONArray? = (map[key] as? JsonArray)?.let { JSONArray(it) }

    fun toJsonElement(): JsonObject = JsonObject(map)
    override fun toString(): String = compactJson.encodeToString(JsonElement.serializer(), toJsonElement())
    fun toString(indentFactor: Int): String =
        if (indentFactor > 0) prettyJson.encodeToString(JsonElement.serializer(), toJsonElement()) else toString()

    companion object {
        /** Sentinel for an explicit JSON null, as in org.json. */
        val NULL: Any = JsonNull
    }
}

class JSONArray {
    private val list = ArrayList<JsonElement>()

    constructor()
    internal constructor(arr: JsonArray) { list.addAll(arr) }

    fun put(value: Any?): JSONArray {
        list.add(if (value == null) JsonNull else toElement(value))
        return this
    }

    fun length(): Int = list.size

    private fun element(index: Int): JsonElement =
        list.getOrNull(index) ?: throw JSONException("Index $index out of range [0, ${list.size})")

    fun getJSONObject(index: Int): JSONObject =
        JSONObject(element(index) as? JsonObject ?: throw JSONException("[$index] is not an object"))
    fun getString(index: Int): String = element(index).primitiveOrThrow("[$index]").stringOrThrow("[$index]")

    fun toJsonElement(): JsonArray = JsonArray(list)
    override fun toString(): String = compactJson.encodeToString(JsonElement.serializer(), toJsonElement())
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --console=plain 2>&1 | grep -E "^e: |BUILD|FAILED"`
Expected: `BUILD SUCCESSFUL`. Check the count: `python3 -c "import glob,xml.etree.ElementTree as E;print(sum(int(E.parse(p).getroot().get('tests')) for p in glob.glob('shared/build/test-results/**/*.xml',recursive=True)))"` → `7`.

If `prettyPrintUsesTwoSpaceIndentAndCompactHasNone` fails on the pretty string, print `o.toString(2)` and adjust the expected literal to kotlinx's actual layout — the assertion exists to document the format, not to force a specific one. Do not weaken the other tests.

- [ ] **Step 5: Verify it is common code**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata --console=plain 2>&1 | grep -E "^e: |BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/json shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/json
git commit -m "shared: org.json-shaped JSON shim over kotlinx.serialization

Exactly the subset BackupJson uses, so that file will change by three import
lines. Round-trip fidelity against the real org.json is pinned by host tests.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UscjZNxDbAXotuM4Y1ojLh"
```

---

### Task 4: `String.format` shim

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/text/Format.kt`
- Create: `shared/src/androidMain/kotlin/io/github/fowles/stochastic_strength/text/Format.android.kt`
- Create: `shared/src/iosMain/kotlin/io/github/fowles/stochastic_strength/text/Format.ios.kt`
- Test: `shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/text/FormatTest.kt`

**Interfaces:**
- Produces: `expect fun String.format(vararg args: Any?): String` in package `io.github.fowles.stochastic_strength.text`, and `internal fun formatPrintfSubset(pattern: String, args: Array<out Any?>): String`. Task 8 adds `import io.github.fowles.stochastic_strength.text.format` to `WeightFormatter` and `PrescriptionTrace`.

`kotlin.text.format` is JVM-only. An explicit import of this same-named extension shadows the default import on the JVM, so call sites like `"%.1f kg".format(kg)` stay byte-identical. The Android actual delegates to the real `java.lang.String.format` (behavior unchanged); iOS gets a printf subset covering what the app uses.

- [ ] **Step 1: Write the failing fidelity test**

`shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/text/FormatTest.kt`:

```kotlin
package io.github.fowles.stochastic_strength.text

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The iOS printf subset must agree with java.lang.String.format for every pattern the app uses. */
class FormatTest {

    private fun viaJava(pattern: String, vararg args: Any?): String = java.lang.String.format(Locale.US, pattern, *args)

    private val patterns = listOf("%.0f", "%.1f", "%.2f", "%.1f kg", "%.0f lbs", "~%.0f%%")

    @Test
    fun subsetMatchesJavaAcrossASweepOfWeights() {
        var v = 0.0
        while (v <= 500.0) {
            for (p in patterns) assertEquals("pattern=$p value=$v", viaJava(p, v), formatPrintfSubset(p, arrayOf(v)))
            v += 0.05
        }
    }

    @Test
    fun subsetMatchesJavaOnTies() {
        for (v in listOf(0.5, 1.5, 2.5, 0.25, 0.35, 1.15, 1.25, 1.35, 2.675, 99.95, 0.05, 0.005))
            for (p in listOf("%.0f", "%.1f", "%.2f"))
                assertEquals("pattern=$p value=$v", viaJava(p, v), formatPrintfSubset(p, arrayOf(v)))
    }

    @Test
    fun subsetHandlesFloatsIntsAndStrings() {
        assertEquals(viaJava("%.1f", 72.5f), formatPrintfSubset("%.1f", arrayOf(72.5f)))
        assertEquals(viaJava("%d sets", 3), formatPrintfSubset("%d sets", arrayOf(3)))
        assertEquals(viaJava("%d", 1234567890123L), formatPrintfSubset("%d", arrayOf(1234567890123L)))
        assertEquals(viaJava("%s and %s", "a", "b"), formatPrintfSubset("%s and %s", arrayOf("a", "b")))
        assertEquals(viaJava("100%%"), formatPrintfSubset("100%%", arrayOf()))
        assertEquals(viaJava("%.2f", -1.005), formatPrintfSubset("%.2f", arrayOf(-1.005)))
    }

    @Test
    fun androidActualIsJavaFormat() {
        assertEquals(viaJava("%.1f kg", 72.5), "%.1f kg".format(72.5))
        assertEquals(viaJava("%.0f", 2.5), "%.0f".format(2.5))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --console=plain 2>&1 | grep -E "^e: |BUILD" | head -5`
Expected: `BUILD FAILED`, unresolved `formatPrintfSubset` and `format`.

- [ ] **Step 3: Write the common declaration and the printf subset**

`shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/text/Format.kt`:

```kotlin
package io.github.fowles.stochastic_strength.text

import kotlin.math.abs
import kotlin.math.floor

/**
 * Explicit-import stand-in for `kotlin.text.format`, which is JVM-only. Importing this in a
 * file shadows the JVM default import, so `"%.1f".format(x)` call sites are unchanged. Android
 * delegates to `java.lang.String.format`; iOS uses [formatPrintfSubset].
 */
expect fun String.format(vararg args: Any?): String

/**
 * The printf subset the app uses: `%s`, `%d`, `%.Nf` (and `%%`). Fixed-point rounding is
 * half-up on the value's decimal form, matching `java.util.Formatter`. Always uses `.` as
 * the decimal separator. Anything else throws [IllegalArgumentException].
 */
internal fun formatPrintfSubset(pattern: String, args: Array<out Any?>): String {
    val out = StringBuilder()
    var argIndex = 0
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c != '%') { out.append(c); i++; continue }
        i++
        if (i >= pattern.length) throw IllegalArgumentException("Dangling '%' in \"$pattern\"")
        if (pattern[i] == '%') { out.append('%'); i++; continue }
        var precision = -1
        if (pattern[i] == '.') {
            i++
            val start = i
            while (i < pattern.length && pattern[i].isDigit()) i++
            precision = pattern.substring(start, i).toIntOrNull()
                ?: throw IllegalArgumentException("Bad precision in \"$pattern\"")
        }
        if (i >= pattern.length) throw IllegalArgumentException("Dangling conversion in \"$pattern\"")
        val conversion = pattern[i++]
        val arg = if (argIndex < args.size) args[argIndex++]
        else throw IllegalArgumentException("Missing argument for %$conversion in \"$pattern\"")
        when (conversion) {
            's' -> out.append(arg.toString())
            'd' -> out.append((arg as Number).toLong().toString())
            'f' -> out.append(fixed((arg as Number).toDouble(), if (precision < 0) 6 else precision))
            else -> throw IllegalArgumentException("Unsupported conversion %$conversion in \"$pattern\"")
        }
    }
    return out.toString()
}

private fun fixed(value: Double, decimals: Int): String {
    var scale = 1.0
    repeat(decimals) { scale *= 10.0 }
    val magnitude = abs(value) * scale
    // Half-up on the decimal form. The tiny nudge keeps binary ties such as 0.35 * 10 = 3.4999...
    // rounding the way their decimal spelling does, as java.util.Formatter rounds them.
    val rounded = floor(magnitude + 0.5 + 1e-9).toLong()
    val intScale = scale.toLong()
    val intPart = rounded / intScale
    val fracPart = rounded % intScale
    val sign = if (value < 0 && rounded != 0L) "-" else ""
    return if (decimals == 0) "$sign$intPart"
    else "$sign$intPart.${fracPart.toString().padStart(decimals, '0')}"
}
```

- [ ] **Step 4: Write the two actuals**

`shared/src/androidMain/kotlin/io/github/fowles/stochastic_strength/text/Format.android.kt`:

```kotlin
package io.github.fowles.stochastic_strength.text

actual fun String.format(vararg args: Any?): String = java.lang.String.format(this, *args)
```

`shared/src/iosMain/kotlin/io/github/fowles/stochastic_strength/text/Format.ios.kt`:

```kotlin
package io.github.fowles.stochastic_strength.text

actual fun String.format(vararg args: Any?): String = formatPrintfSubset(this, args)
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :shared:testAndroidHostTest --console=plain 2>&1 | grep -E "^e: |BUILD|FAILED|FormatTest"`
Expected: `BUILD SUCCESSFUL`, 11 tests total now (7 + 4).

If a tie case in `subsetMatchesJavaOnTies` disagrees with Java, print both strings for that value; the nudge in `fixed` is the knob. Java's `%.Nf` rounds the *shortest decimal representation* half-up (`0.35` → `0.4` at `%.1f`). Do not change the test's expectation — it is the contract.

- [ ] **Step 6: Verify common purity and that the iOS actual is at least type-checked**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata :shared:compileIosMainKotlinMetadata --console=plain 2>&1 | grep -E "^e: |BUILD"`
Expected: `BUILD SUCCESSFUL`. (`compileIosMainKotlinMetadata` runs on Linux; full native compilation does not.)

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/text shared/src/androidMain/kotlin/io/github/fowles/stochastic_strength/text shared/src/iosMain/kotlin/io/github/fowles/stochastic_strength/text shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/text
git commit -m "shared: String.format shim (Java on Android, printf subset on iOS)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UscjZNxDbAXotuM4Y1ojLh"
```

---

### Task 5: `System.currentTimeMillis` shim

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/time/System.kt`
- Test: `shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/time/SystemShadowingTest.kt`

**Interfaces:**
- Produces: `object System { fun currentTimeMillis(): Long }` in package `io.github.fowles.stochastic_strength.time`. Task 8 adds `import io.github.fowles.stochastic_strength.time.System` to four files.

The test deliberately lives in a *different* package with an explicit import, because that is exactly how the four `domain/` files will use it: the explicit import must beat the JVM's default `java.lang.System`.

- [ ] **Step 1: Write the failing test**

`shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/time/SystemShadowingTest.kt`:

```kotlin
package io.github.fowles.stochastic_strength.timeshadow

import io.github.fowles.stochastic_strength.time.System
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** From another package, the explicit import must win over java.lang.System and agree with it. */
class SystemShadowingTest {
    @Test
    fun explicitImportShadowsJavaLangSystemAndAgreesWithIt() {
        val ours = System.currentTimeMillis()
        val jvm = java.lang.System.currentTimeMillis()
        assertTrue("ours=$ours jvm=$jvm", abs(ours - jvm) < 1_000)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --console=plain 2>&1 | grep -E "^e: |BUILD" | head -3`
Expected: `BUILD FAILED` with `e: ... Unresolved reference 'System'` on the import line.

- [ ] **Step 3: Write the shim**

`shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/time/System.kt`:

```kotlin
package io.github.fowles.stochastic_strength.time

import kotlin.time.Clock

/**
 * Explicit-import stand-in for `java.lang.System`, which does not exist in common code.
 * Importing this in a file shadows the JVM default import, so `System.currentTimeMillis()`
 * call sites are unchanged. Deliberately exposes only what the app uses: a future
 * `System.nanoTime()` in shared code fails loudly here instead of silently on iOS.
 */
object System {
    fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest --console=plain 2>&1 | grep -E "^e: |BUILD|FAILED"`
Expected: `BUILD SUCCESSFUL`, 12 tests total (7 + 4 + 1).

- [ ] **Step 5: Verify common purity**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata --console=plain 2>&1 | grep -E "^e: |BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/time shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/time
git commit -m "shared: System.currentTimeMillis shim over kotlin.time.Clock

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UscjZNxDbAXotuM4Y1ojLh"
```

---

### Task 6: CI — shared checks in `android.yml`, new `ios.yml`

**Files:**
- Modify: `.github/workflows/android.yml`
- Create: `.github/workflows/ios.yml`

**Interfaces:**
- Produces: two green workflows. `ios.yml` is the Phase 1 exit criterion's proof.

- [ ] **Step 1: Add the shared checks to `android.yml`**

Replace the three Gradle steps (`Unit tests`, `Lint`, `Assemble debug APK`) with:

```yaml
      - name: Unit tests
        run: ./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest

      # commonMain compiles against only the common stdlib here. An accidental android.* or
      # java.* import in shared code fails this step on Linux instead of on a macOS runner.
      - name: Common-code purity
        run: ./gradlew :shared:compileCommonMainKotlinMetadata

      - name: Lint
        run: ./gradlew :app:lintDebug

      # No device: proves the instrumented tests still resolve every symbol across the module
      # boundary. They execute at the emulator gate, not here.
      - name: Compile instrumented tests
        run: ./gradlew :app:compileDebugAndroidTestKotlin

      - name: Assemble debug APK
        run: ./gradlew :app:assembleDebug
```

Also update the `Upload reports` step's `path:` list to add `shared/build/reports/` and `shared/build/test-results/`.

- [ ] **Step 2: Write `ios.yml`**

```yaml
name: iOS

on:
  push:
    branches: [main, 'claude/**']
  pull_request:
    branches: [main]
  workflow_dispatch:

concurrency:
  group: ios-${{ github.ref }}
  cancel-in-progress: true

jobs:
  link:
    name: Link shared framework for iOS simulator
    runs-on: macos-latest
    timeout-minutes: 60

    steps:
      - uses: actions/checkout@v5

      - name: Set up JDK 21
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '21'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      # The Kotlin/Native toolchain is a several-hundred-MB download keyed on the Kotlin version.
      - name: Cache Kotlin/Native
        uses: actions/cache@v4
        with:
          path: ~/.konan
          key: konan-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml') }}
          restore-keys: konan-${{ runner.os }}-

      # Linking (not just compiling) also proves the framework declaration in shared/build.gradle.kts.
      # iosArm64 is deliberately not built in Phase 1; it is exercised the first time an app is built.
      - name: Link iOS simulator framework
        run: ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

      - name: Upload reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: ios-reports
          path: shared/build/reports/
          if-no-files-found: ignore
```

- [ ] **Step 3: Validate YAML and run the Android sequence locally**

Run: `python3 -c "import yaml;[yaml.safe_load(open(f)) for f in ['.github/workflows/android.yml','.github/workflows/ios.yml']];print('YAML OK')"`
Expected: `YAML OK`.

Run: `./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest :shared:compileCommonMainKotlinMetadata :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug --console=plain 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit and push; watch both workflows**

```bash
git add .github/workflows/android.yml .github/workflows/ios.yml
git commit -m "ci: shared-module checks in android.yml; new ios.yml links the simulator framework

ios.yml lands now, on a tiny module, so the macOS plumbing (Kotlin/Native
toolchain, ~/.konan cache, framework link) is debugged before any code moves.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UscjZNxDbAXotuM4Y1ojLh"
git push -u origin claude/quirky-gauss-op88og
```

Then poll until both runs complete:

```bash
for wf in android.yml ios.yml; do
  until r=$(curl -sS "https://api.github.com/repos/JEHoctor/stochastic-strength/actions/workflows/$wf/runs?per_page=1&branch=claude/quirky-gauss-op88og" | python3 -c "import json,sys;d=json.load(sys.stdin)['workflow_runs'][0];print(d['status'],d['conclusion'],d['html_url'])") && [[ $r == completed* ]]; do sleep 30; done; echo "$wf: $r"
done
```

Expected: both `completed success`. If `ios.yml` fails, read the job log at the printed URL; the likely culprits are a missing `xcode-select` (should not happen on `macos-latest`) or a Kotlin/Native download timeout (re-run once). Fix and re-push before proceeding — this job must be green before any source moves.

---

### Task 7: Room → KMP APIs, in place

**Files:**
- Modify: `app/src/main/java/io/github/fowles/stochastic_strength/data/AppDatabase.kt`
- Create: `app/src/main/java/io/github/fowles/stochastic_strength/data/AppDatabase.android.kt`
- Modify: `app/src/main/java/io/github/fowles/stochastic_strength/domain/WorkoutRepository.kt:3`
- Modify: `app/src/main/java/io/github/fowles/stochastic_strength/domain/backup/BackupManager.kt:3`
- Modify: `app/src/androidTest/java/io/github/fowles/stochastic_strength/data/Migration12To13Test.kt:149`
- Modify: `app/src/androidTest/java/io/github/fowles/stochastic_strength/data/Migration15To16Test.kt:46`
- Modify: `app/build.gradle.kts` (add `sqlite-bundled` for the duration of Tasks 7–8)

**Interfaces:**
- Consumes: `withTransaction` from Task 2.
- Produces: `AppDatabase.Companion.configure(builder: RoomDatabase.Builder<AppDatabase>): RoomDatabase.Builder<AppDatabase>`; public `val MIGRATION_9_10` … `MIGRATION_19_20`; `AppDatabase.Companion.getInstance(context, scope)` and `.reset(context, scope)` as extension functions with unchanged call syntax.

Everything here still lives in `app` and runs on the JVM; behavior is provably unchanged except the SQLite driver (bundled instead of framework — same file format).

- [ ] **Step 1: Give `app` the bundled driver dependency (temporary — removed in Task 9)**

In `app/build.gradle.kts` `dependencies { }`, after `implementation(libs.androidx.room.ktx)`, add:

```kotlin
    implementation(libs.androidx.sqlite.bundled)
```

- [ ] **Step 2: Migration signatures — type change only, `db` name kept**

Run:

```bash
f=app/src/main/java/io/github/fowles/stochastic_strength/data/AppDatabase.kt
sed -i 's/override fun migrate(db: SupportSQLiteDatabase)/override fun migrate(db: SQLiteConnection)/' "$f"
sed -i 's/^        internal val MIGRATION_/        val MIGRATION_/' "$f"
grep -c "migrate(db: SQLiteConnection)" "$f"; grep -c "internal val MIGRATION_" "$f"; grep -c "^        val MIGRATION_" "$f"
```

Expected: `18`, `0`, `11`.

- [ ] **Step 3: Imports and the builder split in `AppDatabase.kt`**

Replace these four imports:

```kotlin
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
```

with (keep the file's alphabetical order; `androidx.sqlite.*` lines go after `androidx.room.migration.Migration`, `kotlinx.*` at the end):

```kotlin
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
```

Then replace the whole block from `        @Volatile private var INSTANCE: AppDatabase? = null` through the closing of `buildDatabase` (the `.build()` line) with:

```kotlin
        /** Applies every migration and the platform-neutral driver/dispatcher; platforms supply the builder. */
        fun configure(builder: RoomDatabase.Builder<AppDatabase>): RoomDatabase.Builder<AppDatabase> =
            builder
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                    MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                    MIGRATION_18_19, MIGRATION_19_20,
                )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
```

The `companion object` closing brace and the class closing brace stay.

- [ ] **Step 4: Create the Android builder file**

`app/src/main/java/io/github/fowles/stochastic_strength/data/AppDatabase.android.kt`:

```kotlin
package io.github.fowles.stochastic_strength.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope

// The Context-dependent construction path. Extension functions on the companion keep the
// call sites (`AppDatabase.getInstance(...)`, `AppDatabase.reset(...)`) unchanged.

@Volatile private var INSTANCE: AppDatabase? = null
private val LOCK = Any()

fun AppDatabase.Companion.getInstance(context: Context, scope: CoroutineScope): AppDatabase =
    INSTANCE ?: synchronized(LOCK) {
        INSTANCE ?: buildDatabase(context, scope).also { INSTANCE = it }
    }

fun AppDatabase.Companion.reset(context: Context, scope: CoroutineScope): AppDatabase {
    synchronized(LOCK) {
        INSTANCE?.close()
        INSTANCE = null
    }
    context.deleteDatabase("stochastic_strength.db")
    return getInstance(context, scope)
}

private fun buildDatabase(context: Context, scope: CoroutineScope): AppDatabase =
    AppDatabase.configure(
        Room.databaseBuilder(context, AppDatabase::class.java, "stochastic_strength.db"),
    ).build()
```

- [ ] **Step 5: Swap the two `withTransaction` imports**

```bash
for f in app/src/main/java/io/github/fowles/stochastic_strength/domain/WorkoutRepository.kt app/src/main/java/io/github/fowles/stochastic_strength/domain/backup/BackupManager.kt; do
  sed -i 's/^import androidx.room.withTransaction$/import io.github.fowles.stochastic_strength.data.withTransaction/' "$f"
done
grep -n "withTransaction" app/src/main/java/io/github/fowles/stochastic_strength/domain/WorkoutRepository.kt | head -1
```

Expected: line 3 now imports `io.github.fowles.stochastic_strength.data.withTransaction`. Kotlin import order may now be off; if `ktlint`/lint is not enforced (it is not — `lintDebug` is Android Lint), leave it, because moving the line would widen the diff.

- [ ] **Step 6: The two instrumented-test wraps**

```bash
for f in app/src/androidTest/java/io/github/fowles/stochastic_strength/data/Migration12To13Test.kt app/src/androidTest/java/io/github/fowles/stochastic_strength/data/Migration15To16Test.kt; do
  sed -i 's/\.migrate(db)$/.migrate(SupportSQLiteConnection(db))/' "$f"
  sed -i 's/^import androidx.sqlite.db.SupportSQLiteDatabase$/import androidx.sqlite.db.SupportSQLiteDatabase\nimport androidx.sqlite.driver.SupportSQLiteConnection/' "$f"
done
grep -n "migrate(SupportSQLiteConnection(db))" app/src/androidTest/java/io/github/fowles/stochastic_strength/data/Migration1*Test.kt
```

Expected: one hit in each file.

- [ ] **Step 7: Verify — the full Android sequence**

Run: `./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug --console=plain 2>&1 | grep -E "^e: |BUILD|warning:.*MIGRATION" | head`
Expected: `BUILD SUCCESSFUL`. 378 + 12 tests. (Warnings about the parameter name `db` vs `connection` are expected in `app` for this and the next task only; `shared` suppresses them after the move.)

- [ ] **Step 8: Review the diff and commit**

Run: `git diff --stat` — expected: `AppDatabase.kt` (~40 lines changed), one new file, four one-line files, `app/build.gradle.kts` one line.

```bash
git add app/build.gradle.kts app/src/main/java/io/github/fowles/stochastic_strength/data/AppDatabase.kt app/src/main/java/io/github/fowles/stochastic_strength/data/AppDatabase.android.kt app/src/main/java/io/github/fowles/stochastic_strength/domain/WorkoutRepository.kt app/src/main/java/io/github/fowles/stochastic_strength/domain/backup/BackupManager.kt app/src/androidTest/java/io/github/fowles/stochastic_strength/data/Migration12To13Test.kt app/src/androidTest/java/io/github/fowles/stochastic_strength/data/Migration15To16Test.kt
git commit -m "data: convert Room to its KMP APIs in place

Migrations take SQLiteConnection (parameter name kept as db, so the 56
execSQL calls are untouched); the 11 internal MIGRATION_* vals become public
so app's instrumented tests can reach them across the coming module boundary.
BundledSQLiteDriver on Android too, for one SQLite version under the replay
engine on every platform. The Context-dependent getInstance/reset/buildDatabase
move to a sibling file as Companion extensions; call sites unchanged.
withTransaction call sites unchanged; import swapped to the shared adapter.
Two migration tests that hand-invoke migrate(db) wrap the support database in
SupportSQLiteConnection, since the default overload throws at runtime.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UscjZNxDbAXotuM4Y1ojLh"
```

---

### Task 8: Swap JVM-only calls for the shims (import lines) + backup round-trip test

**Files:**
- Modify (import lines only): `domain/backup/BackupJson.kt`, `domain/WeightFormatter.kt`, `domain/WorkoutPlanner.kt`, `domain/WorkoutRepository.kt`, `domain/backup/BackupManager.kt`, `domain/progression/ExerciseProgressionSeriesBuilder.kt`, `domain/derived/DerivedStateStore.kt` (all under `app/src/main/java/io/github/fowles/stochastic_strength/`)
- Modify (imports + 3 lines): `domain/belief/PrescriptionTrace.kt`
- Create: `app/src/test/java/io/github/fowles/stochastic_strength/domain/backup/BackupJsonRoundTripTest.kt`
- Modify: `app/build.gradle.kts` (add `kotlinx-datetime` for the duration of this task)

**Interfaces:**
- Consumes: Tasks 3, 4, 5 shims.

Still in `app`, still on the JVM: the Android actuals delegate to the same JVM calls, so behavior is unchanged.

- [ ] **Step 1: Write the failing round-trip test**

`app/src/test/java/io/github/fowles/stochastic_strength/domain/backup/BackupJsonRoundTripTest.kt`:

```kotlin
package io.github.fowles.stochastic_strength.domain.backup

import io.github.fowles.stochastic_strength.data.model.BaselineChangeReason
import io.github.fowles.stochastic_strength.data.model.BaselineOverride
import io.github.fowles.stochastic_strength.data.model.Equipment
import io.github.fowles.stochastic_strength.data.model.Exercise
import io.github.fowles.stochastic_strength.data.model.ExerciseHurtState
import io.github.fowles.stochastic_strength.data.model.KnownLocation
import io.github.fowles.stochastic_strength.data.model.LocationExcludedExercise
import io.github.fowles.stochastic_strength.data.model.MuscleGroup
import io.github.fowles.stochastic_strength.data.model.SavedWorkout
import io.github.fowles.stochastic_strength.data.model.SavedWorkoutExercise
import io.github.fowles.stochastic_strength.data.model.SetFeedback
import io.github.fowles.stochastic_strength.data.model.Sex
import io.github.fowles.stochastic_strength.data.model.StrengthLevel
import io.github.fowles.stochastic_strength.data.model.UserProfile
import io.github.fowles.stochastic_strength.data.model.WeightUnit
import io.github.fowles.stochastic_strength.data.model.WorkoutSession
import io.github.fowles.stochastic_strength.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

/** A full backup survives build → parse, and the output is readable by the real org.json. */
class BackupJsonRoundTripTest {

    private val backup = WorkoutBackup(
        formatVersion = WorkoutBackup.FORMAT_VERSION,
        dbVersion = WorkoutBackup.DB_VERSION,
        exportedAt = 1_757_500_000_000L,
        exercises = listOf(
            Exercise(id = 1, name = "Bench Press", primaryMuscle = MuscleGroup.CHEST,
                secondaryMuscles = listOf(MuscleGroup.CHEST), equipment = Equipment.BARBELL,
                isDisliked = false, isUnilateral = false, isAsymmetric = true, isTimed = false),
            Exercise(id = 2, name = "Plank", primaryMuscle = MuscleGroup.CHEST,
                secondaryMuscles = emptyList(), equipment = Equipment.BARBELL, isTimed = true),
        ),
        knownLocations = listOf(KnownLocation(id = 1, name = "Gym", latitude = 42.36, longitude = -71.06)),
        locationExcludedExercises = listOf(LocationExcludedExercise(locationId = 1, exerciseId = 2)),
        workoutSessions = listOf(
            WorkoutSession(id = 1, locationId = 1, startTime = 1_757_000_000_000L, endTime = 1_757_003_600_000L, stravaActivityId = 99L),
            WorkoutSession(id = 2, locationId = null, startTime = 1_757_100_000_000L, endTime = null, stravaActivityId = null),
        ),
        workoutSets = listOf(
            WorkoutSet(id = 1, sessionId = 1, exerciseId = 1, setNumber = 1, targetWeight = 72.5f, targetReps = 8,
                actualReps = 8, feedback = SetFeedback.TOO_HARD, completedAt = 1_757_000_500_000L, durationSeconds = null),
            WorkoutSet(id = 2, sessionId = 1, exerciseId = 2, setNumber = 1, targetWeight = 0f, targetReps = 1,
                actualReps = null, feedback = null, completedAt = null, durationSeconds = 60),
        ),
        userProfile = listOf(UserProfile(id = 1, sex = Sex.FEMALE, strengthLevel = StrengthLevel.MEDIUM,
            weightUnit = WeightUnit.KG, preferredExerciseCount = 6, preferredRepMin = null, preferredRepMax = 12)),
        baselineOverrides = listOf(BaselineOverride(id = 1, sessionId = null, muscleGroup = MuscleGroup.CHEST,
            baselineWeight = 60.25f, asOf = 1_756_000_000_000L, reason = BaselineChangeReason.INITIAL)),
        exerciseHurtState = listOf(ExerciseHurtState(exerciseId = 1, isHurt = true, asOf = 1_757_000_000_000L)),
        savedWorkouts = listOf(SavedWorkout(id = 1, name = "", createdAt = 1_757_200_000_000L)),
        savedWorkoutExercises = listOf(SavedWorkoutExercise(id = 1, workoutId = 1, exerciseId = 1, position = 0, reps = null)),
    )

    @Test
    fun buildThenParseIsIdentity() {
        val json = BackupJsonBuilder.build(backup)
        assertEquals(backup, BackupJsonParser.parse(json))
    }

    @Test
    fun outputIsReadableByOrgJson() {
        val root = org.json.JSONObject(BackupJsonBuilder.build(backup))
        assertEquals(WorkoutBackup.FORMAT, root.getString("format"))
        assertEquals(WorkoutBackup.DB_VERSION, root.getInt("dbVersion"))
        val sets = root.getJSONObject("tables").getJSONArray("workoutSets")
        assertEquals(72.5, sets.getJSONObject(0).getDouble("targetWeight"), 0.0)
        assertEquals(true, sets.getJSONObject(1).isNull("feedback"))
    }

    @Test
    fun orgJsonProducedBackupParses() {
        // Simulate a backup written by the previous org.json-based builder: same keys, org.json's number formatting.
        val viaShim = org.json.JSONObject(BackupJsonBuilder.build(backup)).toString(2)
        assertEquals(backup, BackupJsonParser.parse(viaShim))
    }
}
```

- [ ] **Step 2: Run it to confirm it passes against the current `org.json` implementation (baseline)**

Run: `./gradlew :app:testDebugUnitTest --tests "io.github.fowles.stochastic_strength.domain.backup.BackupJsonRoundTripTest" --console=plain 2>&1 | grep -E "BUILD|FAILED"`
Expected: `BUILD SUCCESSFUL`. This test is the contract; it must still pass after the swap below.

- [ ] **Step 3: Swap `BackupJson.kt`'s imports**

```bash
f=app/src/main/java/io/github/fowles/stochastic_strength/domain/backup/BackupJson.kt
sed -i 's/^import org.json.JSONArray$/import io.github.fowles.stochastic_strength.json.JSONArray/; s/^import org.json.JSONException$/import io.github.fowles.stochastic_strength.json.JSONException/; s/^import org.json.JSONObject$/import io.github.fowles.stochastic_strength.json.JSONObject/' "$f"
grep -c "org.json" "$f"
```

Expected: `0`.

- [ ] **Step 4: Add the `format` import to the two formatting files**

```bash
f=app/src/main/java/io/github/fowles/stochastic_strength/domain/WeightFormatter.kt
sed -i 's/^import io.github.fowles.stochastic_strength.data.model.WeightUnit$/import io.github.fowles.stochastic_strength.data.model.WeightUnit\nimport io.github.fowles.stochastic_strength.text.format/' "$f"
f=app/src/main/java/io/github/fowles/stochastic_strength/domain/belief/PrescriptionTrace.kt
sed -i 's/^import io.github.fowles.stochastic_strength.domain.policy.PrescriptionPolicy$/import io.github.fowles.stochastic_strength.domain.policy.PrescriptionPolicy\nimport io.github.fowles.stochastic_strength.text.format/' "$f"
grep -n "text.format" app/src/main/java/io/github/fowles/stochastic_strength/domain/WeightFormatter.kt app/src/main/java/io/github/fowles/stochastic_strength/domain/belief/PrescriptionTrace.kt
```

Expected: one hit in each.

- [ ] **Step 5: `PrescriptionTrace` — replace the `SimpleDateFormat` with `kotlinx-datetime`**

Temporarily add to `app/build.gradle.kts` `dependencies { }` (removed in Task 9): `implementation(libs.kotlinx.datetime)`.

In `PrescriptionTrace.kt`, replace the three imports

```kotlin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

with

```kotlin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
```

Replace

```kotlin
    private val dateFormat get() = SimpleDateFormat("MMM d", Locale.US)
```

with

```kotlin
    // "MMM d" in English, in the device's zone — what SimpleDateFormat("MMM d", Locale.US) produced.
    private val monthDay = LocalDate.Format { monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); day(Padding.NONE) }
    private fun formatMonthDay(epochMs: Long): String =
        monthDay.format(Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date)
```

and the one call site

```kotlin
                    "last updated ${dateFormat.format(Date(foldedAt))}",
```

with

```kotlin
                    "last updated ${formatMonthDay(foldedAt)}",
```

- [ ] **Step 6: Add the `System` import to four files and the `Volatile` import to one**

```bash
P=app/src/main/java/io/github/fowles/stochastic_strength
for f in $P/domain/WorkoutPlanner.kt $P/domain/WorkoutRepository.kt $P/domain/backup/BackupManager.kt $P/domain/progression/ExerciseProgressionSeriesBuilder.kt; do
  # insert after the package line's following blank line, i.e. as the first import
  awk 'NR==1{print; next} !done && /^import /{print "import io.github.fowles.stochastic_strength.time.System"; done=1} {print}' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
done
f=$P/domain/derived/DerivedStateStore.kt
awk 'NR==1{print; next} !done && /^import /{print "import kotlin.concurrent.Volatile"; done=1} {print}' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
grep -l "stochastic_strength.time.System" $P/domain/WorkoutPlanner.kt $P/domain/WorkoutRepository.kt $P/domain/backup/BackupManager.kt $P/domain/progression/ExerciseProgressionSeriesBuilder.kt | wc -l
grep -c "kotlin.concurrent.Volatile" $f
```

Expected: `4`, `1`. Each file now has the new import as its first import line (before the alphabetically-sorted block; this is a deliberate one-line insertion, not a re-sort).

- [ ] **Step 7: Verify — full Android sequence, and that no `org.json`/`java.time`/`SimpleDateFormat` remain in the 80 files**

Run:

```bash
./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug --console=plain 2>&1 | grep -E "^e: |BUILD"
cd app/src/main/java/io/github/fowles/stochastic_strength && grep -rlE "^import (org\.json|java\.)" $(find data domain -name '*.kt' | grep -v '/strava/' | grep -v HistoryRows.kt); cd - >/dev/null
```

Expected: `BUILD SUCCESSFUL` (379 app tests now — the new round-trip test — plus 12 shared); the `grep` prints nothing.

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/io/github/fowles/stochastic_strength/domain app/src/test/java/io/github/fowles/stochastic_strength/domain/backup/BackupJsonRoundTripTest.kt
git commit -m "domain: swap JVM-only calls for the shared shims (import lines)

BackupJson: org.json -> the same-shaped shim (3 imports). WeightFormatter and
PrescriptionTrace: explicit import of the shared String.format. WorkoutPlanner,
WorkoutRepository, BackupManager, ExerciseProgressionSeriesBuilder: explicit
import of the shared System. DerivedStateStore: kotlin.concurrent.Volatile.
PrescriptionTrace's one SimpleDateFormat becomes a kotlinx-datetime format
with identical output. Call sites are byte-identical; on the JVM every shim
delegates to the call it replaces. A new host test round-trips a full backup
and cross-checks the output with the real org.json.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UscjZNxDbAXotuM4Y1ojLh"
```

---

### Task 9: The move — `git mv` only

**Files:**
- Move: 80 files `app/src/main/java/.../{data,domain}/**` → `shared/src/commonMain/kotlin/...` (excluding `domain/strava/`, `domain/history/HistoryRows.kt`, `data/AppDatabase.android.kt`)
- Move: `app/src/main/java/.../data/AppDatabase.android.kt` → `shared/src/androidMain/kotlin/.../data/`
- Move: 64 files `app/src/test/java/.../{data,domain}/**` → `shared/src/androidHostTest/kotlin/...` (excluding `domain/strava/`, `domain/history/HistoryRowsTest.kt`; the 64th is Task 8's `BackupJsonRoundTripTest`)
- Move: `app/schemas/` → `shared/schemas/`
- Modify: `app/build.gradle.kts` (Room/KSP/datetime/sqlite removal, schema-assets repoint, androidTest Room deps)

**Interfaces:**
- Consumes: everything above. Produces: `commonMain` that compiles as common code.

**No content edits in this task.** If something does not compile after the move, stop, note it, and finish this task as the pure move it is only if the failure is one the next task already covers (`@ConstructedBy`, backtest path); anything else means the spec's file list was wrong — record it and fix it in a separate commit *after* this one.

- [ ] **Step 1: Move main sources**

```bash
ROOT=$(git rev-parse --show-toplevel); cd "$ROOT"
SRC=app/src/main/java/io/github/fowles/stochastic_strength
DST=shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength
n=0
for f in $(cd $SRC && find data domain -name '*.kt' | grep -v '^domain/strava/' | grep -v '^domain/history/HistoryRows.kt' | grep -v '^data/AppDatabase.android.kt' | sort); do
  mkdir -p "$DST/$(dirname $f)"; git mv "$SRC/$f" "$DST/$f"; n=$((n+1))
done; echo "moved main: $n"
mkdir -p shared/src/androidMain/kotlin/io/github/fowles/stochastic_strength/data
git mv $SRC/data/AppDatabase.android.kt shared/src/androidMain/kotlin/io/github/fowles/stochastic_strength/data/AppDatabase.android.kt
ls $SRC/data 2>/dev/null; ls $SRC/domain
```

Expected: `moved main: 80`; `app/.../data` no longer exists; `app/.../domain` contains only `strava/` and `history/`.

- [ ] **Step 2: Move host tests**

```bash
SRC=app/src/test/java/io/github/fowles/stochastic_strength
DST=shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength
n=0
for f in $(cd $SRC && find data domain -name '*.kt' | grep -v '^domain/strava/' | grep -v '^domain/history/HistoryRowsTest.kt' | sort); do
  mkdir -p "$DST/$(dirname $f)"; git mv "$SRC/$f" "$DST/$f"; n=$((n+1))
done; echo "moved tests: $n"
find $SRC -name '*.kt' | wc -l
```

Expected: `moved tests: 64`; `12` remaining in `app/src/test`.

- [ ] **Step 3: Move schemas**

```bash
git mv app/schemas shared/schemas
ls shared/schemas/io.github.fowles.stochastic_strength.data.AppDatabase | wc -l
```

Expected: `19`.

- [ ] **Step 4: Rewire `app/build.gradle.kts`**

Remove these lines:

```kotlin
    alias(libs.plugins.ksp)
```
```kotlin
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
```
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```
```kotlin
    implementation(libs.androidx.room.runtime)
```
```kotlin
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite.bundled)
```
```kotlin
    ksp(libs.androidx.room.compiler)
```
```kotlin
    implementation(libs.kotlinx.datetime)
```
```kotlin
    // Not used directly. Room 2.8.4's MigrationTestHelper needs kotlinx-serialization >= 1.8.1, but
    // lifecycle 2.11 pulls 1.7.3 into the app runtime and Gradle's consistent resolution then pins
    // the androidTest classpath to that. Declaring it here lifts the app runtime to what Room needs.
    implementation(libs.kotlinx.serialization.core)
```

Re-add the schema assets, now pointing at `shared` (put it back where the removed `sourceSets` block was, inside `android { }`):

```kotlin
    sourceSets {
        // Room writes schemas in :shared; MigrationTestHelper reads them from this module's test assets.
        getByName("androidTest").assets.directories.add("$rootDir/shared/schemas")
    }
```

Keep `androidTestImplementation(libs.androidx.room.testing)` as is. The instrumented tests' direct use of `Room.databaseBuilder`, `SupportSQLiteConnection`, and `MigrationTestHelper` resolves through `shared`'s `api(room-runtime)` plus `room-testing`.

- [ ] **Step 5: Verify the move**

```bash
git status --porcelain | awk '{print $1}' | sort | uniq -c
```

Expected: `R` (renamed) ≈ 164 (80 + 1 + 64 + 19), `M` = 1 (`app/build.gradle.kts`). No `A`, no `D`.

Run: `./gradlew :shared:compileCommonMainKotlinMetadata --console=plain 2>&1 | grep -E "^e: |BUILD"`
Expected: `BUILD SUCCESSFUL`. If an `e:` names a JVM-only symbol the spec did not list, note the file and symbol; finish this task's commit only once the move itself is green, then treat that symbol as a Task 8-style import-line fix in a follow-up commit.

Run: `./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug --console=plain 2>&1 | grep -E "^e: |BUILD"`
Expected: `BUILD SUCCESSFUL`.

Count tests: `python3 -c "import glob,xml.etree.ElementTree as E;print({m:sum(int(E.parse(p).getroot().get('tests')) for p in glob.glob(m+'/build/test-results/**/*.xml',recursive=True)) for m in ['app','shared']})"`
Expected: the two numbers sum to **391** (378 original + 1 round-trip + 12 shim tests); `app`'s share is whatever its 12 remaining test files contain. The backtest tests still skip (`history.json` path not yet updated — Task 10).

- [ ] **Step 6: Commit**

```bash
git add -A app shared
git commit -m "refactor: move data/ and domain/ into shared (renames only)

80 source files to commonMain, the Android database builder to androidMain,
64 host tests to androidHostTest, 19 Room schemas — all by git mv, no content
changes. app/build.gradle.kts loses Room, KSP, and the schema location, and
repoints its androidTest schema assets at shared/schemas. domain/strava/ and
domain/history/HistoryRows.kt stay in app with their consumers.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UscjZNxDbAXotuM4Y1ojLh"
git show --stat HEAD | grep -cE "=> " 
```

Expected: the rename count printed matches the `R` count from Step 5.

---

### Task 10: iOS construction, backtest fixture path, `.gitignore`

**Files:**
- Modify: `shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/data/AppDatabase.kt` (one annotation, one import)
- Create: `shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/data/AppDatabaseConstructor.kt`
- Modify: `shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/domain/backtest/BacktestData.kt:60`
- Modify: `.gitignore:20`

**Interfaces:**
- Produces: `expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>`; Room KSP generates the `actual` per target.

- [ ] **Step 1: The constructor `expect`**

`shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/data/AppDatabaseConstructor.kt`:

```kotlin
package io.github.fowles.stochastic_strength.data

import androidx.room.RoomDatabaseConstructor

/** Lets iOS build the database without reflection. Room's KSP generates the actual. */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
```

- [ ] **Step 2: Annotate the database**

In `AppDatabase.kt`, add `import androidx.room.ConstructedBy` (alphabetically, before `import androidx.room.Database`) and add `@ConstructedBy(AppDatabaseConstructor::class)` on its own line immediately after the closing `)` of the `@Database(...)` annotation, before `@TypeConverters(Converters::class)`.

- [ ] **Step 3: Backtest fixture path and `.gitignore`**

```bash
sed -i 's|File("src/test/resources/backtest")|File("src/androidHostTest/resources/backtest")|' shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/domain/backtest/BacktestData.kt
sed -i 's|^/app/src/test/resources/backtest/$|/shared/src/androidHostTest/resources/backtest/|' .gitignore
grep -n "androidHostTest/resources/backtest" shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/domain/backtest/BacktestData.kt .gitignore
```

Expected: one hit in each file.

- [ ] **Step 4: Verify locally**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata :shared:testAndroidHostTest :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug --console=plain 2>&1 | grep -E "^e: |BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit and push; both workflows must be green**

```bash
git add .gitignore shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/data/AppDatabase.kt shared/src/commonMain/kotlin/io/github/fowles/stochastic_strength/data/AppDatabaseConstructor.kt shared/src/androidHostTest/kotlin/io/github/fowles/stochastic_strength/domain/backtest/BacktestData.kt
git commit -m "data: wire AppDatabase for iOS construction; backtest fixture path

@ConstructedBy + the expect object Room needs to build the database without
reflection on iOS (separate from the rename commit so that one stays pure).
BacktestData's module-relative fixture path follows the tests to shared.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UscjZNxDbAXotuM4Y1ojLh"
git push
```

Poll as in Task 6 Step 4. Expected: `android.yml` and `ios.yml` both `completed success`. **This is the Phase 1 exit criterion for CI.** If `ios.yml` fails on Room KSP for the iOS target, read the log: the two likely causes are the `expect` object's signature (must match `RoomDatabaseConstructor<AppDatabase>` exactly) and a missing `kspIosSimulatorArm64` dependency (Task 1 declared it; confirm it survived).

---

### Task 11: Guard against shared-layer code stranded in `app`

**Files:**
- Modify: `.github/workflows/android.yml`

- [ ] **Step 1: Add the step**

Insert immediately after the `actions/checkout@v5` step:

```yaml
      # After `git merge upstream/main`, a file fowles adds under domain/ or data/ lands here,
      # compiles fine, and is silently absent from commonMain. Make that loud. Allowlisted
      # entries are the Android-only files later phases move; shrink the list as they go.
      - name: Guard against shared-layer code stranded in app
        run: |
          base=app/src/main/java/io/github/fowles/stochastic_strength
          stray=$(find "$base/domain" "$base/data" -name '*.kt' 2>/dev/null \
                  | grep -v '/domain/strava/' \
                  | grep -v '/domain/history/HistoryRows.kt' || true)
          if [ -n "$stray" ]; then
            echo "These files belong in shared/src/commonMain, not app/:"; echo "$stray"; exit 1
          fi
```

- [ ] **Step 2: Test the guard locally, both ways**

```bash
bash -c 'base=app/src/main/java/io/github/fowles/stochastic_strength; stray=$(find "$base/domain" "$base/data" -name "*.kt" 2>/dev/null | grep -v "/domain/strava/" | grep -v "/domain/history/HistoryRows.kt" || true); [ -z "$stray" ] && echo CLEAN || { echo "$stray"; echo STRAY; }'
mkdir -p app/src/main/java/io/github/fowles/stochastic_strength/domain && touch app/src/main/java/io/github/fowles/stochastic_strength/domain/Stray.kt
bash -c 'base=app/src/main/java/io/github/fowles/stochastic_strength; stray=$(find "$base/domain" "$base/data" -name "*.kt" 2>/dev/null | grep -v "/domain/strava/" | grep -v "/domain/history/HistoryRows.kt" || true); [ -z "$stray" ] && echo CLEAN || { echo "$stray"; echo STRAY; }'
rm app/src/main/java/io/github/fowles/stochastic_strength/domain/Stray.kt
```

Expected: `CLEAN`, then the stray path followed by `STRAY`.

- [ ] **Step 3: Commit and push**

```bash
git add .github/workflows/android.yml
git commit -m "ci: guard against shared-layer code stranded in app

Separate commit so it can be cherry-picked out of any upstream PR.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UscjZNxDbAXotuM4Y1ojLh"
git push
```

Expected: `android.yml` `completed success`.

---

### Task 12: Emulator gate — `:app:connectedAndroidTest`

**Files:** none changed. This is the merge gate: the only execution of the migration, DAO, and repository tests against the bundled driver.

- [ ] **Step 1: Install a system image and create a headless AVD (one-time on the VM)**

```bash
export ANDROID_HOME=$HOME/Android/Sdk
SDKM=$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager
$SDKM --install "emulator" "system-images;android-35;google_apis;x86_64" 2>&1 | grep -viE "deprecated|android CLI|d.android.com" | tail -3
echo no | $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n ci35 -k "system-images;android-35;google_apis;x86_64" -d pixel_6 --force 2>&1 | tail -2
ls ~/.android/avd/ | grep ci35
```

Expected: `ci35.avd` listed. (The app's `minSdk` is 33; `android-35` `google_apis;x86_64` is a stable image ≥ 33 with KVM acceleration.)

- [ ] **Step 2: Boot headless and wait for it**

```bash
export ANDROID_HOME=$HOME/Android/Sdk
nohup $ANDROID_HOME/emulator/emulator -avd ci35 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel on > /tmp/emu.log 2>&1 &
$ANDROID_HOME/platform-tools/adb wait-for-device
until [ "$($ANDROID_HOME/platform-tools/adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 5; done; echo BOOTED
$ANDROID_HOME/platform-tools/adb devices
```

Expected: `BOOTED` within ~2 minutes and one `emulator-5554  device` line. If `-accel on` fails, check `ls -la /dev/kvm` (must be readable by the `claude` user; it was `crw-rw-rw-` at planning time).

- [ ] **Step 3: Run the instrumented suite**

Run: `./gradlew :app:connectedAndroidTest --console=plain 2>&1 | grep -E "^e: |BUILD|tests completed|FAILED|Tests on" | tail -8`
Expected: `BUILD SUCCESSFUL` and a line like `Tests on emulator-5554 ... : N tests, 0 failures` (roughly 60 tests across the 20 classes).

Report: `python3 -c "import glob,xml.etree.ElementTree as E;r=[E.parse(p).getroot() for p in glob.glob('app/build/outputs/androidTest-results/connected/**/*.xml',recursive=True)];print('tests',sum(int(x.get('tests')) for x in r),'failures',sum(int(x.get('failures',0))+int(x.get('errors',0)) for x in r))"`
Expected: `failures 0`.

If `Migration19To20Test` fails with a schema-not-found message, the assets repoint in Task 9 Step 4 did not take: check that `app/build/intermediates/assets/debugAndroidTest/` contains `io.github.fowles.stochastic_strength.data.AppDatabase/19.json`. If a migration test fails with `NotImplementedError`, a `migrate(db)` call was missed in Task 7 Step 6.

- [ ] **Step 4: Shut down**

```bash
$ANDROID_HOME/platform-tools/adb emu kill; sleep 3; $ANDROID_HOME/platform-tools/adb devices
```

- [ ] **Step 5: Record the gate in the plan and hand off**

Phase 1 is complete when Task 10's push shows both workflows green, Task 11's push shows `android.yml` green, and Step 3 above shows `failures 0`. Do not merge to `main` from this plan; invoke `superpowers:finishing-a-development-branch`.
