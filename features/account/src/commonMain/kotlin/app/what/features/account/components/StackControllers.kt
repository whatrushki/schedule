package app.what.features.account.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.what.foundation.ui.PlatformBackHandler
import app.what.foundation.ui.applyIf
import app.what.foundation.ui.controllers.DialogController
import app.what.foundation.ui.controllers.SheetController
import app.what.foundation.ui.controllers.rememberDialogController
import app.what.foundation.ui.controllers.rememberSheetController

data class StackModalEntry(
    val id: Long,
    val full: Boolean,
    val content: @Composable () -> Unit
)

interface StackModalController {
    val canGoBack: Boolean
    val size: Int
    fun open(full: Boolean = false, inStack: Boolean = false, content: @Composable () -> Unit)
    fun push(full: Boolean = false, content: @Composable () -> Unit) = open(full = full, inStack = true, content = content)
    fun back(): Boolean
    fun close()
}

class StackDialogController internal constructor(
    private val base: DialogController
) : StackModalController {
    private var nextId = 0L
    private val stack = mutableStateListOf<StackModalEntry>()
    private var isPush by mutableStateOf(true)

    override val canGoBack: Boolean
        get() = stack.size > 1

    override val size: Int
        get() = stack.size

    override fun open(
        full: Boolean,
        inStack: Boolean,
        content: @Composable () -> Unit
    ) {
        if (!inStack) {
            stack.clear()
        }
        isPush = true
        stack.add(StackModalEntry(id = ++nextId, full = full, content = content))

        base.open(full = full) {
            StackModalHost(
                stack = stack,
                isPush = isPush,
                onBack = { back() }
            )
        }
    }

    override fun push(full: Boolean, content: @Composable () -> Unit) {
        open(full = full, inStack = true, content = content)
    }

    override fun back(): Boolean {
        if (stack.size > 1) {
            isPush = false
            stack.removeAt(stack.lastIndex)
            return true
        } else {
            close()
            return false
        }
    }

    override fun close() {
        stack.clear()
        base.close()
    }
}

class StackSheetController internal constructor(
    private val base: SheetController
) : StackModalController {
    private var nextId = 0L
    private val stack = mutableStateListOf<StackModalEntry>()
    private var isPush by mutableStateOf(true)

    override val canGoBack: Boolean
        get() = stack.size > 1

    override val size: Int
        get() = stack.size

    override fun open(
        full: Boolean,
        inStack: Boolean,
        content: @Composable () -> Unit
    ) {
        if (!inStack) {
            stack.clear()
        }
        isPush = true
        stack.add(StackModalEntry(id = ++nextId, full = full, content = content))

        base.open(full = full) {
            StackModalHost(
                stack = stack,
                isPush = isPush,
                onBack = { back() }
            )
        }
    }

    override fun push(full: Boolean, content: @Composable () -> Unit) {
        open(full = full, inStack = true, content = content)
    }

    override fun back(): Boolean {
        if (stack.size > 1) {
            isPush = false
            stack.removeAt(stack.lastIndex)
            return true
        } else {
            close()
            return false
        }
    }

    override fun close() {
        stack.clear()
        base.close()
    }
}

@Composable
fun rememberStackDialogController(): StackDialogController {
    val base = rememberDialogController()
    return remember(base) { StackDialogController(base) }
}

@Composable
fun rememberStackSheetController(): StackSheetController {
    val base = rememberSheetController()
    return remember(base) { StackSheetController(base) }
}

@Composable
private fun StackModalHost(
    stack: List<StackModalEntry>,
    isPush: Boolean,
    onBack: () -> Unit
) {
    PlatformBackHandler(enabled = stack.size > 1) {
        onBack()
    }

    val saveableStateHolder = rememberSaveableStateHolder()
    val currentEntry = stack.lastOrNull()
    if (currentEntry != null) {
        AnimatedContent(
            targetState = currentEntry,
            transitionSpec = {
                if (isPush) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width / 3 } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { width -> -width / 3 } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut()
                    )
                }
            },
            contentKey = { it.id },
            label = "StackModalTransition",
            modifier = Modifier.applyIf(currentEntry.full) { fillMaxSize() }
        ) { entry ->
            saveableStateHolder.SaveableStateProvider(entry.id) {
                entry.content()
            }
        }
    }
}
