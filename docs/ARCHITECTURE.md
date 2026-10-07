# Orpheus Architecture

## Purpose

Orpheus is an Android-first tool for preserving and reconstructing as much of a user's Android environment as possible across destructive system changes.

The primary use case is a bootloader unlock or similar operation that destroys user data. Orpheus does not attempt to defeat the device's security model or prevent a mandatory wipe. It prepares a verified preservation set before the destructive operation and uses that set to reconstruct the environment afterward.

A desktop is optional. An Android device with Shizuku and, on supported versions, wireless debugging should be a first-class operating environment.

## Core principles

1. Survive the wipe rather than bypass it.
2. Android-first. A PC must not be a prerequisite for the main workflow.
3. Capability-driven. Every operation depends on detected capabilities rather than assumptions about an Android version or OEM.
4. Loss must be explicit. Every result is classified as restorable, partial, requires privilege, unavailable, or failed.
5. Verify before destruction. A backup is not ready until its integrity and required contents have been checked.
6. One logical archive format, multiple frontends. Android UI, Termux/CLI, and future desktop tooling should consume the same archive and manifests.
7. Privilege is an adapter. Normal Android APIs, Shizuku/ADB, and eventual root support are capability providers, not separate backup formats.
8. No exploit dependency. The project must not depend on bootloader or kernel vulnerabilities.
9. User-controlled secrets. Portable archives may contain highly sensitive data and must be encrypted.
10. Recoverability over illusion. Orpheus must never claim to produce a perfect device clone when Android does not permit one.

## High-level architecture

~~~text
                    Orpheus Android
                           |
              +------------+------------+
              |                         |
       Normal Android APIs        Privilege backends
                                    /      \
                              Shizuku       Root*
                                  |
                          Capability layer
                                  |
                           Domain collectors
                      +-----------+-----------+
                      |           |           |
                    Files      Packages    Personal data
                      |           |           |
                      +-----------+-----------+
                                  |
                            Backup engine
                                  |
                             Archive engine
                                  |
                          Integrity verifier
                                  |
                           .orpheus archive
                                  |
                          Restoration engine

* Root is a later capability backend, not an MVP requirement.
~~~

## Components

### Presentation layer

The Android application provides the primary user interface.

The UI should expose two major workflows:

- Prepare: inspect capabilities, create a preservation set, verify it, and produce a ready result.
- Restore: open an Orpheus archive, generate a restoration plan, execute it, and verify the result.

Advanced users should also be able to inspect individual capabilities and artifacts.

Termux/CLI support should eventually expose the same engine through commands such as:

~~~text
orpheus scan
orpheus backup
orpheus verify
orpheus inspect
orpheus restore
~~~

The CLI is not required for the Android MVP.

### Capability layer

The capability layer answers:

- Is Orpheus running normally, through Shizuku/ADB, or with root?
- Which system APIs are usable?
- Which shell operations are permitted?
- Which storage locations are readable?
- Which package-management operations are available?
- Which personal-data providers are accessible?
- Which restore actions are possible on this device and Android version?

The layer should expose capabilities as structured data instead of making callers infer them from exceptions.

Example:

~~~json
{
  "backend": "shizuku",
  "privilege": "shell",
  "capabilities": {
    "package_inventory": "full",
    "apk_extraction": "partial",
    "private_app_data": "restricted",
    "user_files": "full"
  }
}
~~~

Shizuku can expose Android system APIs and user services through a process running with shell/ADB or root identity. Its documentation also warns that ADB permissions vary by Android version, so Orpheus must detect and record actual available permissions rather than treating Shizuku as equivalent to root.

References:
- https://github.com/RikkaApps/Shizuku
- https://github.com/RikkaApps/Shizuku-API

### Domain collectors

Collectors convert Android-specific sources into normalized Orpheus artifacts.

Initial domains:

- User files
- Installed packages
- APK/split APK artifacts where obtainable
- Package metadata
- Runtime permission state where obtainable
- Contacts
- SMS
- Call history
- Calendars
- User-selected application exports

Each collector reports both artifacts and a result status.

### Backup engine

The backup engine orchestrates collectors.

It should support:

