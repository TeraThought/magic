package enchant.magic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.properties.ReadWriteProperty
import kotlin.random.Random
import kotlin.reflect.KProperty

/** Foundational class for a MVVM ViewModel component which has the core functionality needed to
 * architect a high-quality application:
 * - Hosts its own CoroutineScope on a background thread (via [Dispatchers.Background]) that allows
 * for simple asynchronous coroutine work and built-in cancellation via [ViewModel.cancel]
 *
 * ```
 * //That's it!
 * fun myEvent(): Unit = launch {
 *      ...async work
 * }
 * ```
 *
 * - Has a default [series] that can be customized to run coroutines in a more flexible manner. Also
 * allows additional [Series] to be added to the ViewModel via specific helpers (
 * ex. [ViewModel.DefaultSeries])
 *
 * ```
 * //Instant queuing or tapjack prevention!
 * fun myComplexEvent() = series.add {
 *      ...async work
 * }
 * init {
 *     series = QueueSeries() //Queueing
 *     series = CancelTentativeSeries() //Tapjack prevention
 * }
 *
 * ```
 *
 * - A simple and flexible [state] system that allows state changes to update Views and other UI
 * observers via [ViewModel.addRefresh]
 * ```
 * //Short and sweet!
 * var name by state("")
 * ```
 *
 * - A [debug] mode which enables in-depth [ViewModel.toString] and [ViewModel.printChanges] methods.
 * The debug mode also propagates to the ViewModel's series.
 *
 * @param Enables debug mode which allows [ViewModel.toString] and [ViewModel.printChanges] to run
 * properly. Debug mode may affect performance so it is not recommended in release builds
 */
open class ViewModel(val debug: Boolean = false) : CoroutineScope {

    /**
     * The "environment" of the ViewModel's [CoroutineScope]. Can be customized to a different
     * dispatcher by overriding this value.
     */
    override val coroutineContext: CoroutineContext = try {
        (Dispatchers.Main + Job()).also { launch { } }
    } catch (e: Exception) {
        if (debug) println("Using Dispatchers.Default because no main dispatcher was found")
        Dispatchers.Default + Job()
    }

    protected val allSeries: ArrayDeque<Series> = ArrayDeque()

    /**
     * A [Series] that comes with the ViewModel. It is set to be a [DefaultSeries] by default, but
     * can be customized to any other type of series by setting the value.
     */
    protected open var series: Series = DefaultSeries()
        set(value) {
            allSeries[0] = (field)
            field = value
        }

    /** Convenience that creates a [DefaultSeries] which inherits the [CoroutineScope] and [debug]
     * behavior of the [ViewModel].
     */
    protected fun DefaultSeries(): DefaultSeries {
        val series = DefaultSeries(debug)
        if (debug) allSeries += series
        return series
    }

    /** Convenience that creates a [QueueSeries] which inherits the [CoroutineScope] and [debug]
     * behavior of the [ViewModel].
     */
    protected fun QueueSeries(): QueueSeries {
        val series = QueueSeries(debug)
        if (debug) allSeries += series
        return series
    }

    /** Convenience that creates a [CancelRunningSeries] which inherits the [CoroutineScope] and [debug]
     * behavior of the [ViewModel].
     */
    protected fun CancelRunningSeries(): CancelRunningSeries {
        val series = CancelRunningSeries(debug)
        if (debug) allSeries += series
        return series
    }

    /** Convenience that creates a [CancelTentativeSeries] which inherits the [CoroutineScope] and [debug]
     * behavior of the [ViewModel].
     */
    protected fun CancelTentativeSeries(): CancelTentativeSeries {
        val series = CancelTentativeSeries(debug)
        if (debug) allSeries += series
        return series
    }

    private val refreshes: MutableMap<Int, () -> Unit> = mutableMapOf()
    private val removes: MutableSet<Int> = mutableSetOf()
    private var isRefreshing: Boolean = false

    /** Adds an [refresh] to be called when the [ViewModel] [refresh]es*/
    fun addRefresh(refresh: () -> Unit) {
        refreshes[Random.nextInt()] = refresh
    }

    /** To be called when a ViewModel state changes. Calls all of the refreshes added to the
     * [ViewModel] using [addRefresh] */
    protected fun refresh() {
        if (!isRefreshing) {
            isRefreshing = true
            refreshes.values.forEach { it.invoke() }
            if (removes.isNotEmpty()) {
                removes.forEach { refreshes.remove(it) }
                refreshes.clear()
            }
            isRefreshing = false
        }
    }


