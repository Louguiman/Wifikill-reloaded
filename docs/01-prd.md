# WiFiKill Reloaded — Product Requirements Document (PRD)

> Version: Draft v1.0 (MVP scope)
> Date: November 2026
> Product: WiFiKill Reloaded
> Product category: Android Network Management & Diagnostics
> Product owner: Sankaré Technology
> Target platform: Android
> Primary language: English initially, with French localization planned.

## 1. Executive summary

WiFiKill Reloaded is an Android application designed to give users visibility and management capabilities over Wi-Fi and local networks they own or are authorized to administer.

The application will combine network discovery, device identification, network diagnostics, traffic monitoring, and router-based access management.

Unlike the original WiFiKill, the new product will be designed for modern Android versions, support both rooted and non-rooted devices, and use a modular integration architecture to accommodate different router manufacturers.

### 1.1 Product vision

Make professional network visibility and administration accessible directly from an Android smartphone.

### 1.2 Product objectives

- Identify devices connected to a local network.
- Provide useful information about discovered devices.
- Monitor device availability and network performance.
- Simplify network diagnostics.
- Enable authorized access management through supported routers.
- Provide advanced diagnostics for rooted Android devices.
- Establish a foundation for a commercial network management product.

### 1.3 Product principles

| Principle     | Description                                                 |
| ------------- | ----------------------------------------------------------- |
| Local-first   | Core features work without cloud connectivity.              |
| Root-optional | Root unlocks additional functionality, not basic features.  |
| Modular       | Router integrations remain independent.                     |
| Secure        | Credentials and privileged operations are protected.        |
| Extensible    | New features and manufacturers can be added later.          |
| Transparent   | The application communicates technical limitations clearly. |

## 2. Target users

### Primary personas

**Persona A — Home network owner**

Wants to know which devices use their Wi-Fi, identify unfamiliar devices, and understand network performance.

**Persona B — IT technician**

Needs a portable tool to inspect customer networks, identify devices, diagnose connectivity issues, and manage supported routers.

**Persona C — Small business administrator**

Needs basic visibility into office network devices and authorized access management without investing in expensive enterprise software.

### User needs

| User          | Primary need               | Expected outcome                 |
| ------------- | -------------------------- | -------------------------------- |
| Home user     | Discover connected devices | Know what is on the network      |
| Technician    | Diagnose network problems  | Identify and troubleshoot issues |
| Administrator | Manage network access      | Apply and review access policies |

## 3. Functional scope

The MVP is organized into seven functional modules.

### MVP feature map

- **Network discovery** — Scan and identify devices
- **Device intelligence** — Profiles and identification
- **Live monitoring** — Availability and statistics
- **Router management** — Router discovery and integration
- **Access management** — Authorized access policies
- **Diagnostics** — Ping, DNS, gateway, and latency
- **Network history and settings** — Previous observations, preferences, and local configuration

### 3.1 Network discovery

**Purpose:** Discover devices visible on the current local network.

Functional requirements:

- FR-001: Detect the active Wi-Fi connection.
- FR-002: Retrieve available network interface information.
- FR-003: Identify the local IP address, subnet, and gateway where accessible.
- FR-004: Perform authorized local network discovery.
- FR-005: Display discovered devices.
- FR-006: Support manual rescanning.
- FR-007: Display scan progress.
- FR-008: Save scan sessions locally.
- FR-009: Distinguish discovery failures from an empty network.
- FR-010: Support IPv4 initially, with IPv6 extensibility.

Expected device information:

- IP address
- MAC address, when available
- Hostname
- Manufacturer/vendor, when identifiable
- Device availability
- First observed timestamp
- Last observed timestamp

> Important: Android and modern Wi-Fi privacy mechanisms can restrict access to MAC addresses and network information. Missing fields must be represented as unknown, not fabricated.

### 3.2 Device intelligence

**Purpose:** Provide a useful profile for each discovered device.

