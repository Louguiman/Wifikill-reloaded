# WiFiKill Reloaded — Technical Design 02

## Application Architecture & Engineering Blueprint

> Architecture v1.0 — MVP foundation — November 2026
> Product: WiFiKill Reloaded
> Owner: Sankaré Technology
> Platform: Android
> Architecture: Modular Clean Architecture + MVVM
> Primary language: Kotlin
> UI: Jetpack Compose
> Storage: Room / SQLite

The objective of this document is to define how we turn the PRD and database architecture into a maintainable Android application. The key architectural decision is to keep network discovery, router integrations, root operations, and the UI independent of one another.

## 1. Architecture principles

The application will follow these principles:

- **Modular by responsibility:** each module has a clear purpose.
- **Domain independence:** business rules must not depend on Android UI, Room, or router vendors.
- **Local-first:** the application must work without an account, backend, or internet connection.
- **Adapter-based integrations:** router vendors are integrated through common interfaces.
- **Root isolation:** root functionality must never be required by the main application.
- **Security by design:** credentials, permissions, and access-control operations are handled through dedicated components.
- **Testability:** business logic and network discovery must be testable independently.

## 2. High-level architecture

```text
Presentation Layer
Jetpack Compose · Navigation · ViewModels · UI State

Domain Layer
Entities · Repository contracts · Use cases · Business rules

Data Layer
Room · DAOs · Repository implementations · Mappers

Integration Layer
Network discovery · Router adapters · Root bridge

Platform Layer
Android APIs · Connectivity · WorkManager · Keystore
```

The dependency direction is inward: presentation depends on domain, and data/integrations implement domain contracts. Domain must not import Android framework classes.

## 3. Gradle multi-module structure

Use a multi-module project rather than placing everything inside `app`.

This is important because the discovery engine and router integrations will become increasingly complex as support is added for different devices and router firmware.

### Proposed project structure

```text
wifikill-reloaded/
│
├── app/
│   └── src/main/
│       ├── java/com/sankare/wifikill/
│       │   ├── MainActivity.kt
│       │   ├── WiFiKillApplication.kt
│       │   ├── navigation/
│       │   └── di/
│       └── AndroidManifest.xml
│
├── core/
│   ├── common/
│   ├── domain/
│   ├── database/
│   ├── network/
│   ├── security/
│   ├── designsystem/
│   └── testing/
│
├── data/
│   ├── local/
│   └── repository/
│
├── feature/
│   ├── dashboard/
│   ├── discovery/
│   ├── devices/
│   ├── monitoring/
│   ├── accessmanagement/
│   ├── diagnostics/
│   ├── routermanagement/
│   ├── history/
│   └── settings/
│
├── integrations/
│   ├── router-api/
│   ├── openwrt/
│   ├── mikrotik/
│   ├── generic-router/
│   └── root/
│
├── background/
│   └── workers/
│
├── build-logic/
│
├── gradle/
│   └── libs.versions.toml
│
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

### Module responsibilities

| Module                      | Responsibility                                    |
| --------------------------- | ------------------------------------------------- |
| app                         | Application entry point and dependency assembly   |
| core:common                 | Shared primitives, errors, dispatchers, utilities |
| core:domain                 | Business models and use cases                     |
| core:database               | Room database, entities and DAOs                  |
| core:network                | Discovery engine and network abstractions         |
| core:security               | Credential storage and security utilities         |
| core:designsystem           | Reusable Compose components and theme             |
| data:local                  | Local data sources and entity mappers             |
| data:repository             | Repository implementations                        |
| feature:*                   | Individual application features                   |
| integrations:router-api     | Common router adapter contracts                   |
| integrations:openwrt        | OpenWrt integration                               |
| integrations:mikrotik       | MikroTik integration                              |
| integrations:generic-router | Generic read-only router support                  |
| integrations:root           | Restricted root operations                        |
| background:workers          | Background refresh and cleanup jobs               |

> Important: Avoid making every feature module depend directly on every other feature. Shared functionality belongs in `core` or domain-level contracts.

## 4. Dependency rules

Enforce the following rules during development:

- `core:domain` must not depend on `app`, `feature`, `data`, or Android UI.
- `feature:*` modules may depend on `core:domain` and `core:designsystem`.
- `data:repository` implements repository interfaces defined in `core:domain`.
- `integrations:*` implement interfaces defined in their respective API modules.
- `core:database` must not contain business rules.
- `integrations:root` must not be imported directly by UI components.
- Only `app` should assemble the complete dependency graph.

## 5. Presentation layer

### UI navigation

The initial navigation structure:

```text
Dashboard
│
├── Network Discovery
│   ├── Scan Results
│   └── Scan Details
│
├── Devices
│   ├── Device Details
│   └── Device History
│
├── Monitoring
│
├── Access Management
│   ├── Router Policies
│   └── Policy Details
│
├── Router Management
│   ├── Add Router
│   └── Router Details
│
├── Diagnostics
│
├── History
│
└── Settings
```

Recommended bottom navigation:

- Home
- Network
- Monitor
- Settings

Proposed navigation concept — not a final visual design.

### MVVM pattern

Each feature should have:

```text
feature/discovery/
└── src/main/java/
    └── discovery/
        ├── DiscoveryScreen.kt
        ├── DiscoveryViewModel.kt
        ├── DiscoveryUiState.kt
        ├── DiscoveryUiEvent.kt
        └── components/
            ├── ScanButton.kt
            ├── ScanProgress.kt
            └── DeviceList.kt
