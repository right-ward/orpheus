package io.github.rightward.orpheus

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val logger = AndroidLogger()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var selectionText: TextView
    private lateinit var estimateText: TextView

    private val sessionStore by lazy { BackupSessionStore(applicationContext) }
    private val selectedTrees = ArrayList<SelectedTree>()

    private var lastEstimate: EstimateResult? = null

    private val backgroundColor by lazy { getColor(R.color.orpheus_bg) }
    private val surfaceColor by lazy { getColor(R.color.orpheus_surface) }
    private val surfaceStrongColor by lazy { getColor(R.color.orpheus_surface_strong) }
    private val accentColor by lazy { getColor(R.color.orpheus_accent) }
    private val buttonColor by lazy { getColor(R.color.orpheus_button) }
    private val primaryTextColor by lazy { getColor(R.color.orpheus_text_primary) }
    private val secondaryTextColor by lazy { getColor(R.color.orpheus_text_secondary) }
    private val mutedTextColor by lazy { getColor(R.color.orpheus_text_muted) }
    private val successColor by lazy { getColor(R.color.orpheus_success) }
    private val warningColor by lazy { getColor(R.color.orpheus_warning) }
    private val errorColor by lazy { getColor(R.color.orpheus_error) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setBackgroundColor(backgroundColor)
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(backgroundColor)
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

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data?.data == null) return

        when (requestCode) {
            REQUEST_TREE -> handleTreeSelected(data)
            REQUEST_CREATE_ARCHIVE -> startBackup(data.data!!)
        }
    }

    private fun render() {
        root.removeAllViews()

        root.addView(
            makeHeader(
                title = "Orpheus",
                subtitle = "Android preservation · Phase 2"
            )
        )

        addSpacer(12)

        addCard {
            addSectionTitle("FILE PRESERVATION")
            addBody(
                "Select folders to preserve. Orpheus enumerates them through Android's " +
                    "user-mediated document access and streams their contents into a " +
                    "verified .orpheus archive."
            )

            addSpacer(8)

            selectionText = addBody(
                selectionSummary(),
                primary = true
            )

            estimateText = addBody(
                estimateSummary(),
                primary = false
            )

            addSpacer(8)

            val selectButton = makeButton("Select folder") {
                openFolderPicker()
            }
            addView(selectButton)

            val backupButton = makeButton("Create verified backup") {
                openArchiveCreator()
            }.apply {
                isEnabled = selectedTrees.isNotEmpty()
                alpha = if (isEnabled) 1.0f else 0.45f
            }
            addView(backupButton)

            addSpacer(4)

            statusText = addBody(
                backupStatusSummary(),
                primary = true
            )
            applyStatusColor(statusText, backupStatusColor())
        }

        addSpacer(12)

        addCard {
            addSectionTitle("LAST BACKUP")
            val session = sessionStore.read()
            if (session == null) {
                addBody("No backup session recorded.", primary = false)
            } else {
                addBody(
                    sessionStateText(session),
                    primary = true
                )

                session.message?.let {
                    addBody(it, primary = false)
                }

                addSpacer(4)

                addView(
                    makeButton("Verify last archive") {
                        verifyLastArchive(session.outputUri)
                    }.apply {
                        isEnabled = session.state != BackupSessionState.RUNNING
                        alpha = if (isEnabled) 1.0f else 0.45f
                    }
                )

                addView(
                    makeSecondaryButton("Forget session") {
                        sessionStore.clear()
                        render()
                    }
                )
            }
        }

        addSpacer(12)

        addCard {
            addSectionTitle("DEVICE")
            val device = DeviceInfo.read()
            addKeyValue("Manufacturer", device.manufacturer)
            addKeyValue("Model", device.model)
            addKeyValue("Android", device.androidRelease + " · API " + device.apiLevel)
            addKeyValue("Security patch", device.securityPatch)
            addKeyValue("ABIs", device.supportedAbis.joinToString())
        }

        addSpacer(12)

        addCard {
            addSectionTitle("STORAGE")
            StorageInfo.read(this@MainActivity).forEach { storage ->
                addKeyValue(
                    storage.label,
                    formatBytes(storage.availableBytes) + " available / " +
                        formatBytes(storage.totalBytes) + " total"
                )
            }
        }

        addSpacer(12)

        addCard {
            addSectionTitle("CAPABILITIES")
            CapabilityScanner().scan().capabilities.forEach { capability ->
                val line = addBody(
                    capability.id.displayName +
                        " · " +
                        capability.availability.name.lowercase(Locale.US),
                    primary = true
                )
                line.setTextColor(
                    when (capability.availability) {
                        CapabilityAvailability.AVAILABLE -> successColor
                        CapabilityAvailability.LIMITED -> warningColor
                        CapabilityAvailability.UNAVAILABLE,
                        CapabilityAvailability.NOT_IMPLEMENTED,
                        CapabilityAvailability.NOT_TESTED -> mutedTextColor
                    }
                )
                addBody(capability.detail, primary = false)
            }
        }
    }

    private fun makeHeader(title: String, subtitle: String): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = roundedBackground(surfaceStrongColor, 18)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 30f
            setTextColor(primaryTextColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 14f
            setTextColor(accentColor)
            setPadding(0, dp(4), 0, 0)
        }

        container.addView(titleView)
        container.addView(subtitleView)
        return container
    }

    private fun addCard(content: LinearLayout.() -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedBackground(surfaceColor, 16)
            content()
        }

        root.addView(card)
    }

    private fun LinearLayout.addSectionTitle(value: String) {
        addView(
            TextView(this@MainActivity).apply {
                text = value
                textSize = 13f
                letterSpacing = 0.08f
                setTextColor(accentColor)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        )
    }

    private fun LinearLayout.addKeyValue(label: String, value: String) {
        addView(
            TextView(this@MainActivity).apply {
                text = label
                textSize = 12f
                setTextColor(mutedTextColor)
                setPadding(0, dp(10), 0, 0)
            }
        )
        addView(
            TextView(this@MainActivity).apply {
                text = value
                textSize = 15f
                setTextColor(primaryTextColor)
                setPadding(0, dp(2), 0, 0)
            }
        )
    }

    private fun LinearLayout.addBody(
        value: String,
        primary: Boolean = true
    ): TextView {
        val view = TextView(this@MainActivity).apply {
            text = value
            textSize = if (primary) 14f else 13f
            setTextColor(if (primary) primaryTextColor else secondaryTextColor)
            setPadding(0, dp(7), 0, dp(0))
        }
        addView(view)
        return view
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            background = roundedBackground(buttonColor, 12)
            stateListAnimator = null
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(48)
            setOnClickListener { action() }
        }

    private fun makeSecondaryButton(
        label: String,
        action: () -> Unit
    ): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(primaryTextColor)
            background = roundedBackground(surfaceStrongColor, 12)
            stateListAnimator = null
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(48)
            setOnClickListener { action() }
        }

    private fun roundedBackground(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun openFolderPicker() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            },
            REQUEST_TREE
        )
    }

    private fun handleTreeSelected(data: Intent) {
        val uri = data.data ?: return
        val readPermission = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION

        runCatching {
            if (readPermission != 0) {
                contentResolver.takePersistableUriPermission(uri, readPermission)
            }
        }.onFailure {
            logger.warn("Persistable tree permission was not granted")
        }

        if (selectedTrees.any { it.uri == uri }) {
            setStatus("Folder already selected.", warningColor)
            return
        }

        val label = getTreeLabel(uri)
        selectedTrees += SelectedTree(uri, label)
        lastEstimate = null
        render()
        estimateSelections()
    }

    private fun getTreeLabel(uri: Uri): String {
        return runCatching {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                documentId
            )
            contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            } ?: "Selected folder"
        }.getOrDefault("Selected folder")
    }

    private fun estimateSelections() {
        if (selectedTrees.isEmpty()) return

        setStatus("Scanning selected folders…", warningColor)

        executor.submit {
            try {
                var files = 0L
                var knownBytes = 0L
                var unknown = 0L

                selectedTrees.forEach { selected ->
                    val result = DocumentTreeWalker(contentResolver)
                        .estimate(selected.uri)
                    files += result.files
                    knownBytes += result.knownBytes
                    unknown += result.unknownSizeFiles
                }

                val estimate = EstimateResult(files, knownBytes, unknown)
                lastEstimate = estimate

                runOnUiThread {
                    selectionText.text = selectionSummary()
                    estimateText.text = estimateSummary()
                    setStatus(
                        "Selection scanned. Ready to create an archive.",
                        successColor
                    )
                }
            } catch (error: Exception) {
                runOnUiThread {
                    setStatus(
                        "Selection scan failed: " +
                            (error.message ?: error.javaClass.simpleName),
                        errorColor
                    )
                }
            }
        }
    }

    private fun openArchiveCreator() {
        if (selectedTrees.isEmpty()) {
            setStatus("Select at least one folder first.", warningColor)
            return
        }

        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, "orpheus-backup.orpheus")
            },
            REQUEST_CREATE_ARCHIVE
        )
    }

    private fun startBackup(outputUri: Uri) {
        val roots = selectedTrees.toList()
        sessionStore.markRunning(outputUri.toString())

        render()
        setStatus("Creating archive…", warningColor)

        executor.submit {
            try {
                val device = DeviceInfo.read()
                val capabilities = CapabilityScanner().scan()

                val writer = ArchiveWriter(
                    resolver = contentResolver,
                    deviceInfo = device,
                    capabilities = capabilities
                )

                val progress = object : BackupProgressListener {
                    override fun onFileStarted(path: String) {
                        runOnUiThread {
                            statusText.text = "Backing up " + path
                        }
                    }

                    override fun onBytesProcessed(
                        delta: Long,
                        totalBytes: Long
                    ) {
                        if (totalBytes % (2L * 1024L * 1024L) == 0L) {
                            runOnUiThread {
                                statusText.text =
                                    "Writing " + formatBytes(totalBytes)
                            }
                        }
                    }
                }

                writer.write(outputUri, roots, progress)

                val verification = ArchiveVerifier(contentResolver)
                    .verify(outputUri)

                if (!verification.valid) {
                    sessionStore.markIncomplete(
                        verification.error ?: "Archive verification failed."
                    )
                    runOnUiThread {
                        render()
                        setStatus(
                            "Archive created, but verification failed.",
                            errorColor
                        )
                    }
                    return@submit
                }

                sessionStore.markVerified()

                runOnUiThread {
                    render()
                    setStatus(
                        "Backup verified: " +
                            verification.artifactCount +
                            " artifacts, " +
                            formatBytes(verification.verifiedBytes) +
                            " checked.",
                        if (verification.failedArtifacts == 0) {
                            successColor
                        } else {
                            warningColor
                        }
                    )
                }
            } catch (error: Exception) {
                sessionStore.markIncomplete(
                    error.message ?: error.javaClass.simpleName
                )
                runOnUiThread {
                    render()
                    setStatus(
                        "Backup did not finish: " +
                            (error.message ?: error.javaClass.simpleName),
                        errorColor
                    )
                }
            }
        }
    }

    private fun verifyLastArchive(outputUri: String) {
        val uri = Uri.parse(outputUri)
        setStatus("Verifying archive…", warningColor)

        executor.submit {
            val result = ArchiveVerifier(contentResolver).verify(uri)

            runOnUiThread {
                if (result.valid) {
                    sessionStore.markVerified()
                    setStatus(
                        "Archive integrity: PASS · " +
                            result.artifactCount +
                            " artifacts · " +
                            formatBytes(result.verifiedBytes),
                        if (result.failedArtifacts == 0) {
                            successColor
                        } else {
                            warningColor
                        }
                    )
                } else {
                    sessionStore.markIncomplete(
                        result.error ?: "Archive verification failed."
                    )
                    setStatus(
                        "Archive integrity: FAIL · " +
                            (result.error ?: "unknown error"),
                        errorColor
                    )
                }
                renderStatusOnly()
            }
        }
    }

    private fun renderStatusOnly() {
        val session = sessionStore.read() ?: return
        statusText.text = backupStatusSummary()
        applyStatusColor(statusText, backupStatusColor())

        if (session.state == BackupSessionState.VERIFIED) {
            lastEstimate?.let { estimateText.text = estimateSummary() }
        }
    }

    private fun setStatus(value: String, color: Int) {
        if (!::statusText.isInitialized) return
        statusText.text = value
        statusText.setTextColor(color)
    }

    private fun backupStatusSummary(): String {
        val session = sessionStore.read()
        return when (session?.state) {
            BackupSessionState.RUNNING ->
                "A backup is in progress or was interrupted. It is not valid until verification passes."
            BackupSessionState.INCOMPLETE ->
                "The previous backup is incomplete and must not be treated as a valid preservation archive."
            BackupSessionState.VERIFIED ->
                "Last archive verified successfully."
            null ->
                "No backup has been created yet."
        }
    }

    private fun backupStatusColor(): Int {
        return when (sessionStore.read()?.state) {
            BackupSessionState.RUNNING -> warningColor
            BackupSessionState.INCOMPLETE -> errorColor
            BackupSessionState.VERIFIED -> successColor
            null -> secondaryTextColor
        }
    }

    private fun sessionStateText(session: BackupSession): String =
        when (session.state) {
            BackupSessionState.RUNNING ->
                "State: RUNNING · archive not yet verified"
            BackupSessionState.INCOMPLETE ->
                "State: INCOMPLETE"
            BackupSessionState.VERIFIED ->
                "State: VERIFIED"
        }

    private fun selectionSummary(): String =
        if (selectedTrees.isEmpty()) {
            "No folders selected."
        } else {
            selectedTrees.joinToString("\n") { "• " + it.label }
        }

    private fun estimateSummary(): String {
        val estimate = lastEstimate ?: return "Estimate: not scanned yet."

        val unknownSuffix = if (estimate.hasUnknownSizes) {
            " · " + estimate.unknownSizeFiles +
                " file sizes unknown"
        } else {
            ""
        }

        return "Estimated content: " +
            estimate.files +
            " files · " +
            formatBytes(estimate.knownBytes) +
            unknownSuffix
    }

    private fun applyStatusColor(view: TextView, color: Int) {
        view.setTextColor(color)
    }

    private fun addSpacer(dp: Int) {
        root.addView(
            View(this),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(dp)
            )
        )
    }

    private fun formatBytes(bytes: Long): String =
        ByteFormatter.format(bytes)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val REQUEST_TREE = 1001
        private const val REQUEST_CREATE_ARCHIVE = 1002
    }
}
