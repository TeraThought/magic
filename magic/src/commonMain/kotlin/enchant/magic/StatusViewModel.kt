package enchant.magic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException

/**
 * A more advanced version of [ViewModel] that integrates [Status] to represent the progress of
 * certain operations:
 *
 * - The process of creating states of type [Status] is automated through the status map, which
 * bundles all status states together. Keys of a custom (enum) type [T] represent the keys which can
 * be used to [get] the values of status states.
 *
 * - The [status] and [singleStatus] builders are able to track the progress of running code and
 * output to a given status state. This allows for automatic error handling, assuming that functions
 * from MVVM models throw errors. These errors are caught and converted to the [Issue] type via
 * [mapResult], which can be overloaded to add conversion to [Status.code].
 *
 * - Includes status states within the status map in [ViewModel.toString] and [ViewModel.printChanges]
 */
open class StatusViewModel<T>(debug: Boolean = false) : ViewModel(debug) {

    /**
     * Returns the associated [Status] value for the [key]. If the status state at the current key
     * has not been set yet, [NotStarted] is returned
     *
     * @param key The unique status key/identifier that identifies the status state to access
     */
    operator fun get(key: T): Status = statuses[key]

    protected val statuses = StatusMap<T>(::refresh)

    protected inner class StatusMap<T>(val refresh: () -> Unit) {
        val map = hashMapOf<T, Status>()

        operator fun get(key: T): Status = map[key] ?: NotStarted()


        /** Resets all [Status] values to NotStarted */
        fun reset() = map.clear()

        /** Updates the value of the status state at the given [key] and refreshes the ViewModel */
        operator fun set(key: T, value: Status) {
            map[key] = value
            if (printChanges) println("$objectLabel: [$key] = $value")
            refresh()
        }

        override fun toString(): String =
            map.toList().sortedBy { it.first.toString() }
                .joinToString("\n") { "[${it.first}] = ${it.second}" }
    }


    /** After the [status] or [singleStatus] builder finishes running its contained code, [mapResult]
     * is called with the code's [result]. The [result] will be [Result.success] if the code finished
     * executing completely. Otherwise, the [result] will be [Result.failure], including the
     * [Throwable] thrown by the code. After [mapResult] returns the [Status] converted from [Result],
     * the status state within the status builder will be set to the returned status.
     *
     * This function should be overridden to add custom error code behavior, so that the UI can
     * understand the issue thrown by the code.
     */
    protected open fun mapResult(result: Result<Unit>): Status =
        if (result.isSuccess) Success() else Issue()

    /** The status builder monitors the progress of the given [action] code and outputs it to the
     * status state at the provided [key]. The status state will automatically update based upon the
     * code that is running inside of it:
     * - When the code is running, the status is set to [Loading]
     * - When the code completes successfully, the status is set to [Success]
     * - When the code throws a system error, the error is sent to [mapResult] and converted to [Issue]
     *
     * Note: When the code throws an error, the status builder stop executing the code, cancel the
     * coroutine its running inside of, and prevent any other code in the same coroutine from
     * running. This mimics the traditional "throw error" behavior but only affects the current coroutine.
     *
     * @param key The unique key of the status state you want to change
     * @param setLoading If true, sets status to [Status.Loading] when the code is running. Make
     * false for super quick [action]s where loading UI isn't helpful.
     * @param action Lambda containing the actual code to run and monitor.
     *
     * @see singleStatus
     */

    protected suspend fun CoroutineScope.status(
        key: T, setLoading: Boolean = true,
        action: suspend () -> Unit
    ): Status {
        if (!isActive) return statuses[key]
        if (setLoading) statuses[key] = Loading()
        val actionStatus: Status = try {
            mapResult(Result.success(action()))
        } catch (t: Throwable) {
            if (t is CancellationException) return statuses[key]
            if (t is IssueException) t.issue else mapResult(Result.failure(t))
        }
        statuses[key] = actionStatus
        if (actionStatus is Issue) throw CancellationException("status() encountered an issue")
        return actionStatus
    }

    /**
     * Similar to [status] except that when the code inside [action] throws an error, no
     * cancellation of the code after [singleStatus] builder occurs, leaving the code free to run.
     * While [status] is more realistic in most scenarios, [singleStatus] is useful for execution
     * outside of a coroutine or when status operations are independent of each other.
     *
     * @see status
     */
    protected inline fun singleStatus(
        key: T, setLoading: Boolean = true,
        action: () -> Unit
    ): Status {
        if (setLoading) statuses[key] = Loading()
        val actionStatus: Status = try {
            mapResult(Result.success(action()))
        } catch (t: Throwable) {
            if (t is CancellationException) return statuses[key]
            if (t is IssueException) t.issue else mapResult(Result.failure(t))
        }

        statuses[key] = actionStatus
        return actionStatus
    }

    /**
     * If [debug] mode is enabled, the string output will contain all of [StatusViewModel]'s
     * associated states, statuses and series. Otherwise the string output will be the standard
     * object [toString].
     *
     * Here is a sample of the [toString] output in [debug] mode:
     * ```
     * StatusViewModel@7fbdb894 states and statuses:
     * name = Michael
     * age = 27
     * [NameValid] = Success
     * [Upload] = Loading
     * (ViewModel) DefaultSeries@119cbf96 has no running tasks
     * ```
     */
    override fun toString(): String {
        return if (!debug) super.toString() else {
            "$objectLabel states and statuses:\n" + states.toList().joinToString("\n")
            { "${it.first} = ${it.second()}" } + "\n" + allSeries
                .joinToString("\n") { "(ViewModel) $series" }
        }
    }
}