package dev.edgeflow

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class WavReaderTest {
    private fun fixture(rate: Int = 16000): ByteArray {
        val size = 16000
        return ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + size); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(size); putShort(16384)
        }.array()
    }
    @Test fun decodesRealPcmScale() { val audio = WavReader.read(fixture()); assertEquals(8000, audio.size); assertEquals(.5f, audio[0]) }
    @Test(expected = IllegalArgumentException::class) fun rejectsWrongSampleRate() { WavReader.read(fixture(48000)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsTruncation() { WavReader.read(fixture().dropLast(1).toByteArray()) }
    @Test(expected = IllegalArgumentException::class) fun rejectsOverflowChunkSize() {
        val bytes = fixture(); ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(40, Int.MAX_VALUE); WavReader.read(bytes)
    }
}