Functional requirements:

- FR-011: Display device details.
- FR-012: Allow users to assign custom names.
- FR-013: Allow device categorization.
- FR-014: Preserve historical observations.
- FR-015: Track changes in IP addresses.
- FR-016: Mark devices as online, offline, or unknown.
- FR-017: Allow users to mark trusted devices.
- FR-018: Support manual device identification.

Suggested categories:

- Smartphone
- Computer
- Tablet
- Smart TV
- Printer
- IoT device
- Router
- Unknown

### 3.3 Live monitoring

**Purpose:** Provide visibility into network activity.

Functional requirements:

- FR-019: Monitor device availability.
- FR-020: Display network latency.
- FR-021: Display gateway connectivity.
- FR-022: Display basic network traffic statistics where supported.
- FR-023: Support configurable monitoring intervals.
- FR-024: Show connectivity changes.
- FR-025: Allow monitoring to be paused.

Distinguish device-level traffic from total interface traffic. Reliable per-device bandwidth measurements generally require router telemetry or an appropriate network monitoring source.

### 3.4 Router management

**Purpose:** Connect to supported routers through authorized management interfaces.

Functional requirements:

- FR-026: Detect the default gateway.
- FR-027: Identify router manufacturer when possible.
- FR-028: Allow manual router configuration.
- FR-029: Support router authentication.
- FR-030: Retrieve router information.
- FR-031: Retrieve connected-client information where supported.
- FR-032: Provide router connection status.
- FR-033: Support adapter-specific capabilities.
- FR-034: Allow disconnection and credential removal.

Initial integrations:

- OpenWrt
- MikroTik RouterOS

Other router manufacturers will be supported through subsequent adapters.

### 3.5 Access management

**Purpose:** Let an authorized administrator manage device access using supported router capabilities.

Functional requirements:

- FR-035: Display supported access-control capabilities.
- FR-036: Allow an administrator to create an access restriction for a selected device.
- FR-037: Require confirmation before applying a restriction.
- FR-038: Display the status of applied policies.
- FR-039: Allow an administrator to remove a restriction.
- FR-040: Record policy changes locally.
- FR-041: Handle unsupported router capabilities gracefully.
- FR-042: Verify the resulting router policy after an operation.

Access management must use documented router administration interfaces and administrator authorization. The app must never claim that a device has been blocked merely because a local command was sent.

### 3.6 Network diagnostics

Functional requirements:

- FR-043: Test gateway connectivity.
- FR-044: Perform ping tests where supported.
- FR-045: Measure response latency.
- FR-046: Test DNS resolution.
- FR-047: Display the active DNS configuration when accessible.
- FR-048: Detect basic connectivity failures.
- FR-049: Provide diagnostic results in understandable language.

### 3.7 History and settings

Functional requirements:

- FR-050: Store network scan history.
- FR-051: Store device observations.
- FR-052: Store access-policy activity.
- FR-053: Allow history filtering.
- FR-054: Allow deletion of local history.
- FR-055: Configure scan preferences.
- FR-056: Configure monitoring intervals.
- FR-057: Configure root mode.
- FR-058: Configure privacy and data retention settings.

## 4. User stories

| ID     | User story                                                                          | Priority |
| ------ | ----------------------------------------------------------------------------------- | -------- |
| US-001 | As a user, I want to scan my Wi-Fi network to discover devices.                     | Must     |
| US-002 | As a user, I want to identify devices by IP and hostname.                           | Must     |
| US-003 | As a user, I want to assign names to devices.                                       | Must     |
| US-004 | As a user, I want to see whether a device is online or offline.                     | Must     |
| US-005 | As a user, I want to view my router information.                                    | Must     |
| US-006 | As a technician, I want to test gateway connectivity.                               | Must     |
| US-007 | As an administrator, I want to configure access restrictions on a supported router. | Must     |
| US-008 | As an administrator, I want to remove an existing restriction.                      | Must     |
| US-009 | As a user, I want to view previous network scans.                                   | Should   |
| US-010 | As a user, I want to access advanced diagnostics using root privileges.             | Should   |
| US-011 | As a user, I want to monitor network activity.                                      | Should   |
| US-012 | As a technician, I want to manage multiple saved network profiles.                  | Could    |

