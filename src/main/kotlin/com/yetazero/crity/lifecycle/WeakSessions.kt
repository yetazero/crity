package com.yetazero.crity.lifecycle

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class WeakSessions<T : Any> {
    private class Entry<T : Any>(val id: UUID, value: T, queue: ReferenceQueue<T>) : WeakReference<T>(value, queue)
    private val queue = ReferenceQueue<T>()
    private val entries = ConcurrentHashMap<UUID, Entry<T>>()

    private fun drain() {
        while (true) {
            val entry = queue.poll() as? Entry<T> ?: return
            entries.remove(entry.id, entry)
        }
    }

    operator fun get(id: UUID): T? {
        drain()
        return entries[id]?.get()
    }

    fun put(id: UUID, value: T) {
        drain()
        entries[id] = Entry(id, value, queue)
    }

    fun remove(id: UUID, expected: T) {
        drain()
        val entry = entries[id]
        if (entry?.get() === expected) entries.remove(id, entry)
    }

    fun values(): List<T> {
        drain()
        return entries.values.mapNotNull { it.get() }
    }

    fun clear() {
        entries.clear()
        drain()
    }
}
