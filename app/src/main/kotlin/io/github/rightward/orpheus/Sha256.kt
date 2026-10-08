package io.github.rightward.orpheus

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object Sha256 {
    fun digest(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .toHex()
    }

    fun newDigest(): MessageDigest =
        MessageDigest.getInstance("SHA-256")

    fun finish(digest: MessageDigest): String =
        digest.digest().toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
}

fun InputStream.copyToWithDigest(
    output: OutputStream,
    digest: MessageDigest,
    bufferSize: Int = 64 * 1024,
    onBytes: (Long) -> Unit = {}
): Long {
    val buffer = ByteArray(bufferSize)
    var total = 0L

    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (read == 0) continue

        output.write(buffer, 0, read)
        digest.update(buffer, 0, read)
        total += read
        onBytes(read.toLong())
    }

    return total
}
