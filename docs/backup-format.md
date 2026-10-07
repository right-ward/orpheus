# Orpheus Backup Format

## Status

This document defines the initial logical specification for Orpheus archives.

It is a design contract, not a frozen binary-format specification. The physical container and serialization details may change during implementation, but the logical concepts below should remain stable.

## Goals

An Orpheus archive must be:

- self-describing
- integrity-verifiable
- versioned
- portable between Orpheus frontends
- capable of representing partial results
- extensible without breaking old readers
- suitable for encrypted storage
- useful without requiring a byte-for-byte Android filesystem image

## Archive identity

The logical archive type is:

~~~text
.orpheus
~~~

The format carries an explicit format version independent of the Orpheus application version.

Example:

~~~json
{
  "format": "orpheus",
  "format_version": 1
}
~~~

## Logical layout

The initial logical layout is:

~~~text
.orpheus
├── manifest
├── device.json
├── capabilities.json
├── files/
├── packages/
├── data/
├── exports/
└── checksums/
~~~

These are logical namespaces. The implementation may package them in a single compressed and/or encrypted container rather than creating a literal directory tree.

## Manifest

The manifest is the authoritative index of archive contents.

Minimum conceptual fields:

~~~json
{
  "format": "orpheus",
  "format_version": 1,
  "archive_id": "uuid",
  "created_at": "timestamp",
  "source": {
    "android_api": 35,
    "android_release": "..."
  },
  "artifacts": [
    {
      "id": "artifact-id",
      "kind": "file",
      "path": "files/DCIM/example.jpg",
      "source": "/storage/emulated/0/DCIM/example.jpg",
      "status": "restorable",
      "size": 123456,
      "sha256": "..."
    }
  ]
}
~~~

Exact field names remain subject to implementation review.

## Artifact model

An artifact represents one preservable unit.

Examples:

- one file
- one directory tree represented by child artifacts
- one APK
- one split APK set
- one contact collection
- one SMS export
- one call-history export
- one calendar export
- one application export
- one structured settings export

Each artifact should have:

- stable archive-local identifier
- domain/kind
- origin information where meaningful
- completeness status
- size where meaningful
- integrity information where meaningful
- restoration requirements
- optional human-readable description

## Status values

The initial status vocabulary is:

### restorable

The artifact was collected completely and the archive contains sufficient information for the supported restoration procedure.

### partial

The artifact represents only a subset of the original information.

Example: an application export that excludes protected credentials.

### requires_privilege

The artifact could not be collected with current privileges but is known to be potentially collectable under a stronger supported backend.

This is different from unavailable: Orpheus knows a stronger capability may help.

### unavailable

The current platform/device policy exposes no supported way for Orpheus to collect the artifact.

### failed

Collection was attempted but failed unexpectedly.

A failed result must retain an error classification suitable for diagnostics without leaking sensitive data into normal logs.

## Device metadata

Device metadata exists to make archives understandable and to help Orpheus generate a restoration plan.

Potential fields:

- manufacturer
- model
- device/product identifiers that are safe to store
- Android release/API level
- build fingerprint where useful
- security patch level
- locale
- supported ABIs
- storage characteristics
- Orpheus version

Sensitive or identifying values should be included only when there is a clear restoration or compatibility benefit.

## Capability snapshot

The archive should preserve the capability environment that produced it.

Example:

~~~json
{
  "backend": "shizuku",
  "privilege": "shell",
  "capabilities": {
    "files": "full",
    "packages": "full",
    "apk_extraction": "partial",
    "private_app_data": "unavailable"
  }
}
~~~

This lets a future restore process distinguish:

- the artifact was not backed up
- it could not be backed up under the old environment
- it exists in the archive but the new device lacks the privilege required to restore it

## Files

File artifacts should be streamed rather than loaded wholly into memory.

Each file should have, at minimum:

- original logical path
- archive path
- byte length
- digest
- file status
- relevant timestamps when useful and reliable

The format should not assume that Android file paths remain identical after restoration.

A restoration target is therefore a logical path plus domain-specific policy, not merely a raw filesystem path.

## Packages

Package entries should describe an installed application independently from its APK bytes.

Potential metadata:

- package name
- version code
- version name
- split names
- install source where available
- signature identity where available
- requested/granted permissions where obtainable
- APK artifact references
- application-data artifact references
- restoration constraints

