package io.github.rightward.orpheus

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val logger = AndroidLogger()
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        setContentView(
            ScrollView(this).apply {
                addView(
                    root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        )

        render()
    }

    private fun render() {
        root.removeAllViews()

        root.addView(text("Orpheus", 28f))
        root.addView(text("Phase 1 — Android skeleton", 18f))
        root.addView(text("No backup or destructive operations are implemented yet.", 14f))

        addSpacer()

        addSection("Device")
        val device = DeviceInfo.read()
        addLine("Manufacturer", device.manufacturer)
        addLine("Model", device.model)
        addLine("Device", device.device)
        addLine("Product", device.product)
        addLine("Android", "${device.androidRelease} (API ${device.apiLevel})")
        addLine("Security patch", device.securityPatch)
        addLine("ABIs", device.supportedAbis.joinToString())

        addSpacer()

        addSection("Storage")
        StorageInfo.read(this).forEach { storage ->
            addLine(
                storage.label,
                "${formatBytes(storage.availableBytes)} available / ${formatBytes(storage.totalBytes)} total"
            )
            addLine("Path", storage.path)
        }

        addSpacer()

        addSection("Capabilities")
        CapabilityScanner().scan().capabilities.forEach { capability ->
            addLine(
                capability.id.displayName,
                "${capability.availability.name}: ${capability.detail}"
            )
        }

        addSpacer()

        root.addView(
            Button(this).apply {
                text = "Refresh scan"
                setOnClickListener {
                    logger.info("Refreshing device and capability scan")
                    render()
                }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addSection(label: String) {
        root.addView(text(label, 20f))
    }

    private fun addLine(label: String, value: String) {
        root.addView(text("$label: $value", 14f))
    }

    private fun addSpacer() {
        root.addView(
            TextView(this),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(16)
            )
        )
    }

    private fun text(value: String, sizeSp: Float): TextView =
        TextView(this).apply {
            text = value
            textSize = sizeSp
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"

        val units = arrayOf("KiB", "MiB", "GiB", "TiB")
        var value = bytes.toDouble()
        var unit = 0

        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }

        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}
