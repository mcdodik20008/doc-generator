# Алгоритм выкачивания исходников из Git-репозитория

## Обзор

Данный документ описывает процесс получения исходного кода из Git-репозитория и подготовки его для дальнейшего анализа и построения графа кода.

## Инструменты

- **JGit** (`org.eclipse.jgit`) - Java-библиотека для работы с Git репозиториями
- **Gradle Wrapper** (`gradlew` / `gradlew.bat`) - для разрешения зависимостей проекта

## Основной процесс

### Фаза 1: Checkout репозитория

**Сервис**: `GitLabCheckoutService.checkoutOrUpdate()`

#### Шаг 1.1: Определение пути checkout

```kotlin
val checkoutDir: Path = Path.of(gitProps.basePath, appKey)
```

- `basePath` - базовый путь для хранения всех репозиториев (из конфигурации)
- `appKey` - уникальный идентификатор приложения
- Результат: `{basePath}/{appKey}` - локальная директория для исходников

#### Шаг 1.2: Проверка существования репозитория

Проверяется наличие директории `.git` в checkout директории:

```kotlin
val gitDir = File(dir, ".git")
if (gitDir.exists()) {
    // UPDATE режим
} else {
    // CLONE режим
}
```

#### Шаг 1.3: UPDATE режим (репозиторий уже существует)

Если репозиторий уже был склонирован ранее:

1. **Открытие существующего репозитория**:
   ```kotlin
   Git.open(dir).use { git ->
       // операции с git
   }
   ```

2. **Получение текущего HEAD** (до обновления):
   ```kotlin
   before = resolveHead(git)  // SHA коммита
   ```

3. **Fetch** - получение изменений из удаленного репозитория:
   ```kotlin
   git.fetch().setCredentialsProvider(creds).call()
   ```

4. **Checkout** - переключение на нужную ветку:
   ```kotlin
   git.checkout().setName(branch).call()
   ```

5. **Pull** - обновление локальной ветки:
   ```kotlin
   git.pull().setCredentialsProvider(creds).call()
   ```

6. **Получение нового HEAD** (после обновления):
   ```kotlin
   after = resolveHead(git)  // SHA коммита
   ```

7. **Результат**: `GitPullSummary` с `operation = GitOperation.PULL`

**Обработка ошибок**: Если pull не удался, возвращается `GitPullSummary` с `operation = GitOperation.NOOP` и текущим HEAD.

#### Шаг 1.4: CLONE режим (первое клонирование)

Если репозитория еще нет:

1. **Разрешение URL репозитория**:
   ```kotlin
   val repoUrl = resolveRepoUrl(gitProps.url, repoPath)
   ```
   
   Логика разрешения:
   - Если `repoPath` начинается с `http://` или `https://` → используется как есть
   - Если `repoPath` заканчивается на `.git` → используется как есть
   - Иначе: `{baseUrl}/{repoPath}.git`

2. **Клонирование репозитория**:
   ```kotlin
   Git.cloneRepository()
       .setURI(repoUrl)
       .setBranch(branch)      // указание ветки
       .setDirectory(dir)       // целевая директория
       .setCredentialsProvider(creds)
       .call()
   ```

3. **Получение HEAD** после клонирования:
   ```kotlin
   val headAfter = Git.open(dir).use { resolveHead(it) }
   ```

4. **Результат**: `GitPullSummary` с `operation = GitOperation.CLONE`

#### Шаг 1.5: Аутентификация

Поддержка двух методов аутентификации (с приоритетом):

1. **Username/Password** (приоритет 1):
   ```kotlin
   UsernamePasswordCredentialsProvider(gitProps.username, gitProps.password)
   ```

2. **OAuth2 Token** (fallback):
   ```kotlin
   UsernamePasswordCredentialsProvider("oauth2", gitProps.token)
   ```

### Фаза 2: Разрешение classpath для Kotlin PSI парсера

**Сервис**: `GradleClasspathResolver.resolveClasspath()`

**Важность**: Без classpath PSI парсер не может разрешить типы, и тела функций будут `NULL`.

#### Шаг 2.1: Поиск Gradle проектов

```kotlin
Files.walk(localPath)
    .filter { it.fileName.toString() == "gradlew" || it.fileName.toString() == "gradlew.bat" }
    .map { it.parent }
    .distinct()
    .toList()
```

- Рекурсивный обход директории с исходниками
- Поиск файлов `gradlew` (Unix) или `gradlew.bat` (Windows)
- Сбор уникальных директорий проектов

#### Шаг 2.2: Создание init-скрипта

Создается временный Groovy-скрипт (`.gradle`), который:

