package com.aiagent.signal.sources

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.view.accessibility.AccessibilityEvent
import com.aiagent.signal.GestureType
import com.aiagent.signal.Signal
import com.aiagent.signal.SignalBus
import com.aiagent.signal.SignalSource
import com.aiagent.signal.UIChangeType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * AccessibilityService를 통해 앱 전환, UI 변화, 터치 제스처를 감지한다.
 *
 * AccessibilityService는 Android에서 화면 캡처 없이 UI 상태를 알 수 있는
 * 가장 저전력 방법이다. 이벤트 기반이므로 폴링 비용이 없다.
 *
 * 사용법: AndroidManifest에 AgentAccessibilityService를 등록하고,
 * onAccessibilityEvent / onGesture를 SignalBus로 위임한다.
 */
class AccessibilitySignalSource(
    private val bus: SignalBus,
) : SignalSource {

    // AccessibilityService 콜백을 Flow로 연결하기 위한 채널.
    // CONFLATED: 처리가 느릴 때 최신 이벤트만 유지 (배터리 절약).
    private val _channel = Channel<Signal>(capacity = Channel.CONFLATED)

    override val signals: Flow<Signal> = _channel.receiveAsFlow()

    @Volatile override var isActive: Boolean = false
        private set

    private var lastPackage: String? = null

    override fun start() {
        isActive = true
    }

    override fun stop() {
        isActive = false
        _channel.close()
    }

    /**
     * AgentAccessibilityService.onAccessibilityEvent에서 호출.
     * 메인 스레드에서 호출되므로 최소한의 작업만 수행한다.
     */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isActive) return

        val signal = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(event)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleWindowContentChanged(event)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> Signal.UITreeChanged(
                changeType = UIChangeType.VIEW_CLICKED,
                sourcePackage = event.packageName?.toString(),
                sourceClassName = event.className?.toString(),
                contentDescription = event.contentDescription?.toString(),
            )
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> Signal.UITreeChanged(
                changeType = UIChangeType.VIEW_SCROLLED,
                sourcePackage = event.packageName?.toString(),
                sourceClassName = event.className?.toString(),
                contentDescription = null,
            )
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> Signal.UITreeChanged(
                changeType = UIChangeType.VIEW_FOCUSED,
                sourcePackage = event.packageName?.toString(),
                sourceClassName = event.className?.toString(),
                contentDescription = event.contentDescription?.toString(),
            )
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTextChanged(event)
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> Signal.UITreeChanged(
                changeType = UIChangeType.ANNOUNCEMENT,
                sourcePackage = event.packageName?.toString(),
                sourceClassName = null,
                contentDescription = event.text.joinToString(" "),
            )
            else -> null
        } ?: return

        bus.emit(signal)
        _channel.trySend(signal)
    }

    /**
     * AccessibilityService.onGesture에서 호출.
     * Android 9+ 에서 FLAG_REQUEST_TOUCH_EXPLORATION_MODE 권한 필요.
     */
    fun onGesture(gestureId: Int): Boolean {
        if (!isActive) return false

        val gestureType = when (gestureId) {
            AccessibilityService.GESTURE_SWIPE_UP -> GestureType.SWIPE_UP
            AccessibilityService.GESTURE_SWIPE_DOWN -> GestureType.SWIPE_DOWN
            AccessibilityService.GESTURE_SWIPE_LEFT -> GestureType.SWIPE_LEFT
            AccessibilityService.GESTURE_SWIPE_RIGHT -> GestureType.SWIPE_RIGHT
            AccessibilityService.GESTURE_DOUBLE_TAP -> GestureType.DOUBLE_TAP
            AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT,
            AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT -> GestureType.TWO_FINGER_SWIPE_UP
            else -> return false
        }

        val signal = Signal.TouchGesture(gestureType = gestureType)
        bus.emit(signal)
        _channel.trySend(signal)
        return false // 제스처를 소비하지 않음 (앱의 원래 동작 유지)
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private fun handleWindowStateChanged(event: AccessibilityEvent): Signal? {
        val newPackage = event.packageName?.toString() ?: return null

        // 같은 앱 내 액티비티/다이얼로그 전환은 AppTransition 대신 UITreeChanged로 처리.
        val isAppSwitch = newPackage != lastPackage
        lastPackage = newPackage

        return if (isAppSwitch) {
            Signal.AppTransition(
                fromPackage = lastPackage,
                toPackage = newPackage,
                toActivityClass = event.className?.toString(),
            )
        } else {
            Signal.UITreeChanged(
                changeType = UIChangeType.WINDOW_STATE_CHANGED,
                sourcePackage = newPackage,
                sourceClassName = event.className?.toString(),
                contentDescription = null,
            )
        }
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent): Signal? {
        // ContentChanged는 매우 빈번하게 발생한다 (애니메이션, 시계 등).
        // 패키지명이 있는 경우만 처리하여 노이즈를 줄인다.
        val pkg = event.packageName?.toString() ?: return null

        return Signal.UITreeChanged(
            changeType = UIChangeType.WINDOW_CONTENT_CHANGED,
            sourcePackage = pkg,
            sourceClassName = event.className?.toString(),
            contentDescription = null,
        )
    }

    private fun handleTextChanged(event: AccessibilityEvent): Signal? {
        val pkg = event.packageName?.toString() ?: return null

        return Signal.UITreeChanged(
            changeType = UIChangeType.VIEW_TEXT_CHANGED,
            sourcePackage = pkg,
            sourceClassName = event.className?.toString(),
            contentDescription = event.text.joinToString(""),
        )
    }

    companion object {
        /** 필요한 AccessibilityServiceInfo 설정값 */
        fun buildServiceInfo(): AccessibilityServiceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            // 이벤트 스로틀링: 100ms 이내 동일 유형 이벤트는 하나만 전달.
            // 화면 업데이트가 잦은 앱에서 과도한 이벤트를 방지한다.
            notificationTimeout = 100
        }
    }
}

/**
 * AccessibilitySignalSource와 연동되는 실제 Android AccessibilityService.
 * AndroidManifest에 등록하고 AccessibilitySignalSource를 주입받아 사용한다.
 */
abstract class AgentAccessibilityService : AccessibilityService() {

    abstract val signalSource: AccessibilitySignalSource

    override fun onServiceConnected() {
        serviceInfo = AccessibilitySignalSource.buildServiceInfo()
        signalSource.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        signalSource.onAccessibilityEvent(event)
    }

    override fun onGesture(gestureId: Int): Boolean {
        return signalSource.onGesture(gestureId)
    }

    override fun onInterrupt() {
        signalSource.stop()
    }

    override fun onDestroy() {
        signalSource.stop()
        super.onDestroy()
    }
}
