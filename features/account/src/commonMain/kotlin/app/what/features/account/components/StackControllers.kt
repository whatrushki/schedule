package app.what.features.account.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.what.navigation.core.AppNavigator
import app.what.navigation.core.NavStackHost
import app.what.foundation.ui.controllers.rememberDialogController
import app.what.foundation.ui.controllers.rememberSheetController

interface StackModalController {
    val canGoBack: Boolean
    val size: Int
    fun open(full: Boolean = false, inStack: Boolean = false, content: @Composable () -> Unit)
    fun push(full: Boolean = false, content: @Composable () -> Unit) = open(full = full, inStack = true, content = content)
    fun back(): Boolean
    fun close()
}

class StackModalControllerImpl(
    val navigator: AppNavigator,
    private val openBase: (full: Boolean, content: @Composable () -> Unit) -> Unit,
    private val closeBase: () -> Unit
) : StackModalController {
    override val canGoBack: Boolean get() = navigator.canGoBack
    override val size: Int get() = navigator.size

    override fun open(full: Boolean, inStack: Boolean, content: @Composable () -> Unit) {
        navigator.open(full = full, inStack = inStack, content = content)
        openBase(full) {
            NavStackHost(navigator = navigator)
        }
    }

    override fun back(): Boolean {
        val result = navigator.pop()
        if (navigator.isEmpty) closeBase()
        return result
    }

    override fun close() {
        navigator.clear()
        closeBase()
    }
}

typealias StackDialogController = StackModalController
typealias StackSheetController = StackModalController

@Composable
fun rememberStackDialogController(): StackDialogController {
    val base = rememberDialogController()
    val nav = remember { AppNavigator() }
    return remember(base, nav) {
        StackModalControllerImpl(
            navigator = nav,
            openBase = { full, c -> base.open(full = full, content = c) },
            closeBase = { base.close() }
        )
    }
}

@Composable
fun rememberStackSheetController(): StackSheetController {
    val base = rememberSheetController()
    val nav = remember { AppNavigator() }
    return remember(base, nav) {
        StackModalControllerImpl(
            navigator = nav,
            openBase = { full, c -> base.open(full = full, content = c) },
            closeBase = { base.close() }
        )
    }
}
