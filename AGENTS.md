# AGENTS.md — WiFiKill Reloaded

Project context and guardrails for AI coding agents. Read this before generating or changing code.

## What this project is

WiFiKill Reloaded is an Android network administration app for networks the user owns or is authorized to administer. It provides network discovery, device intelligence, diagnostics, monitoring, and **router-authorized** access management.

This is **not** a revival of the original WiFiKill's interception techniques.

## Source of truth

Read the documents in order before implementing:

1. `docs/00-product-roadmap.md`
2. `docs/01-prd.md`
3. `docs/02-database-architecture.md`
4. `docs/03-application-architecture.md`
5. `docs/04-api-contracts.md`

When a request conflicts with these documents, surface the conflict instead of silently diverging. Record architecture changes as decisions.

## Hard rules

- **No ARP spoofing, deauthentication, or client-disconnection attacks.** Access enforcement uses documented, authenticated router administration APIs.
- **Verify before claiming success.** A policy operation is `ACTIVE` only after read-back verification from the router. Otherwise `UNKNOWN` or `FAILED`.
- **Root is optional and isolated.** No arbitrary shell execution. Only allowlisted `RootOperation` values. Root must never be required for core features or imported directly by UI code.
- **Local-first.** Core features work with no account, backend, or internet. Cloud is optional and must not be required.
- **Never fabricate data.** Missing IP/MAC/hostname/vendor = `unknown`, not a guess. Missing measurements are not zero. An empty scan is distinguishable from a failed scan.
- **Credentials never in Room or logs.** Store only a `credentialId`; secrets live in Android Keystore-backed storage. Never log secrets or transmit router credentials off-device.
- **Device identity is not an IP or MAC.** Use evidence-based identity resolution with confidence; preserve uncertain matches separately.

## Architecture rules

- Modular Clean Architecture + MVVM; Kotlin; Jetpack Compose; Hilt; Room; Coroutines/Flow.
- Dependencies point inward: `feature` → `core:domain`; `data`/`integrations` implement domain contracts.
- `core:domain` must not depend on Android UI, Room, or vendor code.
- `core:database` contains no business rules.
- Discovery engine is independent of Compose and Room.
- Router vendors implement the shared `RouterAdapter` contract and are registered via `RouterAdapterRegistry`.
- Capabilities are detected and verified per model/firmware — never assumed from vendor/brand.
- UI talks to use cases, not repositories directly.
- Use `AppResult<T>` / `AppErrorCode` for structured errors; do not leak raw exceptions to UI.
- Cross-feature notifications go through `AppEventBus`; persistent state stays in repositories.

## Conventions

- IDs: UUID strings. Timestamps: UTC Unix milliseconds.
- Room entities use the `Entity` suffix; DTOs use `DTO`.
- Use enums/converters for statuses, not loose strings.
- Every external operation defines timeout and cancellation behavior.
- No comments unless necessary; follow existing style.

## Scope

MVP (v1.0): discovery, device profiles, dashboard, diagnostics, local history, root detection, OpenWrt + MikroTik adapters, verified access policies.

Out of scope for v1.0: TP-Link/Huawei adapters, advanced monitoring, cloud sync, multi-network/technician features, iOS, cellular monitoring, anomaly detection.

## Testing expectations

- Unit tests for domain/use cases; state tests for ViewModels; DAO + migration tests for Room.
- Mockable contracts so tests need no real router or physical network.
- Cover: missing permissions, offline router, IP changes, MAC randomization, invalid credentials, unsupported capability, unverified policy, credential leakage, migrations.

## Build

No app code exists yet. When scaffolding is added, pin versions in `gradle/libs.versions.toml` and document the lint/test/typecheck commands here.
