# WiFiKill Reloaded — Technical Design 03

## API & Interface Contracts

> Contract specification v1.0 — MVP — November 2026
> Owner: Sankaré Technology
> Platform: Android / Kotlin
> Architecture: Modular Clean Architecture
> Related documents: PRD v1.0 · Database Architecture · Application Architecture

## 1. Purpose

This document defines the contracts between the application's main components before implementation begins.

The goal is to ensure that:

- The UI doesn't know how network discovery works.
- The discovery engine doesn't depend on Room or Compose.
- Router integrations can be added without changing business logic.
- Root operations remain isolated.
- Every operation has predictable inputs, outputs, and errors.
- Tests can mock individual components without requiring a real router or physical network.

Six contract groups are defined:

- Core domain models.
- Network discovery.
- Device management and persistence.
- Router integrations.
- Access policy management.
- Diagnostics, monitoring, and application events.

## 2. Contract conventions

### 2.1 Naming

| Element         | Convention            | Example                    |
| --------------- | --------------------- | -------------------------- |
| Interface       | PascalCase            | NetworkDiscoveryRepository |
| Data class      | PascalCase            | NetworkContext             |
| Enum            | PascalCase            | ScanStatus                 |
| Function        | camelCase             | startScan()                |
| Property        | camelCase             | networkId                  |
| Use case        | Verb + noun + UseCase | StartNetworkScanUseCase    |
| Database entity | Entity suffix         | DeviceEntity               |
| DTO             | DTO suffix            | RouterDeviceDTO            |

### 2.2 General rules

- All asynchronous operations use Kotlin coroutines.
- Streaming operations use `Flow<T>`.
- All IDs use UUID strings.
- All timestamps use UTC Unix milliseconds.
- Nullable fields mean information is genuinely optional or unavailable.
- Domain models must not expose Room entities or Android framework types.
- Every external operation must have a defined timeout and cancellation behavior.
- Every security-sensitive operation must be auditable.

### 2.3 Standard result type

```kotlin
sealed interface AppResult<out T> {

    data class Success<T>(
        val data: T
    ) : AppResult<T>

    data class Failure(
        val error: AppError
    ) : AppResult<Nothing>
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

Expected operational failures should be returned as structured failures. Cancellation should propagate normally rather than being converted into a generic error.

## 3. Core domain contracts

These are the shared models used throughout the application. They are independent of the database representation.

### 3.1 Network models

```kotlin
data class NetworkContext(
    val networkId: String?,
    val ssid: String?,
    val bssid: String?,
    val gatewayIp: String?,
    val localIp: String?,
    val subnet: String?,
    val interfaceName: String?,
    val networkType: NetworkType,
    val isMetered: Boolean,
    val isValidated: Boolean
)

