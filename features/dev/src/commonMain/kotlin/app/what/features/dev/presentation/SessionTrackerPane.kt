package app.what.schedule.features.dev.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.what.foundation.sessiontracker.ui.SessionTrackerScreen
import app.what.foundation.utils.ShareData
import app.what.foundation.utils.rememberShareManager

@Composable
fun SessionTrackerPane(
    modifier: Modifier = Modifier
) {
    val shareManager = rememberShareManager()

    SessionTrackerScreen(
        onExportJson = { jsonReport ->
            shareManager.share(
                ShareData.Text(
                    text = jsonReport,
                    title = "Сессионный отчёт WHAT"
                )
            )
        },
        modifier = modifier.fillMaxSize()
    )
}
