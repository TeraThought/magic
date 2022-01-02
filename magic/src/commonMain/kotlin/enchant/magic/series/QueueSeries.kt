package enchant.magic

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.coroutines.CancellationException

/** Adds coroutines in a queue, where each coroutine is executed one at a time, so that only one
 * coroutine is active at a time. The coroutines are executed in the order they were added.
 *
 * @see Series
 */
class QueueSeries(scope: CoroutineScope, debug: Boolean = false) : Series(scope, debug) {

    init {
        scope.coroutineContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) queue.removeAll { it.job.cancel(cause); true }
        }
        launchScope.coroutineContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) queue.removeAll { it.job.cancel(cause); true }
        }
    }

    private class QueueJob(
        val block: suspend CoroutineScope.() -> Unit, val job: CompletableJob,
        val label: String = "", var startTimestamp: Long = -1L,
    )

    private val queue = mutableListOf<QueueJob>()
    private var isQueueActive = false

    /**
     * Adds a coroutine to the queue. After adding, if there's only one coroutine in the queue the
     * added will be executed immediately. Otherwise the coroutine will wait until it is at the top
     * of the queue, and then begin executing.
     *
     * Canceling the [Job] of the added coroutine before it begins running will remove the coroutine
     * from the queue and not execute it. Canceling the [Job] while the coroutine is running will
     * stop the execution of the coroutine and begin running the next coroutine in the queue (if
     * there is one).
     *
     * @see Series.addJob
     * */
    override fun addJob(label: String, block: suspend CoroutineScope.() -> Unit): Job {
        val job = Job()
        queue += QueueJob(block, job, label)
        if (printChanges) {
            job.recordOnCompletion(label)
            if (queue.size > 1) println("$objectLabel: \"$label\" queued")
        }
        if (queue.size == 1 && !isQueueActive) launchScope.launch {
            isQueueActive = true
            while (queue.isNotEmpty()) {
                val queueJob = queue.first()
                if (!queueJob.job.isCancelled) {
                    if (debug) queueJob.startTimestamp =
                        Clock.System.now().toEpochMilliseconds()
                    val result = launch(queueJob.job) {
                        if (printChanges) println("$objectLabel: \"${queueJob.label}\" started")
                        queueJob.block(this)
                    }
                    result.join()
                    if (!queueJob.job.isCancelled) {
                        if (result.isCancelled) queueJob.job.cancel(result.getCancellationException())
                        else queueJob.job.complete()
                    }
                }
                queue.removeFirst()
            }
            isQueueActive = false
        }
        return job
    }

    /**
     * To understand how string conversion works for series, take a look at [Series.toString]
     *
     * The advanced string output will print all queued tasks in the order they were added in. It
     * will show the currently running task (if there is one) first, alongside the time it has been
     * running for. Afterwards, it will print queued coroutines without their running times.
     *
     * Below is a sample [toString] output when debug mode is enabled:
     * ```
     * QueueSeries@5717c37 queued tasks:
     * "Task 1" - 10 ms
     * "noId"
     * "Task 3"
     * ```
     * @see Series.toString
     */
    override fun toString(): String = if (!debug) super.toString() else {
        if (queue.isEmpty()) "$objectLabel has no queued tasks" else {
            "$objectLabel queued tasks:\n" + queue.joinToString("\n") {
                "\"${it.label}\"" + if (it.startTimestamp != -1L) " - ${
                    Clock.System.now().toEpochMilliseconds() - it.startTimestamp
                } ms" else ""
            }
        }
    }
}

/**
 * Convenience for making a [QueueSeries] in a [CoroutineScope], without needing to pass in the
 * [CoroutineScope] manually.
 *
 * @see Series
 */
fun CoroutineScope.QueueSeries(debug: Boolean = false) = QueueSeries(this, debug)