### User journey: discovering devices

1. Open WiFiKill Reloaded.
2. Grant required local network permissions.
3. Select the active Wi-Fi network.
4. Start a network scan.
5. View discovered devices and identification information.
6. Open a device profile and assign a custom name.
7. Save the scan in local history.

### User journey: managing device access

1. Connect to a supported router using administrator credentials.
2. Retrieve the router's client inventory.
3. Select a device.
4. Review the available access-control capabilities.
5. Confirm the requested restriction.
6. Apply the administrator-authorized router policy.
7. Verify and display the resulting policy status.

## 5. Acceptance criteria

### AC-01: Network discovery

- The application identifies the active network interface where Android permits access.
- The user can initiate a scan.
- Scan progress is visible.
- Discovered devices appear in a structured list.
- Unavailable device attributes are shown as unknown.
- An empty scan is distinguishable from a failed scan.
- The application does not report devices as discovered without supporting observations.

### AC-02: Device identification

- Each device has a local application identifier.
- Users can assign and edit device names.
- The application stores device observations.
- IP changes do not automatically create duplicate physical-device records when a reliable identity match is available.
- Uncertain identity matches are treated conservatively.

### AC-03: Router integration

- The application can establish an authenticated connection to a supported router.
- Invalid credentials produce a clear error.
- The adapter reports its supported capabilities.
- Router communication failures do not crash the application.
- Credentials are never stored in plaintext.

### AC-04: Access management

- Only an authenticated administrator can apply policies.
- The application displays the selected device and requested action before confirmation.
- The application verifies the result through the router.
- Failed operations are clearly reported.
- Every successful policy change is recorded.

### AC-05: Root functionality

- The app can detect whether root access is available.
- The user explicitly authorizes privileged functionality.
- Root-dependent features are unavailable gracefully when authorization is denied.
- The privileged service accepts only predefined operations.
- No arbitrary remote shell execution is exposed.

### AC-06: Local persistence

- Network and device information persists across application restarts.
- Historical records can be viewed.
- Users can delete local data.
- Database operations remain functional offline.

## 6. Non-functional requirements

| ID      | Requirement     | Target                                                                                            |
| ------- | --------------- | ------------------------------------------------------------------------------------------------- |
| NFR-001 | Performance     | Dashboard should render without noticeable delay.                                                 |
| NFR-002 | Discovery       | A typical /24 LAN scan should target completion within 30 seconds, subject to network conditions. |
| NFR-003 | Stability       | No application crashes during normal scanning and monitoring.                                     |
| NFR-004 | Security        | Secure credential storage and restricted privileged operations.                                   |
| NFR-005 | Privacy         | No mandatory account or cloud dependency.                                                         |
| NFR-006 | Compatibility   | Support Android 10 and above as the initial compatibility target, subject to device testing.      |
| NFR-007 | Maintainability | Modular features and independent router adapters.                                                 |
| NFR-008 | Accessibility   | Readable contrast, scalable text, and accessible controls.                                        |
| NFR-009 | Localization    | Architecture ready for English and French.                                                        |
| NFR-010 | Reliability     | Graceful handling of network interruptions and permission changes.                                |
| NFR-011 | Battery usage   | Avoid unnecessary continuous scans and background activity.                                       |
| NFR-012 | Data integrity  | Transactions and consistent historical records.                                                   |

These are initial engineering targets, not claims of verified performance.

## 7. Features explicitly excluded from v1.0

To prevent scope creep, the following features are not part of the initial release.

