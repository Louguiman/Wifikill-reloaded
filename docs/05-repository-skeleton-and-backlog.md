# WiFiKill Reloaded — Technical Design 04

## Repository Skeleton & Implementation Backlog

> Design v1.0 — MVP construction — November 2026
> Owner: Sankaré Technology
> Platform: Android
> Related documents: PRD v1.0 · Database Architecture · Application Architecture · API Contracts

## 1. Purpose

This document turns the preceding architecture into an implementation-ready repository blueprint and a sequenced backlog that an AI coding agent can execute without inventing architecture while coding.

It defines:

- The Gradle multi-module layout and dependency graph.
- Version catalog and convention plugins.
- Package structure per module.
- Initial Kotlin contracts and implementation skeletons.
- Room, Hilt, navigation, feature, and worker scaffolding.
- Discovery, router, security, and root foundations.
- Backlog epics, stories, tasks, dependencies, acceptance criteria, and tests.
- Definition of done for the first working build.

Conventions follow `04-api-contracts.md`: UUID string IDs, UTC Unix millisecond timestamps, `AppResult<T>`/`AppErrorCode`, `Flow` for streams, and use cases between UI and repositories.

> Version note: the dependency versions below are a working baseline. Before creating the project, verify current stable releases and pin them in `gradle/libs.versions.toml`. Do not silently upgrade major versions without recording an ADR. The implemented M1 scaffold uses `compileSdk`/`targetSdk` 35 with AGP 8.7.3; raise them to 36 when AGP/SDK versions are bumped.

## 2. Module graph

```text
app
 ├── feature:dashboard
 ├── feature:discovery
 ├── feature:devices
 ├── feature:monitoring
 ├── feature:accessmanagement
 ├── feature:diagnostics
 ├── feature:routermanagement
 ├── feature:history
 ├── feature:settings
 ├── data:repository
 ├── integrations:openwrt
 ├── integrations:mikrotik
 ├── integrations:generic-router
 ├── integrations:root
 ├── background:workers
 └── core:designsystem

feature:*        → core:domain, core:designsystem
data:repository  → core:domain, core:database, core:network, core:common, core:security
core:database    → core:common
core:network     → core:common
core:security    → core:common
integrations:*   → core:domain (router-api contracts), core:common
background:*     → core:domain, data:repository (via interfaces only)
app              → all of the above (assembly only)
```

Rules enforced by Gradle and review:

- `core:domain` depends only on `core:common` (and Kotlin stdlib/coroutines).
- Nothing in `core:domain` imports Android framework, Room, Compose, or vendor SDKs.
- Features never import `data:repository`, `core:database`, or `integrations:*` implementations.
- `integrations:root` is referenced only by `app`/`data` wiring and `background`, never by a `feature`.
- Only `app` assembles the full graph.

## 3. Gradle configuration

### 3.1 `settings.gradle.kts`

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "wifikill-reloaded"

