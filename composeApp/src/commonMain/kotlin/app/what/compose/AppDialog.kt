package app.what.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.what.foundation.ui.controllers.DialogController
import app.what.foundation.ui.controllers.LocalDialogController
import app.what.foundation.ui.controllers.rememberDialogHostController

@Composable
expect fun platformDialogProperties(
    full: Boolean,
    cancellable: Boolean
): DialogProperties

@Composable
fun ProvideAppDialog(
    controller: DialogController = rememberDialogHostController(),
    transitionSpec: AnimatedContentTransitionScope<@Composable () -> Unit>.() -> ContentTransform = {
        fadeIn() togetherWith fadeOut()
    },
    content: @Composable () -> Unit
) = CompositionLocalProvider(
    LocalDialogController provides controller,
) {
    content()

    if (controller.opened) {
        Dialog(
            onDismissRequest = controller::close,
            properties = platformDialogProperties(
                full = controller.full,
                cancellable = controller.cancellable
            )
        ) {
            Surface(
                shape = if (controller.full) RectangleShape else shapes.large,
                color = colorScheme.surface,
                modifier = if (controller.full) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.animateContentSize().fillMaxWidth()
                }
            ) {
                if (controller.full) {
                    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                        AnimatedContent(
                            targetState = controller.content,
                            transitionSpec = transitionSpec,
                            label = "AnimatedDialogContent"
                        ) { dialogContent ->
                            dialogContent()
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = controller.content,
                        transitionSpec = transitionSpec,
                        label = "AnimatedDialogContent"
                    ) { dialogContent ->
                        dialogContent()
                    }
                }
            }
        }
    }
}
