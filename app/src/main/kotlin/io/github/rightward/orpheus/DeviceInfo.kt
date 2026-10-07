package io.github.rightward.orpheus

import android.os.Build

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val androidRelease: String,
    val apiLevel: Int,
    val securityPatch: String,
    val supportedAbis: List<String>
) {
    companion object {
        fun read(): DeviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            supportedAbis = Build.SUPPORTED_ABIS.toList()
        )
    }
}
