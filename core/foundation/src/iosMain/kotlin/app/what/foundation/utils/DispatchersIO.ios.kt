package app.what.foundation.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.coroutines.CoroutineContext

actual val DispatchersIO: CoroutineContext get() = Dispatchers.IO