The format must support packages for which no APK was obtainable.

Example:

~~~json
{
  "package_name": "example.app",
  "version_code": 123,
  "status": "partial",
  "apk": null,
  "data": null
}
~~~

This allows Orpheus to say "application was detected but cannot be reconstructed automatically" instead of silently omitting it.

## Structured personal data

Structured data should use versioned domain schemas rather than storing arbitrary Android database files as the sole representation.

Initial domains:

- contacts
- SMS
- call history
- calendars

A domain artifact should identify its schema version.

Example:

~~~text
domain = "contacts"
schema_version = 1
encoding = "..."
~~~

Raw database preservation can be an additional artifact where legitimately accessible, but restoration should prefer normalized data where possible.

## Application exports

Applications that provide their own export/import mechanisms can be represented as opaque artifacts with metadata.

The archive should record:

- source package
- export format
- exporter/application version if known
- required restore procedure
- whether Orpheus can automate the import

User-created exports may be more complete than anything Orpheus can obtain through Android system interfaces.

## Integrity

Every content-bearing artifact should have an integrity record.

The initial implementation should support at least:

~~~text
SHA-256
~~~

The archive-level integrity scheme should additionally authenticate the manifest so that an attacker cannot change the manifest while leaving payload hashes apparently valid.

Integrity and confidentiality are separate concerns:

- hashes detect corruption and can detect tampering with unencrypted content
- authenticated encryption protects confidentiality and detects tampering with encrypted material

## Encryption

Portable archives should support authenticated encryption.

The key should be controlled by the user rather than embedded in the archive.

Requirements:

- authenticated, modern AEAD construction
- salted password-based key derivation when passwords are used
- explicit KDF parameters in the encrypted envelope
- versioned encryption metadata
- no plaintext private-data index when the archive is intended to be fully confidential

The exact cryptographic primitives and parameters are an implementation decision and must be reviewed before release.

## Compression and deduplication

The logical format should permit:

- compressed content
- deduplicated content
- chunked large-file storage

The first implementation does not need to optimize all three.

Correctness, recoverability, and verifiability take priority over maximum compression.

## Resumability

An in-progress backup should not be treated as a valid final archive.

The implementation may use a temporary working format that tracks:

- completed artifacts
- failed artifacts
- pending artifacts
- checksums
- source metadata

Only after successful finalization and verification should Orpheus mark an archive as complete.

## Restoration semantics

Restoration is capability-dependent.

An archive is not a command to blindly recreate a filesystem. It is a collection of artifacts plus enough metadata for Orpheus to produce a restoration plan.

A restore plan should classify each archive artifact as:

- restore
- restore_partial
- requires_privilege
- requires_user_action
- unsupported
- failed

The target device's capabilities determine which actions are executable.

## Compatibility

A newer Orpheus release should be able to read older archive versions where practical.

The format therefore requires:

- explicit format version
- forward-compatible optional fields
- preservation of unknown fields where possible
- migrations for schema changes that alter semantics

Changing the archive schema should not silently change the meaning of existing status values.

## Privacy

The archive format must assume its contents can be highly sensitive.

Do not put sensitive user data into:

- filenames
- debug logs
- crash reports
- ordinary analytics
- unencrypted diagnostic metadata

The application should make it clear when an archive is portable and therefore potentially accessible outside Android's protected app storage.

## Example archive summary

A completed backup should be representable to the user approximately as:

~~~text
Orpheus archive
Format: 1
Created: 2026-10-08

Artifacts
  Files             18.4 GiB   restorable
  Applications       73         61 restorable / 12 partial
  Contacts           428        restorable
  SMS               6,182       restorable
  Call history      2,941       restorable
  App exports          9        6 restorable / 3 partial

Unavailable          8
Failed               0

Archive integrity: PASS
Archive encryption: ENABLED
~~~

The exact display is UI policy; the machine-readable manifest is the source of truth.

## Known limitations

The format deliberately does not promise preservation of:

- arbitrary private application data without sufficient privilege
- hardware-backed secrets
- DRM-protected state
- credentials that Android prevents from being exported
- OEM state that is not exposed through supported interfaces
- an exact clone of the original Android installation

When such state matters, Orpheus should record the limitation explicitly so the user can make an informed decision before the destructive operation.