include(
    ":app",
    ":core:common",
    ":core:domain",
    ":core:database",
    ":core:network",
    ":core:security",
    ":core:designsystem",
    ":core:testing",
    ":data:repository",
    ":feature:dashboard",
    ":feature:discovery",
    ":feature:devices",
    ":feature:monitoring",
    ":feature:accessmanagement",
    ":feature:diagnostics",
    ":feature:routermanagement",
    ":feature:history",
    ":feature:settings",
    ":integrations:router-api",
    ":integrations:openwrt",
    ":integrations:mikrotik",
    ":integrations:generic-router",
    ":integrations:root",
    ":background:workers",
)
```

Module paths on disk mirror the graph. The router contracts live in `integrations:router-api`; concrete adapters depend on it.

### 3.2 `gradle/libs.versions.toml` (baseline)

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
hilt = "2.52"
room = "2.6.1"
composeBom = "2024.12.01"
lifecycle = "2.8.7"
navigation = "2.8.5"
coroutines = "1.9.0"
work = "2.10.0"
okhttp = "4.12.0"
serialization = "1.7.3"
junit = "4.13.2"
mockk = "1.13.13"
turbine = "1.2.0"
androidxTest = "1.6.1"
espresso = "3.6.1"
detekt = "1.23.7"
minSdk = "29"
targetSdk = "35"
compileSdk = "35"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version = "1.2.0" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
junit = { module = "junit:junit", version.ref = "junit" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTest" }
espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

### 3.3 `build-logic` convention plugins

Create these under `build-logic/convention/src/main/kotlin/`:

| Plugin | Purpose |
| --- | --- |
| `wifikill.android.library` | Android library, compileSdk/minSdk, Java 17, Compose off by default |
| `wifikill.android.feature` | Android library + Compose + Hilt + navigation + designsystem |
| `wifikill.android.hilt` | Applies KSP + Hilt |
| `wifikill.kotlin.library` | Pure JVM/Kotlin library (domain, common, router-api) |
| `wifikill.android.room` | Room + KSP schema export |

`core:common` and `core:domain` use `wifikill.kotlin.library` (pure JVM), which structurally prevents Android/Room leakage.

### 3.4 Module build file examples

`core/domain/build.gradle.kts`:

```kotlin
plugins {
    id("wifikill.kotlin.library")
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.sankare.wifikill"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.sankare.wifikill"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":data:repository"))
    implementation(project(":integrations:openwrt"))
    implementation(project(":integrations:mikrotik"))
    implementation(project(":integrations:generic-router"))
    implementation(project(":integrations:root"))
    implementation(project(":background:workers"))
    implementation(project(":feature:dashboard"))
    // ...remaining features
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
}
```

## 4. Package structure

```text
core/common/src/main/kotlin/com/sankare/wifikill/core/common/
├── AppResult.kt
├── AppError.kt
├── DispatchersProvider.kt
├── TimeProvider.kt
└── IdGenerator.kt

core/domain/src/main/kotlin/com/sankare/wifikill/core/domain/
├── model/
│   ├── NetworkContext.kt
│   ├── Device.kt
│   ├── DeviceObservation.kt
│   ├── ScanSession.kt
│   ├── AccessPolicy.kt
│   ├── RouterInfo.kt
│   ├── DiagnosticResult.kt
│   └── TrafficSample.kt
├── repository/
│   ├── NetworkDiscoveryRepository.kt
│   ├── DeviceRepository.kt
│   ├── ScanSessionRepository.kt
│   ├── AccessManagementRepository.kt
│   ├── DiagnosticsRepository.kt
│   ├── MonitoringRepository.kt
│   └── CredentialProvider.kt
├── usecase/
│   ├── ObserveCurrentNetworkUseCase.kt
│   ├── StartNetworkScanUseCase.kt
│   ├── CancelNetworkScanUseCase.kt
│   ├── ObserveDevicesUseCase.kt
│   ├── GetDeviceDetailsUseCase.kt
│   ├── RenameDeviceUseCase.kt
│   ├── SetDeviceTrustUseCase.kt
│   ├── GetDeviceHistoryUseCase.kt
│   ├── ApplyAccessPolicyUseCase.kt
│   ├── RemoveAccessPolicyUseCase.kt
│   └── RunDiagnosticsUseCase.kt
└── service/
    ├── AppEventBus.kt
    └── DeviceIdentityResolver.kt

core/database/src/main/kotlin/com/sankare/wifikill/core/database/
├── WiFiKillDatabase.kt
├── converter/
│   └── EnumConverters.kt
├── entity/
│   ├── NetworkEntity.kt
│   ├── RouterEntity.kt
│   ├── DeviceEntity.kt
│   ├── ScanSessionEntity.kt
│   ├── DeviceObservationEntity.kt
│   ├── AccessPolicyEntity.kt
│   ├── TrafficSampleEntity.kt
│   ├── DiagnosticResultEntity.kt
│   ├── RouterCapabilityEntity.kt
│   └── AppSettingsEntity.kt
└── dao/
    ├── NetworkDao.kt
    ├── RouterDao.kt
    ├── DeviceDao.kt
    ├── ScanSessionDao.kt
    ├── DeviceObservationDao.kt
    ├── AccessPolicyDao.kt
    ├── TrafficSampleDao.kt
    ├── DiagnosticResultDao.kt
    ├── RouterCapabilityDao.kt
    └── AppSettingsDao.kt

core/network/src/main/kotlin/com/sankare/wifikill/core/network/
├── NetworkContextProvider.kt
├── PermissionChecker.kt
├── discovery/
│   ├── DiscoveryEngine.kt
│   ├── DiscoverySource.kt
│   ├── LocalReachabilitySource.kt
│   ├── SubnetScanner.kt
│   └── DeviceNormalizer.kt
└── identity/
    └── DefaultDeviceIdentityResolver.kt

