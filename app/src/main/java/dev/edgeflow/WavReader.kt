package dev.edgeflow

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Deliberately narrow input contract, matching the microphone: PCM16 mono, 16 kHz, <=60s. */
object WavReader {
    fun read(bytes: ByteArray): FloatArray {
        require(bytes.size in 44..4_000_000) { "WAV demasiado grande o incompleto" }
        fun text(offset: Int, size: Int) = String(bytes, offset, size, Charsets.US_ASCII)
        fun integer(offset: Int) = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
        fun short(offset: Int) = ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
        require(text(0, 4) == "RIFF" && text(8, 4) == "WAVE") { "Selecciona un WAV PCM" }
        require(integer(4).toLong() + 8 == bytes.size.toLong()) { "Longitud RIFF incorrecta" }
        var offset = 12
        var formatValid = false
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val type = text(offset, 4)
            val size = integer(offset + 4)
            require(size >= 0 && offset.toLong() + 8 + size <= bytes.size) { "Bloque WAV incompleto" }
            val start = offset + 8
            if (type == "fmt ") {
                require(size >= 16 && short(start) == 1 && short(start + 2) == 1 && integer(start + 4) == 16000 && short(start + 14) == 16) { "Solo WAV PCM16 mono a 16000 Hz" }
                formatValid = true
            }
            if (type == "data") { require(dataOffset == -1) { "Múltiples bloques de audio no admitidos" }; dataOffset = start; dataSize = size }
            offset = start + size + (size and 1)
        }
        require(formatValid && dataOffset >= 0 && dataSize in 16000..1_920_000 && dataSize % 2 == 0) { "Audio de 0,5 a 60 segundos requerido" }
        return FloatArray(dataSize / 2) { short(dataOffset + it * 2).toShort() / 32768f }
    }
}