```

The ViewModel exposes observable state through `StateFlow`.

```kotlin
data class DiscoveryUiState(
    val isScanning: Boolean = false,
    val devices: List<Device> = emptyList(),
    val scanProgress: Float = 0f,
    val error: String? = null
)
```

```kotlin
@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val startNetworkScan: StartNetworkScanUseCase,
    private val observeDevices: ObserveDevicesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState = _uiState.asStateFlow()

    fun startScan() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isScanning = true, error = null)
            }

            try {
                startNetworkScan()
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(error = exception.message)
                }
            } finally {
                _uiState.update {
                    it.copy(isScanning = false)
                }
            }
        }
    }
}
```

For production, scan progress and cancellation should be represented explicitly by the scan use case, rather than relying only on a Boolean flag.

## 6. Network discovery engine

Core engineering component.

This is one of the most important modules in WiFiKill Reloaded.

It should be implemented as a standalone engine that can discover devices independently of Compose, Room, or any router vendor.

### Discovery pipeline

```text
1. Network Context
   SSID, gateway, interface, subnet, connectivity

2. Discovery Sources
   Android network APIs · Authorized router inventory · Local probes

3. Device Normalization
   Normalize observations and merge evidence

4. Device Identity Resolution
   Confidence scoring · Duplicate detection · Historical identity

5. Persist Observations
   Save scan session and device observations in Room
```

### Discovery source abstraction

```kotlin
interface DiscoverySource {

    val sourceType: DiscoverySourceType

    suspend fun discover(
        context: NetworkContext
    ): Flow<DeviceObservation>
}
```

Possible implementations:

```text
NetworkDiscoveryEngine
│
├── AndroidNetworkSource
├── LocalReachabilitySource
├── RouterInventorySource
└── OptionalAdvancedDiagnosticsSource
```

Each source should declare its capabilities and limitations.

For example:

- Android network APIs provide network configuration and connectivity information.
- Local reachability checks can identify responsive devices, but cannot guarantee a complete inventory.
- Authenticated router inventory can provide more reliable connected-client information when supported.
- MAC address and hostname visibility are not guaranteed.

The engine must never interpret a missing response as proof that a device is offline.

### Domain contract

```kotlin
interface NetworkDiscoveryRepository {

    fun observeCurrentNetwork(): Flow<NetworkContext?>

    suspend fun scan(
        networkId: String
    ): Flow<ScanEvent>

    suspend fun getDevices(
        networkId: String
    ): List<Device>
}
```

Use an event model:

```kotlin
sealed interface ScanEvent {

    data object Started : ScanEvent

    data class Progress(
        val completed: Int,
        val total: Int
    ) : ScanEvent

    data class DeviceFound(
        val observation: DeviceObservation
    ) : ScanEvent

    data class Completed(
        val sessionId: String
    ) : ScanEvent

