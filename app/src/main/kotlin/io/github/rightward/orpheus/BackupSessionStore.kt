package io.github.rightward.orpheus

import android.content.Context

enum class BackupSessionState {
    RUNNING,
    INCOMPLETE,
    VERIFIED
}

data class BackupSession(
    val state: BackupSessionState,
    val outputUri: String,
    val startedAt: Long,
    val message: String?
)

class BackupSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "orpheus_backup_session",
        Context.MODE_PRIVATE
    )

    fun markRunning(outputUri: String) {
        preferences.edit()
            .putString("state", BackupSessionState.RUNNING.name)
            .putString("output_uri", outputUri)
            .putLong("started_at", System.currentTimeMillis())
            .remove("message")
            .apply()
    }

    fun markIncomplete(message: String) {
        preferences.edit()
            .putString("state", BackupSessionState.INCOMPLETE.name)
            .putString("message", message)
            .apply()
    }

    fun markVerified() {
        preferences.edit()
            .putString("state", BackupSessionState.VERIFIED.name)
            .remove("message")
            .apply()
    }

    fun read(): BackupSession? {
        val state = preferences.getString("state", null) ?: return null
        val outputUri = preferences.getString("output_uri", null) ?: return null

        return BackupSession(
            state = BackupSessionState.valueOf(state),
            outputUri = outputUri,
            startedAt = preferences.getLong("started_at", 0L),
            message = preferences.getString("message", null)
        )
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
