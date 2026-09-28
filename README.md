<div align="center">

<br />

<!-- 1. Закругленная иконка проекта -->
<img src=".github/res/icon.png" width="80" height="80" alt="Project Icon" style="border-radius: 20%;" />

# SCHEDULE

Кроссплатформенный клиент и экосистема синхронизации академического расписания для студентов и преподавателей.

<br />

<!-- 2. Информационные чипсы (height="24") -->
<img src="https://img.shields.io/badge/status-stable-09090b?style=for-the-badge&labelColor=000000" height="24" />
<img src="https://img.shields.io/badge/version-1.3.15-09090b?style=for-the-badge&labelColor=000000" height="24" />
<img src="https://img.shields.io/badge/platform-Android_%7C_Desktop_%7C_Web-09090b?style=for-the-badge&labelColor=000000" height="24" />
<img src="https://img.shields.io/badge/license-MIT-09090b?style=for-the-badge&labelColor=000000" height="24" />

<br />

<div>
<!-- 4. Витрина (Showcase) -->
<img src=".github/res/image1.jpg" width="31%" />
&nbsp;
<img src=".github/res/image2.jpg" width="31%" />
&nbsp;
<img src=".github/res/image3.jpg" width="31%" />
</div>

<br />

<!-- 3. Кнопки дистрибуции и установки -->
[![Releases](https://img.shields.io/badge/Releases-APK_%2F_Desktop-09090b?style=for-the-badge&logo=github&logoColor=white&labelColor=000000)](https://github.com/whatrushki/schedule/releases)
&nbsp;
[![Web](https://img.shields.io/badge/Launch-Web_WASM-09090b?style=for-the-badge&logo=googlechrome&logoColor=white&labelColor=000000)](https://whatrushki.github.io/schedule/)
&nbsp;
[![GitHub Packages](https://img.shields.io/badge/Packages-Maven_KMP-09090b?style=for-the-badge&logo=apachemaven&logoColor=white&labelColor=000000)](https://github.com/whatrushki/schedule/packages)

<br />
</div>

---

### О проекте

Schedule — мультиплатформенная экосистема для получения, парсинга и отображения расписания учебных заведений. Проект объединяет гетерогенные источники данных (от закрытых веб-API и XLSX-файлов до облачных таблиц) в единую нормализованную модель данных с поддержкой оперативного отслеживания замен, звонков и академических новостей.

Архитектура построена на базе Kotlin Multiplatform и Compose Multiplatform, обеспечивая консистентный пользовательский опыт и единый стек бизнес-логики на Android, десктопных операционных системах (Windows, macOS, Linux) и в браузере через WebAssembly.

#### Ключевые возможности

* **Мультипровайдерная синхронизация**  
  Автономные клиенты парсинга для каждого учебного заведения с извлечением групп, преподавателей, аудиторий и расписания звонков.

* **Автоматическое наложение замен**  
  Интеллектуальное сопоставление базового расписания с оперативными изменениями, вычисление добавленных, отмененных и перенесенных занятий.

* **Личные кабинеты студентов**  
  Авторизация, зачетные книжки, корпоративная почта, заказ справок, переаттестации и платежные QR-коды для поддерживаемых учебных заведений (ДГТУ, РКСИ).

* **Виджеты рабочего стола и уведомления о заменах**  
  Android Glance-виджеты расписания с динамической адаптацией тем оформления и периодический фоновый WorkManager-мониторинг оперативных замен для избранных групп.

* **Академические новости и галереи**  
  Интегрированная лента новостей учебных заведений с адаптивной версткой, предпросмотром медиафайлов, форматированием цитат и полноэкранным просмотром деталей.

* **Автономность и фоновый кэш**  
  Многоуровневое кэширование расписания и метаданных для бесперебойной работы клиентов в условиях нестабильной сети или недоступности серверов учреждений.

* **Периодический мониторинг и синхронизация**  
  Автоматизированный CI-конвейер проверки стабильности парсеров и синхронизации актуальных расписаний (`schedule-sync`) для веб-клиента и облачного fallback-источника.

#### Поддерживаемые учебные заведения

| Организация | Модуль | Источник данных | Статус |
| :--- | :--- | :--- | :--- |
| **РКСИ** — Ростовский-на-Дону колледж связи и информатики | `:libs:schedule:rksi` | XLSX, Google Таблицы, Веб | Поддерживается |
| **ДГТУ** — Донской государственный технический университет | `:libs:schedule:dgtu` | Внутренний REST API | Поддерживается |
| **РГЭУ (РИНХ)** — Ростовский государственный экономический университет | `:libs:schedule:rinh` | Официальный веб-сервис | Поддерживается |
| **ИУБиП** — Институт управления, бизнеса и права | `:libs:schedule:iubip` | Информационный портал | Поддерживается |
| **ЮФУ (Мехмат)** — Институт математики, механики и КН им. И.И. Воровича | `:libs:schedule:sfedu` | Официальный REST API / Веб | Поддерживается |
| **РГУПС** — Ростовский государственный университет путей сообщения | `:libs:schedule:rgups` | Веб-портал расписания | Поддерживается |
| **РГУПС (Туапсе)** — Филиал РГУПС в г. Туапсе | `:libs:schedule:rgups_tuapse` | Информационный сервис | Поддерживается |
| **ТвГУ** — Тверской государственный университет | `:libs:schedule:tvgu` | Официальный REST API / Веб | Поддерживается |

---

### Инженерная спецификация

#### Стек и инструменты

<p align="left">
  <img src="https://img.shields.io/badge/Kotlin-2.1.20--RC-09090b?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=000000" height="24" />
  <img src="https://img.shields.io/badge/Compose_Multiplatform-1.8.0--alpha03-09090b?style=for-the-badge&logo=jetpackcompose&logoColor=white&labelColor=000000" height="24" />
  <img src="https://img.shields.io/badge/Ktor_Client-3.1.0-09090b?style=for-the-badge&logo=ktor&logoColor=white&labelColor=000000" height="24" />
  <img src="https://img.shields.io/badge/Koin-4.0.2-09090b?style=for-the-badge&logo=koin&logoColor=white&labelColor=000000" height="24" />
  <img src="https://img.shields.io/badge/Room_DB-2.7.0--alpha13-09090b?style=for-the-badge&logo=sqlite&logoColor=white&labelColor=000000" height="24" />
  <img src="https://img.shields.io/badge/Ksoup-0.2.0-09090b?style=for-the-badge&logo=html5&logoColor=white&labelColor=000000" height="24" />
</p>

#### Архитектура

```mermaid
graph TD
    subgraph Targets["Точки сборки приложений"]
        APP["app (Android APK / AAB)"]
        DESKTOP["desktopApp (Desktop JVM)"]
        WASM["wasmApp (Web Wasm)"]
    end

    subgraph Presentation["Презентационный слой"]
        COMP_APP["composeApp (Root UI & Navigation)"]
        FEATURES["features (schedule, news, settings, account, dev)"]
    end

    subgraph CoreLayer["Ядро и навигация"]
        CORE_NAV["core:navigation"]
        CORE_FOUND["core:foundation"]
    end

    subgraph BusinessLayer["Бизнес-логика и данные"]
        DOMAIN["domain (Модели, Use Cases)"]
        DATA["data (Room DB, Репозитории, Кэш)"]
    end

    subgraph ParsersLayer["Парсеры заведений (libs:schedule)"]
        CORE_PARSER["core (Базовые DTO и клиенты)"]
        P_RKSI["rksi (Колледж связи)"]
        P_DGTU["dgtu (Донской тех. ун-т)"]
        P_IUBIP["iubip (Институт упр-я и права)"]
        P_RINH["rinh (РГЭУ РИНХ)"]
        P_SFEDU["sfedu (ЮФУ Мехмат)"]
        P_RGUPS["rgups (РГУПС)"]
        P_TUAPSE["rgups_tuapse (РГУПС Туапсе)"]
        P_TVGU["tvgu (ТвГУ Тверь)"]
    end

    subgraph ToolsLayer["Инструменты"]
        SYNC["tools:schedule-sync (CLI & CI Health Check)"]
    end

    APP --> COMP_APP
    DESKTOP --> COMP_APP
    WASM --> COMP_APP

    COMP_APP --> FEATURES
    FEATURES --> CORE_NAV
    FEATURES --> CORE_FOUND
    FEATURES --> DOMAIN

    DATA --> DOMAIN
    DATA --> CORE_PARSER
    DATA --> P_RKSI
    DATA --> P_DGTU
    DATA --> P_IUBIP
    DATA --> P_RINH
    DATA --> P_SFEDU
    DATA --> P_RGUPS
    DATA --> P_TUAPSE
    DATA --> P_TVGU

    P_RKSI --> CORE_PARSER
    P_DGTU --> CORE_PARSER
    P_IUBIP --> CORE_PARSER
    P_RINH --> CORE_PARSER
    P_SFEDU --> CORE_PARSER
    P_RGUPS --> CORE_PARSER
    P_TUAPSE --> CORE_PARSER
    P_TVGU --> CORE_PARSER

    SYNC --> CORE_PARSER
    SYNC --> P_RKSI
    SYNC --> P_DGTU
    SYNC --> P_IUBIP
    SYNC --> P_RINH
    SYNC --> P_SFEDU
    SYNC --> P_RGUPS
    SYNC --> P_TUAPSE
    SYNC --> P_TVGU
```

#### Подключение библиотек парсеров (Maven Packages)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/whatrushki/schedule")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// build.gradle.kts
dependencies {
    implementation("app.what.schedule:core:1.3.15")
    implementation("app.what.schedule:rksi:1.3.15")
    implementation("app.what.schedule:dgtu:1.3.15")
    implementation("app.what.schedule:iubip:1.3.15")
    implementation("app.what.schedule:rinh:1.3.15")
    implementation("app.what.schedule:sfedu:1.3.15")
    implementation("app.what.schedule:rgups:1.3.15")
    implementation("app.what.schedule:rgups_tuapse:1.3.15")
    implementation("app.what.schedule:tvgu:1.3.15")
}
```

#### Сборка и запуск

```bash
# Клонирование репозитория
git clone https://github.com/whatrushki/schedule.git
cd schedule

# Сборка Android APK
./gradlew assembleDebug

# Запуск Desktop-приложения
./gradlew :desktopApp:run

# Запуск Web (Wasm) в dev-режиме
./gradlew :wasmApp:wasmJsBrowserDevelopmentRun
```


---

### Обратная связь и участие

* Ошибки и запросы функционала оформляются через [GitHub Issues](https://github.com/whatrushki/schedule/issues).
* Официальные каналы коммуникации и вопросы разработки: [Telegram](https://t.me/whatrushki).

<br />

<div align="center">
<sub>© WHAT Technologies. Все права защищены. Лицензия MIT.</sub>
</div>
