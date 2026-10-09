# Orpheus

Preserve your Android environment through destructive system changes.

Orpheus is an Android-first preservation and migration tool. It does not try to defeat a mandatory wipe; it tries to make that wipe survivable by preserving the maximum amount of user-controlled state that Android legitimately permits.

## Current status

Phase 4 — Shizuku integration (initial backend foundation).

The current app:

- uses a dark, high-contrast Android UI with a neutral #121212 base background and blue/purple accent surfaces
- lets the user select folders through Android's document picker
- enumerates selected document trees
- streams files into a .orpheus ZIP/DEFLATE container
- records per-file SHA-256 checksums and a logical manifest
- verifies the finished archive by reopening and hashing its file and package payloads
- records interrupted/incomplete backup sessions
- estimates selected content size before backup
- inventories installed packages, version/signature metadata, runtime permission state, and package restore metadata
- lets the user separately enable package metadata preservation and APK content preservation
- lets the user preserve all visible packages or only an explicitly selected package set
- preserves obtainable base APKs and split APKs with per-artifact checksums when APK preservation is enabled
- reports packages whose APKs are unavailable or fail to read instead of hiding them
- detects a connected Shizuku/Sui service and records its reported server UID as shell/ADB, root, or unknown
- requests Shizuku authorization before running privileged operations
- runs a read-only `id`/`getprop` probe in an isolated Shizuku UserService and reports failures explicitly
- keeps broader privilege-dependent preservation collectors and cross-device testing unfinished
- performs no restore or destructive operation

Minimum Android API: 26 (Android 8.0 / Oreo).

Package inventory uses Android's broad package-visibility mechanism because an application-preservation tool needs to enumerate installed packages. Distribution through Google Play is subject to Google's QUERY_ALL_PACKAGES policy; the open-source project is not treating Play distribution as an MVP requirement.

## Build

The project uses Android Gradle Plugin 9.4.1 with Gradle 9.6.0. AGP 9.4 requires Gradle 9.6 and JDK 17.

From a machine with an Android SDK and Gradle available:

gradle test
gradle assembleDebug

Android Studio can import the repository as a standard Gradle Android project.

## Development principles

The archive format, capability model, artifact model, backup lifecycle, restoration semantics, and destructive-readiness rules are specified under docs/.

Implementation should preserve those contracts rather than inventing alternative representations inside individual features.

See:

- docs/ARCHITECTURE.md
- docs/backup-format.md
- docs/specification-contracts.md
- ROADMAP.md

## Shizuku support

Shizuku or Sui must be installed and started by the user separately. On non-rooted Android 11 and later, Shizuku can be started with Wireless debugging. Orpheus requests its own Shizuku permission before using the isolated read-only probe. A connected Shizuku backend is not equivalent to root; actual UID and operation results determine available capabilities. See [docs/shizuku-backend.md](docs/shizuku-backend.md).