- progress reporting
- cancellation
- resumable operations where practical
- per-artifact checksums
- deterministic manifest generation
- failure isolation
- storage-space estimation
- preflight validation

A failed collector must not invalidate unrelated successful artifacts unless the archive itself is structurally incomplete.

### Archive engine

The archive engine writes and reads the Orpheus archive format described in docs/backup-format.md.

The logical format is intentionally independent of the Android UI and implementation language.

### Verification engine

Verification occurs at two stages.

Before the destructive operation:
- validate archive structure
- validate manifest entries
- validate checksums
- ensure mandatory artifacts are present
- ensure the archive can be opened and read
- record unresolved items

After restoration:
- verify expected artifacts exist
- compare checksums when byte identity is meaningful
- run domain-specific validation where applicable
- report incomplete restoration

### Restoration engine

Restoration is plan-based rather than a blind sequence of commands.

A restore plan is derived from:

- archive contents
- current device capabilities
- current Android version
- installed packages
- available storage
- artifact requirements

Example:

~~~text
1. Restore user files
2. Install application packages
3. Restore contacts
4. Restore SMS
5. Restore call history
6. Restore supported exports
7. Apply supported settings
8. Verify restored artifacts
~~~

Every step produces an explicit result.

## Privilege backends

### Normal Android

The lowest common denominator.

Use public Android APIs and user-mediated storage access wherever possible.

Advantages:
- no additional privileged service required
- broad device compatibility

Limitations:
- application sandbox boundaries
- protected settings/data
- limited control over package installation/restoration

### Shizuku / ADB

The principal enhancement for the Android-first design.

Shizuku allows an application to request operations through a service started with ADB or root privileges. On non-rooted Android 11+, users can start Shizuku using the device's wireless debugging support, making a no-PC workflow possible.

Advantages:
- shell-level identity for supported operations
- access to additional system APIs
- useful package and filesystem operations

Limitations:
- shell is not root
- permissions vary by Android release/OEM
- some private application data remains inaccessible
- non-rooted Shizuku generally needs to be restarted after reboot

References:
- https://github.com/RikkaApps/Shizuku
- https://github.com/RikkaApps/Shizuku-API

### Root

A future backend.

Root may make additional artifacts accessible and may permit deeper restoration, but Orpheus must remain useful without root.

Root-specific capabilities must never be represented as mandatory prerequisites for the baseline format.

## Security model

Backup archives can contain private files, messages, contacts, application artifacts, and other sensitive material.

The project should therefore provide:

- authenticated encryption for portable archives
- keys controlled by the user
- no hard-coded secrets
- no telemetry by default
- explicit confirmation before destructive-adjacent operations
- secure-deletion guidance without claiming perfect flash-storage sanitization
- a distinction between Orpheus archive encryption and Android's own backup mechanisms

The archive should include enough metadata to describe its contents without decryption only where that metadata is deliberately non-sensitive. Sensitive metadata should remain encrypted.

Android's backup guidance recommends strong protection for sensitive backup data; Orpheus should follow the same principle for its own portable archive format.

Reference: https://developer.android.com/privacy-and-security/risks/backup-best-practices

## Data classes

Every artifact belongs to a restoration class:

- restorable: expected to restore completely under matching capabilities.
- partial: only part of the original information can be represented.
- requires_privilege: requires Shizuku or root, depending on the artifact.
- unavailable: the platform/device policy exposes no supported collection path.
- failed: collection was attempted but did not complete successfully.

This classification is part of the user-facing contract.

## Explicit non-goals

The following are outside the initial scope:

- bypassing a bootloader unlock wipe
- exploiting vulnerabilities to defeat OEM security
- recovering cryptographic secrets that Android intentionally protects
- defeating DRM
- creating a byte-for-byte clone of every Android installation
- silently extracting another application's private data
- making unsupported restoration claims

## Open architectural questions

These remain intentionally unresolved until implementation design is discussed:

- implementation language
- minimum supported Android API level
- exact archive container/compression choice
- exact authenticated-encryption scheme and key-derivation parameters
- concurrency and job-execution model
- persistence mechanism for resumable jobs
- exact Shizuku integration strategy
- whether the first CLI should share code through a native core or arrive later as a separate frontend
