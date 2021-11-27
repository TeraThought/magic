package enchant.magic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Launches each coroutine independently and simultaneously, equivalent to [CoroutineScope.launch].
 * However, unlike a traditional [CoroutineScope], series have useful [printChanges] and [toString]
 * capabilities.
 *
 *
 * @see Series
 */
class DefaultSeries(scope: CoroutineScope, debug: Boolean = false) : Series(scope, debug) {

    private class DefaultSeriesJob(
        val label: String,
        val startTimestamp: Long = Clock.System.now().toEpochMilliseconds()
    )

    private val tasks = mutableSetOf<DefaultSeriesJob>()

    /** Adds a coroutine and immediately begins executing it independently of other added
     * coroutines. If the added coroutine is canceled, no other coroutines will be affected.
     *
     * @see Series.addJob
     * */
    override fun addJob(label: String, block: suspend CoroutineScope.() -> Unit): Job =
        launchScope.launch {
            if (!debug) block()
            else {
                val defaultSeriesJob = DefaultSeriesJob(label)
                tasks.add(defaultSeriesJob)
                if (printChanges) {
                    println("$objectLabel: \"$label\" started")
                    coroutineContext.job.recordOnCompletion(label)
                }
                block()
                tasks.remove(defaultSeriesJob)
            }
        }

    /**
     * To understand how string conversion works for series, take a look at [Series.toString]
     *
     * Below is a sample [toString] output when debug mode is enabled:
     * ```
     * DefaultSeries@2812b107 current tasks:
     * "Task 1" - 15 ms
     * "noId" - 2 ms
     * ```
     * @see Series.toString
     */
    override fun toString(): String = if (!debug) super.toString() else {
        if (tasks.isEmpty()) "$objectLabel has no running tasks"
        else {
            val currentTimestamp = Clock.System.now().toEpochMilliseconds()
            "$objectLabel current tasks:\n" + tasks.sortedBy { it.startTimestamp }
                .joinToString("\n") { "\"${it.label}\" - ${currentTimestamp - it.startTimestamp} ms" }
        }
    }
}

/**
 * Convenience for making a [DefaultSeries] in a [CoroutineScope], without needing to pass in the
 * [CoroutineScope] manually.
 *
 * @see Series
 */
fun CoroutineScope.DefaultSeries(debug: Boolean = false) = DefaultSeries(this, debug)