package app.what.foundation.data.settings

import platform.Foundation.NSUserDefaults

class IosKeyValueStorage(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : KeyValueStorage {
    private var listener: ((String) -> Unit)? = null

    override fun getString(key: String, defaultValue: String?): String? {
        val value = defaults.stringForKey(key)
        return value ?: defaultValue
    }

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key)
        else defaults.setObject(value, forKey = key)
        listener?.invoke(key)
    }

    override fun setOnChangeListener(listener: (key: String) -> Unit) {
        this.listener = listener
    }
}
