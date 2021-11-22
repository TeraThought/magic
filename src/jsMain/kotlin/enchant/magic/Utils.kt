package enchant.magic

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

internal actual val Dispatchers.Background: CoroutineDispatcher get() = Dispatchers.Default

actual fun runTest(block: suspend CoroutineScope.() -> Unit): dynamic {
    return GlobalScope.async(block = block).asPromise()
}