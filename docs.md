---
layout: default
title: Documentation
permalink: /docs/
description: "Current Orpheus user guide, build instructions, Shizuku setup, archive safety, and implementation limits."
---

# Orpheus: guide and current limits

Orpheus is an Android-first preservation tool for collecting user-selected files and obtainable application state before a destructive system change. Its goal is to make a wipe survivable—not to bypass Android's security model or promise a complete device clone.

**Current state:** app version 0.6.0, Phase 4 — Shizuku integration. The current application creates and verifies archives. It does not restore them or execute bootloader, reset, or other destructive operations.

## What works today

- Choose folders through Android's system document picker and enumerate the selected document trees.
- Stream selected files into a compressed .orpheus ZIP/DEFLATE archive.
- Store a logical manifest and SHA-256 checksums, then reopen the completed archive and verify its file and package payloads.
- Estimate selected file content size and record interrupted or incomplete backup sessions.
- Inventory visible installed packages and record package/version/signing metadata, requested permissions and their granted state where Android exposes them.
- Optionally preserve obtainable base APKs and split APKs, checking their contents and reporting inaccessible or failed items.
- Detect Shizuku/Sui availability, request Orpheus-specific authorization before privileged work, and run a fixed, read-only diagnostics probe.
- Use a narrow Shizuku fallback to read certain PackageManager-reported APK files below /data/app/ when normal access fails.
- Optionally capture a small allow-list of display, rotation, haptic, sound-effects, and animation settings. This snapshot is partial and is not currently restored.

The exact capabilities depend on the device, Android version, package, and active backend. A capability that has not been implemented or tested is not the same as a platform limitation.

## Suggested workflow with the current app

### 1. Prepare the device

Install Orpheus on the Android device you want to preserve. The minimum app API level is Android 8.0 (API 26). Review the planned operation and decide which files and applications you need to keep. Do not assume the archive will contain every part of the device.

### 2. Select user files

Use the Android document picker to grant access to the folder trees you want to preserve. Choose folders deliberately: broad selections can produce very large archives and may include private information you did not intend to copy.

Review the estimated content size and ensure that the target storage has enough free space. Collection is streamed, but archive space is still required.

### 3. Choose package preservation options

Package inventory and APK content preservation are separate choices. Inventory records metadata for packages Android makes visible to Orpheus. Enable APK preservation when you also want obtainable installation packages, and choose whether to cover all visible packages or an explicitly selected set.

Not every package's APK can be read. Unchanged system APKs are catalogued but generally not copied because the target system is expected to provide its own platform packages. Missing or inaccessible artifacts should be reviewed rather than assumed to be restorable.

### 4. Optionally enable the Shizuku backend

Shizuku or Sui is installed and started separately; Orpheus does not install or launch it for you. Follow the [official setup guide](https://shizuku.rikka.app/), then return to Orpheus and grant Orpheus its own Shizuku permission when prompted.

On non-rooted Android 11 and later, Shizuku can commonly be started through Developer options and Wireless debugging without a computer. Exact setup depends on Android version and device. A connected service is not equivalent to root.

Orpheus first runs a read-only diagnostics probe using fixed commands such as id and selected getprop values. If that probe succeeds, the service can support the constrained APK-read fallback. The probe does not grant access to arbitrary protected data.

The optional settings snapshot is deliberately limited to an allow-list. It is not a complete settings backup and it is not applied during restoration.

### 5. Verify and protect the archive

Wait for the archive to finish finalization and verification. Orpheus reopens the ZIP container and hashes the recorded payloads; incomplete or failed sessions should not be treated as verified backups. Keep the archive on storage separate from the device you intend to wipe, and confirm that the copy is actually available before proceeding with any destructive operation.

## Archive format and privacy

The current physical container is a ZIP archive using DEFLATE compression, stored with the .orpheus extension. The logical format has a manifest and content namespaces for files, packages, data, exports, and checksums. The logical schema and physical container may evolve as development continues.

**The current archive is not encrypted.** SHA-256 checksums can help detect accidental payload corruption, but they do not encrypt data or provide confidentiality. A portable archive may include personal files, application metadata, APKs, and other sensitive material. Store it accordingly and do not upload or share it casually.

Archive encryption is part of the design requirements and roadmap, not a currently implemented protection. Do not interpret the presence of checksums as a substitute for encryption.

## Build from source

The Android project uses Android Gradle Plugin 9.4.1, Gradle 9.6.0, and JDK 17. It targets Android API 36 and supports a minimum API level of 26.

With a compatible JDK, Android SDK, and Gradle installation available, run these commands from the repository root:

~~~sh
gradle test
gradle assembleDebug
~~~

Android Studio can also import the repository as a standard Gradle Android project. The debug APK is produced by the Android Gradle build under the app module's build outputs.

For the exact dependency versions and build configuration, consult the repository's [app/build.gradle.kts](https://github.com/right-ward/orpheus/blob/main/app/build.gradle.kts). Tests and builds should be rerun against the current checkout; the requirements above can change as Android tooling evolves.

## Current limitations

### Restoration is not implemented

The app currently creates and verifies preservation archives. It does not import an archive on a fresh device, generate or execute a restoration plan, install applications from an archive, restore personal data, or verify a restored environment. Do not rely on the current release as a complete migration or recovery solution.

### Personal-data collectors are not implemented

Dedicated collectors and restorers for contacts, SMS, call history, calendars, and other structured personal data remain roadmap work. Selecting files may preserve exports you create yourself, but Orpheus does not currently collect those structured domains automatically.

### Private application data remains restricted

The app does not generally preserve arbitrary private app data, credentials, hardware-backed secrets, DRM state, or protected OEM state. Shizuku's shell/ADB identity is not root. The current Shizuku service supports a read-only diagnostics probe and a constrained APK-file fallback; it does not expose general shell commands or a general private-data reader.

### APK and settings coverage is partial

APK files may be inaccessible on a particular device or installation layout; the current fallback is limited to canonical APK files under /data/app/. Unchanged system APKs are not copied. The opt-in system-settings snapshot contains only a fixed list of keys, reports partial coverage, and is not restored.

### Encryption, destructive readiness, and device testing remain unfinished

The archive is currently unencrypted. The guided preparation flow, explicit review of losses, final readiness gate, and external-copy reminders are roadmap items. Broader testing across Android versions, OEMs, and Shizuku/Sui configurations is also unfinished, so compatibility should not be inferred merely from the minimum API level.

### Future work

The roadmap prioritizes deeper preservation collectors, a safer guided pre-wipe workflow, archive import and plan-based restoration, and verification after restoration. Later phases consider root support, a Termux/CLI interface, a desktop companion, and optional device/OEM adapters.

## Project references

- [Repository README](https://github.com/right-ward/orpheus/blob/main/README.md)
- [Development roadmap](https://github.com/right-ward/orpheus/blob/main/ROADMAP.md)
- [Architecture](https://github.com/right-ward/orpheus/blob/main/docs/ARCHITECTURE.md)
- [Backup format](https://github.com/right-ward/orpheus/blob/main/docs/backup-format.md)
- [Specification contracts](https://github.com/right-ward/orpheus/blob/main/docs/specification-contracts.md)
- [Shizuku backend notes](https://github.com/right-ward/orpheus/blob/main/docs/shizuku-backend.md)
- [Issue tracker](https://github.com/right-ward/orpheus/issues)