    /** Creates a reactive state that will [refresh] the [ViewModel] when its value changes
     *
     * @param initialValue The initial (default) value of the state
     * @param get Custom getter function for the state. Defaults to returning the state's value. Cannot
     * change type of state.
     * @param set Custom setter function for the state. Defaults to assigning what is passed in.
     * Cannot change type of state.
     */
    protected fun <T> state(
        initialValue: T,
        name: String? = null,
        get: State<T>.() -> T = { value },
        set: State<T>.(T) -> Unit = { value = it }
    ): State<T> = State(initialValue, name, get, set)

    inner class State<T>(
        var value: T,
        var name: String?,
        var get: State<T>.() -> T,
        var set: State<T>.(T) -> Unit,
    ) : ReadWriteProperty<Any?, T> {

        private var added = false


        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            if (!added) {
                name = name ?: property.name
                states[name!!] = { value.toString() }
                added = true
            }
            return get()
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            set(value)
            if (!added) {
                name = name ?: property.name
                states[name!!] = { value.toString() }
                added = true
            }
            if (printChanges) println("$objectLabel: $name = ${this.value}")
            if (name!! !in _blockedStates) refresh()
        }
    }

    protected val objectLabel by lazy { super.toString().takeLastWhile { it != '.' } }

    protected val states: MutableMap<String, () -> String> by lazy { mutableMapOf() }

    /**
     * If [debug] mode is enabled, the string output will contain all of [ViewModel]'s associated
     * states and series. Otherwise the string output will be the standard object [toString]. The
     * first series that is print out will be the embedded series provided by the ViewModel
     *
     * Here is a sample of the [toString] output in [debug] mode:
     * ```
     * ViewModel@7fbdb894 states:
     * name = Michael
     * age = 27
     * DefaultSeries@119cbf96 has no running tasks
     * ```
     */
    override fun toString(): String {
        return if (!debug) super.toString() else {
            "$objectLabel states:\n" + states.toList().joinToString("\n", postfix = "\n")
            { "${it.first} = ${it.second()}" } + allSeries.joinToString()
        }
    }

    protected var printChanges = false

    /**
     * Enables or disables the option to print out all changes from [state]s and changes from
     * connected series. Only works if [debug] mode is enabled.
     *
     * Note: This method should be called after all ViewModel series have been created.
     *
     * Sample output of [printChanges] in [debug] mode:
     * ```
     * ViewModel@7ceb3185: name = Jen
     * ViewModel@7ceb3185: name = Natalie
     * DefaultSeries@402c3df5: "upload" started
     * ```
     *
     * @param enabled Whether state and series changes should be printed out
     * @throws kotlin.IllegalStateException if printChanges attempts to be enabled from non-[debug] mode.
     */
    open fun printChanges(enabled: Boolean) {
        if (!debug && enabled) error("Cannot enable printChanges in non-debug mode")
        printChanges = enabled
        allSeries.forEach { it.printChanges(enabled) }
    }

    fun cancel() {
        if (debug) println("$this cancelled with ViewModel.cancel()")
        this.cancel("$this cancelled with ViewModel.cancel()")
    }

    /**
     * Prevents multiple refreshes from happening when writing to multiple states. After the code
     * in the [block] is executed, a single refresh will happen to display the updated changes.
     *
     * @param states The names of the states that will be explicitly blocked from refreshing
     * @param block Code that sets the [states]. If user-input states are being changed, it is
     * recommended this code executes very quickly without long operations so that the state does not
     * appear as "frozen."
     */
    protected var _blockedStates = setOf<String>()
    protected inline fun commit(vararg states: String, block: (() -> Unit)) {
        _blockedStates = states.toSet()
        block()
        refresh()
        _blockedStates = setOf()
    }

    /**
     * Suspends (stops execution of) the current code until the [ViewModel] changes. The await()
     * call will resume execution when a ViewModel state changes or any other kind of ViewModel
     * refresh occurs.
     *
     * @param block ViewModel changes that are "awaited" to be happen. ViewModel changes should be
     * put in the block if they execute without delay and very quickly (particularly during tests).
     * This ensures that await() is registered before the ViewModel changes happen and not afterwards.
     */
    suspend fun await(block: (() -> Unit)? = null): Unit = suspendCancellableCoroutine { c ->
        val id = Random.nextInt()
        refreshes[id] = {
            removes.add(id)
            c.resume(Unit)
        }
        block?.invoke()
    }
}