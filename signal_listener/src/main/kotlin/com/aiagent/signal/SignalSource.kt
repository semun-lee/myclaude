package com.aiagent.signal

import kotlinx.coroutines.flow.Flow

/**
 * 개별 신호 소스의 공통 인터페이스.
 * 각 소스는 독립적으로 시작/중단 가능하며 Flow로 신호를 emit한다.
 */
interface SignalSource {
    /** 이 소스가 활성화되어 있는지 여부 */
    val isActive: Boolean

    /** 신호 스트림. collect하는 동안 신호를 emit한다. */
    val signals: Flow<Signal>

    /** 신호 수신 시작. 멱등(idempotent)해야 한다. */
    fun start()

    /** 신호 수신 중단 및 리소스 해제. */
    fun stop()
}
