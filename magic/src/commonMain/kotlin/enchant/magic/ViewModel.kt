package enchant.magic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.properties.ReadOnlyProperty
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
     * dispatcher (like TestDispatcher).
     */
    override val coroutineContext: CoroutineContext = Dispatchers.Main + Job()

    protected val allSeries: ArrayDeque<Series> = ArrayDeque()

    /**
     * A [Series] that comes with the ViewModel. It is set to be a [DefaultSeries] by default, but
     * can be customized to any other type of series by setting the value.
     */
    protected open val series: Series = DefaultSeries()

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

    val _refreshes: MutableMap<Int, () -> Unit> = mutableMapOf()
    val _removes: MutableSet<Int> = mutableSetOf()
    private var isRefreshing: Boolean = false

    /** Adds an [refresh] to be called when the [ViewModel] [refresh]es*/
    fun addRefresh(refresh: () -> Unit) {
        _refreshes[Random.nextInt()] = refresh
    }

    /** To be called when a ViewModel state changes. Calls all of the refreshes added to the
     * [ViewModel] using [addRefresh] */
    protected fun refresh() {
        if (!isRefreshing) {
            isRefreshing = true
            _refreshes.values.forEach { it.invoke() }
            if (_removes.isNotEmpty()) {
                _removes.forEach { _refreshes.remove(it) }
                _removes.clear()
            }
            isRefreshing = false
        }
    }


    /** Creates a reactive state that will [refresh] the [ViewModel] when its value changes
     *
     * @param initialValue The initial (default) value of the state
     * @param get Custom getter function for the state. Defaults to returning the state's value.
     * @param set Custom setter function for the state. Defaults to assigning what is passed in.
     * Cannot change type of state.
     */
    protected fun <T> state(
        initialValue: T,
        name: String? = null,
        get: (T) -> T = { it },
        set: (current: T, new: T) -> T = { _, new -> new }
    ): State<T> = State(initialValue, name, get, set)

    /** Creates a reactive state from an [MutableStateFlow] */
    protected fun <T> state(
        state: MutableStateFlow<T>,
        name: String? = null,
        get: (T) -> T = { it },
        set: (current: T, new: T) -> T = { _, new -> new }
    ): State<T> {
        val _state = State(
            state.value,
            name,
            { get(state.value) },
            { _, new -> state.value = set(state.value, new); state.value }
        )
        state.onEach { _state.value = it }.launchIn(this@ViewModel)
        return _state
    }

    /** Creates a reactive read-only state from a [Flow] */
    protected fun <T> state(
        flow: Flow<T>,
        initialValue: T,
        name: String? = null,
        get: (T) -> T = { it },
    ): FlowState<T> = FlowState(flow, initialValue, name, get)

    inner class FlowState<T>(
        flow: Flow<T>,
        initialValue: T,
        var name: String?,
        private val get: (T) -> T,
    ) : ReadOnlyProperty<Any?, T> {
        private var added = false

        private var _field: T = initialValue
        val value: T get() = get(_field)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            if (!added) {
                name = name ?: property.name
                states[name!!] = { _field.toString() }
                added = true
            }
            return value
        }

        init {
            flow.onEach {
                val oldValue = _field
                _field = it
                if (printChanges) println("$objectLabel: $name = ${this.value}")
                if (_field != oldValue)
                    if (name ?: error("This state is initially set manually, it must have a defined name") in _blockedStates)
                        _commitChanges = true else refresh()
            }.launchIn(this@ViewModel)
        }
    }

    inner class State<T>(
        initialValue: T,
        var name: String?,
        private val get: (T) -> T,
        private val set: (current: T, new: T) -> T
    ) : ReadWriteProperty<Any?, T> {

        private var added = false

        private var _field: T = initialValue
        var value: T
            get() = get(_field)
            set(value) {
                val oldValue = _field
                _field = set(_field, value)
                if (printChanges) println("$objectLabel: $name = ${this.value}")
                if (_field != oldValue)
                    if (name ?: error("This state is initially set manually, it must have a defined name") in _blockedStates)
                        _commitChanges = true else refresh()
            }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            if (!added) {
                name = name ?: property.name
                states[name!!] = { _field.toString() }
                added = true
            }
            return value
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (!added) {
                name = name ?: property.name
                states[name!!] = { _field.toString() }
                added = true
            }
            this.value = value
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
            { "${it.first} = ${it.second()}" } + allSeries.joinToString("\n")
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
     * in the [block] is executed, a single refresh will happen to display the updated changes (if any
     * changes have occurred).
     *
     * Note that if a [CancellationException] is thrown inside the commit block, the changes will
     * still be committed. [commit] only supports one execution at a time, running multiple [commit]s
     * at the same time will produce unintended results.
     *
     * @param states The names of the states that will be explicitly blocked from refreshing
     * @param block Code that sets the [states]. If user-input states are being changed, it is
     * recommended this code executes very quickly without long operations so that the state does not
     * appear as "frozen."
     */
    protected var _blockedStates = setOf<String>()
    protected var _commitChanges = false
    protected inline fun commit(vararg states: String, block: (() -> Unit)) {
        _blockedStates = states.toSet()
        _commitChanges = false
        try {
            block()
        } catch (e: CancellationException) {
        }
        if (_commitChanges) {
            refresh()
            _commitChanges = false
        }
        _blockedStates = setOf()
    }

    /**
     * Suspends (stops execution of) the current code until the [ViewModel] changes. The await()
     * call will resume execution when a ViewModel state changes or any other kind of ViewModel
     * refresh occurs.
     *
     * @param awaits The number of state refreshes that need to occur before the ViewModel [await] continues
     * @param block ViewModel changes that are "awaited" to be happen. ViewModel changes should be
     * put in the block if they execute without delay and very quickly (particularly during tests).
     * This ensures that await() is registered before the ViewModel changes happen and not afterwards.
     */
    suspend inline fun await(awaits: Int = 1, crossinline block: () -> Unit = { }): Unit =
        suspendCancellableCoroutine { c ->
            var awaitCounter = awaits
            val id = Random.nextInt()
            _refreshes[id] = {
                awaitCounter--
                if (awaitCounter == 0) {
                    _removes.add(id)
                    c.resume(Unit)
                }
            }
            block()
        }
}