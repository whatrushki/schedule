package app.what.schedule.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.awt.ComposeWindow
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.local.settings.ThemeType
import app.what.schedule.ui.theme.getAppColorScheme
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import java.awt.Color
import java.awt.Image
import javax.imageio.ImageIO
import kotlin.math.roundToInt

interface Dwmapi : StdCallLibrary {
    fun DwmSetWindowAttribute(
        hwnd: WinDef.HWND,
        dwAttribute: Int,
        pvAttribute: Pointer,
        cbAttribute: Int
    ): Int

    companion object {
        val INSTANCE: Dwmapi? = runCatching {
            Native.load("dwmapi", Dwmapi::class.java)
        }.getOrNull()
    }
}

@Composable
fun DesktopWindowThemeEffect(
    window: ComposeWindow,
    settings: AppValues
) {
    val themeType by settings.themeType.collect()
    val themeStyle by settings.themeStyle.collect()
    val themeColor by settings.themeColor.collect()
    val isSystemDark = isSystemInDarkTheme()

    val isDarkTheme = when (themeType) {
        ThemeType.Dark -> true
        ThemeType.System -> isSystemDark
        else -> false
    }

    val colorScheme = getAppColorScheme(themeType, themeStyle, themeColor, isSystemDark)

    LaunchedEffect(Unit) {
        runCatching {
            val iconList = mutableListOf<Image>()
            listOf("icons/icon.png", "icons/icon_256.png").forEach { resPath ->
                Dwmapi::class.java.classLoader.getResourceAsStream(resPath)?.use { stream ->
                    ImageIO.read(stream)?.let { iconList.add(it) }
                }
            }
            if (iconList.isNotEmpty()) {
                window.iconImages = iconList
            }
        }
    }

    LaunchedEffect(colorScheme, isDarkTheme) {
        runCatching {
            val bg = colorScheme.background
            val surface = colorScheme.surface
            val onSurface = colorScheme.onSurface

            val awtBg = Color(
                (bg.red * 255).roundToInt().coerceIn(0, 255),
                (bg.green * 255).roundToInt().coerceIn(0, 255),
                (bg.blue * 255).roundToInt().coerceIn(0, 255)
            )
            window.background = awtBg

            val os = System.getProperty("os.name", "")
            if (os.contains("Windows", ignoreCase = true)) {
                val hwnd = WinDef.HWND(Native.getWindowPointer(window))
                val dwm = Dwmapi.INSTANCE
                if (dwm != null) {
                    // DWMWA_USE_IMMERSIVE_DARK_MODE = 20
                    val darkModeMemory = Memory(4).apply { setInt(0, if (isDarkTheme) 1 else 0) }
                    dwm.DwmSetWindowAttribute(hwnd, 20, darkModeMemory, 4)

                    // DWMWA_CAPTION_COLOR = 35 (Windows 11 build 22000+)
                    val sr = (surface.red * 255).roundToInt().coerceIn(0, 255)
                    val sg = (surface.green * 255).roundToInt().coerceIn(0, 255)
                    val sb = (surface.blue * 255).roundToInt().coerceIn(0, 255)
                    val captionColorRef = sr or (sg shl 8) or (sb shl 16)
                    val captionMemory = Memory(4).apply { setInt(0, captionColorRef) }
                    dwm.DwmSetWindowAttribute(hwnd, 35, captionMemory, 4)

                    // DWMWA_TEXT_COLOR = 36 (Windows 11 build 22000+)
                    val tr = (onSurface.red * 255).roundToInt().coerceIn(0, 255)
                    val tg = (onSurface.green * 255).roundToInt().coerceIn(0, 255)
                    val tb = (onSurface.blue * 255).roundToInt().coerceIn(0, 255)
                    val textColorRef = tr or (tg shl 8) or (tb shl 16)
                    val textMemory = Memory(4).apply { setInt(0, textColorRef) }
                    dwm.DwmSetWindowAttribute(hwnd, 36, textMemory, 4)

                    window.repaint()
                }
            }
        }
    }
}
