package app.what.schedule.utils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

object AppUserIdProvider {
    private const val PREFIX = "WHAT-SCH-"
    private const val PREFS_NAME = "what_device_identity"
    private const val KEY_STABLE_USER_ID = "stable_user_id"

    @SuppressLint("HardwareIds")
    fun getOrCreateUserId(context: Context, existingId: String? = null): String {
        // 1. If existingId is already in the valid format "WHAT-SCH-XXXXXX", keep it
        if (!existingId.isNullOrBlank() && existingId.startsWith(PREFIX) && existingId.length >= 14) {
            persistFallback(context, existingId)
            return existingId
        }

        // 2. Check unencrypted fallback SharedPreferences (survives Keystore glitches)
        val fallbackId = getPersistedFallback(context)
        if (!fallbackId.isNullOrBlank() && fallbackId.startsWith(PREFIX)) {
            return fallbackId
        }

        // 3. Deterministic calculation from ANDROID_ID
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }

        val rawSeed = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            existingId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        }

        val hashBytes = MessageDigest.getInstance("SHA-256").digest(rawSeed.toByteArray(Charsets.UTF_8))
        // Take 3 bytes -> 6 hex characters (e.g. WHAT-SCH-8F2A4B)
        val hex = hashBytes.take(3).joinToString("") { "%02X".format(it) }
        val generatedId = "$PREFIX$hex"

        persistFallback(context, generatedId)
        return generatedId
    }

    private fun persistFallback(context: Context, userId: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_STABLE_USER_ID, null) != userId) {
                prefs.edit().putString(KEY_STABLE_USER_ID, userId).apply()
            }
        } catch (_: Exception) {}
    }

    private fun getPersistedFallback(context: Context): String? {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_STABLE_USER_ID, null)
        } catch (_: Exception) {
            null
        }
    }
}
