package com.aiagent.signal

import android.content.Context
import com.aiagent.signal.sources.AccessibilitySignalSource
import com.aiagent.signal.sources.AudioSignalSource
import com.aiagent.signal.sources.ScreenStateSignalSource
import com.aiagent.signal.sources.SensorSignalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 모든 SignalSource를 조율하는 최상위 코디네이터.
 *
 * 책임:
 *  1. 각 소스의 생명주기 관리 (start / stop).
 *  2. 소스 간 신호를 통합하여 단일 스트림을 제공.
 *  3. 신호 간 교차 반응 처리:
 *     - 화면 꺼짐 → VAD 일시 정지 (마이크 배터리 절약)
 *     - 기기 내려놓음 → 비핵심 소스 일시 정지
 *     - 배터리 임계치 교차 → 소스 집합 동적 축소
 *  4. 현재 처리 등급(ProcessingTier)을 상위 레이어에 노출.
 */
class SignalListener(
    private val context: Context,
    private val bus: SignalBus = SignalBus(),
) {
    // ── 소스 인스턴스 ─────────────────────────────────────────────────────────

    val accessibility = AccessibilitySignalSource(bus)
    val sensor = SensorSignalSource(context, bus)
    val screen = ScreenStateSignalSource(context, bus)
    val audio = AudioSignalSource(context, bus)

    private val allSources: List<SignalSource> = listOf(accessibility, sensor, screen, audio)

    // ── 공개 스트림 ───────────────────────────────────────────────────────────

    /** 모든 소스에서 오는 신호를 단일 Flow로 머지한 스트림. */
    val signals = bus.signals

    // ── 상태 ──────────────────────────────────────────────────────────────────

    @Volatile var currentTier: ProcessingTier = ProcessingTier.FULL
        private set

    @Volatile var isRunning: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var crossReactionJob: Job? = null

    // ── 생명주기 ──────────────────────────────────────────────────────────────

    fun start() {
        if (isRunning) return
        isRunning = true

        allSources.forEach { it.start() }
        crossReactionJob = scope.launch { observeCrossReactions() }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false

        crossReactionJob?.cancel()
        allSources.forEach { it.stop() }
        scope.cancel()
    }

    // ── 교차 반응 (Cross-Source Reactions) ────────────────────────────────────

    /**
     * 특정 신호가 다른 소스의 동작을 제어하는 연동 규칙.
     *
     * 예:
     *  - 화면 꺼짐 → VAD 중단 (사용자가 기기를 사용 안 함)
     *  - 기기 내려놓음 → 센서 샘플링 최소화
     *  - 배터리 임계치 하향 → 소스 단계적 비활성화
     */
    private suspend fun observeCrossReactions() {
        bus.signals.collect { signal ->
            when (signal) {
                is Signal.ScreenStateChanged -> handleScreenStateChanged(signal)
                is Signal.DevicePutDown -> handleDevicePutDown()
                is Signal.DeviceLifted -> handleDeviceLifted()
                is Signal.BatteryThresholdCrossed -> handleBatteryThreshold(signal)
                else -> Unit
            }
        }
    }

    private fun handleScreenStateChanged(signal: Signal.ScreenStateChanged) {
        if (!signal.isOn) {
            // 화면 꺼짐: VAD 일시 정지 (마이크는 여전히 wakeword용으로 유지할 수 있으므로 suspend)
            audio.suspended = true
            updateTier(ProcessingTier.SCREEN_OFF)
        } else {
            audio.suspended = false
            updateTier(ProcessingTier.FULL)
        }
    }

    private fun handleDevicePutDown() {
        // 기기 내려놓음: 모션 기반 소스 외 처리 등급 낮춤
        audio.suspended = true
        updateTier(ProcessingTier.MINIMAL)
    }

    private fun handleDeviceLifted() {
        audio.suspended = false
        updateTier(ProcessingTier.FULL)
    }

    private fun handleBatteryThreshold(signal: Signal.BatteryThresholdCrossed) {
        if (!signal.crossedDown) return // 올라가는 방향은 무시

        val newTier = when {
            signal.level <= 10 -> ProcessingTier.SUSPENDED
            signal.level <= 20 -> ProcessingTier.MINIMAL
            signal.level <= 50 -> ProcessingTier.BALANCED
            else -> ProcessingTier.FULL
        }
        updateTier(newTier)

        // 배터리 10% 이하: 오디오 소스 완전 중단
        if (signal.level <= 10) {
            audio.stop()
        }
    }

    private fun updateTier(tier: ProcessingTier) {
        if (currentTier == tier) return
        currentTier = tier
        // Tier 변경 신호를 버스에 게시하여 상위 레이어(TriggerEngine 등)가 반응할 수 있게 함
        bus.emit(InternalSignal.TierChanged(tier))
    }
}

// ── Supporting types ─────────────────────────────────────────────────────────

/**
 * Trigger Engine과 Context Extractor가 허용된 처리 최대 등급.
 * 배터리/화면 상태에 따라 SignalListener가 동적으로 변경한다.
 */
enum class ProcessingTier(val maxModelTier: Int) {
    /** 충전 중 또는 배터리 풍부: 모든 Tier 허용 */
    FULL(maxModelTier = 3),

    /** 배터리 50% 이하: Tier 2까지 허용 */
    BALANCED(maxModelTier = 2),

    /** 화면 꺼짐 또는 배터리 20% 이하: Tier 1 (경량 온디바이스)만 */
    MINIMAL(maxModelTier = 1),

    /** 배터리 10% 이하: 규칙 기반 Tier 0만 */
    LOW_POWER(maxModelTier = 0),

    /** 기기 내려놓음: 분석 중단, 신호 수신만 유지 */
    SCREEN_OFF(maxModelTier = 0),

    /** 긴급 절전: 모든 분석 중단 */
    SUSPENDED(maxModelTier = -1),
}

/** SignalListener 내부에서만 사용하는 메타 신호 */
sealed class InternalSignal : Signal() {
    data class TierChanged(
        val newTier: ProcessingTier,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : InternalSignal()
}
