# Shizuku backend

## User setup

Orpheus does not install or start Shizuku/Sui. Install and start either service separately, then return to Orpheus. On non-rooted Android 11 and later, Shizuku can be started with Android Wireless debugging. On other configurations, the required start method depends on the device and whether root is already available.

Official setup guide: https://shizuku.rikka.app/

## Detection and permission

Orpheus tracks the live Shizuku binder, whether the server API supports UserService (v11 or later), the permission granted specifically to Orpheus, and the server-reported UID. A missing binder is reported as not tested, not as a platform limitation. The app requests permission only when the user starts a Shizuku operation.

UID 2000 is classified as shell/ADB and UID 0 as root. Any other or unavailable UID is reported as unknown. Shizuku running under root does not mean Orpheus's separate root backend is implemented.

## Initial operation

The first privileged operation is a short, read-only diagnostics probe in an isolated Shizuku UserService. It runs only:

- `/system/bin/id`
- `/system/bin/getprop ro.build.version.sdk`
- `/system/bin/getprop ro.build.version.release`

The process uses fixed argument arrays (no shell-string interpolation), bounded command waits, and explicit errors for timeout, command failure, missing output, permission denial, or service disconnection. Orpheus verifies that the UserService's UID matches the UID reported by Shizuku before considering the probe successful. After a successful probe, the service remains bound for the activity lifecycle so package preservation can use the narrow APK reader; it is unbound and stopped on activity destruction, probe failure, or service disconnection.

The probe does not change device state, expose arbitrary command execution, read private application data, or prove that unrelated shell operations are permitted. Actual capabilities remain operation-specific and must be checked by each collector.

## APK read fallback

After a successful probe, package preservation can supply a Shizuku-backed reader to the existing APK archiver. The app first tries to open each PackageManager-reported base/split APK in its own process. Only when that fails does it request a read-only file descriptor from the UserService.

The UserService canonicalizes the requested path and only opens regular files contained below `/data/app/`. This matches ordinary user applications and updated system apps stored in the standard app-install directory. Other locations, including adopted-storage app paths, are not allowed by this initial fallback. The archive continues streaming bytes directly into the ZIP writer and records the actual size, SHA-256, source provider, and per-APK result. The fallback never accepts shell command text or paths outside the allow-list.

APK access is tested per source path. A successful read does not mean every APK is accessible, does not include unchanged system APKs, and does not grant access to private application data. The aggregate APK-preservation capability remains limited; the Shizuku fallback capability changes from not tested to available only after at least one fallback read succeeds. Failed fallback attempts remain explicit failed artifacts.

The service exposes one additional operation: open a read-only file descriptor for a canonical regular file with an `.apk` extension under `/data/app/`. Paths outside this root and non-APK files are rejected; the interface does not accept shell commands or expose arbitrary file reads. The UserService version is incremented when its interface changes so an older bound implementation is not reused.

## Capability states

- Shizuku absent or stopped: backend is `not_tested`; no privileged operation is attempted.
- Server API older than v11: UserService capabilities are `unavailable`.
- Binder connected but Orpheus permission missing: backend is `limited`.
- Permission granted but diagnostics not yet run: diagnostics are `not_tested`.
- Probe succeeds: the read-only diagnostics capability is `available`.
- Probe fails: diagnostics are `limited`, and the failure is shown explicitly.

## Known limitations

This implementation establishes the detection, permission, privilege classification, an isolated read-only diagnostics probe, and an APK-read fallback. Further preservation-domain-specific collectors and a multi-device test matrix remain open Phase 4 work.
