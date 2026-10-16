package app.what.data.network.traffic

object TrafficClassifier {
    fun classify(url: String, contentType: String? = null): TrafficCategory {
        val lowerUrl = url.lowercase()
        val lowerType = contentType?.lowercase().orEmpty()

        return when {
            // 1. Медиа и изображения
            lowerType.startsWith("image/") ||
            lowerUrl.endsWith(".png") || lowerUrl.endsWith(".jpg") ||
            lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".webp") ||
            lowerUrl.endsWith(".svg") || lowerUrl.endsWith(".gif") ||
            lowerUrl.endsWith(".ico") -> TrafficCategory.MEDIA

            // 2. Расписание (gh-pages снапшоты, парсеры институтов, google drive)
            lowerUrl.contains("schedule-data") ||
            lowerUrl.contains("schedule.whatrushki-tech.workers.dev") ||
            lowerUrl.contains("timetable") ||
            lowerUrl.contains("/schedule") ||
            lowerUrl.contains("mobile_schedule") ||
            lowerUrl.contains("drive.google.com") ||
            lowerUrl.contains("edu.susu.ru") ||
            lowerUrl.contains("iubip.ru") ||
            lowerUrl.contains("rgups.ru") -> TrafficCategory.SCHEDULE

            // 3. Личный кабинет и оценки
            lowerUrl.contains("grade.sfedu.ru") ||
            lowerUrl.contains("edu.donstu.ru") ||
            lowerUrl.contains("/student") ||
            lowerUrl.contains("/grade") ||
            lowerUrl.contains("/auth") ||
            lowerUrl.contains("/login") ||
            lowerUrl.contains("bill") -> TrafficCategory.ACCOUNT

            // 4. Новости
            lowerUrl.contains("/news") ||
            lowerUrl.contains("mmcs.sfedu.ru") ||
            lowerUrl.contains("tversu.ru/news") ||
            lowerUrl.contains("rksi.ru/news") -> TrafficCategory.NEWS

            // 5. Обновления и система
            lowerUrl.contains("api.github.com/repos") ||
            lowerUrl.contains("github.com/whatrushki/schedule/releases") ||
            lowerUrl.contains("crashlytics") ||
            lowerUrl.contains("firebase") ||
            lowerUrl.contains("telemetry") -> TrafficCategory.SYSTEM

            else -> TrafficCategory.SYSTEM
        }
    }
}
