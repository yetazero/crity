package com.yetazero.crity.compat

import com.hypixel.hytale.server.core.HytaleServer
import com.hypixel.hytale.server.core.universe.world.World
import java.lang.reflect.Method
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Runs a job on a world's thread after a delay. Older servers have World.scheduleAfter; Update 8 removed it, and a module that still called it
 * stopped with a NoSuchMethodError (the target health bar was left on screen for good). The server's own scheduler does the waiting
 * and the world still runs the job, so both kinds of server work with one jar.
 */
internal object WorldTimer {
    private val legacy: Method? = runCatching {
        World::class.java.getMethod("scheduleAfter", Runnable::class.java, java.lang.Long.TYPE, TimeUnit::class.java)
    }.getOrNull()

    /** The server's own scheduler (getTaskScheduler), looked up by name so a server without it still compiles and loads. */
    private val scheduler: Method? = runCatching { HytaleServer::class.java.getMethod("getTaskScheduler") }.getOrNull()

    @Suppress("UNCHECKED_CAST")
    fun after(world: World, task: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        legacy?.let { return it.invoke(world, task, delay, unit) as ScheduledFuture<*> }
        val executor = checkNotNull(scheduler) { "This server has neither World.scheduleAfter nor a task scheduler" }
            .invoke(HytaleServer.get()) as ScheduledExecutorService
        return executor.schedule({
            try {
                if (world.isAlive) world.execute(task)
            } catch (_: RuntimeException) {
                // the world stopped between the check and the queueing
            }
        }, delay, unit)
    }
}
