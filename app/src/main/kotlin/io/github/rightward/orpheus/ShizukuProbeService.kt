package io.github.rightward.orpheus

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.annotation.Keep
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

@Keep
class ShizukuProbeService @Keep constructor() : IShizukuProbeService.Stub() {
    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun collectReadOnlyDiagnostics(): String {
        val identity = runReadOnlyCommand(listOf("/system/bin/id"))
        val sdk = runReadOnlyCommand(
            listOf("/system/bin/getprop", "ro.build.version.sdk")
        )
        val release = runReadOnlyCommand(
            listOf("/system/bin/getprop", "ro.build.version.release")
        )

        return JSONObject()
            .put("uid", Os.getuid())
            .put("pid", Os.getpid())
            .put("identity", identity)
            .put("android_sdk", sdk.toIntOrNull() ?: -1)
            .put("android_release", release)
            .toString()
    }

    override fun openPackageApk(absolutePath: String): ParcelFileDescriptor {
        val apkRoot = File(APK_SOURCE_ROOT).canonicalFile
        val candidate = File(absolutePath).canonicalFile
        val allowedPrefix = apkRoot.path + File.separator

        if (!candidate.path.startsWith(allowedPrefix)) {
            throw SecurityException(
                "Only PackageManager APK paths under /data/app may be opened."
            )
        }
        if (!candidate.isFile) {
            throw FileNotFoundException("The package APK is not a regular file.")
        }

        return ParcelFileDescriptor.open(
            candidate,
            ParcelFileDescriptor.MODE_READ_ONLY
        )
    }

    override fun destroy() {
        exitProcess(0)
    }

    private fun runReadOnlyCommand(command: List<String>): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = ByteArrayOutputStream()
        val reader = Thread(
            {
                process.inputStream.use { input ->
                    input.copyTo(output)
                }
            },
            "orpheus-shizuku-probe-output"
        ).apply {
            isDaemon = true
            start()
        }

        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            reader.join(OUTPUT_DRAIN_TIMEOUT_MILLIS)
            throw IOException("Read-only command timed out: " + command.first())
        }

        reader.join(OUTPUT_DRAIN_TIMEOUT_MILLIS)
        if (reader.isAlive) {
            process.destroyForcibly()
            throw IOException("Could not collect command output: " + command.first())
        }

        val result = output.toString("UTF-8").trim()
        if (process.exitValue() != 0) {
            throw IOException("Read-only command failed: " + command.first())
        }
        if (result.isEmpty()) {
            throw IOException("Read-only command returned no output: " + command.first())
        }
        return result
    }

    private companion object {
        const val APK_SOURCE_ROOT = "/data/app"
        const val COMMAND_TIMEOUT_SECONDS = 5L
        const val OUTPUT_DRAIN_TIMEOUT_MILLIS = 500L
    }
}
