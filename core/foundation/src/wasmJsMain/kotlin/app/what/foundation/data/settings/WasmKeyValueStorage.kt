package app.what.foundation.data.settings

import kotlinx.browser.localStorage

class WasmKeyValueStorage : KeyValueStorage {
    private var listener: ((String) -> Unit)? = null

    override fun getString(key: String, defaultValue: String?): String? {
        val v = localStorage.getItem(key)
        return v ?: defaultValue
    }

    override fun putString(key: String, value: String?) {
        if (value == null) localStorage.removeItem(key)
        else localStorage.setItem(key, value)
        listener?.invoke(key)
    }

    override fun setOnChangeListener(listener: (key: String) -> Unit) {
        this.listener = listener
    }
}
