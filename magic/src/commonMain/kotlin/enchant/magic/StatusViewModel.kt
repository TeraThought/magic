package enchant.magic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

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
     * Returns the associated [Status] value for the [k]. If the status state at the current key
     * has not been set yet, [NotStarted] is returned
     *
     * @param k The unique status key/identifier that identifies the status state to access
     */
    operator fun get(k: T): Status = statuses[k]

    protected val statuses = StatusMap(::refresh)

    protected inner class StatusMap(val refresh: () -> Unit) {
        val map = hashMapOf<T, Status>()

        operator fun get(key: T): Status = map[key] ?: NotStarted()


        /** Resets all [Status] values to NotStarted */
        fun reset() = map.clear()

        /** Updates the value of the status state at the given [key] and refreshes the ViewModel */
        operator fun set(key: T, value: Status) = set(key, value, refresh = true)

        fun set(key: T, value: Status, refresh: Boolean = true) {
            map[key] = value
            if (printChanges) println("$objectLabel: [$key] = $value")
            if (refresh && key !in _blockedStatuses) refresh()
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
     * @param loading If true, sets status to [Status.Loading] when the code is running. Make
     * false for super quick [action]s where loading UI isn't helpful.
     * @param throws If true, the status will throw a coroutine [CancellationException] and not execute
     * the code after if the resulting status is [Issue]. Status builder must be run in a coroutine.
     * @param action Lambda containing the actual code to run and monitor.
     *
     */

    protected inline fun status(
        key: T?,
        loading: Boolean = true,
        throws: Boolean = true,
        action: () -> Unit
    ): Status {
        if (loading) statuses[key!!] = Loading()
        val actionStatus: Status = try {
            mapResult(Result.success(action()))
        } catch (t: Throwable) {
            if (t is IssueException) t.issue else mapResult(Result.failure(t))
        }
        if(key != null) statuses[key] = actionStatus
        if (actionStatus is Issue && throws) throw CancellationException("status() encountered an issue")
        return actionStatus
    }

    /**
     * Prevents multiple refreshes from happening when writing to multiple states and/or statuses.
     * After the code in the [block] is executed, a single refresh will happen to display the updated
     * changes.
     *
     * @param states The names of the states and keys of the statuses that will be explicitly blocked
     * from refreshing. Items must be a [String] of a state's name or a [T] of a status' key
     * @param block Code that sets the [states] and [statuses]. If user-input states are being changed, it is
     * recommended this code executes very quickly without long operations so that the state does not
     * appear as "frozen."
     */
    protected var _blockedStatuses: MutableSet<T> = mutableSetOf()
    @Suppress("UNCHECKED_CAST")
    protected inline fun commit(vararg values: Any , block: (() -> Unit)) {
        val states: ArrayDeque<String> = ArrayDeque(states.size)
        for(state in values) {
            if(state is String) states += state else _blockedStatuses += state as T
        }
        commit(states = states.toTypedArray(), block)
        _blockedStatuses = mutableSetOf()
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
            "$objectLabel states and statuses:\n" + states.toList()
                .joinToString("\n", postfix = "\n")
                { "${it.first} = ${it.second()}" } + "$statuses\n" + allSeries.joinToString("\n")
        }
    }
}