package enchant.magic

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

internal actual val Dispatchers.Background: CoroutineDispatcher get() = Dispatchers.IO
