package enchant.magic

import kotlinx.coroutines.*
import kotlin.coroutines.cancellation.CancellationException


abstract class Series(scope: CoroutineScope) {

    init {
        scope.coroutineContext.job.invokeOnCompletion(onCancelling = true) {
            if (it is CancellationException) job.cancel(it)
        }
    }

    private val job = Job()
    protected val launchScope = CoroutineScope(scope.coroutineContext + job)

    /** Adds a new task/coroutine w/ a given [block] of code to a series.
     *
     * @param block Just a block of code. A lambda that returns Unit.
     *
     * "CoroutineScope" references the [CoroutineScope] that the parent [Series] extends - no impact or specification required.*/
    fun add(block: suspend CoroutineScope.() -> Unit) {
        addJob(block)
    }

    /**
     * Similar to [add] but returns the associated [Job] with the coroutine afterward
     */
    abstract fun addJob(block: suspend CoroutineScope.() -> Unit): Job

    /** Cancels all of the coroutines added to this series **/
    fun cancel(cause: CancellationException? = null): Unit = launchScope.cancel(cause)

    /** Convenience for canceling all of the coroutines added to this series.
     * @see Series.cancel
     **/
    fun cancel(message: String, cause: Throwable? = null): Unit =
        cancel(CancellationException(message, cause))
}

/** Launches each coroutine independently and simultaneously, equivalent to [CoroutineScope.launch]
 *
 * See [Magic Series diagram](https://bit.ly/magic-series) for a timeline flow of different series types
 */
class DefaultSeries(scope: CoroutineScope) : Series(scope) {
    /** [launch]es the [block] passed in independently
     *
     * @param block The actual code you want to run in the coroutine*/
    override fun addJob(block: suspend CoroutineScope.() -> Unit): Job = launchScope.launch { block() }
}

fun CoroutineScope.DefaultSeries() = DefaultSeries(this)

/** Waits for the previous coroutine to finish executing before running the latest coroutine
 *
 * See [Magic Series diagram](https://bit.ly/magic-series) for a timeline flow of different series types
 */

class QueueSeries(scope: CoroutineScope) : Series(scope) {

    init {
        scope.coroutineContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) queue.removeAll { it.job.cancel(cause); true }
        }
        launchScope.coroutineContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) queue.removeAll { it.job.cancel(cause); true }
        }
    }

    private class QueueJob(
        val block: suspend CoroutineScope.() -> Unit,
        val job: CompletableJob
    )

    /** An ordered list of coroutines/tasks to be executed in the series*/
    private val queue = mutableListOf<QueueJob>()

    /* Overrides the default [Series.add] function.
     *
     * 1. Adds [block] to the end of the [queue]
     * 2. Removes the first coroutine/tasks from the [queue] (always the same as [block])
     * 3. Runs the new "first" coroutine. "this" references the parent CoroutineScope */
    private var isQueueActive = false

    /**
     * Adds the given [block] to the queue. If there's only 1 task in the queue, the task will be
     * executed immediately.
     *
     * @param block The actual code you want to run in the coroutine task*/
    override fun addJob(block: suspend CoroutineScope.() -> Unit): Job {
        val job = Job()
        queue += QueueJob(block, job)

        if (queue.size == 1 && !isQueueActive) launchScope.launch {
            isQueueActive = true
            while (queue.isNotEmpty()) {
                val queueJob = queue.first()
                if (!queueJob.job.isCancelled) {
                    val result = launch(queueJob.job, block = queueJob.block)
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
}

fun CoroutineScope.QueueSeries() = QueueSeries(this)

/** Cancels the coroutine that is currently being executed and runs the latest coroutine
 *
 * See [Magic Series diagram](https://bit.ly/magic-series) for a timeline flow of different series types
 * */
class CancelRunningSeries(scope: CoroutineScope) : Series(scope) {
    private var currentJob: Job = Job().apply { complete() }

    /** Cancels the current job running (if there is one), and begins executing the [block] passed in. */
    override fun addJob(block: suspend CoroutineScope.() -> Unit): Job {
        if (currentJob.isActive) currentJob.cancel()
        currentJob = launchScope.launch { block() }
        return currentJob
    }
}

fun CoroutineScope.CancelRunningSeries() = CancelRunningSeries(this)

/** Ignores the latest coroutine and lets the current coroutine continue running
 *
 * See [Magic Series diagram](https://bit.ly/magic-series) for a timeline flow of different series types
 * */
class CancelTentativeSeries(scope: CoroutineScope) : Series(scope) {
    private var currentJob: Job = Job().apply { complete() }

    /** When there's nothing currently running the [block] will be executed. When there is
     * something running already, the [block] will be ignored.
     */
    override fun addJob(block: suspend CoroutineScope.() -> Unit): Job {
        return if (currentJob.isCompleted) {
            currentJob = launchScope.launch { block() }
            currentJob
        } else Job().apply { cancel() }
    }
}

fun CoroutineScope.CancelTentativeSeries() = CancelTentativeSeries(this)