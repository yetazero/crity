package com.yetazero.crity.display

internal class TargetLease<T>(val target: T, private var lastHitNanos: Long) {
    fun refresh(now: Long) {
        lastHitNanos = now
    }

    fun remainingSeconds(now: Long, durationMs: Int): Float =
        ((durationMs * 1_000_000L - (now - lastHitNanos)).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
}
