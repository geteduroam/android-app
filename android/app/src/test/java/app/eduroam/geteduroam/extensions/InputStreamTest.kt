package app.eduroam.geteduroam.extensions

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class InputStreamTest {

    @Test
    fun readsSmallStreamCompletely() {
        val data = ByteArray(10_000) { it.toByte() }
        assertArrayEquals(data, ByteArrayInputStream(data).readBytes())
    }

    @Test
    fun readsStreamOfExactlyTheLimit() {
        val data = ByteArray(8192)
        assertEquals(8192, ByteArrayInputStream(data).readBytes(maxBytes = 8192).size)
    }

    @Test
    fun rejectsStreamOneByteOverTheLimit() {
        try {
            ByteArrayInputStream(ByteArray(8193)).readBytes(maxBytes = 8192)
            fail("Expected FileTooLargeException")
        } catch (e: FileTooLargeException) {
            assertEquals(8192, e.maxBytes)
        }
    }

    @Test
    fun stopsReadingAnEndlessStream() {
        var bytesServed = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 0.also { bytesServed++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int = len.also { bytesServed += it }
        }
        try {
            endless.readBytes()
            fail("Expected FileTooLargeException")
        } catch (e: FileTooLargeException) {
            // Must stop within one buffer of the limit instead of allocating until OOM
            assertTrue(bytesServed <= MAX_CONFIG_FILE_BYTES + 4096)
        }
    }

    @Test
    fun closesStreamWhenLimitIsExceeded() {
        var closed = false
        val stream = object : ByteArrayInputStream(ByteArray(100)) {
            override fun close() { closed = true }
        }
        try {
            stream.readBytes(maxBytes = 10)
        } catch (_: FileTooLargeException) {
        }
        assertTrue(closed)
    }
}
