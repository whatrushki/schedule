package app.what.schedule.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.local.settings.ThemeStyle
import app.what.schedule.data.local.settings.ThemeType
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object AppIconManager {
    enum class Palette(val darkAlias: String, val lightAlias: String, val approxColor: Long) {
        NAVY(
            "app.what.schedule.MainActivity",
            "app.what.schedule.MainActivityLight",
            0xFF2A3478 // Тёмный синий — лучше представляет нави-иконку
        ),
        TERRACOTTA(
            "app.what.schedule.MainActivityTerracottaDark",
            "app.what.schedule.MainActivityTerracottaLight",
            0xFFD27731
        ),
        CRIMSON(
            "app.what.schedule.MainActivityCrimsonDark",
            "app.what.schedule.MainActivityCrimsonLight",
            0xFF691616
        ),
        FOREST(
            "app.what.schedule.MainActivityForestDark",
            "app.what.schedule.MainActivityForestLight",
            0xFF275325
        ),
        PLUM(
            "app.what.schedule.MainActivityPlumDark",
            "app.what.schedule.MainActivityPlumLight",
            0xFF9B3782
        );

        companion object {
            /**
             * Находит ближайшую палитру по HSV-расстоянию.
             * Hue-based сравнение корректнее для восприятия, чем Euclidean RGB.
             */
            fun findClosest(targetColor: Long): Palette {
                val targetHsv = colorToHsv(targetColor)
                val targetSat = targetHsv[1]

                // Если цвет почти ахроматический (серый/чёрный/белый), берём Navy
                if (targetSat < 0.10f) return NAVY

                return entries.minByOrNull { palette ->
                    val paletteHsv = colorToHsv(palette.approxColor)
                    hsvDistance(targetHsv, paletteHsv)
                } ?: NAVY
            }

            private fun colorToHsv(color: Long): FloatArray {
                val r = ((color shr 16) and 0xFF).toFloat() / 255f
                val g = ((color shr 8) and 0xFF).toFloat() / 255f
                val b = (color and 0xFF).toFloat() / 255f

                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val delta = max - min

                val h = when {
                    delta == 0f -> 0f
                    max == r -> 60f * (((g - b) / delta) % 6f)
                    max == g -> 60f * (((b - r) / delta) + 2f)
                    else -> 60f * (((r - g) / delta) + 4f)
                }.let { if (it < 0) it + 360f else it }

                val s = if (max == 0f) 0f else delta / max
                val v = max

                return floatArrayOf(h, s, v)
            }

            private fun hsvDistance(hsv1: FloatArray, hsv2: FloatArray): Double {
                // Циклическое расстояние по hue (0..360)
                val hueDiff = min(abs(hsv1[0] - hsv2[0]), 360f - abs(hsv1[0] - hsv2[0]))
                val satDiff = hsv1[1] - hsv2[1]
                val valDiff = hsv1[2] - hsv2[2]

                // Hue — основной фактор, saturation и value — вспомогательные
                return sqrt(
                    (hueDiff * 2.0).pow(2) +
                    (satDiff * 100.0).pow(2) +
                    (valDiff * 50.0).pow(2)
                )
            }
        }
    }

    private val ALL_ALIASES by lazy {
        Palette.entries.flatMap { listOf(it.darkAlias, it.lightAlias) }
    }

    private var isForeground = false
    private var pendingUpdate = false
    private var isInitialized = false

    fun init(application: android.app.Application, appValues: AppValues) {
        if (isInitialized) return
        isInitialized = true

        application.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: android.app.Activity) {
                startedActivities++
                isForeground = startedActivities > 0
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivities--
                isForeground = startedActivities > 0
                if (!isForeground && pendingUpdate) {
                    pendingUpdate = false
                    updateIconInternal(activity.applicationContext, appValues)
                }
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    fun updateIcon(context: Context, appValues: AppValues, force: Boolean = false) {
        if (context is android.app.Application && !isInitialized) {
            init(context, appValues)
        }

        if (isForeground && !force) {
            // Приложение на переднем плане: откладываем переключение алиаса лаунчера до ухода в фон,
            // иначе Android OS принудительно закроет текущую Activity (kill task)
            Auditor.debug("AppIcon", "Приложение на переднем плане, обновление иконки отложено до ухода в фон")
            pendingUpdate = true
            return
        }

        updateIconInternal(context, appValues)
    }

    private fun updateIconInternal(context: Context, appValues: AppValues) {
        try {
            val themeType = appValues.themeType.get() ?: ThemeType.System
            val themeStyle = appValues.themeStyle.get() ?: ThemeStyle.Default

            val isDark = when (themeType) {
                ThemeType.Dark -> true
                ThemeType.Light -> false
                ThemeType.System -> {
                    val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    currentNightMode == Configuration.UI_MODE_NIGHT_YES
                }
            }

            val palette = when (themeStyle) {
                ThemeStyle.Material -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            val accentInt = context.getColor(android.R.color.system_accent1_500)
                            Palette.findClosest(accentInt.toLong() and 0xFFFFFFFFL)
                        } catch (_: Exception) {
                            Palette.NAVY
                        }
                    } else {
                        Palette.NAVY
                    }
                }
                ThemeStyle.CustomColor -> {
                    val customColor = appValues.themeColor.get()
                    if (customColor != null) {
                        // Color.value хранит packed SRGB — извлекаем ARGB через Compose Color
                        val color = androidx.compose.ui.graphics.Color(customColor)
                        val argb = android.graphics.Color.argb(
                            (color.alpha * 255).toInt(),
                            (color.red * 255).toInt(),
                            (color.green * 255).toInt(),
                            (color.blue * 255).toInt()
                        ).toLong() and 0xFFFFFFFFL
                        Palette.findClosest(argb)
                    } else {
                        Palette.NAVY
                    }
                }
                ThemeStyle.Monochrome -> Palette.NAVY
                ThemeStyle.Default -> Palette.NAVY
            }

            val targetAlias = if (isDark) palette.darkAlias else palette.lightAlias
            val pm = context.packageManager
            val targetComponent = ComponentName(context, targetAlias)

            val currentState = pm.getComponentEnabledSetting(targetComponent)
            if (currentState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                Auditor.info("AppIcon", "Переключение иконки на $targetAlias (palette=$palette, isDark=$isDark)")

                // Сначала включаем целевой алиас, чтобы ярлык не удалился лаунчером
                pm.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                // Затем отключаем все остальные алиасы
                for (alias in ALL_ALIASES) {
                    if (alias != targetAlias) {
                        val comp = ComponentName(context, alias)
                        if (pm.getComponentEnabledSetting(comp) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                            pm.setComponentEnabledSetting(
                                comp,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Auditor.debug("AppIcon", "Ошибка при обновлении иконки: ${e.message}")
        }
    }
}
