package app.what.foundation.data.settings

import java.util.prefs.Preferences

class JvmKeyValueStorage(
    private val prefs: Preferences = Preferences.userNodeForPackage(PreferenceStorage::class.java)
) : KeyValueStorage {
    private var changeListener: ((String) -> Unit)? = null

    init {
        prefs.addPreferenceChangeListener { evt ->
            changeListener?.invoke(evt.key)
        }
    }

    override fun getString(key: String, defaultValue: String?): String? {
        val v = prefs.get(key, null)
        return v ?: defaultValue
    }

    override fun putString(key: String, value: String?) {
        if (value == null) prefs.remove(key)
        else prefs.put(key, value)
    }

    override fun setOnChangeListener(listener: (key: String) -> Unit) {
        changeListener = listener
    }
}