core/security/src/main/kotlin/com/sankare/wifikill/core/security/
├── KeystoreManager.kt
├── EncryptedCredentialProvider.kt
├── SessionManager.kt
└── SecurityAuditLogger.kt

integrations/router-api/src/main/kotlin/com/sankare/wifikill/integrations/router/
├── RouterAdapter.kt
├── RouterAdapterRegistry.kt
├── RouterCapability.kt
├── RouterAccessPolicy.kt
└── model/…

integrations/openwrt/…    OpenWrtAdapter.kt, OpenWrtCapabilityProbe.kt
integrations/mikrotik/…   MikroTikAdapter.kt, MikroTikCapabilityProbe.kt
integrations/generic-router/… GenericRouterAdapter.kt
integrations/root/…       RootManager.kt, RootBridge.kt, RootOperation.kt

data/repository/src/main/kotlin/com/sankare/wifikill/data/repository/
├── discovery/NetworkDiscoveryRepositoryImpl.kt
├── device/DeviceRepositoryImpl.kt
├── scan/ScanSessionRepositoryImpl.kt
├── access/AccessManagementRepositoryImpl.kt
├── diagnostics/DiagnosticsRepositoryImpl.kt
├── monitoring/MonitoringRepositoryImpl.kt
└── mapper/…

background/workers/src/main/kotlin/com/sankare/wifikill/background/
├── RetentionWorker.kt
├── DeviceRefreshWorker.kt
└── WorkerModule.kt

app/src/main/kotlin/com/sankare/wifikill/
├── WiFiKillApplication.kt
├── MainActivity.kt
├── navigation/WiFiKillNavHost.kt
├── navigation/Destinations.kt
├── di/AppModule.kt
└── di/RepositoryModule.kt
```

Feature modules follow the pattern in `03-application-architecture.md` (`Screen`, `ViewModel`, `UiState`, `components`).

## 5. Core contracts skeleton

`core/common/src/main/kotlin/.../AppResult.kt`:

```kotlin
package com.sankare.wifikill.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

data class AppError(
    val code: AppErrorCode,
    val message: String? = null,
    val retryable: Boolean = false
)

enum class AppErrorCode {
    NETWORK_UNAVAILABLE,
    PERMISSION_DENIED,
    INVALID_NETWORK_CONTEXT,
    SCAN_TIMEOUT,
    ROUTER_UNREACHABLE,
    AUTHENTICATION_FAILED,
    UNSUPPORTED_CAPABILITY,
    POLICY_VERIFICATION_FAILED,
    OPERATION_CANCELLED,
    STORAGE_ERROR,
    UNKNOWN
}
```

`core/common/src/main/kotlin/.../DispatchersProvider.kt`:

```kotlin
package com.sankare.wifikill.core.common

import kotlinx.coroutines.CoroutineDispatcher

interface DispatchersProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

