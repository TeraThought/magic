package enchant.magic

/** Status tracks the progress of a given task.
 *
 * The super class for all Status types.
 *
 * "sealed" can only extend class from within the class itself (e.g. "class NotStarted: Status() )
 * */
sealed class Status {

    /** Represents a not started state, when the operation has not began, and is awaiting action to begin */
    class NotStarted : Status()

    /**
     * Represents a successful state, when the operation as completed with the expected outcome
     */
    class Success : Status()

    /** Represents a loading state, when the operation is currently still going on.
     * @param progress The progress of the task in between [0.0, 1.0]. Defaults to -1 if showing
     * progress isn't supported. Useful for timing actions or showing loading bars.
     */
    class Loading(override val progress: Float = -1f) : Status()

    /**
     * Represents an error state, when their was an issue with the requested operation
     * @param errorType Emits an error code associated with the issue
     */
    class Issue(override val errorCode: Int = -1) : Status()

    /**
     * Returns the progress if the current status is [Loading], crashes otherwise
     */
    open val progress: Float get() = (this as Loading).progress

    /**
     * Returns the error code if the current status is [Issue], crashes otherwise
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