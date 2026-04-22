package com.aiagent.signal

/**
 * 모든 저비용 시스템 신호를 표현하는 sealed class 계층.
 * 화면 캡처 없이 감지 가능한 이벤트만 포함한다.
 */
sealed class Signal {
    abstract val timestamp: Long

    // ── Accessibility ────────────────────────────────────────────────────────

    /** 포그라운드 앱이 바뀐 경우 */
    data class AppTransition(
        val fromPackage: String?,
        val toPackage: String,
        val toActivityClass: String?,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    /** AccessibilityEvent 기반 UI 트리 변화 */
    data class UITreeChanged(
        val changeType: UIChangeType,
        val sourcePackage: String?,
        val sourceClassName: String?,
        val contentDescription: String?,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    /** AccessibilityService가 감지한 터치 제스처 */
    data class TouchGesture(
        val gestureType: GestureType,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    // ── IME / Keyboard ───────────────────────────────────────────────────────

    /** 소프트 키보드 표시/숨김 전환 */
    data class KeyboardVisibilityChanged(
        val isVisible: Boolean,
        /** android.text.InputType 플래그 조합 */
        val inputType: Int,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    /** 키 입력 버스트: 짧은 시간 내 연속 타이핑 시작/종료 */
    data class TypingBurstChanged(
        val isActive: Boolean,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    // ── Sensor ───────────────────────────────────────────────────────────────

    /** 사용자가 기기를 집어든 것으로 판단됨 (Lift-to-wake 유사) */
    data class DeviceLifted(
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    /** 사용자가 기기를 내려놓은 것으로 판단됨 */
    data class DevicePutDown(
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    /** 걷기/달리기 등 지속 이동 상태 변화 */
    data class MotionStateChanged(
        val motionState: MotionState,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    // ── Screen & Power ───────────────────────────────────────────────────────

    /** 화면 켜짐/꺼짐 */
    data class ScreenStateChanged(
        val isOn: Boolean,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    /** 충전 상태 변화 */
    data class ChargingStateChanged(
        val isCharging: Boolean,
        val batteryLevel: Int,       // 0–100
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    /** 배터리 수준 임계치 교차 */
    data class BatteryThresholdCrossed(
        val level: Int,
        val crossedDown: Boolean,    // true = 임계치 아래로 내려감
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    // ── Audio ────────────────────────────────────────────────────────────────

    /** 마이크 근처에서 음성 활동 감지됨 */
    data class VoiceActivityDetected(
        val durationMs: Long,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    /** 음성 활동 종료 */
    data class VoiceActivityEnded(
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    // ── Network ──────────────────────────────────────────────────────────────

    /** 특정 앱의 네트워크 트래픽 급증 (앱 활동 간접 신호) */
    data class NetworkBurst(
        val packageName: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    // ── Notification ────────────────────────────────────────────────────────

    /** 새 알림 게시됨 */
    data class NotificationPosted(
        val packageName: String,
        val category: String?,       // android.app.Notification.CATEGORY_*
        val hasAction: Boolean,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()

    /** 알림 제거됨 (사용자 스와이프 or 앱 취소) */
    data class NotificationRemoved(
        val packageName: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : Signal()
}

// ── Supporting enums ─────────────────────────────────────────────────────────

enum class UIChangeType {
    WINDOW_STATE_CHANGED,
    WINDOW_CONTENT_CHANGED,
    VIEW_CLICKED,
    VIEW_SCROLLED,
    VIEW_FOCUSED,
    VIEW_TEXT_CHANGED,
    ANNOUNCEMENT,
}

enum class GestureType {
    SINGLE_TAP,
    DOUBLE_TAP,
    LONG_PRESS,
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    TWO_FINGER_SWIPE_UP,
}

enum class MotionState {
    STILL,
    WALKING,
    RUNNING,
    VEHICLE,
}
