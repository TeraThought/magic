package enchant.magic

/** [Status] is a general-purpose construct that is meant to track the progress of any given task. */
sealed class Status(val type: StatusType) {

    /** Represents when an operation has not began, and is awaiting action to begin */
    class NotStarted : Status(StatusType.NotStarted) {
        override fun toString(): String = "NotStarted"
    }

    /** Represents a loading state, when the operation is currently still going on and active.
     *
     * @param progress The progress of the operation as a percentage between [0.0, 1.0]. Defaults to
     * -1 if showing [progress] isn't supported.
     */
    data class Loading(override val progress: Float = -1f) : Status(StatusType.Loading)

    /**
     * Represents a successful state, when the operation has completed with the expected outcome
     */
    class Success : Status(StatusType.Success) {
        override fun toString(): String = "Success"
    }

    /**
     * Represents an error state, when executing the operation caused an error to occur
     * @param code An error code associated with the encountered error. Defaults to -1 if error
     * codes aren't supported.
     */
    data class Issue(override val message: String = "", override val code: Int = -1) : Status(StatusType.Issue)

    /**
     * Returns the [Loading.progress] if the current status is [Loading], crashes otherwise
     */
    open val progress: Float get() = (this as Loading).progress

    /**
     * Returns the [Issue.message] if the current status is [Issue], crashes otherwise
     */
    open val message: String get() = (this as Issue).message

    /**
     * Returns the [Issue.code] if the current status is [Issue], crashes otherwise
     */
    open val code: Int get() = (this as Issue).code
}

//Allows Kotlin/Native users to read the types of statuses
enum class StatusType { NotStarted, Loading, Success, Issue }

/** @see Status.NotStarted*/
typealias NotStarted = Status.NotStarted

/** @see Status.Loading*/
typealias Loading = Status.Loading

/** @see Status.Success*/
typealias Success = Status.Success

/** @see Status.Issue*/
typealias Issue = Status.Issue

class IssueException(val issue: Status.Issue) :
    Exception("Issue exception with $issue is meant to be caught within a StatusViewModel")

fun issue(message: String = "", code: Int = -1): Nothing =
    throw IssueException(Issue(message, code))