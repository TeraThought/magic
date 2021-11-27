package enchant.magic

/** [Status] is a general-purpose construct that is meant to track the progress of any given task. */
sealed class Status {

    /** Represents when an operation has not began, and is awaiting action to begin */
    class NotStarted : Status() {
        override fun toString(): String = "NotStarted"
    }

    /** Represents a loading state, when the operation is currently still going on and active.
     *
     * @param progress The progress of the operation as a percentage between [0.0, 1.0]. Defaults to
     * -1 if showing [progress] isn't supported.
     */
    data class Loading(override val progress: Float = -1f) : Status() {
        override fun toString(): String = if (progress == -1f) "Loading" else super.toString()
    }

    /**
     * Represents a successful state, when the operation has completed with the expected outcome
     */
    class Success : Status() {
        override fun toString(): String = "Success"
    }

    /**
     * Represents an error state, when executing the operation caused an error to occur
     * @param errorCode An error code associated with the encountered error. Defaults to -1 if error
     * codes aren't supported.
     */
    data class Issue(override val errorCode: Int = -1) : Status() {
        override fun toString(): String = if (errorCode == -1) "Issue" else super.toString()
    }

    /**
     * Returns the [Loading.progress] if the current status is [Loading], crashes otherwise
     */
    open val progress: Float get() = (this as Loading).progress

    /**
     * Returns the [Issue.errorCode] if the current status is [Issue], crashes otherwise
     */
    open val errorCode: Int get() = (this as Issue).errorCode
}

/** @see Status.NotStarted*/
typealias NotStarted = Status.NotStarted

/** @see Status.Loading*/
typealias Loading = Status.Loading

/** @see Status.Success*/
typealias Success = Status.Success

/** @see Status.Issue*/
typealias Issue = Status.Issue