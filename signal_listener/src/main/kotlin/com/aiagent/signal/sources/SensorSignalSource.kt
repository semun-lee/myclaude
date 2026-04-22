package com.aiagent.signal.sources

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.aiagent.signal.MotionState
import com.aiagent.signal.Signal
import com.aiagent.signal.SignalBus
import com.aiagent.signal.SignalSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.sqrt

/**
 * 가속도계 + 유효 모션 감지를 통해 기기 들어올림(Lift)과 내려놓음(Put Down),
 * 이동 상태(정지/걷기/달리기)를 감지한다.
 *
 * 배터리 전략:
 *  - SensorManager.SENSOR_DELAY_NORMAL (200ms 간격) 사용 — UI용 GAME보다 10배 절약.
 *  - 가속도 크기(magnitude)만 계산하여 CPU 연산 최소화.
 *  - 상태가 바뀔 때만 신호를 emit한다 (폴링이 아닌 엣지 트리거).
 */
class SensorSignalSource(
    private val context: Context,
    private val bus: SignalBus,
) : SignalSource, SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _channel = Channel<Signal>(capacity = Channel.CONFLATED)
    override val signals: Flow<Signal> = _channel.receiveAsFlow()

    @Volatile override var isActive: Boolean = false
        private set

    // ── 상태 추적 ─────────────────────────────────────────────────────────────

    private var currentMotionState = MotionState.STILL
    private var isLifted = false

    // 수평 유지 판단을 위한 이동 평균 버퍼 (8개 샘플 ≈ 1.6초)
    private val magnitudeBuffer = FloatArray(8)
    private var bufferIndex = 0
    private var bufferFilled = false

    // 기기가 수평에 가까운 가속도를 유지한 연속 횟수
    private var flatCount = 0

    override fun start() {
        if (isActive) return
        isActive = true
        accelerometer?.also { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL,  // ~5Hz — 저전력
            )
        }
    }

    override fun stop() {
        if (!isActive) return
        isActive = false
        sensorManager.unregisterListener(this)
        _channel.close()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val (ax, ay, az) = Triple(event.values[0], event.values[1], event.values[2])
        val magnitude = sqrt(ax * ax + ay * ay + az * az)

        updateBuffer(magnitude)
        detectLift(az, magnitude)
        detectMotionState(magnitude)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    // ── 들어올림 / 내려놓음 감지 ──────────────────────────────────────────────

    /**
     * Lift-to-wake 알고리즘:
     *  1. z축(중력 방향) 가속도가 갑자기 증가 → 들어올림
     *  2. 이동 평균 크기가 중력(9.8m/s²)에 가까워지고 안정 → 내려놓음
     */
    private fun detectLift(az: Float, magnitude: Float) {
        val isCurrentlyFlat = magnitude in GRAVITY_MIN..GRAVITY_MAX && az > GRAVITY_FLAT_Z_MIN

        if (isCurrentlyFlat) {
            flatCount++
        } else {
            flatCount = 0
        }

        // 이전에 내려놓인 상태였다가 크게 흔들림 → 들어올림
        if (!isLifted && magnitude > LIFT_MAGNITUDE_THRESHOLD) {
            isLifted = true
            flatCount = 0
            val signal = Signal.DeviceLifted()
            bus.emit(signal)
            _channel.trySend(signal)
        }

        // 들어올린 상태에서 여러 번 연속으로 수평 안정 → 내려놓음
        if (isLifted && flatCount >= FLAT_COUNT_FOR_PUT_DOWN) {
            isLifted = false
            flatCount = 0
            val signal = Signal.DevicePutDown()
            bus.emit(signal)
            _channel.trySend(signal)
        }
    }

    // ── 이동 상태 감지 ────────────────────────────────────────────────────────

    /**
     * 이동 평균의 분산(variance)으로 이동 강도를 추정한다.
     * 분산이 낮으면 정지, 중간이면 걷기, 높으면 달리기/차량.
     */
    private fun detectMotionState(magnitude: Float) {
        if (!bufferFilled) return

        val mean = magnitudeBuffer.average().toFloat()
        val variance = magnitudeBuffer.map { (it - mean) * (it - mean) }.average().toFloat()

        val newState = when {
            variance < VARIANCE_STILL -> MotionState.STILL
            variance < VARIANCE_WALKING -> MotionState.WALKING
            variance < VARIANCE_RUNNING -> MotionState.RUNNING
            else -> MotionState.VEHICLE
        }

        if (newState != currentMotionState) {
            currentMotionState = newState
            val signal = Signal.MotionStateChanged(motionState = newState)
            bus.emit(signal)
            _channel.trySend(signal)
        }
    }

    private fun updateBuffer(magnitude: Float) {
        magnitudeBuffer[bufferIndex] = magnitude
        bufferIndex = (bufferIndex + 1) % magnitudeBuffer.size
        if (bufferIndex == 0) bufferFilled = true
    }

    companion object {
        private const val GRAVITY_MIN = 8.5f         // m/s²
        private const val GRAVITY_MAX = 11.0f
        private const val GRAVITY_FLAT_Z_MIN = 7.0f  // z축이 중력 방향일 때
        private const val LIFT_MAGNITUDE_THRESHOLD = 14.0f
        private const val FLAT_COUNT_FOR_PUT_DOWN = 5 // 연속 5샘플(~1초) 안정 시

        private const val VARIANCE_STILL = 0.1f
        private const val VARIANCE_WALKING = 2.0f
        private const val VARIANCE_RUNNING = 8.0f
    }
}
