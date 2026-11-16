# Security Review & Release Checklist

> Owner: Sankaré Technology

This document records the M6 release-candidate review for WiFiKill Reloaded. Items are marked
`PASS`, `FAIL`, or `NOT RUN`. A `NOT RUN` item is a known gap, not an inferred pass.

## 1. Security review (T14.5)

| Area | Check | Result | Evidence |
| ---- | ----- | ------ | -------- |
| Credentials | Router secrets are never stored in Room | PASS | `RouterEntity` stores only `credentialId`; `EncryptedCredentialProvider` uses Keystore-backed encryption in app-private storage |
| Credentials | Secrets are not logged or reported | PASS | `ErrorSanitizer` redacts password/token/authorization values and URL userinfo before reporting; no credential fields are logged |
| Credentials | Deleting local data removes stored credentials | PASS | `LocalDataRepositoryImpl` deletes every router `credentialId` before deleting router rows; covered by `LocalDataRepositoryImplTest` |
| Backups | App data is excluded from backups | PASS | `android:allowBackup="false"` in the manifest |
| Root | Root is optional | PASS | Root is only used by `integrations:root`; standard discovery, router, access, diagnostics, and monitoring paths do not depend on it |
| Root | No arbitrary shell execution | PASS | `RootManager.execute` accepts only `RootOperation`; commands are fixed in `RootManagerImpl.APPROVED_COMMANDS` |
| Root | Root operations have timeouts and validation | PASS | Availability 2 s, operations 5 s, output capped at 8 KiB; covered by `RootManagerImplTest` |
| Root | Explicit user authorization | PASS | Advanced diagnostics shows a confirmation dialog before every root operation |
| Root | Root denial is graceful | PASS | Unavailable root reports `ROOT_UNAVAILABLE`; UI keeps standard diagnostics available |
| Network | No ARP spoofing, deauthentication, or client disconnection | PASS | Access changes only through router adapters and verified policies |
| Policy | No success without read-back verification | PASS | `ACTIVE` is set only after `getAccessPolicies()` verification |
| Database | Foreign-key-safe deletion order | PASS | Children are deleted before `devices`, `routers`, and `networks` |
| Database | Settings are not deleted by history wipe | PASS | `DeleteLocalDataUseCase` clears history/profiles but leaves `app_settings` intact |
| Localization | No secrets in resources | PASS | Only UI copy is stored in string resources |
| Error handling | UI receives structured errors | PASS | `AppResult`/`AppError`/`AppErrorCode`; `INVALID_ARGUMENT`, `ROOT_UNAVAILABLE`, and `ROOT_OPERATION_FAILED` added for M6 |

### Open security follow-ups

- Run a dependency and manifest audit on the release build (for example Play Console pre-launch
  reports) before publishing.
- Confirm Keystore behaviour after OS upgrade and device restore on physical hardware.
- Re-check that no future adapter logs raw HTTP request or response bodies containing credentials.

## 2. Localization (T14.4)

- `en` and `fr` string resources exist for dashboard, discovery, devices, router management,
  access management, diagnostics, monitoring, onboarding, and settings.
- Screen-level labels, empty states, buttons, dialogs, and formatted summaries are externalized.
- `res/xml/locales_config.xml` declares `en` and `fr` for per-app language selection on Android 13+.
- ViewModel-authored transient status and error messages are localized through
  `core:common.StringProvider`, with an Android `Context`-backed implementation bound in the app.
- Technical error details supplied by domain or integration layers may still surface in English;
  they are diagnostic detail rather than primary UI copy.

## 3. Release checklist (T14.6)

| Check | Result | Notes |
| ----- | ------ | ----- |
| `./gradlew test` | PASS | Unit tests cover domain, repositories, root engine, ViewModels, and migrations |
| `./gradlew lint` | PASS | Android lint runs clean |
| `./gradlew :app:assembleDebug` | PASS | Debug APK builds |
| `./gradlew :app:assembleRelease` (unsigned) | PASS | Builds `app-release-unsigned.apk` when signing environment variables are absent |
| `./gradlew :app:assembleRelease` (signed) | NOT RUN | Requires release signing environment variables and keystore |
| Release signing secrets configured | NOT RUN | `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` |
| `prod` branch created and protected | NOT RUN | CI release workflow targets `prod` |
| Version code/name injected by CI | PASS | CI passes `versionCode`/`versionName` Gradle properties |
| Onboarding grant path | NOT RUN | Requires a physical device or emulator with permission UI |
| Onboarding deny path | NOT RUN | Requires a physical device or emulator with permission UI |
| Data deletion on device | NOT RUN | Requires a device with populated local history |
| Root available path | NOT RUN | Requires a rooted test device |
| Root unavailable path | NOT RUN | Requires an unrooted device |
| Background retention worker | NOT RUN | Schedules daily; verify with WorkManager inspector on device |

## 4. Hardware / firmware test matrix

Record actual observations only. Do not mark a row PASS without a completed run.

| Device / OS | Router model / firmware | Scenario | Result | Notes |
| ----------- | ----------------------- | -------- | ------ | ----- |
| Android 10 emulator | N/A | Launch, onboarding, settings | NOT RUN | |
| Android 13+ emulator | N/A | Permission grant/deny, locale switch | NOT RUN | |
| Android 14/15 physical | OpenWrt (model TBD) | Discovery, router connect, policy apply/verify/remove | NOT RUN | |
| Android 14/15 physical | MikroTik RouterOS (model TBD) | Discovery, router connect, policy apply/verify/remove | NOT RUN | |
| Rooted Android device | OpenWrt (model TBD) | Root available advanced diagnostics | NOT RUN | |
| Unrooted Android device | OpenWrt (model TBD) | Root unavailable graceful path | NOT RUN | |
| Physical device | N/A | Monitoring pause/stop, diagnostics history | NOT RUN | |

## 5. Known release-candidate gaps

1. Hardware matrix rows are not completed.
2. Release signing and `prod` branch protection remain to be configured.
3. Router firmware variance testing is limited to the mock/fixture tests committed so far.
4. Some domain/integration error details remain technical English rather than localized copy.
