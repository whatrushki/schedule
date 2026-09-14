package app.what.schedule.desktop.ui

import androidx.compose.foundation.gestures.DesktopScrollable_desktopKt
import androidx.compose.foundation.gestures.ScrollConfig
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold
import java.awt.event.MouseWheelEvent

object DesktopSmoothScrollConfig : ScrollConfig {
    override val isSmoothScrollingEnabled: Boolean
        get() = false

    override fun Density.calculateMouseWheelScroll(event: PointerEvent, bounds: IntSize): Offset {
        val totalScrollDelta = event.changes.fastFold(Offset.Zero) { acc, c -> acc + c.scrollDelta }
        val amount = (event.awtEventOrNull as? MouseWheelEvent)?.scrollAmount?.toFloat() ?: 1f
        val step = 28.dp.toPx()
        return Offset(
            x = totalScrollDelta.x * step * -amount,
            y = totalScrollDelta.y * step * -amount
        )
    }
}
