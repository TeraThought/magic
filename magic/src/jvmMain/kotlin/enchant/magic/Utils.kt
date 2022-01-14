@file:JvmName("UtilsImpl")
package enchant.magic

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val Dispatchers.Background: CoroutineDispatcher get() = Dispatchers.IO