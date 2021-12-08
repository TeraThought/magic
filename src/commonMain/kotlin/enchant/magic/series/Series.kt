package enchant.magic

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.coroutines.CancellationException

/**
 * Tools for managing the order of execution of [add]ed coroutines. [Series] is the general
 * interface that defines behavior common amongst different [Series] implementations.
 *
 * Currently there are four different variants of series:
 * - [DefaultSeries] - Runs all coroutines at the same time
 * - [QueueSeries] - Runs coroutines one by one in the order they were added in
 * - [CancelRunningSeries] - Runs the most recently added coroutine, canceling others in progress
 * - [CancelTentativeSeries] - Runs the first added coroutine, ignoring other "added" coroutines
 *
 * See the [Magic Series diagram](https://bit.ly/magic-series) which visually demonstrates the
 * control flow and order of execution for the different Series variants.
 *
 * @param scope The parent [CoroutineScope] that the [Series] should be created and maintained under.
 * If the scope is canceled while the series is active, the series and its running coroutines will
 * be canceled.
 *
 * @param debug If true, enables debug mode. THis allows for an advanced [Series.toString]
 * printout and logging from [printChanges]. Since the behavior may increases memory usage and slow
 * down coroutine scheduling, this option is not meant for production environments.
 */
@Suppress("UNREACHABLE_CODE")
abstract class Series(scope: CoroutineScope, protected val debug: Boolean) {

    protected var printChanges = false

    /**
     * Enables or disables the option to print out all coroutine operations (start, stop, cancel,
     * etc.) executed for this series. Only can be enabled if [debug] is set to true.
     *
     * Here is an example of the printChanges output for a [DefaultSeries]
     * ```
     * DefaultSeries@291f18: "First Coroutine" started
     * DefaultSeries@291f18: "noId" started
     * DefaultSeries@291f18: "Final Coroutine" started
     * DefaultSeries@291f18: "First Coroutine" cancelled
     * DefaultSeries@291f18: "noId" cancelled
     * DefaultSeries@291f18: "Final Coroutine" completed successfully
     * ```
     *
     * @param enabled Controls whether the print changes output should print coroutine operations.
     * @throws IllegalArgumentException if printChanges attempts to be enabled without debug mode.
     */
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


    /** Adds a coroutine with the given [label] and [block] of code to the series. Returns the [Job]
     * created for the added coroutine.
     *
     * @param label A string that refers to the added coroutine. The label does not need to be
     * unique and is not used as an identifier. It only is used for the outputs in [Series.toString]
     * and [printChanges] in [debug] mode. In non-[debug] mode, the [label] is ignored.
     *
     * @param block The asynchronous code to be executed inside the added coroutine. The coroutine
     * has its own [CoroutineScope] provisioned through [CoroutineScope.launch] so it is
     * independently cancelable.
     *
     * @return The [Job] associated with the coroutine so that the coroutine is cancelable through
     * [Job.cancel] and can be checked for completion via [Job.isCompleted].
     */
    abstract fun addJob(label: String = "noId", block: suspend CoroutineScope.() -> Unit): Job

    /**
     * A convenience for [addJob] that doesn't return the created coroutine [Job]. Primarily useful
     * for equals "=" functions, where you may intend to return [Unit] instead of the coroutine's
     * [Job] object.
     *
     * @see addJob
     */
    fun add(label: String = "noId", block: suspend CoroutineScope.() -> Unit) {
        addJob(label, block)
    }

    /** Cancels all of the coroutines added to the series. After [cancel] is called, the series
     * is unusable. However, the [CoroutineScope] the series was created under will still be usable
     * (if [cancel] was not called automatically).
     *
     * @param cause The coroutine [CancellationException] that may have a message for why the
     * series is being canceled. Use the convenience [cancel] method to avoid creating this object
     * manually
     * **/
    fun cancel(cause: CancellationException? = null) {
        if (printChanges) println("$objectLabel cancelled")
        launchScope.cancel(cause)
    }

    /** Convenience for canceling all of the coroutines added to this series.
     *
     * @param message The reason for why this series is being canceled
     * @param cause An optional [Throwable] error that may be the cause for the cancellation
     *
     * @see Series.cancel
     **/
    fun cancel(message: String, cause: Throwable? = null): Unit =
        cancel(CancellationException(message, cause))


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

    /**
     * If [debug] mode is not enabled, gives the traditional [toString] output:
     * ```
     * DefaultSeries@2812b107
     * ```
     * If [debug] mode is enabled, gives a more detailed output that includes each coroutine's label
     * (using "noId" if the coroutine has no label) and how long the coroutine has been executing
     * for (in milliseconds). Checkout [DefaultSeries.toString] for an example.
     *
     * @see DefaultSeries.toString
     */
    @Suppress("RedundantOverride")
    override fun toString() = super.toString()
}