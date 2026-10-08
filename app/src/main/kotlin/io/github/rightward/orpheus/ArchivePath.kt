package io.github.rightward.orpheus

object ArchivePath {
    fun sanitizeComponent(value: String): String {
        val cleaned = value
            .replace('\\', '_')
            .replace('/', '_')
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "_")
            .trim()

        return when {
            cleaned.isEmpty() -> "unnamed"
            cleaned == "." -> "_"
            cleaned == ".." -> "__"
            else -> cleaned
        }
    }

    fun sanitizeRelativePath(value: String): String =
        value
            .replace('\\', '/')
            .split('/')
            .map { sanitizeComponent(it) }
            .filter { it.isNotEmpty() }
            .joinToString("/")
            .ifEmpty { "unnamed" }
}
