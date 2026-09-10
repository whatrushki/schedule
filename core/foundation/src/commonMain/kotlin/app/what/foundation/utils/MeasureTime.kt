package app.what.foundation.utils

import kotlin.time.TimeSource

fun measureTime(block: () -> Unit): Long {
    val mark = TimeSource.Monotonic.markNow()
    block()
    return mark.elapsedNow().inWholeNanoseconds
}

suspend fun suspendMeasureTime(block: suspend () -> Unit): Long {
    val mark = TimeSource.Monotonic.markNow()
    block()
    return mark.elapsedNow().inWholeNanoseconds
}