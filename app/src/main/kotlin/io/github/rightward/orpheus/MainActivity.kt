package io.github.rightward.orpheus

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import org.json.JSONObject
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private val logger = AndroidLogger()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var selectionText: TextView
    private lateinit var estimateText: TextView
    private lateinit var packageSummaryText: TextView
    private lateinit var packageEstimateText: TextView

    private val sessionStore by lazy { BackupSessionStore(applicationContext) }
    private val selectedTrees = ArrayList<SelectedTree>()

    private var lastEstimate: EstimateResult? = null
    private var packageInventory: PackageInventory? = null
    private var packageScanInProgress = false
    private var includePackagePreservation = true
    private var includePackageContent = true
    private var preserveOnlySelectedPackages = false
    private var selectedPackageNames = linkedSetOf<String>()
    private var packageSelectionInitialized = false

    private var shizukuBackendStatus = ShizukuBackendStatus()
    private var shizukuPermissionRequestPending = false
    private var shizukuProbeInProgress = false
    private var shizukuServiceBound = false
    private var shizukuProbeService: IShizukuProbeService? = null
    private lateinit var shizukuStatusText: TextView
    private lateinit var shizukuDetailsText: TextView

    private val shizukuUserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(this, ShizukuProbeService::class.java)
        )
            .processNameSuffix("orpheus_probe")
            .tag("io.github.rightward.orpheus.read_only_probe")
            .version(1)
            .daemon(false)
    }

    private val shizukuBinderReceivedListener =
        Shizuku.OnBinderReceivedListener {
            runOnUiThread {
                refreshShizukuState()
                render()
            }
        }

    private val shizukuBinderDeadListener =
        Shizuku.OnBinderDeadListener {
            runOnUiThread {
                shizukuBackendStatus = ShizukuBackendStatus()
                shizukuPermissionRequestPending = false
                shizukuProbeInProgress = false
                shizukuServiceBound = false
                shizukuProbeService = null
                render()
            }
        }

    private val shizukuPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_SHIZUKU_PERMISSION) return@OnRequestPermissionResultListener
            shizukuPermissionRequestPending = false
            refreshShizukuState()
            render()
            setStatus(
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    "Shizuku permission granted. Run the read-only probe to verify the backend."
                } else {
                    "Shizuku permission was not granted. Privileged operations remain disabled."
                },
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    successColor
                } else {
                    warningColor
                }
            )
        }

    private val shizukuServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val remote = service?.let { IShizukuProbeService.Stub.asInterface(it) }
            if (remote == null) {
                runOnUiThread {
                    completeShizukuProbe(
                        rawResult = null,
                        failure = IllegalStateException("Shizuku probe service returned no binder.")
                    )
                }
                return
            }

            shizukuProbeService = remote
            runShizukuProbe(remote)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shizukuProbeService = null
            shizukuServiceBound = false
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (shizukuProbeInProgress) {
                    completeShizukuProbe(
                        rawResult = null,
                        failure = IllegalStateException("Shizuku probe service disconnected.")
                    )
                } else {
                    shizukuBackendStatus = shizukuBackendStatus.copy(
                        probeOutcome = ShizukuProbeOutcome.FAILED,
                        probeSummary = null,
                        probeFailure = "The Shizuku UserService disconnected."
                    )
                    render()
                }
            }
        }
    }

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

        Shizuku.addBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
        refreshShizukuState()

        render()
        scanPackages()
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuState()
        render()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
        shizukuPermissionRequestPending = false
        shizukuProbeInProgress = false
        stopShizukuProbeService()
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
                subtitle = "Android preservation · Phase 4"
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

            addView(
                makeButton("Select folder") {
                    openFolderPicker()
                }
            )
        }

        addSpacer(12)

        addCard {
            addSectionTitle("PACKAGE PRESERVATION")
            addBody(
                "Choose whether to preserve installed package metadata and the " +
                    "actual APK files. Package content means installable APKs, not " +
                    "private application data."
            )

            addSpacer(8)

            packageSummaryText = addBody(
                packageSummary(),
                primary = true
            )

            packageEstimateText = addBody(
                packageEstimateSummary(),
                primary = false
            )

            addSpacer(6)

            addView(
                Switch(this@MainActivity).apply {
                    text = "Preserve installed packages"
                    isChecked = includePackagePreservation
                    setTextColor(primaryTextColor)
                    setOnCheckedChangeListener { _, checked ->
                        includePackagePreservation = checked
                        render()
                    }
                }
            )

            addBody(
                "Includes package/version/signature metadata and runtime permission state.",
                primary = false
            )

            addSpacer(4)

            addView(
                Switch(this@MainActivity).apply {
                    text = "Preserve package content (APKs)"
                    isChecked = includePackageContent
                    isEnabled = includePackagePreservation
                    setTextColor(primaryTextColor)
                    setOnCheckedChangeListener { _, checked ->
                        includePackageContent = checked
                        render()
                    }
                }
            )

            addBody(
                "Adds obtainable base and split APK files to the archive. If normal app access fails after the Shizuku probe succeeds, Orpheus may use a read-only fallback for PackageManager-reported APK paths under /data/app.",
                primary = false
            )

            addSpacer(4)

            addView(
                Switch(this@MainActivity).apply {
                    text = "Preserve only selected packages"
                    isChecked = preserveOnlySelectedPackages
                    isEnabled = includePackagePreservation &&
                        packageInventory != null
                    setTextColor(primaryTextColor)
                    setOnCheckedChangeListener { _, checked ->
                        preserveOnlySelectedPackages = checked
                        render()
                    }
                }
            )

            addView(
                makeSecondaryButton(
                    if (packageInventory == null) {
                        "Choose packages"
                    } else {
                        "Choose packages (" + selectedPackageNames.size + ")"
                    }
                ) {
                    openPackageSelectionDialog()
                }.apply {
                    isEnabled = includePackagePreservation &&
                        packageInventory != null
                }
            )

            addSpacer(4)

            addView(
                makeSecondaryButton(
                    if (packageScanInProgress) {
                        "Scanning installed apps…"
                    } else {
                        "Rescan installed apps"
                    }
                ) {
                    scanPackages()
                }.apply {
                    isEnabled = !packageScanInProgress
                    alpha = if (isEnabled) 1.0f else 0.45f
                }
            )
        }

        addSpacer(12)

        addCard {
            addSectionTitle("BACKUP")
            addBody(
                backupConfigurationSummary(),
                primary = true
            )

            addSpacer(8)

            statusText = addBody(
                backupStatusSummary(),
                primary = true
            )
            applyStatusColor(statusText, backupStatusColor())

            addSpacer(4)

            val backupButton = makeButton("Create verified backup") {
                openArchiveCreator()
            }.apply {
                isEnabled = selectedTrees.isNotEmpty() ||
                    includePackagePreservation
                alpha = if (isEnabled) 1.0f else 0.45f
            }
            addView(backupButton)
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
            addSectionTitle("SHIZUKU BACKEND")
            shizukuStatusText = addBody(
                shizukuBackendStatus.summary(
                    permissionRequestPending = shizukuPermissionRequestPending,
                    probeInProgress = shizukuProbeInProgress
                ),
                primary = true
            )
            applyStatusColor(shizukuStatusText, shizukuStatusColor())

            shizukuDetailsText = addBody(
                shizukuDetailsSummary(),
                primary = false
            )

            addSpacer(6)

            addView(
                makeButton(shizukuActionLabel()) {
                    onShizukuActionClicked()
                }.apply {
                    isEnabled = !shizukuPermissionRequestPending &&
                        !shizukuProbeInProgress
                    alpha = if (isEnabled) 1.0f else 0.45f
                }
            )

            addView(
                makeSecondaryButton("Setup instructions") {
                    showShizukuSetupHelp()
                }
            )
        }

        addSpacer(12)

        addCard {
            addSectionTitle("CAPABILITIES")
            CapabilityScanner(shizukuBackendStatus).scan().capabilities.forEach { capability ->
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

    private fun refreshShizukuState() {
        val previous = shizukuBackendStatus
        val binderConnected = runCatching {
            Shizuku.pingBinder()
        }.getOrDefault(false)

        if (!binderConnected) {
            shizukuBackendStatus = ShizukuBackendStatus()
            shizukuPermissionRequestPending = false
            shizukuProbeInProgress = false
            shizukuServiceBound = false
            shizukuProbeService = null
            return
        }

        val serverApiSupported = runCatching {
            !Shizuku.isPreV11()
        }.getOrDefault(false)
        val permissionGranted = serverApiSupported && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val serverUid = runCatching {
            Shizuku.getUid().takeIf { it >= 0 }
        }.getOrNull()

        val preserveProbe = previous.binderConnected &&
            previous.serverApiSupported == serverApiSupported &&
            previous.permissionGranted &&
            permissionGranted &&
            previous.serverUid == serverUid &&
            previous.probeOutcome != ShizukuProbeOutcome.NOT_RUN

        shizukuBackendStatus = ShizukuBackendStatus(
            binderConnected = true,
            serverApiSupported = serverApiSupported,
            permissionGranted = permissionGranted,
            serverUid = serverUid,
            probeOutcome = if (preserveProbe) {
                previous.probeOutcome
            } else {
                ShizukuProbeOutcome.NOT_RUN
            },
            probeSummary = if (preserveProbe) previous.probeSummary else null,
            probeFailure = if (preserveProbe) previous.probeFailure else null,
            apkFallbackOutcome = if (preserveProbe) {
                previous.apkFallbackOutcome
            } else {
                ShizukuApkFallbackOutcome.NOT_RUN
            },
            apkFallbackSummary = if (preserveProbe) previous.apkFallbackSummary else null,
            apkFallbackFailure = if (preserveProbe) previous.apkFallbackFailure else null
        )
    }

    private fun shizukuStatusColor(): Int = when (
        shizukuBackendStatus.backendAvailability()
    ) {
        CapabilityAvailability.AVAILABLE -> successColor
        CapabilityAvailability.LIMITED -> warningColor
        CapabilityAvailability.UNAVAILABLE -> errorColor
        CapabilityAvailability.NOT_IMPLEMENTED,
        CapabilityAvailability.NOT_TESTED -> mutedTextColor
    }

    private fun shizukuDetailsSummary(): String {
        val uidDetail = shizukuBackendStatus.serverUid?.let {
            "Server UID: " + it + " (" +
                ShizukuPrivilegeClassifier.classify(it).displayName + ")."
        } ?: "Server UID: not available."

        val pendingDetail = if (shizukuPermissionRequestPending) {
            "Approve or deny the request in the Shizuku permission dialog."
        } else {
            null
        }

        return listOfNotNull(
            shizukuBackendStatus.backendDetail(),
            uidDetail,
            pendingDetail,
            shizukuBackendStatus.diagnosticsDetail(),
            shizukuBackendStatus.apkFallbackDetail()
        ).joinToString("\n")
    }

    private fun shizukuActionLabel(): String = when {
        !shizukuBackendStatus.binderConnected -> "Refresh Shizuku status"
        !shizukuBackendStatus.serverApiSupported -> "Shizuku API unsupported"
        !shizukuBackendStatus.permissionGranted -> "Grant Shizuku access"
        shizukuBackendStatus.privilege == ShizukuPrivilege.UNKNOWN ->
            "Privilege level unknown"
        else -> "Run read-only probe"
    }

    private fun onShizukuActionClicked() {
        refreshShizukuState()
        when {
            !shizukuBackendStatus.binderConnected -> {
                render()
                setStatus(
                    "Shizuku/Sui is not connected. Start it, then return and refresh status.",
                    warningColor
                )
            }
            !shizukuBackendStatus.serverApiSupported -> {
                render()
                setStatus(
                    "This Shizuku server is too old for UserService operations; v11 or later is required.",
                    errorColor
                )
            }
            !shizukuBackendStatus.permissionGranted -> requestShizukuPermission()
            else -> startShizukuProbe()
        }
    }

    private fun requestShizukuPermission() {
        if (shizukuPermissionRequestPending) return

        try {
            shizukuPermissionRequestPending = true
            render()
            Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION)
        } catch (error: Exception) {
            shizukuPermissionRequestPending = false
            refreshShizukuState()
            render()
            setStatus(
                "Could not request Shizuku permission: " +
                    (error.message ?: error.javaClass.simpleName),
                errorColor
            )
        }
    }

    private fun showShizukuSetupHelp() {
        AlertDialog.Builder(this)
            .setTitle("Set up Shizuku")
            .setMessage(
                "Install Shizuku or configure Sui separately, start the service, " +
                    "then return to Orpheus. On non-rooted Android 11 and later, " +
                    "Shizuku can be started with Wireless debugging. Orpheus will " +
                    "request its own authorization before running the read-only probe. " +
                    "Shizuku access is not the same as root access."
            )
            .setNegativeButton("Close", null)
            .setPositiveButton("Open setup guide") { _, _ ->
                runCatching {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(SHIZUKU_SETUP_URL)
                        )
                    )
                }.onFailure {
                    setStatus("Could not open the Shizuku setup guide.", warningColor)
                }
            }
            .show()
    }

    private fun startShizukuProbe() {
        refreshShizukuState()
        if (!shizukuBackendStatus.binderConnected ||
            !shizukuBackendStatus.serverApiSupported ||
            !shizukuBackendStatus.permissionGranted
        ) {
            render()
            return
        }

        if (shizukuBackendStatus.privilege == ShizukuPrivilege.UNKNOWN) {
            render()
            setStatus(
                "Shizuku's privilege level is unknown. Orpheus will not start the probe until UID 0 (root) or UID 2000 (shell/ADB) is reported.",
                warningColor
            )
            return
        }

        shizukuProbeInProgress = true
        shizukuBackendStatus = shizukuBackendStatus.copy(
            probeOutcome = ShizukuProbeOutcome.NOT_RUN,
            probeSummary = null,
            probeFailure = null
        )
        render()

        val existingService = shizukuProbeService
        if (shizukuServiceBound && existingService != null) {
            runShizukuProbe(existingService)
            return
        }

        try {
            shizukuServiceBound = true
            Shizuku.bindUserService(
                shizukuUserServiceArgs,
                shizukuServiceConnection
            )
        } catch (error: Exception) {
            shizukuServiceBound = false
            completeShizukuProbe(
                rawResult = null,
                failure = error
            )
        }
    }

    private fun runShizukuProbe(remote: IShizukuProbeService) {
        executor.submit {
            val result = runCatching { remote.collectReadOnlyDiagnostics() }
            runOnUiThread {
                if (!isDestroyed) {
                    completeShizukuProbe(
                        rawResult = result.getOrNull(),
                        failure = result.exceptionOrNull()
                    )
                }
            }
        }
    }

    private fun completeShizukuProbe(
        rawResult: String?,
        failure: Throwable?
    ) {
        if (!shizukuProbeInProgress || isDestroyed) return
        shizukuProbeInProgress = false

        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            shizukuBackendStatus = ShizukuBackendStatus()
            stopShizukuProbeService()
            render()
            return
        }

        val outcome = runCatching {
            if (failure != null) throw failure
            val raw = rawResult
                ?: throw IllegalStateException("The probe returned no result.")
            val report = JSONObject(raw)
            val userServiceUid = report.getInt("uid")
            val expectedUid = shizukuBackendStatus.serverUid
                ?: throw IllegalStateException("Shizuku did not report its server UID.")

            if (userServiceUid != expectedUid) {
                throw SecurityException(
                    "UserService UID " + userServiceUid +
                        " did not match Shizuku server UID " + expectedUid + "."
                )
            }

            val classifiedPrivilege = ShizukuPrivilegeClassifier.classify(userServiceUid)
            if (classifiedPrivilege == ShizukuPrivilege.UNKNOWN) {
                throw SecurityException(
                    "Unexpected Shizuku UserService UID: " + userServiceUid + "."
                )
            }
            val privilege = classifiedPrivilege.displayName
            val release = report.optString("android_release", "unknown")
            val apiLevel = report.optInt("android_sdk", -1)
            val identity = report.getString("identity")
            val processId = report.getInt("pid")

            "UserService UID: " + userServiceUid + " (" + privilege + ")" +
                "\nProcess ID: " + processId +
                "\nIdentity: " + identity +
                "\nAndroid: " + release + " (API " + apiLevel + ")"
        }

        if (outcome.isSuccess) {
            shizukuBackendStatus = shizukuBackendStatus.copy(
                probeOutcome = ShizukuProbeOutcome.SUCCEEDED,
                probeSummary = outcome.getOrThrow(),
                probeFailure = null
            )
        } else {
            val message = outcome.exceptionOrNull()?.let {
                it.message ?: it.javaClass.simpleName
            } ?: "Unknown probe failure"
            shizukuBackendStatus = shizukuBackendStatus.copy(
                probeOutcome = ShizukuProbeOutcome.FAILED,
                probeSummary = null,
                probeFailure = message
            )
        }

        if (shizukuBackendStatus.probeOutcome == ShizukuProbeOutcome.FAILED) {
            stopShizukuProbeService()
        }
        render()
        if (shizukuBackendStatus.probeOutcome == ShizukuProbeOutcome.FAILED) {
            setStatus(
                "Shizuku read-only probe failed: " +
                    (shizukuBackendStatus.probeFailure ?: "unknown error"),
                warningColor
            )
        } else {
            setStatus("Shizuku read-only probe succeeded.", successColor)
        }
    }

    private fun stopShizukuProbeService() {
        val wasBound = shizukuServiceBound
        shizukuServiceBound = false
        shizukuProbeService = null

        if (wasBound && runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            runCatching {
                Shizuku.unbindUserService(
                    shizukuUserServiceArgs,
                    shizukuServiceConnection,
                    true
                )
            }
        }
    }

    private fun scanPackages() {
        if (packageScanInProgress) return

        packageScanInProgress = true
        render()

        executor.submit {
            try {
                val inventory = PackageInventoryCollector(packageManager)
                    .collect()

                runOnUiThread {
                    packageInventory = inventory

                    val currentNames = inventory.packages
                        .mapTo(linkedSetOf()) { it.packageName }

                    if (!packageSelectionInitialized) {
                        selectedPackageNames = currentNames
                        packageSelectionInitialized = true
                    } else {
                        selectedPackageNames.retainAll(currentNames)
                    }

                    packageScanInProgress = false
                    render()
                }
            } catch (error: Exception) {
                logger.warn(
                    "Package inventory scan failed: " +
                        (error.message ?: error.javaClass.simpleName)
                )

                runOnUiThread {
                    packageInventory = null
                    packageScanInProgress = false
                    render()
                    setStatus(
                        "Package inventory failed: " +
                            (error.message ?: error.javaClass.simpleName),
                        errorColor
                    )
                }
            }
        }
    }

    private fun packageSummary(): String {
        if (!includePackagePreservation) {
            return "Package preservation disabled."
        }

        if (packageScanInProgress) {
            return "Package inventory: scanning…"
        }

        val inventory = packageInventory
            ?: return "Package inventory: not scanned yet."

        val configured = configuredPackageInventory()
        val scope = if (preserveOnlySelectedPackages) {
            configured.packageCount.toString() + " selected"
        } else {
            inventory.packageCount.toString() + " visible"
        }

        val missing = if (configured.packagesWithoutReadableBaseApk > 0) {
            " · " + configured.packagesWithoutReadableBaseApk +
                " base APKs unavailable"
        } else {
            ""
        }

        return "Installed packages: " +
            inventory.packageCount +
            " visible · " +
            scope +
            missing
    }

    private fun packageEstimateSummary(): String {
        if (!includePackagePreservation) {
            return "Package content: not included."
        }

        val inventory = packageInventory
            ?: return "Package content: not scanned yet."

        val configured = configuredPackageInventory()

        if (!includePackageContent) {
            return "Package content: APK preservation disabled."
        }

        return "Package content: " +
            formatBytes(configured.obtainableApkBytes) +
            " obtainable · " +
            configured.obtainableApkCount +
            " APKs"
    }

    private fun configuredPackageInventory(): PackageInventory {
        val inventory = packageInventory
            ?: return PackageInventory(emptyList())

        if (!preserveOnlySelectedPackages) {
            return inventory
        }

        return PackageInventory(
            inventory.packages.filter {
                it.packageName in selectedPackageNames
            }
        )
    }

    private fun backupConfigurationSummary(): String {
        val fileSummary = if (selectedTrees.isEmpty()) {
            "Files: none selected"
        } else {
            "Files: " +
                selectedTrees.size +
                " folder(s) · " +
                formatBytes(lastEstimate?.knownBytes ?: 0L)
        }

        val packageSummary = when {
            !includePackagePreservation -> "Packages: disabled"
            preserveOnlySelectedPackages -> {
                "Packages: " + selectedPackageNames.size + " selected"
            }
            packageInventory != null -> {
                "Packages: " + packageInventory!!.packageCount + " visible"
            }
            else -> "Packages: will be scanned during backup"
        }

        val apkSummary = when {
            !includePackagePreservation || !includePackageContent ->
                "APK content: disabled"
            packageInventory != null ->
                "APK content: " +
                    formatBytes(configuredPackageInventory().obtainableApkBytes)
            else ->
                "APK content: enabled"
        }

        return fileSummary + " · " + packageSummary + " · " + apkSummary
    }

    private fun openPackageSelectionDialog() {
        val inventory = packageInventory
            ?: return

        val packages = inventory.packages.sortedWith(
            compareBy<InstalledPackage>(
                { it.label.lowercase(Locale.US) },
                { it.packageName }
            )
        )
        val workingSelection = selectedPackageNames.toMutableSet()
        val labels = packages.map {
            it.label + "\n" + it.packageName
        }.toTypedArray()
        val checked = packages.map {
            it.packageName in workingSelection
        }.toBooleanArray()

        lateinit var dialog: AlertDialog

        fun setAllPackagesChecked(isChecked: Boolean) {
            workingSelection.clear()
            if (isChecked) {
                workingSelection.addAll(packages.map { it.packageName })
            }
            packages.indices.forEach { index ->
                dialog.listView.setItemChecked(index, isChecked)
            }
        }

        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(16), dp(4))

            addView(
                TextView(this@MainActivity).apply {
                    text = "Select packages"
                    textSize = 20f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
            )

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END

                    addView(
                        TextView(this@MainActivity).apply {
                            text = "Select all"
                            textSize = 14f
                            gravity = Gravity.CENTER
                            isClickable = true
                            isFocusable = true
                            setPadding(dp(12), dp(12), dp(12), dp(12))
                            setOnClickListener {
                                setAllPackagesChecked(true)
                            }
                        }
                    )

                    addView(
                        TextView(this@MainActivity).apply {
                            text = "Select none"
                            textSize = 14f
                            gravity = Gravity.CENTER
                            isClickable = true
                            isFocusable = true
                            setPadding(dp(12), dp(12), dp(8), dp(12))
                            setOnClickListener {
                                setAllPackagesChecked(false)
                            }
                        }
                    )
                }
            )
        }

        dialog = AlertDialog.Builder(this)
            .setCustomTitle(titleContainer)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val packageName = packages[which].packageName
                if (isChecked) {
                    workingSelection += packageName
                } else {
                    workingSelection -= packageName
                }
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done") { _, _ ->
                selectedPackageNames.clear()
                selectedPackageNames.addAll(workingSelection)
                render()
            }
            .create()

        dialog.show()
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
        if (selectedTrees.isEmpty() && !includePackagePreservation) {
            setStatus(
                "Select a folder or enable installed-app preservation.",
                warningColor
            )
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
        val preservePackages = includePackagePreservation
        val preservePackageApks = includePackageContent
        val packageNames = if (preserveOnlySelectedPackages) {
            selectedPackageNames.toSet()
        } else {
            null
        }
        val capabilitySnapshot = CapabilityScanner(shizukuBackendStatus).scan()
        val shizukuApkReader = if (
            shizukuBackendStatus.diagnosticsAvailability() ==
                CapabilityAvailability.AVAILABLE &&
            shizukuServiceBound
        ) {
            shizukuProbeService?.let(::ShizukuPackageApkReader)
        } else {
            null
        }
        sessionStore.markRunning(outputUri.toString())

        render()
        setStatus("Creating archive…", warningColor)

        executor.submit {
            try {
                val device = DeviceInfo.read()
                val capabilities = capabilitySnapshot

                val writer = ArchiveWriter(
                    resolver = contentResolver,
                    deviceInfo = device,
                    capabilities = capabilities,
                    packageManager = packageManager
                )

                val progress = object : BackupProgressListener {
                    override fun onPackageStarted(packageName: String) {
                        runOnUiThread {
                            statusText.text = "Preserving package " + packageName
                        }
                    }

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

                val writeResult = writer.write(
                    outputUri = outputUri,
                    selectedTrees = roots,
                    includePackages = preservePackages,
                    includePackageApks = preservePackageApks,
                    selectedPackageNames = packageNames,
                    listener = progress,
                    shizukuApkReader = shizukuApkReader
                )

                val verification = ArchiveVerifier(contentResolver)
                    .verify(outputUri)

                if (!verification.valid) {
                    sessionStore.markIncomplete(
                        verification.error ?: "Archive verification failed."
                    )
                    runOnUiThread {
                        updateShizukuApkFallbackStatus(writeResult)
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
                    updateShizukuApkFallbackStatus(writeResult)
                    render()
                    val fallbackSummary =
                        if (writeResult.shizukuApkFallbackAttempts > 0) {
                            " Shizuku APK fallback: " +
                                writeResult.shizukuApkFallbackSuccesses + "/" +
                                writeResult.shizukuApkFallbackAttempts +
                                " attempted source(s) collected."
                        } else {
                            ""
                        }
                    setStatus(
                        "Backup verified: " +
                            verification.artifactCount +
                            " artifacts, " +
                            formatBytes(verification.verifiedBytes) +
                            " checked." + fallbackSummary,
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

    private fun updateShizukuApkFallbackStatus(result: BackupWriteResult) {
        if (result.shizukuApkFallbackAttempts <= 0) return

        val successes = result.shizukuApkFallbackSuccesses
        shizukuBackendStatus = shizukuBackendStatus.copy(
            apkFallbackOutcome = if (successes > 0) {
                ShizukuApkFallbackOutcome.SUCCEEDED
            } else {
                ShizukuApkFallbackOutcome.FAILED
            },
            apkFallbackSummary = if (successes > 0) {
                "Shizuku read " + successes + " of " +
                    result.shizukuApkFallbackAttempts +
                    " APK source(s) that the normal app process could not open."
            } else {
                null
            },
            apkFallbackFailure = if (successes == 0) {
                "No inaccessible APK source could be opened through Shizuku."
            } else {
                null
            }
        )
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
        private const val REQUEST_SHIZUKU_PERMISSION = 1003
        private const val SHIZUKU_SETUP_URL = "https://shizuku.rikka.app/"
    }
}
