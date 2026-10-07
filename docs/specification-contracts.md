# Orpheus Specification Contracts

## Status

These contracts lock the remaining theory decisions for the initial architecture. They define behavior and interfaces at the conceptual level; implementation details may vary as long as they preserve these semantics.

## Artifact model

An artifact is one preservable unit known to Orpheus.

Examples include files, APKs, split APK sets, package metadata, contacts, SMS, call history, calendars, and application exports.

Every artifact should conceptually contain:

- stable archive-local identifier
- domain/type
- origin/provenance where meaningful
- source reference where meaningful
- completeness/status
- size where meaningful
- integrity information where meaningful
- restoration requirements
- dependencies where meaningful
- additional domain metadata where needed
- safe human-readable description where useful

The artifact model is structured rather than minimal so that collection, verification, diagnostics, and restoration can share the same contract.

Artifact collection status values are:

- `restorable`: collected completely and sufficient information exists for supported restoration.
- `partial`: only part of the original information is represented.
- `requires_privilege`: stronger supported privilege may permit collection.
- `unavailable`: no supported collection path is exposed by the current platform/device.
- `failed`: collection was attempted but did not complete successfully.

Orpheus must distinguish platform limitations from implementation state. In particular, `not_yet_implemented` and `not_tested` must never be silently represented as `unavailable`.

## Capability model

Internally, capabilities use a provider-based model.

Each backend exposes individual operations/capabilities rather than only declaring a global privilege level. Initial providers are:

- Normal Android
- Shizuku/ADB
- future root

A capability is therefore something such as:

```text
read_user_files
inventory_packages
extract_apk
read_contacts
restore_packages
```

Providers advertise which operations they can perform and at what effective privilege.

The application may expose a simpler structured capability snapshot to the UI and archive, for example:

```text
backend = shizuku
privilege = shell

files = full
packages = full
apk_extraction = partial
private_app_data = none
```

The structured snapshot is a representation of the provider results, not the internal capability model itself.

No provider is assumed to equal root. Actual device permissions and policy determine the available capability set.

## Backup lifecycle

The backup engine uses an explicit lifecycle:

```text
DRAFT
  ↓
RUNNING
  ↓
FINALIZING
  ↓
VERIFIED
  ↓
READY
```

Exceptional terminal or recoverable states include:

- `CANCELLED`
- `FAILED`
- `INCOMPLETE`

An in-progress or incomplete backup is never presented as a valid final archive.

The working state may record completed, pending, and failed artifacts so an interrupted operation can later be resumed where practical.

Verification is a real lifecycle transition, not merely a progress message. A backup becomes `READY` only after finalization and the required integrity checks succeed.

## Restore-plan semantics

Restoration is selective and plan-based.

Before changing the target device, Orpheus generates a plan from:

- archive artifacts
- artifact requirements
- target capabilities
- Android version
- installed packages
- available storage
- other relevant target constraints

Each planned action is classified as one of:

- `restore`
- `restore_partial`
- `requires_privilege`
- `requires_user_action`
- `unsupported`
- `failed`

Independent actions should not be allowed to fail silently because another action failed.

The user should be able to review the plan before execution and see why an action cannot be automated.

## Known limitations

Limitations are represented both in human-readable documentation and, where useful, as structured records attached to capabilities or artifacts.

The vocabulary should distinguish at least:

- `known_unavailable`: the platform/device exposes no supported route.
- `known_requires_privilege`: stronger supported privilege may provide a route.
- `partial_by_design`: the supported representation intentionally cannot contain everything.
- `not_yet_implemented`: Orpheus has not implemented the capability.
- `not_tested`: the capability may exist but the current implementation has not established reliable behavior.

This prevents implementation gaps from being misrepresented as Android limitations.

## Cryptography model

The MVP uses whole-archive encryption rather than independently encrypting each artifact.

Conceptually:

```text
user password/key
        ↓
password-based key derivation
        ↓
archive encryption key
        ↓
authenticated whole-archive encryption
```

The exact KDF, AEAD construction, parameters, and envelope encoding remain implementation decisions that must be reviewed before release.

Encryption is optional at the product level. When sensitive data is selected, Orpheus should actively ask the user to enable encryption. If the user explicitly skips it, the application must show a strong warning that the portable archive can expose private data.

The archive must never contain the secret required to decrypt itself.

The encryption envelope must be versioned and carry the parameters required for future readers to interpret it.

External key sources may be added later without changing the logical artifact model.

## Destructive-operation readiness

Readiness is risk-based but has a mandatory integrity gate.

The conceptual flow is:

```text
NOT READY
    ↓
VERIFIED
    ↓
REVIEW LOSSES
    ↓
READY
or
READY WITH WARNINGS
```

The application must never report `READY` or `READY WITH WARNINGS` for an archive that failed finalization or required integrity verification.

Legitimate platform limitations do not automatically block readiness. Instead, known losses are presented explicitly and can lead to `READY WITH WARNINGS`.

Examples of conditions that must block readiness include:

- archive cannot be reopened
- manifest is structurally invalid
- required checksums fail
- backup remains incomplete when completeness is required for the selected preservation set
- required verification cannot be completed

Warnings may include:

- artifacts marked partial
- artifacts known to be unavailable
- artifacts requiring privilege that is not currently available
- encryption deliberately skipped for sensitive data
- recommendation to place a copy somewhere other than the device about to be wiped

The final readiness decision must require explicit user acknowledgement of material warnings.

## Design intent

These contracts preserve the project's central distinction:

> Orpheus does not try to defeat the wipe. It makes the wipe survivable.

The system should maximize recoverable, user-controlled state without pretending that Android permits a complete clone.
