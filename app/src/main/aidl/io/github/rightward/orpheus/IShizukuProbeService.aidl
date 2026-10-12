package io.github.rightward.orpheus;

import android.os.ParcelFileDescriptor;

interface IShizukuProbeService {
    void destroy() = 16777114;
    String collectReadOnlyDiagnostics() = 1;
    ParcelFileDescriptor openPackageApk(String absolutePath) = 2;
    String collectAllowlistedSystemSettings() = 3;
}
