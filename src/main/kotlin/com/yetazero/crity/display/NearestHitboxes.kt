package com.yetazero.crity.display

import java.util.PriorityQueue

internal class NearestHitboxes<T>(private val limit: Int, range: Int) {
    private data class Entry<T>(val value: T, val distanceSquared: Double)
    private val rangeSquared = range.toDouble() * range
    private val nearest = PriorityQueue<Entry<T>>(compareByDescending { it.distanceSquared })

    fun offer(value: T, distanceSquared: Double) {
        if (!distanceSquared.isFinite() || distanceSquared < 0 || distanceSquared > rangeSquared || limit <= 0) return
        if (nearest.size < limit) nearest.add(Entry(value, distanceSquared))
        else if (distanceSquared < nearest.peek().distanceSquared) {
            nearest.poll()
            nearest.add(Entry(value, distanceSquared))
        }
    }

    fun values(): List<T> = nearest.sortedBy { it.distanceSquared }.map { it.value }
}
