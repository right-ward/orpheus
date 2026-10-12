# Orpheus Roadmap

This roadmap prioritizes a reliable Android-first preservation workflow before adding deep privilege-dependent functionality.

## Phase 0 — Specification

Goal: establish contracts before implementation.

- [x] Define artifact model and capability statuses
- [x] Define backup lifecycle
- [x] Define archive layout and manifest schema
- [x] Define encryption and integrity requirements
- [x] Define restoration-plan semantics
- [x] Define minimum supported Android API level: API 26 (Android 8.0)
- [x] Record known Android/OEM limitations
- [x] Decide implementation language: Kotlin

Exit condition: architecture, archive, capability, lifecycle, restoration, security, and readiness contracts are stable enough that implementation can begin without inventing them ad hoc.

## Phase 1 — Android skeleton

Goal: a runnable Android application with no destructive behavior.

- [x] Android project structure
- [x] Application identity and package metadata
- [x] Device/Android information collector
- [x] Storage-space inspection
- [x] Capability model
- [x] Capability scan UI
- [x] Structured local logging
- [x] Test harness for capability results

Exit condition:

~~~text
Orpheus
  -> scans the device
  -> reports capabilities
  -> does not modify device state
~~~

## Phase 2 — File preservation

Goal: reliably preserve user-accessible files.

- [x] User-selected directories
- [x] Media/file enumeration
- [x] Large-file streaming
- [x] Checksums
- [x] Archive writer
- [x] Manifest generation
- [x] Archive integrity verification
- [x] Interrupted-backup recovery
- [x] Storage estimation

Exit condition:

~~~text
select files
  -> archive
  -> close archive
  -> reopen archive
  -> verify every artifact
~~~

## Phase 3 — Package preservation

Goal: reconstruct the installed application set as far as Android permits.

- [x] Installed-package inventory
- [x] Package/version/signature metadata
- [x] Obtainable APK handling
- [x] Split APK handling
- [x] Package checksum verification
- [x] Runtime permission inventory where obtainable
- [x] Package restoration metadata
- [x] Unsupported-package reporting

Exit condition: Orpheus can produce a verified description of the application installation set and preserve obtainable package artifacts.

## Phase 4 — Shizuku integration

Goal: make Android-only operation substantially more capable.

- [x] Detect Shizuku/Sui binder availability and track binder death/reconnection
- [x] Detect reported Shizuku server UID as shell/ADB, root, or unknown
- [x] Request/check Shizuku authorization before backend calls
- [x] Implement an isolated Shizuku UserService with explicit cleanup
- [x] Add an initial read-only shell diagnostics probe using allow-listed `id` and `getprop` commands
- [x] Record backend-specific capability states and gate diagnostic availability on a successful probe
- [x] Add a read-only Shizuku fallback for PackageManager-reported APK paths under `/data/app`
- [x] Add an opt-in, allow-listed read-only system-settings snapshot with per-key read status and explicit partial coverage
- [ ] Add further preservation-domain-specific shell-backed collectors
- [ ] Test across multiple Android versions and actual Shizuku/Sui configurations where possible

Exit condition: all operations using Shizuku are capability-gated and degrade cleanly when Shizuku is absent or insufficiently privileged.

## Phase 5 — Personal data

Goal: preserve the user's own structured data through supported Android interfaces.

Initial targets:

- [ ] Contacts
- [ ] SMS
- [ ] Call history
- [ ] Calendars
- [ ] Other high-value user data with a stable public interface

For each domain:

- [ ] collector
- [ ] normalized artifact schema
- [ ] export
- [ ] restore
- [ ] post-restore verification
- [ ] documented limitations

Exit condition: each supported domain has an explicit completeness contract rather than an undocumented best effort.

## Phase 6 — Prepare workflow

Goal: turn the individual collectors into one safe pre-unlock workflow.

~~~text
SCAN
  ↓
SELECT
  ↓
ESTIMATE
  ↓
BACKUP
  ↓
VERIFY
  ↓
REVIEW LOSSES
  ↓
READY
~~~

Features:

- [ ] Guided preparation flow
- [ ] Preservation summary
- [ ] Unresolved-item list
- [ ] Archive integrity gate
- [ ] "Ready for destructive operation" state
- [ ] Optional external-copy recommendation
- [ ] Recovery instructions

Exit condition: a user can perform a complete verified preparation without needing to understand Orpheus internals.

## Phase 7 — Fresh-device restoration

Goal: restore onto a wiped/reinstalled Android environment.

~~~text
IMPORT ARCHIVE
  ↓
SCAN TARGET
  ↓
GENERATE RESTORE PLAN
  ↓
REVIEW
  ↓
RESTORE
  ↓
VERIFY
  ↓
REPORT
~~~

- [ ] Archive import
- [ ] Target capability scan
- [ ] Restore plan generation
- [ ] Package installation
- [ ] File restoration
- [ ] Personal-data restoration
- [ ] Partial/failed result handling
- [ ] Post-restore verification
- [ ] Final recovery report

Exit condition: a supported device can be substantially reconstructed after a wipe using only the phone and required Android-side tooling.

## Phase 8 — Root backend

Goal: expand the preservation surface for users who already have root.

- [ ] Root detection
- [ ] Root capability provider
- [ ] Additional protected-data collectors where legitimately accessible
- [ ] Deeper application-data preservation
- [ ] Root-aware restoration
- [ ] Root-only capability documentation

This phase must not change the baseline archive semantics.

## Phase 9 — Termux / CLI

Goal: make Orpheus scriptable and useful to advanced Android users.

Example commands:

~~~text
orpheus scan
orpheus backup
orpheus verify backup.orpheus
orpheus inspect backup.orpheus
orpheus restore backup.orpheus
~~~

- [ ] CLI interface
- [ ] Non-interactive mode
- [ ] JSON output
- [ ] Shell-friendly exit codes
- [ ] Shared archive implementation
- [ ] Termux packaging/documentation

Exit condition: common backup and inspection tasks can be scripted from Termux.

## Phase 10 — Desktop companion

Optional future phase.

- [ ] Desktop transport/backup target
- [ ] ADB integration
- [ ] Large-archive management
- [ ] Cross-device migration
- [ ] Desktop restore tooling

A desktop companion should extend Orpheus rather than become a prerequisite for the Android workflow.

## Phase 11 — Device/OEM adapters

Only add an adapter when device-specific behavior provides real value.

Potential areas:

- Samsung
- Pixel
- Xiaomi
- OnePlus
- other OEM-specific package/storage behavior

Adapters must remain optional and isolated from the generic backup format.

## Quality gates

Before calling the project safe for real-world backup use:

- [ ] Every backup artifact has a checksum or documented verification method
- [ ] Incomplete backups are visibly marked
- [ ] Corruption tests exist
- [ ] Interrupted operations have a defined recovery path
- [ ] Restoration failures do not silently continue
- [ ] Destructive-adjacent actions require explicit confirmation
- [ ] Sensitive archives are encrypted by default and explicitly warned about when encryption is skipped; encrypted archives are authenticated
- [ ] Tests cover at least one normal Android configuration and one Shizuku-enabled configuration
- [ ] Documentation clearly distinguishes tested behavior from theoretical capability

## Explicitly not a milestone

Orpheus will not have a milestone named "bypass factory reset" or "unlock without wiping."

The project's success criterion is instead:

> Given a destructive operation that cannot be avoided, preserve and reconstruct the maximum amount of user-controlled state that Android and the device legitimately permit.
