# Orpheus

Preserve your Android environment through destructive system changes.

Orpheus is an Android-first preservation and migration tool. It does not try to defeat a mandatory wipe; it tries to make that wipe survivable by preserving the maximum amount of user-controlled state that Android legitimately permits.

## Current status

Phase 2 — File preservation.

The current app:

- uses a dark, high-contrast Android UI
- lets the user select folders through Android's document picker
- enumerates selected document trees
- streams files into a `.orpheus` ZIP/DEFLATE container
- records per-file SHA-256 checksums and a logical manifest
- verifies the finished archive by reopening and hashing its file entries
- records interrupted/incomplete backup sessions
- estimates selected content size before backup
- performs no restore or destructive operation

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
