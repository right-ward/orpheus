# Orpheus

Preserve your Android environment through destructive system changes.

Orpheus is an Android-first preservation and migration tool. It does not try to defeat a mandatory wipe; it tries to make that wipe survivable by preserving the maximum amount of user-controlled state that Android legitimately permits.

## Current status

Phase 1 — Android skeleton.

The current app:

- reports basic device and Android metadata
- inspects storage capacity
- exposes the initial capability model
- provides structured local logging
- has host-side tests for the capability model
- performs no backup, restore, or destructive operation

Minimum Android API: 26 (Android 8.0 / Oreo).

## Build

The project uses Android Gradle Plugin 9.4.1 with Gradle 9.6.0. AGP 9.4 requires Gradle 9.6 and JDK 17.

From a machine with an Android SDK and Gradle available:

```sh
gradle test
gradle assembleDebug
```

Android Studio can import the repository as a standard Gradle Android project.

## Development principles

The archive format, capability model, artifact model, backup lifecycle, restoration semantics, and destructive-readiness rules are specified under `docs/`.

Implementation should preserve those contracts rather than inventing alternative representations inside individual features.

See:

- `docs/ARCHITECTURE.md`
- `docs/backup-format.md`
- `docs/specification-contracts.md`
- `ROADMAP.md`
