package app.what.foundation.utils

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

actual val DispatchersIO: CoroutineContext get() = Dispatchers.Default
