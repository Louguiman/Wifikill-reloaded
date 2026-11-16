# WiFiKill Reloaded

**Network visibility. Device intelligence. Access management.**

A mobile-first network administration toolkit for Android. It discovers, monitors, and manages devices on networks the user owns or is authorized to administer.

WiFiKill Reloaded is a fresh product, not a clone of the original WiFiKill. It targets modern Android, works with optional root, and uses an adapter architecture for router integrations. Access enforcement is based on **router-authorized policies** — not ARP spoofing, deauthentication, or other fragile interception techniques.

## Status

M1–M6 are implemented. The walking skeleton (Gradle multi-module graph, convention plugins, `core:common`, `core:domain` contracts, design system, Hilt app shell, navigation, CI) and local discovery (Room, network context, permission handling, discovery engine, identity resolution, device UI) are in place. The router foundation adds the `RouterAdapter` registry, a generic HTTP(S) adapter, Android Keystore-backed credential storage, and router management UI. M4 adds verified access: OpenWrt and MikroTik adapters plus router-authorized policies that become `ACTIVE` only after read-back verification, with an access management UI. M5 adds diagnostics (gateway reachability, DNS resolution, internet connectivity, latency, packet loss, and network configuration with local history) and source-aware local monitoring that labels Android interface totals as this-device measurements and stops when paused. M6 adds onboarding, persistent settings, local data deletion, configurable retention with startup and WorkManager cleanup, optional allowlisted root advanced diagnostics, sanitized error reporting, and en/fr resources for all screen-level UI copy. `./gradlew test lint :app:assembleDebug` passes, and `:app:assembleRelease` builds an unsigned APK. Hardware/firmware verification and release signing remain before v1.0.

## Documentation

Read in order:

| # | Document | Contents | Status |
| - | -------- | -------- | ------ |
| 00 | [Product Architecture & Roadmap](docs/00-product-roadmap.md) | Vision, positioning, architecture, stack, router strategy, root strategy, roadmap, monetization, testing | Drafted |
| 01 | [Product Requirements Document](docs/01-prd.md) | Personas, feature modules, functional requirements, user stories, acceptance criteria, NFRs, scope, risks | Drafted |
| 02 | [Database Architecture & ERD](docs/02-database-architecture.md) | Entities, relationships, indexes, Room entities/DAOs, retention, testing | Drafted |
| 03 | [Application Architecture](docs/03-application-architecture.md) | Modules, dependency rules, discovery engine, router/root layers, security, background work, ADRs | Drafted |
| 04 | [API & Interface Contracts](docs/04-api-contracts.md) | Domain models, discovery/device/router/access/diagnostics/monitoring/security contracts, use cases, event bus | Drafted |
| 05 | [Repository Skeleton & Backlog](docs/05-repository-skeleton-and-backlog.md) | Gradle modules, version catalog, package layout, code skeletons, CI, epics/stories/tasks, milestones | Drafted |
| 06 | [Security Review & Release Checklist](docs/06-security-review-and-release.md) | Security review, localization status, release checklist, hardware matrix, known gaps | Drafted |

## Key decisions

| Decision | Choice |
| -------- | ------ |
| Platform | Android |
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | Modular Clean Architecture + MVVM |
| Local database | Room / SQLite |
| Router strategy | Adapter-based (OpenWrt + MikroTik first) |
| Access enforcement | Router-authorized policies, verified after apply |
| Root | Optional, isolated, allowlisted operations only |
| Default mode | Local-first; no account or cloud required |
| Business model | Freemium |

## Scope boundaries

Explicitly excluded from v1.0: automatic Wi-Fi client disconnection, deauthentication attacks, ARP-spoofing-based blocking, cellular monitoring, cloud-only features, and an iOS client.

## Next milestone

M6 (release candidate) is implemented in code. Before v1.0, complete the hardware/firmware test matrix and configure release signing and `prod` branch protection. See `docs/06-security-review-and-release.md`.

## Builds and releases

| Branch | CI output |
| ------ | --------- |
| `main`, feature branches, PRs | Debug APK artifact (plus unit tests and lint) |
| `prod` | Signed release APK attached to a GitHub Release |

Promote a release by merging `main` into the protected `prod` branch. Production builds are versioned from the workflow run (for example `0.1.42.1`) and tagged `v<version>`.

Configure these repository secrets before the first production build:

| Secret | Purpose |
| ------ | ------- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Release key alias |
| `ANDROID_KEY_PASSWORD` | Release key password |

Generate the encoded secret without committing the keystore:

```bash
base64 -w0 release.keystore
```

Local `./gradlew :app:assembleRelease` builds an unsigned APK unless the four `ANDROID_KEYSTORE_FILE` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD` environment variables are set.

## Assets

Referenced images are stored under `docs/assets/`. A few source images could not be downloaded at capture time; those documents link to the original remote URL and are marked with an `image unavailable` comment.
