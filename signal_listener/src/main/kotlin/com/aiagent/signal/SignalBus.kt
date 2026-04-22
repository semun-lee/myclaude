package com.aiagent.signal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 모든 SignalSource에서 오는 신호를 단일 스트림으로 합산하는 버스.
 * replay=0: 구독 전 신호는 수신하지 않음 (과거 컨텍스트 오염 방지).
 * extraBufferCapacity=64: 느린 소비자가 있어도 빠른 소스가 블로킹되지 않음.
 */
class SignalBus {

    private val _flow = MutableSharedFlow<Signal>(
        replay = 0,
        extraBufferCapacity = 64,
    )

    val signals: SharedFlow<Signal> = _flow.asSharedFlow()

    /** 소스들이 신호를 게시할 때 사용. tryEmit 실패 시 유실(drop) 허용 — 배터리 절약 우선. */
    fun emit(signal: Signal) {
        _flow.tryEmit(signal)
    }
}
