package enchant.magic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Extends [ViewModel], which handles state and manages asynchronous tasks (coroutines).
 */
open class StatusViewModel<T>() : ViewModel() {

    /** Calls [StatusMap.get] for a given status [key].
     *
     * Returns the associated [Status] value for the [key]. If none is found, defaults to NotStarted
     *
     * @param key The status key/identifier (something for which the status needs to be tracked) that we want to access from the [StatusMap]
     */
    operator fun get(key: T): Status = statuses[key]

    /** An instance of [StatusMap] allowing access to different statuses. */
    protected val statuses = StatusMap<T>(::refresh)

    /** A structure that pairs status keys (identifiers for things whose status needs to be tracked) to [Status] values (the actual [Status]es themselves).
     *
     * @param refresh A lambda returning unit. To be called each time a status key is added/changed. Typically refreshes the view.*/
    protected class StatusMap<T>(val refresh: () -> Unit) {
        /** A mutable map ("hash"map) that pairs a generic key ("T") to a [Status] value*/
        val hashMap = hashMapOf<T, Status>()


        /** Custom getter function for a [Status] value.
         *
         * Given a status [key] returns the [Status] value in [hashMap]. If none is found, defaults to NotStarted */
        operator fun get(key: T): Status = hashMap[key] ?: NotStarted()

        /** A [MutableSet] of key-value pairs from [hashMap].
         *
         * Makes for easier iteration. */
        val entries: MutableSet<MutableMap.MutableEntry<T, Status>> = hashMap.entries

        /** Maintains all keys in [hashMap] but resets all [Status] values to NotStarted */
        fun reset() = hashMap.clear()


        /** Given a status [key] and a [Status] [value]:
         * 1. Sets the [value] of a status at [key]
         * 2. Refreshes the view
         * 3. Returns the [value] assigned.
         */
        operator fun set(key: T, value: Status): Status {
            hashMap[key] = value
            refresh()
            return value
        }

    }


    /** "Maps" the result of a code block ([action]) to a [Status]
     *
     * @param action A lambda that takes in a [Result] (what's actually passed in) that returns a [Status]*/
    protected open fun mapResult(result: Result<Unit>): Status =
        if (result.isSuccess) Success() else Issue()

    /** Status builder - manipulates status in [statuses] ([StatusMap])
     *
     * Status will automatically update based upon the code that is running inside of it:
     * - When the code is running, the status is set to Loading
     * - When the code completes successfully, the status is set to Success
     * - When the code throws a system error, the status is set to Issue
     *
     * @param key From [statuses] - the identifier of the status we're tracking
     * @param mapper Either a Default or Input [Mapper] - defaults to [Mapper.Default] - converts result of [action] to a [Status]
     * @param setLoading If true, sets status to [Status.Loading] initially. Defaulted to "true". Make false for super quick [action]s where loading UI isn't helpful.
     * @param action Lambda containing the actual code we want to run and get status of.
     *
     */
    protected suspend fun CoroutineScope.status(
        key: T, setLoading: Boolean = true,
        action: suspend () -> Unit
    ): Status {
        val scope :CoroutineScope = this
        if (!isActive) return statuses[key]
        if (setLoading) statuses[key] = Loading()
        val actionStatus = mapResult(
            try {
                Result.success(action())
            } catch (t: Throwable) {
                if (t is CancellationException) return statuses[key]
                Result.failure(t)
            }
        )
        statuses[key] = actionStatus
        if (actionStatus is Issue) scope.cancel()
        return actionStatus
    }

    protected fun singleStatus(
        key: T, setLoading: Boolean = true,
        action: () -> Unit
    ): Status {
        if (setLoading) statuses[key] = Loading()

        val actionStatus = mapResult(
            try {
                Result.success(action())
            } catch (t: Throwable) {
                if (t is CancellationException) return statuses[key]
                Result.failure(t)
            }
        )

        statuses[key] = actionStatus
        return actionStatus
    }
}