    data class Failed(
        val reason: String
    ) : ScanEvent
}
```

This gives the UI the ability to display progress, new devices, and errors without knowing how discovery works internally.

## 7. Router integration architecture

Router support must be extensible without modifying the core application.

### Common adapter contract

```kotlin
interface RouterAdapter {

    val adapterType: RouterAdapterType

    suspend fun connect(
        config: RouterConnectionConfig
    ): RouterConnectionResult

    suspend fun getRouterInfo(): RouterInfo

    suspend fun discoverDevices(): List<RouterDevice>

    suspend fun getCapabilities(): Set<RouterCapability>

    suspend fun getAccessPolicies(): List<RouterAccessPolicy>

    suspend fun applyAccessPolicy(
        request: AccessPolicyRequest
    ): PolicyOperationResult

    suspend fun removeAccessPolicy(
        policyId: String
    ): PolicyOperationResult

    suspend fun disconnect()
}
```

The contract represents authorized router administration, not arbitrary network interference.

### Adapter implementations

![OpenWrt logo](assets/logo-openwrt-alt.jpg)

**OpenWrt Adapter** — Priority 1
ubus / RPC · Firewall management · Connected-client inventory

![MikroTik](assets/logo-mikrotik-alt.jpg)

**MikroTik Adapter** — Priority 1
RouterOS API / REST · DHCP leases · Firewall policies

<!-- Image unavailable at capture time: logo-tplink-alt -->
![TP-Link](https://images.openai.com/static-rsc-4/BMiRxQ8khhPwaP8wR5f5WOqVdDt7w92H_RYUvA1_ljPUrjoBCt4Zn0e7eLHbvOx9I_vjonjrrmjLr5T7tonF-FTKiDa_D9tQObcjJLme4xc_8x_z7v3pjm0XxHDhlligdQt6ZbapFANe4nZdpF8Z-PvA720IzQifTo7e9csnjrnh5EPeAythFAKVa22DQx-D?purpose=fullsize)

**TP-Link Adapter** — Priority 2
Model- and firmware-specific authenticated integrations

<!-- Image unavailable at capture time: logo-huawei-alt -->
![Huawei](https://images.openai.com/static-rsc-4/uTUnD4iTIHRGRb7RXrl9pvyy2i_8G6DtbY14jg20lBz5btQDrD8MLPPr_z_i30xLyqsrEVtVg1-Y-vg-WhiGnOubEw3bAI9PWRE0q9k6Wrwk4CtWfjMn5doZT-zV3U9N94-4v7FIvP2RHJVFvpEIRs1eeTJQuVH8ftde3Re9OEGhkPx66g_EkIAnPqxuq6Aa?purpose=fullsize)

**Huawei Adapter** — Priority 2
Model-specific management and client inventory

A vendor adapter must not claim universal compatibility. Compatibility should be tracked by model, firmware, and verified capabilities.

### Capability-based support

For instance, one router may support device inventory but not access policies.

```kotlin
enum class RouterCapability {
    DEVICE_DISCOVERY,
    ROUTER_INFORMATION,
    DHCP_CLIENTS,
    ACCESS_POLICY_READ,
    ACCESS_POLICY_WRITE,
    TRAFFIC_STATISTICS,
    WIFI_CONFIGURATION
}
```

The application should render features according to detected capabilities, rather than assuming that all routers support every operation.

### Access policy execution

The correct sequence is:

1. User selects a device.
2. App checks router connection and permissions.
3. App checks whether the router supports the requested policy.
4. App asks for confirmation.
5. Adapter applies the policy through the authenticated router API.
6. Adapter reads back the resulting policy.
7. App marks it successful only after verification.
8. App stores an audit record.

If verification fails, the result should be `UNKNOWN` or `FAILED`, not `SUCCESS`.

## 8. Root integration architecture

Root support is an advanced optional capability. It must not be a dependency of the standard discovery and router-management modules.

```text
Application
    │
    ▼
RootManager
    │
    ▼
RootBridge
    │
    ▼
Allowlisted Operations
```

Suggested interface:

```kotlin
interface RootManager {

    suspend fun isAvailable(): Boolean

    suspend fun getCapabilities(): Set<RootCapability>

