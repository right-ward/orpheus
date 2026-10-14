# Contacts export

## User consent and privacy

Contacts export is disabled by default. Enabling it first presents a warning that contact details are sensitive and the current Orpheus archive format is not encrypted. The user must continue through that warning and grant Android's runtime `READ_CONTACTS` permission.

The collector uses the public Android Contacts Provider. It never writes to the provider and does not request write permission. Revoked permission or provider failures are recorded as an explicit failed artifact; unrelated backup artifacts remain available.

Do not place an archive containing contact details in a public or untrusted location. Because archives are not encrypted in this release, the exported JSON can be read by anyone who obtains the file.

## Schema and archive layout

The snapshot is written to `data/personal/contacts.json` and described in the archive manifest as a `contacts_snapshot` artifact. The JSON schema identifier is `orpheus.android.contacts.snapshot`, schema version `1`.

The snapshot contains a collection timestamp, source identifier, coverage declaration, `restore_supported: false`, limitations, contact and field counts, and normalized contact records. Records contain a display name when available and a list of typed fields. Provider contact IDs and raw-contact IDs are not exported.

## Included data types

The current allow-list includes:

- structured display name
- phone numbers
- email addresses
- postal addresses
- organization and title
- nicknames
- dates/events
- websites
- relations

Fields retain their value and available type/custom-label metadata. Records and fields are normalized into deterministic order before encoding.

## Explicit omissions and limitations

This is not a complete Contacts Provider database image. Contact photos, notes, account/sync metadata, raw-contact identifiers, unsupported MIME types, contact-group membership, and provider-specific fields are omitted. Aggregated contacts may combine data from multiple raw contacts, and provider/OEM behavior can affect which fields are exposed.

The artifact is always marked `partial` and has a SHA-256 digest that the archive verifier checks. This release exports contacts only; it does not restore them and does not perform post-restore verification. Do not interpret a verified archive as proof that every original contact field was captured.