| Feature                                            | Decision | Reason                                                |
| -------------------------------------------------- | -------- | ----------------------------------------------------- |
| Automatic disconnection of arbitrary Wi-Fi clients | Excluded | Not a reliable universal Android capability           |
| Deauthentication attacks                           | Excluded | Disruptive and unsuitable for the product             |
| ARP spoofing-based blocking                        | Excluded | Fragile and inappropriate as a core enforcement model |
| Cellular network monitoring                        | Excluded | Different technical scope                             |
| Full enterprise network management                 | Deferred | Requires a larger infrastructure                      |
| Remote management through cloud                    | Deferred | Not necessary for MVP                                 |
| Multi-user organizations                           | Deferred | Commercial expansion                                  |
| AI network anomaly detection                       | Deferred | Requires sufficient monitoring data                   |
| iOS application                                    | Deferred | Separate platform implementation                      |

## 8. Security and privacy requirements

Security is particularly important because the application may have router administrator credentials and optional root privileges.

### Security requirements

- Store sensitive credentials using Android Keystore-backed encryption.
- Never transmit router credentials to an external server.
- Use HTTPS for cloud communications.
- Validate router certificates where applicable.
- Restrict router API operations to documented capabilities.
- Avoid exposing privileged services over the network.
- Maintain an audit history of access-policy changes.
- Allow users to delete saved router credentials.
- Require explicit authorization before changing router configurations.
- Avoid collecting network information that is unnecessary for product functionality.

### Privacy model

Default data flow:

```text
Android device
Scan results, device profiles, credentials
        │
        ▼
Local
        │
        ├── Router
        │   Authorized configuration and client inventory (local network)
        │
        └── Cloud backend
            Optional account, licensing, and subscription services (optional)
```

The initial product must not require uploading discovered device inventories to a cloud service.

## 9. MVP release definition

Version 1.0 — Initial release (Required)

- Network scanning
- Device discovery and identification
- Device profiles
- Network information dashboard
- Basic diagnostics
- Local database and history
- Root detection and advanced diagnostics
- Initial OpenWrt integration
- Initial MikroTik integration
- Router-authorized access management

Version 1.1 — Expansion

- Additional TP-Link and Huawei adapters
- Improved traffic monitoring
- Network profile management
- Exportable diagnostic reports
- Enhanced device classification

Version 2.0 — Commercial expansion

- Premium subscriptions
- Multi-network management
- Technician workspace
- Cloud synchronization
- Advanced analytics
- Business administration features

## 10. Success metrics

| Metric                                  | Initial target                                                           |
| --------------------------------------- | ------------------------------------------------------------------------ |
| Successful scan sessions                | 95% on supported test networks                                           |
| Device identification                   | 80% of discovered devices have at least one useful identifying attribute |
| Application crash rate                  | Below 1% of sessions                                                     |
| Successful supported-router connections | 95%                                                                      |
| Verified access-policy operations       | 100% of reported successes                                               |
| Offline core functionality              | Fully operational                                                        |

Targets will be validated against a defined hardware test matrix.

## 11. Risks and dependencies

| Risk                            | Impact | Mitigation                                             |
| ------------------------------- | ------ | ------------------------------------------------------ |
| Android permission restrictions | High   | Permission abstraction and device testing              |
| Router firmware differences     | High   | Independent adapters                                   |
| MAC randomization               | Medium | Confidence-based device identity                       |
| Client isolation                | High   | Explain discovery limitations                          |
| Root compatibility              | Medium | Isolated root bridge                                   |
| Router API changes              | High   | Adapter versioning and tests                           |
| Battery consumption             | Medium | Configurable monitoring                                |
| Google Play policies            | High   | Review permissions and distribution requirements early |

### External technical dependencies

- Android SDK and Jetpack Compose.
- Magisk / KernelSU compatibility research.
- OpenWrt ubus documentation.
- MikroTik RouterOS API documentation.
- IEEE MAC vendor database or another appropriately licensed vendor lookup source.
- Google Play developer and permission policies.