1. Подключается к `allprojects.afterEvaluate`
2. Для проектов с плагинами `java`/`java-library`/`org.jetbrains.kotlin.jvm`:
   - Получает конфигурацию `runtimeClasspath`
   - Выводит каждый файл с маркером `CLASSPATH_ENTRY:`

```groovy
def CLASSPATH_MARKER = "CLASSPATH_ENTRY:"

allprojects {
    afterEvaluate { project ->
        if (project.plugins.hasPlugin("java") || 
            project.plugins.hasPlugin("java-library") || 
            project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
            
            def runtimeClasspath = project.configurations.getByName("runtimeClasspath")
            runtimeClasspath.files.each { file ->
                println(CLASSPATH_MARKER + file.absolutePath)
            }
        }
    }
}
```

#### Шаг 2.3: Запуск Gradle

Команда для запуска:

**Windows**:
```kotlin
cmd.exe /c gradlew.bat --init-script {script} tasks --quiet
```

**Unix/Linux**:
```kotlin
/bin/sh gradlew --init-script {script} tasks --quiet
```

- Запускается безвредная задача `tasks` с флагом `--quiet`
- Это заставляет Gradle выполнить init-скрипт
- Init-скрипт выводит все файлы classpath с маркером

#### Шаг 2.4: Парсинг вывода

```kotlin
while (reader.readLine().also { line = it } != null) {
    if (line.startsWith("CLASSPATH_ENTRY:")) {
        val filePath = line.substringAfter("CLASSPATH_ENTRY:")
        val file = File(filePath)
        if (file.exists()) {
            classpathFiles.add(file)
        }
    }
}
```

- Поиск строк с префиксом `CLASSPATH_ENTRY:`
- Извлечение пути к файлу
- Проверка существования файла
- Добавление в список

#### Шаг 2.5: Результат

Возвращается `List<File>` - все JAR-файлы зависимостей проекта, которые необходимы для корректного парсинга Kotlin кода через PSI.

## Схема потока данных

```
GitLabIngestOrchestrator.runOnce()
  ↓
GitLabCheckoutService.checkoutOrUpdate()
  ├─→ Проверка существования .git
  │   ├─→ Да: UPDATE режим
  │   │   ├─→ git.fetch()
  │   │   ├─→ git.checkout(branch)
  │   │   └─→ git.pull()
  │   │
  │   └─→ Нет: CLONE режим
  │       └─→ Git.cloneRepository()
  │
  └─→ GitPullSummary (локальный путь к исходникам)
      ↓
GradleClasspathResolver.resolveClasspath()
  ├─→ Поиск gradlew
  ├─→ Создание init-скрипта
  ├─→ Запуск: gradlew --init-script tasks
  └─→ Парсинг вывода → List<File> (classpath)
      ↓
LibraryBuildRequestedEvent (асинхронно)
  └─→ Передача sourceRoot и classpath в Graph Builder
```

## Структура GitPullSummary

```kotlin
data class GitPullSummary(
    val repoUrl: String,           // URL репозитория
    val branch: String,             // Ветка
    val appKey: String,             // Идентификатор приложения
    val localPath: Path,            // Локальный путь к исходникам
    val operation: GitOperation,    // CLONE | PULL | NOOP
    val beforeHead: String?,        // SHA до обновления
    val afterHead: String?,        // SHA после обновления
    val fetchedAt: OffsetDateTime  // Время операции
)
```

## Обработка ошибок

1. **TransportException** при клонировании:
   - Логируется ошибка
   - Исключение пробрасывается выше

2. **Ошибка при pull**:
   - Логируется предупреждение
   - Возвращается `GitPullSummary` с `operation = NOOP`
   - Используется текущий HEAD

3. **Отсутствие gradlew**:
   - Логируется предупреждение
   - Возвращается пустой список classpath
   - Анализ продолжится, но PSI парсер может работать некорректно

4. **Ошибка при запуске Gradle**:
   - Логируется ошибка
   - Возвращается пустой список classpath

## Конфигурация

Параметры настраиваются через `GitLabProps`:

```kotlin
data class GitLabProps(
    val url: String,           // Базовый URL GitLab
    val basePath: String,      // Базовый путь для checkout
    val username: String?,    // Username для аутентификации
    val password: String?,     // Password для аутентификации
    val token: String?         // OAuth2 token (fallback)
)
```

## Связанные компоненты

- `GitLabIngestOrchestrator` - оркестратор процесса индексации
- `RepoUrlParser` - парсер URL репозитория для определения провайдера
- `KotlinSourceWalker` - обход исходников после checkout
- `KotlinGraphBuilder` - построитель графа кода

