package enchant.magic

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlin.coroutines.cancellation.CancellationException


abstract class Series(scope: CoroutineScope, protected val debug: Boolean) {

    protected var printChanges = false

    fun printChanges(enabled: Boolean) {
        if (!debug && enabled) throw error("Cannot enable printChanges in non-debug mode")
        printChanges = enabled
    }

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
    fun add(label: String = "noId", block: suspend CoroutineScope.() -> Unit) {
        addJob(label, block)
    }

    /**
     * Similar to [add] but returns the associated [Job] with the coroutine afterward
     */
    abstract fun addJob(label: String = "noId", block: suspend CoroutineScope.() -> Unit): Job

    /** Cancels all of the coroutines added to this series **/
    fun cancel(cause: CancellationException? = null) {
        if (printChanges) println("$objectLabel cancelled")
        launchScope.cancel(cause)
    }

    /** Convenience for canceling all of the coroutines added to this series.
     * @see Series.cancel
     **/
    fun cancel(message: String, cause: Throwable? = null): Unit =
        cancel(CancellationException(message, cause))

    //enchant.potion.
    protected val objectLabel by lazy { super.toString().substring(14) }

    protected fun Job.recordOnCompletion(label: String) {
        invokeOnCompletion { cause ->
            if (printChanges) when (cause) {
                null -> println("$objectLabel: \"$label\" completed successfully")
                is CancellationException -> println("$objectLabel: \"$label\" cancelled")
                else -> println("$objectLabel: \"$label\" failed with an error")
            }
        }
    }
}

/** Launches each coroutine independently and simultaneously, equivalent to [CoroutineScope.launch]
 *
 * See [Magic Series diagram](https://bit.ly/magic-series) for a timeline flow of different series types
 */
class DefaultSeries(scope: CoroutineScope, debug: Boolean = false) :
    Series(scope, debug) {

    private class DefaultSeriesJob(
        val label: String,
        val startTimestamp: Long = Clock.System.now().toEpochMilliseconds()
    )

    private val tasks = mutableSetOf<DefaultSeriesJob>()

    /** [launch]es the [block] passed in independently
     *
     * @param block The actual code you want to run in the coroutine*/
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

    override fun toString(): String = if (!debug) super.toString() else {
        if (tasks.isEmpty()) "$objectLabel has no running tasks"
        else {
            val currentTimestamp = Clock.System.now().toEpochMilliseconds()
            "$objectLabel current tasks:\n" + tasks.sortedBy { it.startTimestamp }
                .joinToString("\n") { "\"${it.label}\" - ${currentTimestamp - it.startTimestamp} ms" }
        }
    }
}

fun CoroutineScope.DefaultSeries(debug: Boolean = false) = DefaultSeries(this, debug)

/** Waits for the previous coroutine to finish executing before running the latest coroutine
 *
 * See [Magic Series diagram](https://bit.ly/magic-series) for a timeline flow of different series types
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

fun CoroutineScope.QueueSeries(debug: Boolean = false) = QueueSeries(this, debug)

/** Cancels the coroutine that is currently being executed and runs the latest coroutine
 *
 * See [Magic Series diagram](https://bit.ly/magic-series) for a timeline flow of different series types
 * */
class CancelRunningSeries(scope: CoroutineScope, debug: Boolean = false) :
    Series(scope, debug) {

    private class CancelRunningJob(
        val job: Job, val label: String = "", val startTimestamp: Long = -1L
    )

    private var currentJob: CancelRunningJob = CancelRunningJob(Job().apply { complete() })

    /** Cancels the current job running (if there is one), and begins executing the [block] passed in. */
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

    override fun toString(): String = if (!debug) super.toString() else {
        if (currentJob.job.isCompleted) "$objectLabel has no running task" else {
            "$objectLabel running task:\n\"${currentJob.label}\" - ${
                Clock.System.now().toEpochMilliseconds() - currentJob.startTimestamp
            } ms"
        }
    }
}

fun CoroutineScope.CancelRunningSeries(debug: Boolean = false) = CancelRunningSeries(this, debug)

/** Ignores the latest coroutine and lets the current coroutine continue running
 *
 * See [Magic Series diagram](https://bit.ly/magic-series) for a timeline flow of different series types
 * */
class CancelTentativeSeries(scope: CoroutineScope, debug: Boolean = false) :
    Series(scope, debug) {

    private class CancelTentativeJob(
        val job: Job, val label: String = "", val startTimestamp: Long = -1L
    )

    private var currentJob: CancelTentativeJob = CancelTentativeJob(Job().apply { complete() })

    /** When there's nothing currently running the [block] will be executed. When there is
     * something running already, the [block] will be ignored.
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

    override fun toString(): String = if (!debug) super.toString() else {
        if (currentJob.job.isCompleted) "$objectLabel has no running task" else {
            "$objectLabel running task:\n\"${currentJob.label}\" - ${
                Clock.System.now().toEpochMilliseconds() - currentJob.startTimestamp
            } ms"
        }
    }
}

fun CoroutineScope.CancelTentativeSeries(debug: Boolean = false) =
    CancelTentativeSeries(this, debug)