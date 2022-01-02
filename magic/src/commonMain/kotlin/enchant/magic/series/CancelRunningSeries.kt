package enchant.magic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Ensures that only the most recently added coroutine is running, allowing for only one coroutine
 * to be executing.
 *
 * @see Series
 * */
class CancelRunningSeries(scope: CoroutineScope, debug: Boolean = false) :
    Series(scope, debug) {

    private class CancelRunningJob(
        val job: Job, val label: String = "", val startTimestamp: Long = -1L
    )

    private var currentJob: CancelRunningJob = CancelRunningJob(Job().apply { complete() })

    /** When a new coroutine is added, the currently running coroutine is canceled, and the newly
     * added coroutine begins running.
     *
     * If the [Job] for the newly added coroutine is canceled, the series will have no coroutine
     * executing, leaving space for a new coroutine to be added and ran.
     *
     * @see Series.addJob
     * */
    override fun addJob(label: String, block: suspend CoroutineScope.() -> Unit): Job {
        if (currentJob.job.isActive) currentJob.job.cancel()
        currentJob = if (!debug) CancelRunningJob(launchScope.launch { block() }) else
            CancelRunningJob(
                launchScope.launch {
                    if (printChanges) {
                        println("$objectLabel: \"$label\" started")
                        coroutineContext.job.recordOnCompletion(label)
                    }
                    block()
                }, label, Clock.System.now().toEpochMilliseconds()
            )
        return currentJob.job
    }

    /**
     * To understand how string conversion works for series, take a look at [Series.toString]
     *
     * The advanced string output will print the currently running coroutine (if there is one)
     * alongside the time it has been running for.
     *
     * Below is a sample [toString] output when debug mode is enabled:
     * ```
     * CancelRunningSeries@df6620a running task:
     * "noId" - 43 ms
     * ```
     */
    override fun toString(): String = if (!debug) super.toString() else {
        if (currentJob.job.isCompleted) "$objectLabel has no running task" else {
            "$objectLabel running task:\n\"${currentJob.label}\" - ${
                Clock.System.now().toEpochMilliseconds() - currentJob.startTimestamp
            } ms"
        }
    }
}

/**
 * Convenience for making a [CancelRunningSeries] in a [CoroutineScope], without needing to pass in the
 * [CoroutineScope] manually.
 *
 * @see Series
 */
fun CoroutineScope.CancelRunningSeries(debug: Boolean = false) = CancelRunningSeries(this, debug)