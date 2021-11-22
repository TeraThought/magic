package enchant.magic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmInline
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Foundational class for ViewModels.
 *
 * Handles state and manages asynchronous tasks via coroutines ([CoroutineScope]).*/
open class ViewModel : CoroutineScope {

    /** Used for specifying when we want to run tasks as a background thread */
    final override val coroutineContext: CoroutineContext = Dispatchers.Background + Job()

    protected var series: Series = CancelTentativeSeries()

    /** [MutableList] of lambdas (returning [Unit]) that are called when any state changes*/
    val refreshes: MutableList<() -> Unit> = mutableListOf()

    /** Adds an [action] (lambda returning [Unit]) to [onRefreshes] so it's called any state refreshes */
    fun addRefresh(action: () -> Unit) {
        refreshes += action
    }

    /** Used when state changes to refresh views or associated observers.
     *
     * Runs each refresh within [onRefreshes] **/
    protected fun refresh() {
        refreshes.forEach { it.invoke() }
    }

    /** To be called when the view no longer needs a particular ViewModel (e.g. moves to a different screen)
     *
     * Runs each function within [onCloseActions]
     *
     * Stops the [CoroutineScope] and ends all running tasks. Relies on [CoroutineScope.cancel]. */
    open fun close() {
        onCloseActions.forEach { it.invoke() }
        cancel("${this::class.simpleName}  and its children coroutines were canceled")
    }

    /** [MutableList] of lambdas (returning [Unit]) that are called when the ViewModel is closed*/
    private val onCloseActions = mutableListOf<() -> Unit>()

    /** Adds an lambda returning [Unit] ([onClose]) to [onCloseActions] so it's called when the ViewModel is closed*/
    protected fun onClose(onClose: () -> Unit) {
        onCloseActions += onClose
    }


    /** Creates a reactive state that will refresh the ViewModel when its value changes
     *
     * @param initialValue The initial (default) value of a state
     * @param get Custom getter function for the state. Defaults to returning the state's value. Cannot
     * change type of state.
     * @param set Custom setter function for the state. Defaults to assigning whatever is passed in.
     * Cannot change type of state.
     */
    protected fun <T> state(
        initialValue: T,
        get: State<T>.() -> T = { value },
        set: State<T>.(T) -> Unit = { value = it }
    ): State<T> = State(initialValue, get) { set(it); refresh() }

}

/** Container for key properties of any state.*/
class State<T>(
    var value: T,
    var get: State<T>.() -> T,
    var set: State<T>.(T) -> Unit,
) : ReadWriteProperty<Any?, T> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = get()
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T): Unit = set(value).let { }
}