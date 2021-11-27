package enchant.magic

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * A custom dispatcher that dispatches coroutines to a background thread using the following:
 * - Kotlin/JVM and Kotlin/Android utilize Dispatchers.IO
 * - Kotlin/iOS utilizes a dispatch global queue with DISPATCH_QUEUE_PRIORITY_HIGH
 * - Kotlin/JS utilizes Dispatchers.Default (sorry, I can't work miracles :( )
 */
expect val Dispatchers.Background: CoroutineDispatcher