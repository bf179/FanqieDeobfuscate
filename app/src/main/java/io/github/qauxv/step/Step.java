package io.github.qauxv.step

/**
 * [FanqieDeobfuscate] Minimal stub for Step interface.
 */
interface Step {
    fun step(): Boolean
    fun isDone(): Boolean
    fun getPriority(): Int
    fun getDescription(): String
}
