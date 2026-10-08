package io.github.rightward.orpheus

import org.json.JSONArray
import org.json.JSONObject

data class ArchiveSource(
    val androidApi: Int,
    val androidRelease: String,
    val manufacturer: String,
    val model: String
)

data class ArchiveManifest(
    val archiveId: String,
    val createdAt: String,
    val source: ArchiveSource,
    val selectedRoots: List<String>,
    val artifacts: List<ArchiveArtifact>,
    val selectionErrors: List<String>
)

object ArchiveManifestCodec {
    const val FORMAT = "orpheus"
    const val FORMAT_VERSION = 1

    fun encode(manifest: ArchiveManifest): String {
        val rootArray = JSONArray()
        manifest.selectedRoots.forEach { rootArray.put(it) }

        val artifacts = JSONArray()
        manifest.artifacts.forEach { artifact ->
            val item = JSONObject()
                .put("id", artifact.id)
                .put("kind", artifact.kind)
                .put("path", artifact.archivePath)
                .put("source", artifact.source)
                .put("status", artifact.status.name.lowercase())
                .put("size", artifact.sizeBytes)

            artifact.sha256?.let { item.put("sha256", it) }
            artifact.modifiedAtEpochMs?.let {
                item.put("modified_at_epoch_ms", it)
            }
            artifact.errorCode?.let { item.put("error_code", it) }

            artifacts.put(item)
        }

        val errors = JSONArray()
        manifest.selectionErrors.forEach { errors.put(it) }

        return JSONObject()
            .put("format", FORMAT)
            .put("format_version", FORMAT_VERSION)
            .put("archive_id", manifest.archiveId)
            .put("created_at", manifest.createdAt)
            .put(
                "source",
                JSONObject()
                    .put("android_api", manifest.source.androidApi)
                    .put("android_release", manifest.source.androidRelease)
                    .put("manufacturer", manifest.source.manufacturer)
                    .put("model", manifest.source.model)
            )
            .put("selected_roots", rootArray)
            .put("artifacts", artifacts)
            .put("selection_errors", errors)
            .toString(2)
    }

    fun decode(json: String): ArchiveManifest {
        val root = JSONObject(json)

        require(root.getString("format") == FORMAT) {
            "Unsupported archive format"
        }
        require(root.getInt("format_version") == FORMAT_VERSION) {
            "Unsupported archive format version"
        }

        val sourceJson = root.getJSONObject("source")
        val rootsJson = root.getJSONArray("selected_roots")
        val artifactsJson = root.getJSONArray("artifacts")
        val errorsJson = root.getJSONArray("selection_errors")

        val roots = buildList(rootsJson.length()) {
            for (index in 0 until rootsJson.length()) {
                add(rootsJson.getString(index))
            }
        }

        val artifacts = buildList(artifactsJson.length()) {
            for (index in 0 until artifactsJson.length()) {
                val item = artifactsJson.getJSONObject(index)
                add(
                    ArchiveArtifact(
                        id = item.getString("id"),
                        kind = item.getString("kind"),
                        archivePath = item.getString("path"),
                        source = item.getString("source"),
                        status = ArtifactStatus.valueOf(
                            item.getString("status").uppercase()
                        ),
                        sizeBytes = item.getLong("size"),
                        sha256 = item.optString("sha256").takeIf { it.isNotEmpty() },
                        modifiedAtEpochMs = if (
                            item.has("modified_at_epoch_ms") &&
                            !item.isNull("modified_at_epoch_ms")
                        ) {
                            item.getLong("modified_at_epoch_ms")
                        } else {
                            null
                        },
                        errorCode = item.optString("error_code").takeIf { it.isNotEmpty() }
                    )
                )
            }
        }

        val errors = buildList(errorsJson.length()) {
            for (index in 0 until errorsJson.length()) {
                add(errorsJson.getString(index))
            }
        }

        return ArchiveManifest(
            archiveId = root.getString("archive_id"),
            createdAt = root.getString("created_at"),
            source = ArchiveSource(
                androidApi = sourceJson.getInt("android_api"),
                androidRelease = sourceJson.getString("android_release"),
                manufacturer = sourceJson.getString("manufacturer"),
                model = sourceJson.getString("model")
            ),
            selectedRoots = roots,
            artifacts = artifacts,
            selectionErrors = errors
        )
    }
}
