package io.github.rightward.orpheus

import org.json.JSONArray
import org.json.JSONObject

object ArchiveJson {
    fun device(device: DeviceInfo): String =
        JSONObject()
            .put("manufacturer", device.manufacturer)
            .put("model", device.model)
            .put("device", device.device)
            .put("product", device.product)
            .put("android_release", device.androidRelease)
            .put("api_level", device.apiLevel)
            .put("security_patch", device.securityPatch)
            .put("supported_abis", JSONArray(device.supportedAbis))
            .toString(2)

    fun capabilities(snapshot: CapabilitySnapshot): String {
        val items = JSONArray()

        snapshot.capabilities.forEach { capability ->
            items.put(
                JSONObject()
                    .put("id", capability.id.name.lowercase())
                    .put("display_name", capability.id.displayName)
                    .put("provider", capability.provider.name.lowercase())
                    .put("availability", capability.availability.name.lowercase())
                    .put("detail", capability.detail)
            )
        }

        return JSONObject()
            .put("capabilities", items)
            .toString(2)
    }
}
