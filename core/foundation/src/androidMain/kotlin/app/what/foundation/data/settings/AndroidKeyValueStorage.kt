package app.what.foundation.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit

class AndroidKeyValueStorage(private val prefs: SharedPreferences) : KeyValueStorage {
    override fun getString(key: String, defaultValue: String?): String? =
        prefs.getString(key, defaultValue)

    override fun putString(key: String, value: String?) {
        prefs.edit {
            putString(key, value)
        }
    }

    private var changeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun setOnChangeListener(listener: (key: String) -> Unit) {
        changeListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
        val newListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            key?.let(listener)
        }
        changeListener = newListener
        prefs.registerOnSharedPreferenceChangeListener(newListener)
    }
}
