package app.what.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

@Composable
actual fun platformDialogProperties(
    full: Boolean,
    cancellable: Boolean
): DialogProperties = DialogProperties(
    usePlatformDefaultWidth = !full,
    dismissOnBackPress = cancellable,
    dismissOnClickOutside = cancellable
)
