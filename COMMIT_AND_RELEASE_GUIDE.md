# Регламент подготовки к коммиту и релизам (Release & Commit Guide)

Данный документ описывает обязательный пошаговый чеклист действий перед фиксацией изменений (`git commit`) и созданием релиза в репозитории **WHAT Schedule**. Предназначен для разработчиков и ИИ-ассистентов.

---

## 1. Чеклист перед коммитом обычной функциональности / фикса (Feature / Fix)

Выполняется перед каждым коммитом, даже если релиз не создаётся.

- [ ] **1. Проверка статуса и diff**:
  ```bash
  git status
  git diff
  ```
  Убедиться, что в изменения не попали временные файлы, тестовые токены, пароли, логи (`.system_generated`, `.DS_Store`, отладочный вывод и т.д.).
- [ ] **2. Чистота репозиториев (`settings.gradle.kts`)**:
  - Убедиться, что подключены только публичные/удаленные реестры (Maven Central, Google, GitHub Packages).
  - Если использовался `mavenLocal()` для локального тестирования, убедиться, что внешние библиотеки не зависят исключительно от локальных артефактов на рабочей машине.
- [ ] **3. Локальная компиляция и тесты**:
  - Запустить сборку затронутого таргета (или общий `compileCommonMainKotlinLibrary`):
    ```bash
    ./gradlew :wasmApp:wasmJsBrowserDistribution --no-daemon
    # или
    ./gradlew assembleDebug --no-daemon
    ```
- [ ] **4. Форматирование коммит-сообщения**:
  Использовать Conventional Commits на английском языке:
  - `feat(...)`: новая функциональность.
  - `fix(...)`: исправление багов.
  - `refactor(...)`: рефакторинг без изменения логики.
  - `docs(...)`: обновление документации.
  - `chore(...)`: рутинные изменения зависимостей, скриптов сборки.

---

## 2. Полный чеклист подготовки релиза (Release Checklist)

Выполняется строго по шагам при поднятии версии и публикации релиза.

### Шаг 1. Поднятие версии в проекте
Версия проекта задается в одном месте:
- **`gradle/libs.versions.toml`**:
  ```toml
  [versions]
  appVersion = "X.Y.Z" # Например: "1.4.0"
  ```
  *(Примечание: `app/build.gradle.kts` автоматически считывает `versionName = libs.versions.appVersion.get()`, а `versionCode` генерируется по формуле `yyMMddHH`).*

### Шаг 2. Обновление Release Notes
Релиз-ноты используются мобильным приложением (шторка онбординга обновлений), десктопом и веб-клиентом. Необходимо обновить **оба** файла:

1. **`metadata/release-notes.json`** — первоисточник для CI/CD и автообновлений.
2. **`features/onboarding/src/commonMain/composeResources/files/release-notes.json`** — встроенный ресурс для оффлайн-отображения при первом открытии новой версии.

Структура `release-notes.json`:
```json
{
  "schema_version": 1,
  "version": "X.Y.Z",
  "version_code": 1400,
  "release_date": "YYYY-MM-DD",
  "title": "Краткий заголовок релиза",
  "short_description": "Одно-два предложения с описанием главного в релизе.",
  "highlights": [],
  "changelog": [
    "Пункт 1 изменений",
    "Пункт 2 изменений",
    "Пункт 3 изменений"
  ]
}
```

### Шаг 3. Фиксация изменений (Commit & Push)
1. Добавить файлы в индекс:
   ```bash
   git add .
   ```
2. Проверить `git status`: убедиться, что затронуты версия, код и оба файла `release-notes.json`.
3. Создать коммит релиза:
   ```bash
   git commit -m "release: vX.Y.Z - краткое описание ключевых фич"
   ```
4. Отправить в ветку `master`:
   ```bash
   git push origin master
   ```

### Шаг 4. Создание и публикация релиза GitHub
Создание релиза запускает GitHub Actions workflow (`build_and_attach_to_release_on_publication.yml`), который собирает и прикрепляет все артефакты (Android APK, Desktop JAR/EXE/DMG/DEB, Web, iOS).

1. Создать релиз через GitHub CLI (`gh`):
   ```bash
   gh release create vX.Y.Z --title "vX.Y.Z" --notes "### Что нового в vX.Y.Z:

   - **Ключевой блок**:
     - Описание изменений."
   ```
2. Проверить статус запуска пайплайнов:
   ```bash
   gh run list --limit 3
   ```
   Должны запуститься:
   - `Build, Release and Publish All Targets`
   - `Sync Schedule Data for Web` (если применимо)

---

## 3. Таблица ключевых файлов релиза

| Назначение | Путь к файлу | Что проверять |
| :--- | :--- | :--- |
| Версия приложения | [`gradle/libs.versions.toml`](gradle/libs.versions.toml) | Поле `appVersion` |
| Метаданные релиза (CI/CD) | [`metadata/release-notes.json`](metadata/release-notes.json) | `version`, `version_code`, `release_date`, `changelog` |
| Ресурс онбординга | [`features/onboarding/src/commonMain/composeResources/files/release-notes.json`](features/onboarding/src/commonMain/composeResources/files/release-notes.json) | Должен совпадать с `metadata/release-notes.json` |
| Репозитории зависимостей | [`settings.gradle.kts`](settings.gradle.kts) | Доступность публичных репозиториев |
| Workflow публикации | [`.github/workflows/build_and_attach_to_release_on_publication.yml`](.github/workflows/build_and_attach_to_release_on_publication.yml) | Триггеры и параметры сборки |