## 12. Development deliverables

| Deliverable            | Expected output                     |
| ---------------------- | ----------------------------------- |
| Product specification  | Approved PRD                        |
| UX specification       | Screen map and wireframes           |
| Architecture           | Technical design document           |
| Database design        | ERD and SQL/Room entities           |
| API contracts          | Router adapter interfaces           |
| Security specification | Root and credential handling        |
| Test specification     | Acceptance and integration tests    |
| Development backlog    | Epics, stories and tasks            |
| Release documentation  | Deployment and publishing checklist |

## 13. Initial development backlog

| Epic                      | Tasks                                    | Priority |
| ------------------------- | ---------------------------------------- | -------- |
| E01 — Foundation          | Project setup, Compose, Hilt, navigation | P0       |
| E02 — Permissions         | Wi-Fi and local network permissions      | P0       |
| E03 — Discovery engine    | Interface detection, subnet discovery    | P0       |
| E04 — Device intelligence | Identification, profiles, history        | P0       |
| E05 — Dashboard           | Network overview and device list         | P0       |
| E06 — Diagnostics         | Gateway, DNS, ping, latency              | P0       |
| E07 — Root engine         | Root detection, isolated helper          | P1       |
| E08 — Router engine       | Adapter contracts and authentication     | P0       |
| E09 — OpenWrt             | First router adapter                     | P0       |
| E10 — MikroTik            | Second router adapter                    | P1       |
| E11 — Access management   | Policy management and verification       | P1       |
| E12 — Quality assurance   | Testing, security and optimization       | P0       |

P0 means necessary for the initial release; P1 means important but can follow the first working discovery build.

## 14. Product decisions register

| Decision               | Proposed choice            |
| ---------------------- | -------------------------- |
| Application name       | WiFiKill Reloaded          |
| Platform               | Android                    |
| Minimum OS             | Android 10 initially       |
| Language               | Kotlin                     |
| UI                     | Jetpack Compose            |
| Architecture           | Clean Architecture + MVVM  |
| Local database         | Room                       |
| Backend                | NestJS, optional           |
| Root support           | Optional                   |
| Router strategy        | Adapter-based              |
| First integrations     | OpenWrt and MikroTik       |
| Primary enforcement    | Router-authorized policies |
| Default operating mode | Local-first                |
| Business model         | Freemium                   |

## 15. Definition of MVP completion

The MVP is ready for release when:

- Users can install and launch the application.
- Permissions are correctly requested and handled.
- Users can discover devices on supported networks.
- Devices can be identified and assigned custom names.
- Network information is displayed accurately.
- Scan history persists locally.
- Basic diagnostics work.
- Root functionality is isolated and tested.
- Supported router integrations authenticate correctly.
- Access policies are applied and verified correctly.
- Security and privacy tests pass.
- The application passes release testing on representative Android devices.

## 16. Next engineering milestone: Technical Architecture Document

PRD baseline established.

The next document should translate these requirements into a buildable engineering design, in this order:

1. **Database architecture** — Define entities, relationships, Room schemas, indexes, and migrations.
2. **Application architecture** — Modules, packages, dependencies, repositories, use cases, and state management.
3. **Network engine** — Discovery mechanisms, Android permissions, scanning strategy, and device identity.
4. **Router integration** — Adapter contracts, OpenWrt, MikroTik, and access-policy lifecycle.
5. **Security architecture** — Root service, credential storage, authentication, and privacy.
6. **AI coding backlog** — Implementation-ready tasks, dependencies, acceptance criteria, and testing instructions.

One architectural recommendation before proceeding: keep the Android application independent from the cloud backend. This makes the product useful immediately, reduces infrastructure costs, and allows introducing subscriptions and centralized management later without redesigning the core.

The next step is **01 — Database Architecture and ERD**, establishing the actual data model before implementation.