enum class NetworkType {
    WIFI,
    ETHERNET,
    CELLULAR,
    OTHER,
    UNKNOWN
}
```

The context describes the network currently visible to Android. It does not imply that all connected clients are discoverable.

### 3.2 Device models

```kotlin
data class Device(
    val id: String,
    val customName: String?,
    val hostname: String?,
    val manufacturer: String?,
    val deviceType: DeviceType,
    val identityConfidence: IdentityConfidence,
    val isTrusted: Boolean,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

enum class DeviceType {
    PHONE,
    TABLET,
    COMPUTER,
    ROUTER,
    SMART_TV,
    IOT,
    PRINTER,
    UNKNOWN
}

enum class IdentityConfidence {
    LOW,
    MEDIUM,
    HIGH
}
```

An IP address or MAC address is not the permanent identity of a device. The application maintains a logical device identity and associates observations with it over time.

### 3.3 Device observation

```kotlin
data class DeviceObservation(
    val id: String,
    val deviceId: String?,
    val networkId: String,
    val scanSessionId: String?,
    val ipAddress: String?,
    val macAddress: String?,
    val hostname: String?,
    val manufacturer: String?,
    val source: ObservationSource,
    val reachability: ReachabilityStatus,
    val responseTimeMs: Long?,
    val observedAt: Long
)

enum class ObservationSource {
    ANDROID_NETWORK,
    LOCAL_DISCOVERY,
    ROUTER_INVENTORY,
    MANUAL
}

enum class ReachabilityStatus {
    RESPONDING,
    NOT_RESPONDING,
    UNKNOWN
}
```

The observation may initially have no `deviceId`. Identity resolution is a separate step, and uncertain matches should not be merged automatically.

## 4. Network discovery contracts

### 4.1 Repository interface

The domain layer owns the discovery repository contract.

```kotlin
interface NetworkDiscoveryRepository {

    fun observeCurrentNetwork(): Flow<NetworkContext?>

    fun observeScanEvents(
        sessionId: String
    ): Flow<ScanEvent>

    suspend fun startScan(
        request: ScanRequest
    ): AppResult<String>

    suspend fun cancelScan(
        sessionId: String
    ): AppResult<Unit>

    suspend fun getScan(
        sessionId: String
    ): ScanSession?

    fun observeDevices(
        networkId: String
    ): Flow<List<Device>>
}
```

### 4.2 Scan request

```kotlin
data class ScanRequest(
    val networkId: String,
    val sources: Set<ObservationSource>,
    val timeoutMs: Long = 30_000,
    val includeRouterInventory: Boolean = true
)
```

The requested sources are preferences, not guarantees. The engine must report which sources actually ran and which were unavailable.

### 4.3 Scan events

```kotlin
sealed interface ScanEvent {

    data class Started(
        val sessionId: String,
        val startedAt: Long
    ) : ScanEvent

    data class SourceStarted(
        val source: ObservationSource
    ) : ScanEvent

    data class SourceCompleted(
        val source: ObservationSource,
        val devicesFound: Int
    ) : ScanEvent

    data class DeviceDiscovered(
        val observation: DeviceObservation
    ) : ScanEvent

    data class Progress(
        val completedSources: Int,
        val totalSources: Int
    ) : ScanEvent

    data class Completed(
        val sessionId: String,
        val devicesFound: Int,
        val completedAt: Long
    ) : ScanEvent

    data class Failed(
        val sessionId: String,
        val error: AppError
    ) : ScanEvent
}
```

A scan should have a single terminal state: completed, failed, or cancelled. Progress events must not be emitted after a terminal state.

### 4.4 Discovery engine contract

```kotlin
interface DiscoveryEngine {

    suspend fun discover(
        context: NetworkContext,
        sources: Set<ObservationSource>
    ): Flow<DiscoveryResult>
}

data class DiscoveryResult(
    val source: ObservationSource,
    val observation: DeviceObservation?,
    val status: DiscoverySourceStatus,
    val error: AppError? = null
)

enum class DiscoverySourceStatus {
    DEVICE_FOUND,
    NO_DEVICE_FOUND,
    SOURCE_UNAVAILABLE,
    SOURCE_FAILED
}
```

### 4.5 Discovery source contract

```kotlin
interface DiscoverySource {

    val sourceType: ObservationSource

    suspend fun isAvailable(
        context: NetworkContext
    ): Boolean

    fun discover(
        context: NetworkContext
    ): Flow<DeviceObservation>
}
```

Each implementation must have its own timeout and cancellation support. It must also respect Android permissions and local-network restrictions.

### 4.6 Identity resolution

```kotlin
interface DeviceIdentityResolver {

    suspend fun resolve(
        observation: DeviceObservation,
        existingDevices: List<Device>
    ): IdentityResolution
}

data class IdentityResolution(
    val deviceId: String?,
    val confidence: IdentityConfidence,
    val shouldCreateNewDevice: Boolean,
    val reasons: List<String>
)
```

Rules:

- A matching stable identifier can provide strong evidence.
- IP-only matches should not automatically establish identity.
- MAC changes must not automatically create a new physical device.
- Ambiguous matches should remain separate until more evidence exists.
- The resolver must retain the evidence used for its decision.

## 5. Device management contracts

### 5.1 Device repository

```kotlin
interface DeviceRepository {

    fun observeDevices(
        networkId: String
    ): Flow<List<Device>>

    fun observeDevice(
        deviceId: String
    ): Flow<Device?>

    suspend fun getDevice(
        deviceId: String
    ): Device?

    suspend fun updateCustomName(
        deviceId: String,
        name: String?
    ): AppResult<Unit>

    suspend fun updateTrustedStatus(
        deviceId: String,
        isTrusted: Boolean
    ): AppResult<Unit>

    suspend fun updateNotes(
        deviceId: String,
        notes: String?
    ): AppResult<Unit>

    fun observeDeviceHistory(
        deviceId: String
    ): Flow<List<DeviceObservation>>
}
```

### 5.2 Scan session repository

```kotlin
interface ScanSessionRepository {

    suspend fun createSession(
        networkId: String,
        startedAt: Long
    ): String

    suspend fun completeSession(
        sessionId: String,
        devicesFound: Int,
        completedAt: Long
    )

    suspend fun failSession(
        sessionId: String,
        error: AppError
    )

    fun observeHistory(
        networkId: String
    ): Flow<List<ScanSession>>
}
```

### 5.3 Persistence responsibilities

The implementation layer handles:

- Mapping domain models to Room entities.
- Transactional persistence of scan observations.
- Updating existing logical device records.
- Maintaining historical observations.
- Applying retention policies.
- Database migrations.

A scan must not partially overwrite a previous successful scan if it fails halfway through. Its partial observations should be associated with its own session and clearly marked.

## 6. Router integration contracts

Critical integration boundary.

Router integrations need a common interface but must retain their vendor-specific implementation details internally.

### 6.1 Router connection configuration

```kotlin
data class RouterConnectionConfig(
    val routerId: String,
    val host: String,
    val port: Int,
    val protocol: RouterProtocol,
    val credentialId: String
)

enum class RouterProtocol {
    HTTP,
    HTTPS,
    ROUTER_API,
    UNKNOWN
}
```

`credentialId` references a secure credential record. It is never the password itself.

### 6.2 Router information

```kotlin
data class RouterInfo(
    val routerId: String,
    val manufacturer: String?,
    val model: String?,
    val firmwareVersion: String?,
    val hostname: String?,
    val managementAddress: String
)
```

### 6.3 Router device

```kotlin
data class RouterDevice(
    val externalId: String?,
    val ipAddress: String?,
    val macAddress: String?,
    val hostname: String?,
    val interfaceName: String?,
    val connectionType: String?,
    val isActive: Boolean?,
    val leaseExpiresAt: Long?
)
```

The router's external identifier is not automatically the same as the application's device ID.

### 6.4 Router adapter

```kotlin
interface RouterAdapter {

    val adapterType: RouterAdapterType

    suspend fun connect(
        config: RouterConnectionConfig
    ): AppResult<RouterInfo>

    suspend fun getRouterInfo(): AppResult<RouterInfo>

    suspend fun discoverDevices(): AppResult<List<RouterDevice>>

    suspend fun getCapabilities(): Set<RouterCapability>

    suspend fun getAccessPolicies(): AppResult<List<RouterAccessPolicy>>

    suspend fun applyAccessPolicy(
        request: AccessPolicyRequest
    ): AppResult<PolicyOperationReceipt>

    suspend fun removeAccessPolicy(
        policyId: String
    ): AppResult<PolicyOperationReceipt>

    suspend fun verifyAccessPolicy(
        request: PolicyVerificationRequest
    ): AppResult<PolicyVerificationResult>

    suspend fun disconnect()
}
```

The adapter must be explicitly connected before authenticated operations. It must not silently reuse expired credentials or sessions.

### 6.5 Adapter registry

```kotlin
interface RouterAdapterRegistry {

    fun getSupportedAdapters(): List<RouterAdapterType>

    fun getAdapter(
        type: RouterAdapterType
    ): RouterAdapter?
}

enum class RouterAdapterType {
    OPENWRT,
    MIKROTIK,
    TP_LINK,
    HUAWEI,
    GENERIC
}
```

### 6.6 Router capability contract

```kotlin
enum class RouterCapability {
    ROUTER_INFORMATION,
    DEVICE_DISCOVERY,
    DHCP_CLIENTS,
    WIFI_CLIENTS,
    ACCESS_POLICY_READ,
    ACCESS_POLICY_WRITE,
    TRAFFIC_STATISTICS,
    WIFI_CONFIGURATION
}
```

A capability must be detected and verified against the actual router configuration. A vendor's advertised feature list is not sufficient evidence that a given model and firmware support it.

### 6.7 Router adapter behavior

| Operation        | Required behavior                         |
| ---------------- | ----------------------------------------- |
| Connect          | Authenticate and validate the session     |
| Get router info  | Return model and firmware where available |
| Discover devices | Return available client inventory         |
| Get capabilities | Report verified supported operations      |
| Apply policy     | Apply an authorized router configuration  |
| Verify policy    | Read back and compare actual state        |
| Remove policy    | Remove the identified policy              |
| Disconnect       | Release session resources                 |

For access-policy operations, the adapter must distinguish between a successful API response and a successfully verified resulting configuration.

## 7. Access management contracts

### 7.1 Access policy model

```kotlin
data class AccessPolicy(
    val id: String,
    val routerId: String,
    val deviceId: String,
    val action: AccessPolicyAction,
    val status: AccessPolicyStatus,
    val routerPolicyReference: String?,
    val reason: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long?
)

enum class AccessPolicyAction {
    BLOCK_INTERNET,
    ALLOW_INTERNET
}

enum class AccessPolicyStatus {
    PENDING,
    APPLYING,
    ACTIVE,
    REMOVING,
    REMOVED,
    FAILED,
    UNKNOWN
}
```

These actions represent router-supported access policies, not Wi-Fi radio disconnection or attacks against client devices.

### 7.2 Policy request

```kotlin
data class AccessPolicyRequest(
    val routerId: String,
    val deviceId: String,
    val action: AccessPolicyAction,
    val reason: String?,
    val expiresAt: Long?
)
```

### 7.3 Policy operation result

```kotlin
data class PolicyOperationReceipt(
    val operationId: String,
    val routerPolicyReference: String?,
    val submittedAt: Long
)

data class PolicyVerificationRequest(
    val operationId: String,
    val routerPolicyReference: String?,
    val expectedAction: AccessPolicyAction,
    val deviceId: String
)

data class PolicyVerificationResult(
    val isVerified: Boolean,
    val actualAction: AccessPolicyAction?,
    val checkedAt: Long,
    val details: String?
)
```

### 7.4 Access management repository

```kotlin
interface AccessManagementRepository {

    fun observePolicies(
        routerId: String
    ): Flow<List<AccessPolicy>>

    suspend fun applyPolicy(
        request: AccessPolicyRequest
    ): AppResult<AccessPolicy>

    suspend fun removePolicy(
        policyId: String
    ): AppResult<Unit>

    suspend fun verifyPolicy(
        policyId: String
    ): AppResult<PolicyVerificationResult>
}
```

### 7.5 Required execution workflow

```text
User requests an access policy
        │
        ▼
Validate router, device, capability and authorization
        │
        ▼
Persist operation as PENDING
        │
        ▼
Execute authenticated router operation
        │
        ▼
Read back router configuration
        │
        ├── Verified   → Set ACTIVE
        │
        └── Not verified → Set UNKNOWN or FAILED
```

The workflow must be idempotent where possible. If the app loses connectivity after submitting a policy, it must query the router before retrying, to avoid duplicate rules.

## 8. Diagnostics contracts

Diagnostics are independent operations that can run without router administration.

### 8.1 Diagnostic models

```kotlin
data class DiagnosticRequest(
    val networkId: String,
    val type: DiagnosticType,
    val target: String?
)

enum class DiagnosticType {
    GATEWAY_REACHABILITY,
    DNS_RESOLUTION,
    INTERNET_CONNECTIVITY,
    LATENCY,
    PACKET_LOSS,
    NETWORK_CONFIGURATION
}

enum class DiagnosticStatus {
    RUNNING,
    SUCCESS,
    WARNING,
    FAILED,
    UNAVAILABLE
}
```

### 8.2 Diagnostic result

```kotlin
data class DiagnosticResult(
    val id: String,
    val networkId: String,
    val type: DiagnosticType,
    val target: String?,
    val status: DiagnosticStatus,
    val latencyMs: Long?,
    val resultMessage: String?,
    val executedAt: Long
)
```

### 8.3 Repository contract

```kotlin
interface DiagnosticsRepository {

    fun execute(
        request: DiagnosticRequest
    ): Flow<DiagnosticExecutionEvent>

    fun observeHistory(
        networkId: String
    ): Flow<List<DiagnosticResult>>
}
```

```kotlin
sealed interface DiagnosticExecutionEvent {

    data object Started : DiagnosticExecutionEvent

    data class Progress(
        val message: String
    ) : DiagnosticExecutionEvent

    data class Completed(
        val result: DiagnosticResult
    ) : DiagnosticExecutionEvent

    data class Failed(
        val error: AppError
    ) : DiagnosticExecutionEvent
}
```

The diagnostics module must distinguish between gateway connectivity, DNS resolution, and internet access. A successful gateway ping does not prove internet connectivity.

## 9. Monitoring contracts

Monitoring should use a source-aware measurement model.

Per-device bandwidth figures must never be invented based only on the fact that a device was discovered on the LAN.

### 9.1 Traffic sample

```kotlin
data class TrafficSample(
    val id: String,
    val deviceId: String?,
    val networkId: String,
    val source: TrafficSource,
    val bytesReceived: Long?,
    val bytesSent: Long?,
    val sampledAt: Long
)

enum class TrafficSource {
    ROUTER_TELEMETRY,
    ANDROID_LOCAL,
    UNKNOWN
}
```

`ANDROID_LOCAL` measurements must be labeled as local-device measurements, not measurements of another LAN client.

### 9.2 Monitoring repository

```kotlin
interface MonitoringRepository {

    fun observeTraffic(
        networkId: String
    ): Flow<List<TrafficSample>>

    suspend fun refreshTraffic(
        networkId: String
    ): AppResult<Unit>

    suspend fun getLatestSample(
        deviceId: String
    ): TrafficSample?
}
```

### 9.3 Monitoring rules

- Every metric must have a source.
- Every sample must have a timestamp.
- Unsupported metrics must be shown as unavailable.
- Missing measurements must not be interpreted as zero.
- Traffic history must be subject to retention limits.
- Monitoring must stop cleanly when the user disables it.

## 10. Security contracts

### 10.1 Credential provider

```kotlin
interface CredentialProvider {

    suspend fun save(
        credential: RouterCredential
    ): AppResult<String>

    suspend fun get(
        credentialId: String
    ): AppResult<RouterCredential>

    suspend fun delete(
        credentialId: String
    ): AppResult<Unit>
}

data class RouterCredential(
    val username: String,
    val password: String
)
```

The `RouterCredential` object must remain in memory only for the duration necessary to authenticate. Do not persist it in Room, serialize it into logs, or expose it through UI state.

### 10.2 Security audit

```kotlin
data class SecurityAuditEvent(
    val id: String,
    val eventType: SecurityEventType,
    val resourceId: String?,
    val outcome: OperationOutcome,
    val timestamp: Long,
    val details: String?
)

enum class SecurityEventType {
    ROUTER_AUTHENTICATION,
    CREDENTIAL_UPDATE,
    ACCESS_POLICY_APPLIED,
    ACCESS_POLICY_REMOVED,
    ROOT_PERMISSION_REQUESTED,
    SENSITIVE_SETTINGS_CHANGED
}

enum class OperationOutcome {
    SUCCESS,
    FAILURE,
    UNKNOWN
}
```

```kotlin
interface SecurityAuditRepository {

    suspend fun record(
        event: SecurityAuditEvent
    )

    fun observeEvents(): Flow<List<SecurityAuditEvent>>
}
```

Audit records must not include passwords, access tokens, private keys, or other secrets.

### 10.3 Root capability contract

```kotlin
interface RootManager {

    suspend fun isAvailable(): Boolean

    suspend fun getCapabilities(): Set<RootCapability>

    suspend fun execute(
        operation: RootOperation
    ): AppResult<RootOperationResult>
}

enum class RootCapability {
    ADVANCED_NETWORK_DIAGNOSTICS,
    LOCAL_INTERFACE_INSPECTION
}

enum class RootOperation {
    INSPECT_LOCAL_INTERFACE,
    READ_NETWORK_CONFIGURATION
}

data class RootOperationResult(
    val operation: RootOperation,
    val output: String?,
    val executedAt: Long
)
```

Only explicitly defined operations are allowed. No general-purpose command execution should be exposed.

## 11. Use-case contracts

Use cases are the entry points for business operations. ViewModels should call use cases rather than directly accessing repositories.

| Use case                     | Input               | Output                         |
| ---------------------------- | ------------------- | ------------------------------ |
| ObserveCurrentNetworkUseCase | None                | Flow<NetworkContext?>          |
| StartNetworkScanUseCase      | ScanRequest         | AppResult<String>              |
| CancelNetworkScanUseCase     | Session ID          | AppResult<Unit>                |
| ObserveDevicesUseCase        | Network ID          | Flow<List<Device>>             |
| GetDeviceDetailsUseCase      | Device ID           | Flow<Device?>                  |
| RenameDeviceUseCase          | Device ID + name    | AppResult<Unit>                |
| SetDeviceTrustUseCase        | Device ID + Boolean | AppResult<Unit>                |
| GetDeviceHistoryUseCase      | Device ID           | Flow<List<DeviceObservation>>  |
| ConnectRouterUseCase         | Connection config   | AppResult<RouterInfo>          |
| GetRouterCapabilitiesUseCase | Router ID           | Set<RouterCapability>          |
| ApplyAccessPolicyUseCase     | Policy request      | AppResult<AccessPolicy>        |
| RemoveAccessPolicyUseCase    | Policy ID           | AppResult<Unit>                |
| RunDiagnosticsUseCase        | Diagnostic request  | Flow<DiagnosticExecutionEvent> |
| RefreshTrafficUseCase        | Network ID          | AppResult<Unit>                |

Example implementation:

```kotlin
class StartNetworkScanUseCase @Inject constructor(
    private val repository: NetworkDiscoveryRepository
) {
    suspend operator fun invoke(
        request: ScanRequest
    ): AppResult<String> {
        if (request.networkId.isBlank()) {
            return AppResult.Failure(
                AppError(
                    code = AppErrorCode.INVALID_NETWORK_CONTEXT
                )
            )
        }

        return repository.startScan(request)
    }
}
```

## 12. Application event contracts

Some events are relevant across multiple features. For example, when Android changes Wi-Fi networks, the dashboard, discovery engine, and monitoring module may all need to react.

Create a shared application event stream.

```kotlin
sealed interface AppEvent {

    data class NetworkChanged(
        val networkId: String?
    ) : AppEvent

    data class RouterConnectionChanged(
        val routerId: String,
        val connected: Boolean
    ) : AppEvent

    data class DeviceDiscovered(
        val deviceId: String
    ) : AppEvent

    data class ScanCompleted(
        val sessionId: String
    ) : AppEvent

    data class AccessPolicyChanged(
        val policyId: String,
        val status: AccessPolicyStatus
    ) : AppEvent
}
```

```kotlin
interface AppEventBus {

    val events: SharedFlow<AppEvent>

    suspend fun emit(event: AppEvent)
}
```

Use the event bus for cross-feature notifications, not as a replacement for repositories. Persistent application state must always be available through repository queries.

## 13. API contract map

The complete dependency map to preserve:

```text
Feature ViewModels
Dashboard · Discovery · Devices · Monitoring · Access Management
        │
        ▼
Domain Use Cases
StartScan · ObserveDevices · ApplyPolicy · RunDiagnostics
        │
        ▼
Domain repositories
NetworkDiscovery · Device · Router · AccessManagement · Diagnostics
        │
        ▼
Domain services
IdentityResolver · AppEventBus · CredentialProvider
        │
        ▼
Data implementations
Room · DAOs · Mappers · Repository implementations
        │
        ▼
Integration implementations
Discovery sources · OpenWrt · MikroTik · Root · Keystore
```

The most important rule is that integrations implement contracts; they do not dictate the application's business logic.

## 14. Module-to-contract ownership

| Contract                      | Owning module                |
| ----------------------------- | ---------------------------- |
| Domain models                 | core:domain                  |
| Repository interfaces         | core:domain                  |
| Use cases                     | core:domain                  |
| Discovery interfaces          | core:network                 |
| Discovery implementations     | core:network                 |
| Room entities and DAOs        | core:database                |
| Repository implementations    | data:repository              |
| Router adapter interface      | integrations:router-api      |
| OpenWrt adapter               | integrations:openwrt         |
| MikroTik adapter              | integrations:mikrotik        |
| Root manager                  | integrations:root            |
| Credential provider interface | core:domain                  |
| Keystore implementation       | core:security                |
| UI state and events           | Individual feature:* modules |
| Background jobs               | background:workers           |

One architectural refinement: `core:network` should own the discovery-engine abstraction, while the domain layer owns the use-case-facing repository interface. The data/integration assembly connects the two.

## 15. Contract acceptance criteria

The implementation is considered contract-compliant when the following conditions are met.

| ID      | Acceptance criterion                                                     |
| ------- | ------------------------------------------------------------------------ |
| API-001 | Features access business operations through use cases.                   |
| API-002 | Domain models contain no Room or Compose dependencies.                   |
| API-003 | Discovery sources can be independently enabled or disabled.              |
| API-004 | Discovery reports unavailable sources instead of silently ignoring them. |
| API-005 | Device identity resolution preserves uncertain matches.                  |
| API-006 | Router adapters implement the same common interface.                     |
| API-007 | Router capabilities are detected rather than assumed.                    |
| API-008 | Access-policy operations require verification.                           |
| API-009 | Router credentials are not stored in plaintext in Room.                  |
| API-010 | Traffic measurements identify their source.                              |
| API-011 | Root functionality is optional and restricted to allowlisted operations. |
| API-012 | Scan and diagnostic operations support cancellation.                     |
| API-013 | Application events do not replace persistent repository state.           |
| API-014 | Errors are structured and safe to display.                               |
| API-015 | Every security-sensitive operation produces an audit record.             |

## 16. Final architecture decisions

Recommended baseline:

| Decision                | Selected approach                        |
| ----------------------- | ---------------------------------------- |
| Communication           | Kotlin interfaces                        |
| Asynchronous operations | Coroutines                               |
| Streaming data          | Flow / StateFlow                         |
| Operation results       | AppResult<T>                             |
| Discovery               | Independent source-based engine          |
| Device identity         | Evidence-based resolution                |
| Router support          | Adapter pattern                          |
| Access management       | Authenticated router APIs + verification |
| Persistence             | Room repositories                        |
| Credentials             | Android Keystore-backed storage          |
| Root                    | Restricted optional bridge               |
| Cross-feature events    | SharedFlow event bus                     |
| Testing                 | Mockable contracts and integration tests |

These contracts are the implementation reference. Any future changes to them should be recorded as an architecture decision rather than silently introducing different interfaces in individual features.

## 17. Next engineering deliverable

We are now ready to move from architecture documents into actual project construction.

Recommended next artifact: **Technical Design 04 — Repository Skeleton & Implementation Backlog**.

It will contain:

- Complete Gradle multi-module configuration.
- Package structure and module dependency graph.
- Kotlin interfaces and initial implementation files.
- Room entities, DAO contracts, and database migrations.
- Hilt dependency injection setup.
- Compose navigation and feature scaffolding.
- Discovery engine foundation.
- Router adapter registry and initial adapters.
- AI-coding-agent task definitions with dependencies, acceptance criteria, and test requirements.

The intention is to produce a repository blueprint that can be handed to OpenCode or another coding agent without asking it to invent the architecture as it codes.
