# WiFiKill Reloaded

**Network visibility. Device intelligence. Access management.**

A mobile-first network administration toolkit for Android. It discovers, monitors, and manages devices on networks the user owns or is authorized to administer.

WiFiKill Reloaded is a fresh product, not a clone of the original WiFiKill. It targets modern Android, works with optional root, and uses an adapter architecture for router integrations. Access enforcement is based on **router-authorized policies** — not ARP spoofing, deauthentication, or other fragile interception techniques.

## Status

Product planning. No application code yet. The documents in `docs/` define the product, data model, architecture, and interface contracts for the MVP.

## Documentation

Read in order:

| # | Document | Contents | Status |
| - | -------- | -------- | ------ |
| 00 | [Product Architecture & Roadmap](docs/00-product-roadmap.md) | Vision, positioning, architecture, stack, router strategy, root strategy, roadmap, monetization, testing | Drafted |
| 01 | [Product Requirements Document](docs/01-prd.md) | Personas, feature modules, functional requirements, user stories, acceptance criteria, NFRs, scope, risks | Drafted |
| 02 | [Database Architecture & ERD](docs/02-database-architecture.md) | Entities, relationships, indexes, Room entities/DAOs, retention, testing | Drafted |
| 03 | [Application Architecture](docs/03-application-architecture.md) | Modules, dependency rules, discovery engine, router/root layers, security, background work, ADRs | Drafted |
| 04 | [API & Interface Contracts](docs/04-api-contracts.md) | Domain models, discovery/device/router/access/diagnostics/monitoring/security contracts, use cases, event bus | Drafted |

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

Technical Design 04 — Repository Skeleton & Implementation Backlog: Gradle multi-module setup, package structure, Hilt, Compose navigation, discovery foundation, router adapter registry, and AI-agent implementation tasks.

## Assets

Referenced images are stored under `docs/assets/`. A few source images could not be downloaded at capture time; those documents link to the original remote URL and are marked with an `image unavailable` comment.
