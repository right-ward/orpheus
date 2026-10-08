package io.github.rightward.orpheus

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale

data class SelectedTree(
    val uri: Uri,
    val label: String
)

data class TreeFile(
    val documentUri: Uri,
    val name: String,
    val relativePath: String,
    val sizeBytes: Long?,
    val modifiedAtEpochMs: Long?
)

class DocumentTreeWalker(
    private val resolver: ContentResolver
) {
    private data class TreeNode(
        val documentId: String,
        val name: String,
        val mimeType: String?,
        val sizeBytes: Long?,
        val modifiedAtEpochMs: Long?
    )

    fun walk(
        treeUri: Uri,
        onFile: (TreeFile) -> Unit
    ) {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        walkDirectory(
            treeUri = treeUri,
            parentDocumentId = rootDocumentId,
            relativeDirectory = "",
            onFile = onFile
        )
    }

    fun estimate(treeUri: Uri): EstimateResult {
        var bytes = 0L
        var files = 0L
        var unknownSizes = 0L

        walk(treeUri) { file ->
            files++
            if (file.sizeBytes == null || file.sizeBytes < 0L) {
                unknownSizes++
            } else {
                bytes += file.sizeBytes
            }
        }

        return EstimateResult(
            files = files,
            knownBytes = bytes,
            unknownSizeFiles = unknownSizes
        )
    }

    private fun walkDirectory(
        treeUri: Uri,
        parentDocumentId: String,
        relativeDirectory: String,
        onFile: (TreeFile) -> Unit
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId
        )

        queryChildren(childrenUri).forEach { child ->
            val childPath = if (relativeDirectory.isEmpty()) {
                ArchivePath.sanitizeComponent(child.name)
            } else {
                relativeDirectory + "/" + ArchivePath.sanitizeComponent(child.name)
            }

            if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                walkDirectory(
                    treeUri = treeUri,
                    parentDocumentId = child.documentId,
                    relativeDirectory = childPath,
                    onFile = onFile
                )
            } else {
                onFile(
                    TreeFile(
                        documentUri = DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            child.documentId
                        ),
                        name = child.name,
                        relativePath = childPath,
                        sizeBytes = child.sizeBytes,
                        modifiedAtEpochMs = child.modifiedAtEpochMs
                    )
                )
            }
        }
    }

    private fun queryChildren(uri: Uri): List<TreeNode> {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val result = ArrayList<TreeNode>()

        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val nameColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            val mimeColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            val sizeColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_SIZE
            )
            val modifiedColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            while (cursor.moveToNext()) {
                result += TreeNode(
                    documentId = cursor.getString(idColumn),
                    name = cursor.getString(nameColumn) ?: "unnamed",
                    mimeType = cursor.getString(mimeColumn),
                    sizeBytes = if (cursor.isNull(sizeColumn)) {
                        null
                    } else {
                        cursor.getLong(sizeColumn)
                    },
                    modifiedAtEpochMs = if (cursor.isNull(modifiedColumn)) {
                        null
                    } else {
                        cursor.getLong(modifiedColumn)
                    }
                )
            }
        } ?: error("Unable to enumerate document tree")

        return result.sortedWith(
            compareBy(
                { it.name.lowercase(Locale.US) },
                { it.name }
            )
        )
    }
}

data class EstimateResult(
    val files: Long,
    val knownBytes: Long,
    val unknownSizeFiles: Long
) {
    val hasUnknownSizes: Boolean
        get() = unknownSizeFiles > 0L
}
