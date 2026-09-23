package app.what.foundation.services.auto_update

import android.content.Context
import android.content.pm.InstallSourceInfo
import android.os.Build

fun getInstallSource(context: Context): InstallSource {
    val pm = context.packageManager
    val packageName = context.packageName

    val source = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            val installSourceInfo: InstallSourceInfo = pm.getInstallSourceInfo(packageName)
            installSourceInfo.installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName)
        }
    } catch (e: Exception) {
        null
    }

    return when {
        source == "ru.vk.store" -> InstallSource.RuStore
        else -> InstallSource.APK
    }
}
