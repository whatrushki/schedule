package app.what.schedule.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.utils.LogCat
import app.what.foundation.utils.LogScope
import app.what.foundation.utils.buildTag
import java.util.concurrent.TimeUnit

object ScheduleWorkManager {
    private const val WORK_NAME = "schedule_replacements_check"

    fun schedulePeriodicCheck(context: Context, periodHours: Int) {
        val tag = buildTag(LogScope.CORE, LogCat.INIT)
        Auditor.info(tag, "ScheduleWorkManager: планирование проверки замен каждые $periodHours ч.")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val hours = periodHours.coerceIn(1, 24).toLong()

        val workRequest = PeriodicWorkRequestBuilder<ScheduleCheckWorker>(
            hours, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancelPeriodicCheck(context: Context) {
        val tag = buildTag(LogScope.CORE, LogCat.INIT)
        Auditor.info(tag, "ScheduleWorkManager: отмена фоновой проверки замен")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
