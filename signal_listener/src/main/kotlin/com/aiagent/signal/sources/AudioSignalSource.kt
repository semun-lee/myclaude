package com.aiagent.signal.sources

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.aiagent.signal.Signal
import com.aiagent.signal.SignalBus
import com.aiagent.signal.SignalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 저전력 Voice Activity Detection (VAD).
 *
 * 하드웨어 SoundTrigger가 없는 기기를 위한 소프트웨어 폴백 구현.
 * AudioRecord를 짧은 주기로 polling하되 RMS 에너지만 계산하여
 * 음성이 있을 가능성이 있을 때만 신호를 emit한다.
 *
 * 배터리 전략:
 *  - SAMPLE_RATE_HZ를 8000Hz로 제한 (전화품질, 음성 감지엔 충분).
 *  - WINDOW_MS = 30ms — 짧은 윈도우로 처리 CPU 시간을 최소화.
 *  - POLL_INTERVAL_MS = 100ms — 100ms마다 한 번만 AudioRecord를 읽음.
 *  - 화면이 꺼지거나 기기가 내려놓인 경우 VAD를 자동 일시 정지.
 *
 * 에너지 임계치(ENERGY_THRESHOLD)는 실측값으로 튜닝 필요.
 */
class AudioSignalSource(
    private val context: Context,
    private val bus: SignalBus,
) : SignalSource {

    private val _channel = Channel<Signal>(capacity = Channel.CONFLATED)
    override val signals: Flow<Signal> = _channel.receiveAsFlow()

    @Volatile override var isActive: Boolean = false
        private set

    // 화면 꺼짐 또는 기기 내려놓음 시 VAD 일시 정지
    @Volatile var suspended: Boolean = false

    private var audioRecord: AudioRecord? = null
    private var pollingJob: Job? = null

    private var voiceStartTime: Long = -1L
    private var consecutiveSilenceCount = 0

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start() {
        if (isActive) return
        isActive = true

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(WINDOW_SAMPLES * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // 노이즈 억제 HW 경로 사용
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        ).also { it.startRecording() }

        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ShortArray(WINDOW_SAMPLES)
            while (isActive) {
                if (!suspended) {
                    processWindow(buffer)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun stop() {
        if (!isActive) return
        isActive = false
        pollingJob?.cancel()
        pollingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _channel.close()
    }

    // ── VAD 핵심 로직 ─────────────────────────────────────────────────────────

    private fun processWindow(buffer: ShortArray) {
        val record = audioRecord ?: return
        val read = record.read(buffer, 0, buffer.size)
        if (read <= 0) return

        val rms = computeRms(buffer, read)
        val hasVoice = rms > ENERGY_THRESHOLD

        if (hasVoice) {
            consecutiveSilenceCount = 0
            if (voiceStartTime < 0) {
                voiceStartTime = System.currentTimeMillis()
                val signal = Signal.VoiceActivityDetected(durationMs = 0)
                bus.emit(signal)
                _channel.trySend(signal)
            }
        } else {
            if (voiceStartTime >= 0) {
                consecutiveSilenceCount++
                // 연속 침묵이 임계치를 넘으면 발화 종료로 판단
                if (consecutiveSilenceCount >= SILENCE_FRAMES_FOR_END) {
                    val duration = System.currentTimeMillis() - voiceStartTime
                    voiceStartTime = -1L
                    consecutiveSilenceCount = 0

                    // 너무 짧은 소리(300ms 미만)는 잡음으로 간주하고 무시
                    if (duration >= MIN_VOICE_DURATION_MS) {
                        val signal = Signal.VoiceActivityEnded()
                        bus.emit(signal)
                        _channel.trySend(signal)
                    }
                }
            }
        }
    }

    /**
     * 16-bit PCM 샘플의 RMS 에너지를 계산한다.
     * 정수 연산만 사용하여 FPU 비용을 줄인다.
     */
    private fun computeRms(buffer: ShortArray, count: Int): Long {
        var sum = 0L
        for (i in 0 until count) {
            val s = buffer[i].toLong()
            sum += s * s
        }
        return if (count > 0) sum / count else 0L
    }

    companion object {
        private const val SAMPLE_RATE_HZ = 8_000          // 8kHz — 음성 감지에 충분
        private const val WINDOW_MS = 30L
        private const val WINDOW_SAMPLES = (SAMPLE_RATE_HZ * WINDOW_MS / 1000).toInt()
        private const val POLL_INTERVAL_MS = 100L

        // RMS² 임계치. 조용한 실내 ≈ 500, 대화 ≈ 5000~50000.
        // 기기/마이크 특성에 따라 튜닝 필요.
        private const val ENERGY_THRESHOLD = 3_000L

        // POLL_INTERVAL_MS 단위로 몇 프레임 연속 침묵 시 발화 종료로 판단
        private const val SILENCE_FRAMES_FOR_END = 5  // 500ms

        private const val MIN_VOICE_DURATION_MS = 300L
    }
}