    suspend fun execute(
        operation: RootOperation
    ): RootOperationResult
}
```

Use a strict allowlist:

```kotlin
enum class RootCapability {
    ADVANCED_NETWORK_DIAGNOSTICS,
    LOCAL_INTERFACE_INSPECTION
}
```

Security requirements:

- No arbitrary shell-command interface.
- No unrestricted root process exposed to the UI.
- No inbound root service listener.
- Explicit user authorization.
- Strict input validation and operation timeouts.
- Clear disclosure when a feature requires root.
- Root functionality must be isolated and independently testable.

The first release can ship without root support and introduce it after the core product is stable.

## 9. Security and credential management

Router credentials are sensitive and must not be stored in Room as plain text.

Recommended design:

```text
Router Connection
       │
       ▼
CredentialProvider
       │
       ▼
Android Keystore
       │
       ▼
Encrypted Credential Storage
```

Recommended components:

| Component           | Responsibility                       |
| ------------------- | ------------------------------------ |
| CredentialProvider  | Secure credential retrieval          |
| KeystoreManager     | Key generation and management        |
| RouterAuthenticator | Authenticated router sessions        |
| SessionManager      | Session lifecycle                    |
| SecurityAuditLogger | Security-sensitive operation history |

Room stores only a credential reference, such as `credentialId`.

The actual secret is encrypted using a key protected by Android Keystore. Credentials should not be exported in backups or included in logs.

## 10. Background execution

Use different Android mechanisms depending on the task.

| Task                               | Recommended mechanism                   |
| ---------------------------------- | --------------------------------------- |
| Manual network scan                | Coroutine                               |
| User-visible continuous monitoring | Foreground service, when justified      |
| Periodic history cleanup           | WorkManager                             |
| Periodic device refresh            | WorkManager                             |
| Router synchronization             | WorkManager or user-triggered coroutine |
| Database maintenance               | WorkManager                             |

Do not use WorkManager for high-frequency real-time monitoring. It is designed for deferrable work, not precise continuous polling.

Monitoring must also respect Android background execution restrictions, battery optimization, network changes, and user permission revocation.

## 11. Dependency injection

Use Hilt for dependency injection.

Example:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindDiscoveryRepository(
        implementation: NetworkDiscoveryRepositoryImpl
    ): NetworkDiscoveryRepository

    @Binds
    abstract fun bindDeviceRepository(
        implementation: DeviceRepositoryImpl
    ): DeviceRepository
}
```

Router adapters should be registered through a router adapter registry:

```kotlin
interface RouterAdapterRegistry {

    fun getAdapter(
        type: RouterAdapterType
    ): RouterAdapter?
}
```

This makes adding another router implementation possible without rewriting the access-management feature.

## 12. Error handling strategy

Avoid passing raw exceptions to the UI. Use a shared result model:

```kotlin
sealed interface AppResult<out T> {

    data class Success<T>(
        val data: T
    ) : AppResult<T>

    data class Error(
        val code: AppErrorCode,
        val message: String? = null
    ) : AppResult<Nothing>
}
```

Suggested error categories:

- `NETWORK_UNAVAILABLE`
- `PERMISSION_DENIED`
- `SCAN_TIMEOUT`
- `ROUTER_UNREACHABLE`
- `AUTHENTICATION_FAILED`
- `UNSUPPORTED_CAPABILITY`
- `POLICY_VERIFICATION_FAILED`
- `ROOT_NOT_AVAILABLE`
- `DATABASE_ERROR`
- `UNKNOWN_ERROR`

Errors should be actionable and should not expose passwords, tokens, or sensitive network details.

## 13. Testing strategy

Testing should be designed into the architecture, not postponed until release.

| Layer            | Testing approach                                   |
| ---------------- | -------------------------------------------------- |
| Domain           | Unit tests                                         |
| ViewModels       | State and event tests                              |
| Repositories     | Integration tests                                  |
| Room             | DAO and migration tests                            |
| Discovery        | Controlled network fixtures and device simulations |
| Router adapters  | Mock server and actual router tests                |
| Root integration | Rooted test devices                                |
| UI               | Compose UI tests                                   |
| Full app         | End-to-end scenarios                               |

### Critical test scenarios

