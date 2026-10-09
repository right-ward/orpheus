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

The process uses fixed argument arrays (no shell-string interpolation), bounded command waits, and explicit errors for timeout, command failure, missing output, permission denial, or service disconnection. Orpheus verifies that the UserService's UID matches the UID reported by Shizuku before considering the probe successful. The service is unbound and stopped after use.

The probe does not change device state, expose arbitrary command execution, read private application data, or prove that unrelated shell operations are permitted. Actual capabilities remain operation-specific and must be checked by each future collector.

## Capability states

- Shizuku absent or stopped: backend is `not_tested`; no privileged operation is attempted.
- Server API older than v11: UserService capabilities are `unavailable`.
- Binder connected but Orpheus permission missing: backend is `limited`.
- Permission granted but diagnostics not yet run: diagnostics are `not_tested`.
- Probe succeeds: the read-only diagnostics capability is `available`.
- Probe fails: diagnostics are `limited`, and the failure is shown explicitly.

## Known limitations

This implementation establishes the detection, permission, privilege classification, and isolated read-only probe path. It does not yet contain preservation-domain-specific shell collectors or a multi-device test matrix. Those remain open Phase 4 work.
