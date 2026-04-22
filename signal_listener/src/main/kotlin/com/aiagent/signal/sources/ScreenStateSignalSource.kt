package com.aiagent.signal.sources

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.aiagent.signal.Signal
import com.aiagent.signal.SignalBus
import com.aiagent.signal.SignalSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 화면 켜짐/꺼짐, 충전 상태, 배터리 수준 임계치를 BroadcastReceiver로 감지한다.
 *
 * BroadcastReceiver는 이벤트가 없을 때 CPU를 전혀 사용하지 않으므로
 * 가장 저전력 신호 소스 중 하나다.
 *
 * 배터리 임계치: [CRITICAL=10, LOW=20, MEDIUM=50, HIGH=80]
 * 임계치를 교차할 때만 신호를 emit하여 Battery Manager가 처리 등급을 조절할 수 있게 한다.
 */
class ScreenStateSignalSource(
    private val context: Context,
    private val bus: SignalBus,
) : SignalSource {

    private val _channel = Channel<Signal>(capacity = Channel.BUFFERED)
    override val signals: Flow<Signal> = _channel.receiveAsFlow()

    @Volatile override var isActive: Boolean = false
        private set

    private var lastBatteryLevel: Int = -1
    private var lastIsCharging: Boolean = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> emitSignal(Signal.ScreenStateChanged(isOn = true))
                Intent.ACTION_SCREEN_OFF -> emitSignal(Signal.ScreenStateChanged(isOn = false))

                Intent.ACTION_POWER_CONNECTED -> {
                    val level = getBatteryLevel(intent)
                    lastIsCharging = true
                    emitSignal(Signal.ChargingStateChanged(isCharging = true, batteryLevel = level))
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    val level = getBatteryLevel(intent)
                    lastIsCharging = false
                    emitSignal(Signal.ChargingStateChanged(isCharging = false, batteryLevel = level))
                }

                Intent.ACTION_BATTERY_CHANGED -> handleBatteryChanged(intent)
            }
        }
    }

    override fun start() {
        if (isActive) return
        isActive = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        context.registerReceiver(receiver, filter)
    }

    override fun stop() {
        if (!isActive) return
        isActive = false
        runCatching { context.unregisterReceiver(receiver) }
        _channel.close()
    }

    // ── private ──────────────────────────────────────────────────────────────

    private fun handleBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return

        val percent = (level * 100 / scale)
        val isCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let {
            it == BatteryManager.BATTERY_STATUS_CHARGING ||
                    it == BatteryManager.BATTERY_STATUS_FULL
        }

        // 충전 상태 변화 감지
        if (isCharging != lastIsCharging) {
            lastIsCharging = isCharging
            emitSignal(Signal.ChargingStateChanged(isCharging = isCharging, batteryLevel = percent))
        }

        // 임계치 교차 감지
        if (lastBatteryLevel >= 0) {
            checkThresholdCrossed(lastBatteryLevel, percent)
        }
        lastBatteryLevel = percent
    }

    /**
     * 배터리 레벨이 정의된 임계치를 교차했는지 확인하고 신호를 emit한다.
     * 예: 21% → 19% 이면 LOW(20) 임계치를 하향 교차 → crossedDown=true
     */
    private fun checkThresholdCrossed(prev: Int, curr: Int) {
        for (threshold in BATTERY_THRESHOLDS) {
            val crossedDown = prev >= threshold && curr < threshold
            val crossedUp = prev < threshold && curr >= threshold

            if (crossedDown || crossedUp) {
                emitSignal(
                    Signal.BatteryThresholdCrossed(
                        level = threshold,
                        crossedDown = crossedDown,
                    )
                )
            }
        }
    }

    private fun getBatteryLevel(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level >= 0 && scale > 0) level * 100 / scale else lastBatteryLevel
    }

    private fun emitSignal(signal: Signal) {
        bus.emit(signal)
        _channel.trySend(signal)
    }

    companion object {
        val BATTERY_THRESHOLDS = listOf(10, 20, 50, 80)
    }
}