interface TimeProvider { fun nowMillis(): Long }
interface IdGenerator { fun newId(): String }
```

## 6. Room skeleton

`WiFiKillDatabase.kt`:

```kotlin
@Database(
    entities = [
        NetworkEntity::class,
        RouterEntity::class,
        DeviceEntity::class,
        ScanSessionEntity::class,
        DeviceObservationEntity::class,
        AccessPolicyEntity::class,
        TrafficSampleEntity::class,
        DiagnosticResultEntity::class,
        RouterCapabilityEntity::class,
        AppSettingsEntity::class,
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(EnumConverters::class)
abstract class WiFiKillDatabase : RoomDatabase() {
    abstract fun networkDao(): NetworkDao
    abstract fun routerDao(): RouterDao
    abstract fun deviceDao(): DeviceDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun deviceObservationDao(): DeviceObservationDao
    abstract fun accessPolicyDao(): AccessPolicyDao
    abstract fun trafficSampleDao(): TrafficSampleDao
    abstract fun diagnosticResultDao(): DiagnosticResultDao
    abstract fun routerCapabilityDao(): RouterCapabilityDao
    abstract fun appSettingsDao(): AppSettingsDao
}
```

Database provider module (in `core:database` or `data:repository`):

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WiFiKillDatabase =
        Room.databaseBuilder(context, WiFiKillDatabase::class.java, "wifikill.db")
            .addMigrations(*Migrations.ALL)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides fun provideDeviceDao(db: WiFiKillDatabase) = db.deviceDao()
    // ...remaining DAOs
}
```

Rules from `02-database-architecture.md` apply: foreign keys enabled, no IP/MAC uniqueness, `(routerId, capabilityCode)` unique, transactional scan writes, explicit migrations. Enable FK enforcement with a callback (`PRAGMA foreign_keys = ON`).

## 7. Discovery foundation

```kotlin
interface NetworkContextProvider {
    fun observeCurrentNetwork(): Flow<NetworkContext?>
}

interface PermissionChecker {
    fun hasLocalNetworkAccess(): Boolean
    fun hasWifiScanPermission(): Boolean
}

interface DiscoverySource {
    val sourceType: ObservationSource
    suspend fun isAvailable(context: NetworkContext): Boolean
    fun discover(context: NetworkContext): Flow<DeviceObservation>
}

interface DiscoveryEngine {
    suspend fun discover(
        context: NetworkContext,
        sources: Set<ObservationSource>
    ): Flow<DiscoveryResult>
}
```

`DefaultDiscoveryEngine` runs available sources concurrently, emits `DiscoveryResult` per observation, reports `SOURCE_UNAVAILABLE`/`SOURCE_FAILED` per source, enforces per-source timeout, and never converts one source's failure into an empty success. `SubnetScanner` bounds concurrency (e.g., 32 probes) and respects the configured scan timeout.

`DefaultDeviceIdentityResolver` precedence:

1. Stable MAC match → `HIGH`.
2. Router external identifier match → `HIGH`.
3. Hostname + prior network history → `MEDIUM`.
4. IP-only match → `LOW`, never auto-merge; keep separate unless corroborated.
5. Otherwise `shouldCreateNewDevice = true`, `LOW`.

## 8. Router integration foundation

`integrations/router-api` holds the `RouterAdapter`, `RouterAdapterRegistry`, `RouterCapability`, and model contracts exactly as in `04-api-contracts.md`.

Registry implementation:

```kotlin
class DefaultRouterAdapterRegistry @Inject constructor(
    private val factories: Map<RouterAdapterType, RouterAdapter>
) : RouterAdapterRegistry {
    override fun getSupportedAdapters() = factories.keys.toList()
    override fun getAdapter(type: RouterAdapterType) = factories[type]
}
```

Adapter scaffolding requirements:

- `OpenWrtAdapter`: ubus/RPC session handling, client inventory, firewall-backed policy apply/remove, mandatory read-back verification. Capability probe per model/firmware.
- `MikroTikAdapter`: RouterOS REST (and API fallback), DHCP leases, firewall address-list policy apply/remove, mandatory read-back. Probe capabilities.
- `GenericRouterAdapter`: read-only discovery/diagnostics; advertises only verification-supported capabilities.
- Every adapter defines explicit connect/operation timeouts, cancellation, and never logs secrets.

Adapters are registered in `app`/`di` only; features depend on `RouterAdapterRegistry`.

## 9. Security and root foundation

```kotlin
interface CredentialProvider {
    suspend fun save(credential: RouterCredential): AppResult<String>
    suspend fun get(credentialId: String): AppResult<RouterCredential>
    suspend fun delete(credentialId: String): AppResult<Unit>
}
```

`EncryptedCredentialProvider` uses `KeystoreManager` (Android Keystore AES-GCM key) and stores only ciphertext + `credentialId` reference. Credentials are never written to Room, logs, crash reports, or backups.

```kotlin
enum class RootOperation { INSPECT_LOCAL_INTERFACE, READ_NETWORK_CONFIGURATION }

interface RootManager {
    suspend fun isAvailable(): Boolean
    suspend fun getCapabilities(): Set<RootCapability>
    suspend fun execute(operation: RootOperation): AppResult<RootOperationResult>
}
```

`RootBridge` maps only allowlisted operations to fixed internal commands with input validation and timeouts. No arbitrary shell string is accepted. Root absence returns `AppErrorCode.UNKNOWN`/capability-empty gracefully.

## 10. DI, app shell, and navigation

- `WiFiKillApplication` annotated `@HiltAndroidApp`.
- `MainActivity` annotated `@AndroidEntryPoint`, hosts `WiFiKillTheme` and `WiFiKillNavHost`.
- `Destinations` sealed objects for dashboard, discovery, devices, monitoring, access management, router management, diagnostics, history, settings.
- Hilt modules: `AppModule` (dispatchers, time, id), `DatabaseModule`, `RepositoryModule` (`@Binds`), `NetworkModule`, `RouterModule` (registry + adapters), `SecurityModule`, `RootModule`, `WorkerModule`.
- `RepositoryModule` binds implementation to domain interface; features receive use cases only.

Navigation skeleton:

```text
dashboard
├── discovery
│   ├── discovery/results
│   └── discovery/{sessionId}
├── devices
│   ├── devices/{deviceId}
│   └── devices/{deviceId}/history
├── monitoring
├── access/{routerId}
├── routers
│   ├── routers/add
│   └── routers/{routerId}
├── diagnostics
├── history
└── settings
```

## 11. Feature scaffolding

Each feature module ships with:

- `Screen` composable (stateless where possible) + `Route` wrapper.
- `ViewModel` with `MutableStateFlow<UiState>` + `asStateFlow()`.
- `UiState` and `UiEvent`.
- `components/` subpackage.
- Preview + Compose test tag on primary list.

Initial content per feature is minimal but wired to use cases; no feature reads repositories or databases directly.

## 12. Test scaffolding

`core:testing` provides:

- `MainDispatcherRule` (replaces `Dispatchers.setMain`).
- Fake `TimeProvider` and `IdGenerator`.
- Fake repositories and a fake `NetworkContextProvider`.
- `TestRouterAdapter` implementing `RouterAdapter` for verification-flow tests.
- In-memory Room database factory.

Test layers and minimum coverage:

| Layer | Tooling | Minimum |
| --- | --- | --- |
| Domain use cases | JUnit + MockK | Happy path, validation failure, cancellation |
| ViewModels | JUnit + Turbine | State transitions, error mapping |
| Room | `Room.inMemoryDatabaseBuilder` | CRUD, FK enforcement, index queries, migrations |
| Discovery | Fake sources | Empty vs failed, unavailable source reported, timeout |
| Identity | Table-driven | MAC change, IP change, ambiguous match stays separate |
| Router | MockWebServer/fake | Auth failure, unsupported capability, apply-then-verify |
| Security | Instrumented/Robolectric | Credential round-trip, no plaintext persistence, no secret logging |
| UI | Compose UI test | Scan starts, error shown, device detail renders unknown fields |

## 13. CI gates

- `./gradlew detekt` (or `./gradlew lint`).
- `./gradlew test`.
- `./gradlew :app:assembleDebug`.
- Room schema export committed; migration tests fail on schema drift.
- Dependency check: verify no forbidden module edges (e.g., `core:domain` Android deps) via a Gradle task or `dependencyAnalysis`.

Add these commands to `AGENTS.md` under Build once the project is scaffolded.

## 14. Implementation backlog

Epics map to `03-application-architecture.md` §15. Tasks are sized for an AI agent; each requires the listed tests before being marked done.

### E01 — Project foundation (Must, P0)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T01.1 Create Gradle multi-module skeleton + convention plugins | — | `./gradlew projects` lists all modules; dependency rules compile |
| T01.2 Add version catalog and pin versions | T01.1 | Versions resolve; no dynamic versions |
| T01.3 Add `core:common` (`AppResult`, dispatchers, time, id) | T01.1 | Unit tests for result/error mapping |
| T01.4 Add `core:designsystem` theme + base components | T01.1 | Theme preview renders |
| T01.5 Add Hilt + Application + MainActivity + empty nav host | T01.3 | Debug app builds and launches |
| T01.6 Add CI (detekt, test, assembleDebug) | T01.1 | CI green on skeleton |

### E02 — Domain foundation (Must, P0)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T02.1 Domain models + enums (`NetworkContext`, `Device`, `DeviceObservation`, `ScanEvent`, `AccessPolicy`, …) | T01.3 | Compiles with no Android imports |
| T02.2 Repository interfaces | T02.1 | Interfaces match `04-api-contracts.md` |
| T02.3 Use case skeletons with validation | T02.2 | Unit tests: invalid network id → `INVALID_NETWORK_CONTEXT` |
| T02.4 `AppEventBus` + identity resolver interface | T02.1 | Flow emission test |

### E03 — Local persistence (Must, P0)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T03.1 Room entities + converters (10 entities) | T02.1 | Schema compiles; `exportSchema` on |
| T03.2 DAOs with Flow queries and indexes | T03.1 | DAO tests pass |
| T03.3 `WiFiKillDatabase` + FK callback + WAL | T03.2 | FK violation rejected |
| T03.4 Repository implementations + mappers | T03.2, T02.2 | Round-trip tests |
| T03.5 Scan persistence transaction | T03.4 | Partial scan isolated to its session; prior scan intact |
| T03.6 Migration test harness (v1 baseline) | T03.3 | Migration test template passes |
| T03.7 Retention queries + `RetentionWorker` | T03.4 | Expired rows cleaned; profiles/policies preserved |

### E04 — Network context & permissions (Must, P0)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T04.1 Permission abstraction + manifest declarations | T01.5 | Missing permission surfaces `PERMISSION_DENIED` |
| T04.2 `NetworkContextProvider` (ConnectivityManager) | T04.1 | Emits SSID/gateway/subnet when permitted; null fields otherwise |
| T04.3 Network-change observation | T04.2 | `AppEvent.NetworkChanged` emitted |

### E05 — Discovery engine (Must, P0)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T05.1 `DiscoverySource` + `DiscoveryEngine` contracts | T02.1 | Fake-source tests |
| T05.2 `SubnetScanner` with bounded concurrency + timeout | T05.1 | /24 fixture completes; timeout produces `SOURCE_FAILED`/`SCAN_TIMEOUT` |
| T05.3 `LocalReachabilitySource` | T05.2 | Responding host observed; no-response ≠ offline proof |
| T05.4 `DefaultDeviceIdentityResolver` | T02.4 | IP change preserved; MAC change not auto-split; ambiguous stays separate |
| T05.5 `NetworkDiscoveryRepositoryImpl` wiring | T05.2, T05.4, T03.5 | Scan events single terminal state |
| T05.6 Scan cancellation | T05.5 | Cancel emits cancelled terminal state; resources released |

### E06 — Device management UI (Must, P0)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T06.1 Dashboard screen + ViewModel | T05.5, T01.4 | Renders network/device summary; empty vs failed states distinct |
| T06.2 Discovery screen (progress, list, cancel) | T06.1 | Scan button drives use case; progress shown; cancel works |
| T06.3 Device detail + rename/trust/notes | T05.5 | Edits persist; unknown fields shown as unknown |
| T06.4 Device history | T06.3 | Observations ordered by time; IP change visible |
| T06.5 Navigation integration | T06.2 | All destinations reachable |

### E07 — Diagnostics (Must, P0)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T07.1 Diagnostic contracts + repository | T02.2 | Model tests |
| T07.2 Gateway reachability + latency | T07.1, T04.2 | Gateway success ≠ internet success (separate results) |
| T07.3 DNS resolution + internet connectivity | T07.2 | Distinct statuses; failures actionable |
| T07.4 Diagnostics UI + history | T07.3 | Results displayed; history persists |

### E08 — Router API foundation (Must, P0)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T08.1 `integrations:router-api` contracts + models | T02.1 | Compiles; no vendor deps |
| T08.2 `RouterAdapterRegistry` + DI wiring | T08.1 | Registry returns adapters; unknown returns null |
| T08.3 Credential storage (Keystore) | T01.5 | Round-trip; no plaintext in DB/logs |
| T08.4 Router connection lifecycle + timeout/cancel | T08.2, T08.3 | Auth failure → `AUTHENTICATION_FAILED`; timeout handled |
| T08.5 Router management UI (add/configure/status) | T08.4 | Invalid credentials shown clearly |

### E09 — OpenWrt adapter (Must, P0)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T09.1 ubus/RPC client + session auth | T08.4 | Connects to fixture; validates session |
| T09.2 Client inventory mapping | T09.1 | `RouterDevice` list; missing fields null |
| T09.3 Capability probe | T09.2 | Capabilities detected, not assumed |
| T09.4 Access policy apply/remove + verification | T09.3, T10.1 | `ACTIVE` only after read-back; failure → `UNKNOWN`/`FAILED` |
| T09.5 Adapter tests (mock server + hardware) | T09.4 | Documented model/firmware matrix |

### E10 — MikroTik adapter (Must, P0)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T10.1 RouterOS REST client + auth | T08.4 | Connects to fixture; session validated |
| T10.2 DHCP lease inventory | T10.1 | Active/inactive flags where available |
| T10.3 Firewall address-list policy apply/remove | T10.2 | Idempotent; no duplicate rules |
| T10.4 Read-back verification | T10.3 | Verified result only on matching state |
| T10.5 Adapter tests (mock server + hardware) | T10.4 | Model/firmware matrix documented |

### E11 — Access management (Must, P1)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T11.1 Policy workflow (validate → pending → apply → verify → active/failed) | T09.4, T10.4, T03.4 | State machine tests; no success without verification |
| T11.2 Idempotency and reconnect-query behavior | T11.1 | Duplicate apply prevented after reconnect |
| T11.3 Access management UI + confirmation + status | T11.1 | Device/action shown before confirm; failures reported |
| T11.4 Policy audit records | T11.1, T08.3 | Security audit entries without secrets |

### E12 — Root engine (Should, P1)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T12.1 Root availability detection | T01.5 | Works without root; reports unavailable gracefully |
| T12.2 Allowlisted `RootOperation` bridge | T12.1 | Only fixed operations accepted; invalid input rejected |
| T12.3 Advanced diagnostics UI (opt-in) | T12.2 | Explicit authorization; denied path clean |

### E13 — Monitoring (Should, P1)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T13.1 Traffic sample contracts + source labeling | T02.2 | `ANDROID_LOCAL` not shown as per-client |
| T13.2 Monitoring repository + refresh | T13.1 | Missing data ≠ zero |
| T13.3 Monitoring UI + pause | T13.2 | Stops cleanly; no infinite background polling |

### E14 — Productization & release (Must, P0)

| Task | Depends on | Acceptance |
| --- | --- | --- |
| T14.1 Onboarding + permission rationale | E04 | Grant/deny paths handled |
| T14.2 Settings (intervals, retention, root toggle, data deletion) | E03, E12 | Values persist; delete works |
| T14.3 Error reporting without secrets | E01 | Sanitized; no credential leakage |
| T14.4 Localization scaffolding (en/fr) | E06 | Strings externalized |
| T14.5 Security review (credentials, backups, logs) | E08 | Checklist signed off |
| T14.6 Release checklist + hardware test matrix | all | Documented PASS/FAIL matrix |

## 15. Milestones

| Milestone | Includes | Exit criteria |
| --- | --- | --- |
| M1 — Walking skeleton | E01, E02 | App launches; empty nav; CI green |
| M2 — Local discovery | E03, E04, E05, E06 | Scan a real /24; devices persist; identity stable across IP change |
| M3 — Router foundation | E08 | Connect to a router; credentials secure; capabilities reported |
| M4 — Verified access | E09, E10, E11 | Apply/remove policy; `ACTIVE` only after read-back |
| M5 — Diagnostics & monitoring | E07, E13 | Gateway/DNS/internet distinguished; traffic sources labeled |
| M6 — Release candidate | E12, E14 | Root optional; security review; hardware matrix pass |

## 16. Definition of done (per task)

A task is done only when:

- Code compiles and passes detekt/lint.
- Unit tests cover success, failure, and cancellation where applicable.
- No forbidden dependency edges were introduced.
- No secret appears in code, logs, fixtures, or database schemas.
- Missing data is represented as `unknown`/`null`, never fabricated.
- Policy operations cannot report success without verification.
- Public contracts match `04-api-contracts.md`; deviations recorded as an ADR.

## 17. Risks specific to construction

| Risk | Mitigation |
| --- | --- |
| Router firmware variance breaks adapters | Capability probes, per-model matrix, mock-server tests |
| Android permission changes across API levels | Permission abstraction, instrumented tests on API 29/33/36 |
| Room schema drift | Exported schema + migration tests in CI |
| Root availability differs (Magisk/KernelSU) | Feature-flag availability; never required for core |
| Discovery false negatives from client isolation | Explicitly report source limitations; never claim absence proof |
| AI-generated scope creep | Enforce backlog IDs and acceptance criteria; reject undocumented contracts |

## 18. Immediate first commit sequence

1. T01.1–T01.6: skeleton, catalog, common, design system, Hilt shell, CI.
2. T02.1–T02.2: domain models and repository interfaces.
3. T03.1–T03.4: Room entities, DAOs, database, repository implementations.
4. Tag `m1-walking-skeleton`; run CI; update `AGENTS.md` build commands.
