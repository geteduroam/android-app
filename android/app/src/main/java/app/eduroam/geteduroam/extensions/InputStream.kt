package app.eduroam.geteduroam.extensions

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Largest .eap-config file we read into memory. Real profiles are well below 1 MB, even with
 * embedded logos, so anything above this is not a profile we can use.
 */
const val MAX_CONFIG_FILE_BYTES = 10 * 1024 * 1024

class FileTooLargeException(val maxBytes: Int) : IOException("Stream is larger than $maxBytes bytes")

/**
 * Reads the whole stream into memory, and throws [FileTooLargeException] as soon as it holds
 * more than [maxBytes], so a huge or endless stream cannot exhaust the heap.
 */
@Throws(IOException::class)
fun InputStream.readBytes(maxBytes: Int = MAX_CONFIG_FILE_BYTES): ByteArray {
    val bufLen = 4 * 0x400 // 4KB
    val buf = ByteArray(bufLen)
    var readLen: Int = 0
    var total = 0

    ByteArrayOutputStream().use { o ->
        this.use { i ->
            while (i.read(buf, 0, bufLen).also { readLen = it } != -1) {
                total += readLen
                if (total > maxBytes) throw FileTooLargeException(maxBytes)
                o.write(buf, 0, readLen)
            }
        }

        return o.toByteArray()
    }
}
