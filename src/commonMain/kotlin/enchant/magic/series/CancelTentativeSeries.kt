package enchant.magic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Runs only one coroutine at a time, when the series has a coroutine running it will ignore (never
 * run) all other coroutines added to the series.
 *
 * @see Series
 */
class CancelTentativeSeries(scope: CoroutineScope, debug: Boolean = false) : Series(scope, debug) {

    private class CancelTentativeJob(
        val job: Job, val label: String = "", val startTimestamp: Long = -1L
    )

    private var currentJob: CancelTentativeJob = CancelTentativeJob(Job().apply { complete() })

    /** If the series has no running coroutine, the added coroutine will begin executing immediately.
     * Otherwise, the added coroutine will never be ran.
     *
     * @see Series.addJob
     */
    override fun addJob(label: String, block: suspend CoroutineScope.() -> Unit): Job {
        return if (currentJob.job.isCompleted) {
            currentJob =
                if (!debug) CancelTentativeJob(launchScope.launch { block() }) else CancelTentativeJob(
                    launchScope.launch {
                        if (printChanges) {
                            println("$objectLabel: \"$label\" started")
                            coroutineContext.job.recordOnCompletion(label)
                        }
                        block()
                    },
                    label, Clock.System.now().toEpochMilliseconds()
                )
            currentJob.job
        } else {
            if (printChanges) println("$objectLabel: \"$label\" ignored")
            Job().apply { cancel() }
        }
    }

    /**
     * To understand how string conversion works for series, take a look at [Series.toString]
     *
     * The advanced string output will print the currently running coroutine (if there is one)
     * alongside the time it has been running for.
     *
     * Below is a sample [toString] output when debug mode is enabled:
     * ```
     * CancelTentativeSeries@df6620a running task:
     * "Task 1" - 21 ms
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
 * Convenience for making a [CancelTentativeSeries] in a [CoroutineScope], without needing to pass in the
 * [CoroutineScope] manually.
 *
 * @see Series
 */
fun CoroutineScope.CancelTentativeSeries(debug: Boolean = false) =
    CancelTentativeSeries(this, debug)