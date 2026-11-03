package com.micflow.android

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Records the microphone as 16 kHz mono 16-bit PCM, the format the live transcription takes, in
 * 0.1-second chunks. Each chunk comes with its loudness (RMS, 0 to 1) for the sound wave.
 */
class AudioCapture(private val onChunk: (pcm: ByteArray, level: Float) -> Unit) {
    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 10
    }

    @Volatile private var running = false
    private var record: AudioRecord? = null

    /** False when the microphone couldn't be opened (no permission, or in use). */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, CHUNK_SAMPLES * 4))
        } catch (e: Exception) {
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }
        record = recorder
        running = true
        recorder.startRecording()
        thread(name = "MicFlow audio") {
            val samples = ShortArray(CHUNK_SAMPLES)
            var chunks = 0
            while (running) {
                val read = recorder.read(samples, 0, samples.size)
                if (read <= 0) continue
                val bytes = ByteArray(read * 2)
                var sum = 0.0
                for (i in 0 until read) {
                    val s = samples[i].toInt()
                    bytes[i * 2] = (s and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    val f = s / 32768.0
                    sum += f * f
                }
                val level = sqrt(sum / read).toFloat()
                // Every 2 s: how loud the mic is (silence here means Android is muting the mic).
                if (chunks++ % 20 == 0) android.util.Log.i("MicFlow", "Mic level %.4f".format(level))
                onChunk(bytes, level)
            }
        }
        return true
    }

    fun stop() {
        running = false
        record?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        record = null
    }
}
