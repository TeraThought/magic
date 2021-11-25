package enchant.magic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Foundational class for ViewModels.
 *
 * Handles state and manages asynchronous tasks via coroutines ([CoroutineScope]).*/
open class ViewModel(val debug: Boolean = false) : CoroutineScope {

    /** Used for specifying when we want to run tasks as a background thread */
    final override val coroutineContext: CoroutineContext = Dispatchers.Background + Job()

    protected open var series: Series = DefaultSeries(debug)
    protected val additionalSeries: MutableList<Series> = mutableListOf()

    protected fun DefaultSeries(): DefaultSeries {
        val series = DefaultSeries(debug)
        additionalSeries += series
        return series
    }

    protected fun QueueSeries(): QueueSeries {
        val series = QueueSeries(debug)
        additionalSeries += series
        return series
    }

    protected fun CancelRunningSeries(): CancelRunningSeries {
        val series = CancelRunningSeries(debug)
        additionalSeries += series
        return series
    }

    protected fun CancelTentativeSeries(): CancelTentativeSeries {
        val series = CancelTentativeSeries(debug)
        additionalSeries += series
        return series
    }

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
    ): State<T> = State(initialValue, get, set)

    /** Container for key properties of any state.*/
    inner class State<T>(
        var value: T,
        var get: State<T>.() -> T,
        var set: State<T>.(T) -> Unit,
    ) : ReadWriteProperty<Any?, T> {

        private var added = false
        private lateinit var name: String


        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            if (debug) {
                if (!added) {
                    name = property.name
                    states[name] = { value.toString() }
                    added = true
                }
            }
            return get()
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            set(value)
            if (debug) {
                if (!added) {
                    name = property.name
                    states[name] = { this.value.toString() }
                    added = true
                }
                if (printChanges) println("$objectLabel: $name = ${this.value}")
            }
            refresh()
        }
    }

    protected val objectLabel by lazy { super.toString().takeLastWhile { it != '.' } }

    protected val states: MutableMap<String, () -> String> by lazy { mutableMapOf() }

    override fun toString(): String {
        return if (!debug) super.toString() else {
            "$objectLabel states:\n" + states.toList().joinToString("\n")
            { "${it.first} = ${it.second()}" } + "\n" + (listOf(series) + additionalSeries)
                .joinToString("\n") { "(ViewModel) $series" }
        }
    }

    protected var printChanges = false

    open fun printChanges(enabled: Boolean) {
        if (!debug && enabled) throw error("Cannot enable printChanges in non-debug mode")
        printChanges = enabled
        (listOf(series) + additionalSeries).forEach { it.printChanges(enabled) }
    }
}