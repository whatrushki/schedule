package app.what.foundation.data.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.vector.ImageVector
import app.what.foundation.ui.useState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

interface Named {
    val displayName: String
}

interface KeyValueStorage {
    fun getString(key: String, defaultValue: String? = null): String?
    fun putString(key: String, value: String?)
    fun setOnChangeListener(listener: (key: String) -> Unit)
}

class MemoryKeyValueStorage(
    private val map: MutableMap<String, String?> = mutableMapOf()
) : KeyValueStorage {
    private var listener: ((String) -> Unit)? = null

    override fun getString(key: String, defaultValue: String?): String? = map[key] ?: defaultValue

    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
        listener?.invoke(key)
    }

    override fun setOnChangeListener(listener: (key: String) -> Unit) {
        this.listener = listener
    }
}

abstract class PreferenceStorage(protected val storage: KeyValueStorage) {
    private val preferencesFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        storage.setOnChangeListener { key ->
            preferencesFlow.tryEmit(key)
        }
    }

    fun <T : Any> createValue(
        key: String,
        defaultValue: T?,
        serializer: KSerializer<T>,
        title: String = "",
        description: String? = null,
        icon: ImageVector? = null
    ): Value<T> = Value(storage, preferencesFlow, key, defaultValue, serializer, title, description, icon)

    class Value<T : Any>(
        private val storage: KeyValueStorage,
        private val preferencesFlow: MutableSharedFlow<String>,
        val key: String,
        private val defaultValue: T?,
        private val serializer: KSerializer<T>,
        val title: String,
        val description: String? = null,
        val icon: ImageVector? = null,
    ) {
        fun get(): T? = storage
            .getString(key, null)
            ?.let { Json.decodeFromString(serializer, it) }
            ?: defaultValue

        fun set(value: T?) {
            storage.putString(
                key,
                if (value == null) null
                else Json.encodeToString(serializer, value)
            )
            preferencesFlow.tryEmit(key)
        }

        fun observe(): Flow<T?> = preferencesFlow
            .filter { it == key }
            .map { get() }
            .onStart { emit(get()) }
            .distinctUntilChanged()

        @Composable
        fun collect() = observe().collectAsState(get())

        @Composable
        fun <R> collect(block: (T?) -> R): State<R?> {
            val state = useState<R?>(null)

            LaunchedEffect(Unit) {
                observe().collect { state.value = block(it) }
            }
            return state
        }
    }
}