- Network scan without root.
- Network scan with missing permissions.
- Router unavailable during a scan.
- Device IP changes between scans.
- MAC randomization changes device identity evidence.
- Multiple devices sharing similar hostnames.
- Router credentials are invalid.
- Router supports inventory but not access management.
- Access policy is applied but cannot be verified.
- Android process is killed during background work.
- Room migration from the previous database version.
- Credentials are not exposed through logs or backups.

## 14. Technical dependency baseline

The following are the intended technology choices. Exact library versions should be pinned in the Gradle version catalog after checking the current stable releases.

| Area                 | Technology                       |
| -------------------- | -------------------------------- |
| Language             | Kotlin                           |
| UI                   | Jetpack Compose                  |
| Architecture         | Clean Architecture + MVVM        |
| Dependency injection | Hilt                             |
| Persistence          | Room / SQLite                    |
| Async operations     | Coroutines + Flow                |
| Navigation           | Navigation Compose               |
| Networking           | OkHttp                           |
| Background tasks     | WorkManager                      |
| Security             | Android Keystore                 |
| Testing              | JUnit, MockK, Compose UI testing |
| Build system         | Gradle Kotlin DSL                |
| Code quality         | Detekt / Android Lint            |

No backend is required for MVP. If cloud licensing or multi-device synchronization is introduced later, the backend can be built separately using NestJS, PostgreSQL, and Redis.

## 15. Implementation sequence

Recommended engineering order.

- **E01 — Project foundation** (Must): Set up Gradle, modules, version catalog, Hilt, Compose theme, navigation, and CI.
- **E02 — Domain foundation** (Must): Implement domain models, repository contracts, use cases, error types, and shared result types.
- **E03 — Local persistence** (Must): Implement Room entities, DAOs, migrations, repositories, and database tests.
- **E04 — Network context** (Must): Implement Android connectivity integration, permissions, network context, and network-change observation.
- **E05 — Discovery engine** (Must): Implement discovery sources, scan events, normalization, device identity resolution, and observation persistence.
- **E06 — Device management UI** (Must): Build discovery results, device details, custom naming, trust status, and history.
- **E07 — Router API foundation** (Must): Define adapter contracts, connection lifecycle, capabilities, and credential management.
- **E08 — First router adapters** (Must): Implement and test OpenWrt and MikroTik integrations against supported configurations.
- **E09 — Authorized access management** (Must): Implement policy creation, removal, read-back verification, and audit history.
- **E10 — Productization** (Should): Add diagnostics, monitoring, localization, settings, licensing groundwork, and release testing.

## 16. Architecture decisions register

| ID      | Decision                                                 | Status   |
| ------- | -------------------------------------------------------- | -------- |
| ADR-001 | Native Android using Kotlin                              | Accepted |
| ADR-002 | Jetpack Compose UI                                       | Accepted |
| ADR-003 | Modular Clean Architecture                               | Accepted |
| ADR-004 | MVVM + StateFlow                                         | Accepted |
| ADR-005 | Room / SQLite local database                             | Accepted |
| ADR-006 | Hilt dependency injection                                | Proposed |
| ADR-007 | Adapter-based router integrations                        | Accepted |
| ADR-008 | Root integration isolated and optional                   | Accepted |
| ADR-009 | Local-first architecture                                 | Accepted |
| ADR-010 | No ARP spoofing or deauthentication-based access control | Accepted |
| ADR-011 | OpenWrt and MikroTik as initial router targets           | Accepted |
| ADR-012 | Optional cloud backend after MVP                         | Proposed |

## 17. What comes next?

We now have three foundational documents:

1. **01 — Product Requirements Document** — Product scope, requirements, acceptance criteria, and roadmap. (Drafted)
2. **02 — Database Architecture & ERD** — Entities, relationships, indexes, and persistence strategy. (Drafted)
3. **03 — Application Architecture** — Modules, dependency boundaries, discovery, router integrations, and security. (Drafted)

The recommended next document: **API & Interface Contracts**.

Before implementation, define the exact contracts between the discovery engine, domain layer, Room repositories, router adapters, and UI. This will prevent architectural drift when coding with OpenCode or another AI coding agent.

After that, create the implementation-ready repository skeleton and the first development tasks.
