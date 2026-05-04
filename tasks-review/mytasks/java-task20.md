# Надежность(как мониторить OOM и как пофиксить)

Отличный вопрос! `OutOfMemoryError` (OOME) — это критическая ситуация, но её можно мониторить, диагностировать и в некоторых случаях исправить.

## 1. Как мониторить OutOfMemoryError

### Способ 1: Добавление параметров JVM (рекомендуется для прода)

```bash
-XX:+HeapDumpOnOutOfMemoryError 
-XX:HeapDumpPath=/path/to/dumps/ 
-XX:OnOutOfMemoryError="kill -3 %p"  # выполнить действие при OOME
-XX:+ExitOnOutOfMemoryError          # завершить JVM при OOME
-XX:+PrintGCDetails 
-XX:+PrintGCTimeStamps 
# GC-логи — позволяют увидеть, как растёт использование heap, сколько времени тратит GC и т.д.
-Xloggc:/var/logs/gc.log
# Для Java 9+
-Xlog:gc*:file=/var/logs/gc.log:time,level,tags
```

При возникновении OOME автоматически создастся heap dump.

### Способ 2: Использование платформенных инструментов

- **Prometheus + Grafana** (через JMX Exporter)
- **Datadog, New Relic, AppDynamics**
- **ELK Stack** (через logs и heap dumps)
- **Java Melody** (легковесный мониторинг)

```java
// Пример метрик для Prometheus (с Micrometer)
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;

new JvmMemoryMetrics().bindTo(registry);
```


# Выгрузка дампа

Выгрузить дамп можно несколькими способами, в зависимости от ситуации: когда приложение уже «зависло», когда оно еще работает или чтобы оно само сделало дамп в момент падения.

Вот 3 самых популярных способа:

## 1. Автоматически при падении (Самый полезный)
Это «страховка». Ты добавляешь специальные флаги при запуске Java-приложения, и если оно упадет с ошибкой OutOfMemoryError, JVM сама сохранит файл дампа.

* Флаги: -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./logs/dumps/
* Плюс: Тебе не нужно караулить момент аварии, файл уже будет ждать тебя на сервере.

## 2. Вручную через консоль (Утилита jmap)
Если приложение работает, но ты видишь, что память растет, или оно «тормозит», можно снять дамп «на лету». Это делается стандартной утилитой, которая идет вместе с Java (JDK).

   1. Сначала узнай ID процесса (PID) твоего приложения (командой jps или ps -ef | grep java).
   2. Выполни команду:
   jmap -dump:live,format=b,file=heap_dump.hprof <PID>
   * live — выгрузит только те объекты, которые реально нужны (сначала запустит сборщик мусора).
      * format=b — бинарный формат (его понимает Eclipse MAT).
      * file=... — имя файла.
   
## 3. Через визуальный интерфейс (VisualVM)
Если ты запускаешь приложение на своем компьютере или подключился к серверу удаленно через мониторинг:

   1. Запусти программу VisualVM (она часто лежит в папке bin твоего JDK).
   2. Найди свое приложение в списке слева.
   3. Нажми правую кнопку мыши -> Heap Dump.
   4. Программа сама создаст файл и даже предложит его сразу посмотреть.

## На что стоит обратить внимание (для интервью):

* Размер файла: Дамп весит столько же, сколько оперативная память, занятая приложением. Если у тебя -Xmx8g, готовься, что файл будет весить 8 ГБ. Убедись, что на диске есть место!
* Пауза (STW): В момент снятия дампа через jmap приложение может «замереть» на несколько секунд (или даже минут, если памяти очень много). На рабочем сервере (Production) это нужно делать осторожно.



# Анализ дампа

**Анализ heap dump** — это основной способ понять, почему в Java возникает **OutOfMemoryError** или постоянно растёт потребление памяти. Самый популярный и мощный бесплатный инструмент — **Eclipse Memory Analyzer Tool (MAT)**.

### 1. Подготовка к анализу

**Скачайте последнюю версию MAT** (на 2026 год — 1.16.1 или новее):

- Официальный сайт: https://eclipse.dev/mat/
- Рекомендуется **standalone** версия (не плагин в Eclipse).

**Увеличьте память для самого MAT** (очень важно для больших дампов!):

Откройте файл `MemoryAnalyzer.ini` (или `MemoryAnalyzer.ini` в корне) и измените:

```ini
-vmargs
-Xmx8g          # или -Xmx16g / -Xmx32g, в зависимости от размера дампа
-XX:+UseG1GC
```

Правило: у MAT должно быть **минимум в 1.5–2 раза больше RAM**, чем размер heap dump’а.

### 2. Как снять хороший heap dump

Рекомендуемые способы:

- Автоматически при OOM:
  ```bash
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/path/to/dumps/
  ```

- Вручную (лучше с `live` — только живые объекты):
  ```bash
  jcmd <pid> GC.heap_dump /path/to/heapdump.hprof
  # или
  jmap -dump:live,format=b,file=/path/to/heapdump.hprof <pid>
  ```

- Из VisualVM / JConsole / JDK Mission Control.

**Совет**: снимайте 2–3 дампа с интервалом (например, через 30–60 минут при растущей памяти) — это помогает увидеть, что именно растёт.

### 3. Пошаговый анализ в Eclipse MAT

1. **Запустите MAT** → **File → Open Heap Dump** → выберите `.hprof` файл.
2. MAT начнёт парсинг (может занять от нескольких секунд до десятков минут). Создаст индексы.
3. После открытия появится **Overview** (обзор) и wizard **Getting Started**.

**Первый и самый важный шаг — запустите "Leak Suspects Report"**

- Это автоматический отчёт, который ищет подозрительные утечки по эвристикам.
- MAT покажет:
  - **Problem Suspect #1, #2...**
  - Сколько памяти удерживает проблема (`Retained Heap`)
  - Краткое описание: "One instance of X is holding Y MB"

Очень часто уже на этом этапе видно виновника.

### 4. Основные инструменты анализа в MAT

| Инструмент              | Что показывает                                      | Когда использовать                          |
|-------------------------|-----------------------------------------------------|---------------------------------------------|
| **Leak Suspects Report**| Автоматические подозреваемые утечки                | Всегда в начале                             |
| **Histogram**           | Список классов по количеству объектов и Shallow size | Найти, каких объектов очень много          |
| **Dominator Tree**      | Дерево объектов, которые "держат" больше всего памяти (Retained) | Самый мощный инструмент для поиска утечек  |
| **Top Consumers**       | Топ объектов/классов по удерживаемой памяти        | Быстрый обзор                               |
| **Duplicate Objects**   | Дублирующиеся строки, массивы и т.д.               | Поиск неэффективного использования памяти  |
| **Path to GC Roots**    | Как объект удерживается в памяти (от GC Root)      | Когда нашли подозрительный объект          |
| **OQL**                 | SQL-подобный язык запросов к дампу                 | Продвинутый поиск                           |

**Ключевые понятия:**

- **Shallow Heap** — память, занимаемая самим объектом (без учёта того, на что он ссылается).
- **Retained Heap** — вся память, которая будет освобождена, если этот объект (и только он) станет недоступен. **Самая важная метрика!**

### 5. Типичный процесс анализа (рекомендуемый)

1. Запустите **Leak Suspects Report**.
2. Если ничего явного — откройте **Dominator Tree**.
   - Сортируйте по Retained Heap (descending).
   - Ищите большие группы объектов.
3. Кликните правой кнопкой на подозрительном объекте → **List Objects → with outgoing references** (что он держит) или **with incoming references**.
4. Самое важное — **Path to GC Roots** (exclude weak/soft references).
   - GC Roots — это то, что JVM никогда не собирает: статические поля, потоки, классы, JNI и т.д.
5. Проверьте **Histogram** на классы вашего приложения (например, `MyEntity`, `UserSession`, `CacheEntry` и т.п.).

### 6. Самые частые причины утечек памяти

- **Статические коллекции** (`static Map`, `static List`), которые постоянно заполняются.
- **Кэши без лимита** (Guava Cache / ConcurrentHashMap без `maximumSize` или eviction).
- **ThreadLocal** без `remove()` в пуле потоков (особенно в веб-приложениях).
- **Listener'ы / Event Bus**, которые не отписываются.
- **Класслоадер leaks** (в приложениях с горячей перезагрузкой классов, OSGi, плагинах).
- Большое количество **String** / `byte[]` / `char[]` (дубликаты строк, неиспользуемые буферы).
- Не закрытые ресурсы (хотя в современном Java с try-with-resources реже).
- Сессии пользователей, которые не инвалидируются.

### 7. Дополнительные полезные инструменты

- **VisualVM** — проще и быстрее для начального взгляда.
- **HeapHero** (онлайн/платный) — очень удобные отчёты + ML-подсказки.
- **JProfiler** / **YourKit** — коммерческие, но мощные (могут анализировать дампы).
- **JDK Mission Control + JFR** — для анализа до снятия дампа.

### Советы по практике

- Анализируйте **live** дампы (`-dump:live`) — они меньше и чище.
- Сравнивайте несколько дампов (MAT умеет сравнивать).
- Для очень больших дампов (>20–30 GB) иногда удобнее коммерческие инструменты или облачные сервисы.
- После нахождения проблемы — ищите в коде, где создаются/добавляются эти объекты и почему они не удаляются.


# Анализ дампа и выбор типа исключения

Отличный вопрос! Здесь кроется важный нюанс: **сам heap dump не содержит строки с типом `OutOfMemoryError`**. Это бинарный слепок кучи, он не знает, какая именно ошибка произошла. Но **по содержимому дампа вы всегда можете определить, какой тип OOME случился**, потому что разные типы оставляют разные «следы» в памяти.


## 1. `Java heap space` (самый частый)

**Что вы увидите в дампе:**
- Куча (heap) заполнена под завязку — использовано почти 100% от `-Xmx`
- Histogram показывает огромное количество объектов (миллионы)
- Обычно один класс или несколько доминируют по retained heap

**Как понять, что это именно `Java heap space` (а не, скажем, Metaspace):**
- Дамп кучи успешно открывается (в отличие от случая с native memory)
- В отчете Leak Suspects: «The memory is exhausted in the Java heap»
- Проверьте в MAT: вкладка **Overview** -> **Heap Size** (должна быть почти равна Max Heap)

**Пример:** Вы видите `byte[]` на 2 ГБ, `HashMap` с миллионом записей — OOME из-за нехватки места в куче.

## 2. `Metaspace` (или PermGen в Java 7 и ниже)

**Что вы увидите в дампе:**
- Куча (heap) может быть почти пустой (10-20% заполнения) – ключевой признак!
- А дамп всё равно огромный, потому что метаданные классов занимают память вне кучи, но **heap dump их тоже включает** (не полностью, но видно загруженные классы).
- В histogram вы увидите тысячи экземпляров `java.lang.Class`, `java.lang.reflect.Method`, `sun.reflect.GeneratedMethodAccessor`, а также много объектов `java.lang.String` (константы классов).

**Как подтвердить:**
- Вкладка **Class Loader Explorer** (MAT) покажет множество загрузчиков классов, которые не были выгружены.
- Ищите `org.apache.catalina.loader.WebappClassLoader` (Tomcat) или `org.springframework.boot.loader.LaunchedURLClassLoader` – если их много, утечка ClassLoader'ов.

**Пример лога (но его нет в дампе, так что ориентируйтесь на признаки):**
- В реальности JVM напишет `java.lang.OutOfMemoryError: Metaspace`
- Но если вы видите дамп с пустой кучей, но гигантским количеством классов – это Metaspace.

**Как отличить от `Java heap space`:** при `Java heap space` куча почти полная, при `Metaspace` – куча может быть почти пуста, а ошибка всё равно произошла (закончилась память вне кучи).

## 3. `Direct buffer memory` (NIO direct buffers)

**Что вы увидите в дампе:**
- В histogram будут присутствовать объекты `java.nio.DirectByteBuffer` (или `sun.nio.ch.DirectBuffer`).
- Их direct memory (вне кучи) не видна в heap dump, но видно сами объекты-обертки.
- Retained heap у `DirectByteBuffer` обычно маленький (объект обертка), но вы можете заподозрить проблему, если у вас десятки тысяч таких буферов и лог до OOME указывал на direct memory.

**Как подтвердить нехватку direct memory:**
- В дампе нет точной цифры использования direct memory, но вы можете:
   - Посчитать количество `DirectByteBuffer` → каждое ок. 1 МБ direct memory → если 1000 штук, то 1 ГБ direct.
   - Сравнить с `-XX:MaxDirectMemorySize` (если не задан, равен `-Xmx`). Если количество буферов × типичный размер приближается к лимиту – причина, скорее всего, в этом.

**Как отличить от `Java heap space`:**
- При `Java heap space` в куче полно объектов (включая буфера). При `Direct buffer memory` куча может быть относительно свободна, но много объектов `DirectByteBuffer`, а остальной heap пуст.

## 4. `Unable to create new native thread`

**Особенность:** Эта ошибка **вообще не создаёт heap dump!** Она происходит до того, как JVM попытается создать поток. Система не может выделить память под стек потока, и JVM выбрасывает ошибку, не производя дамп.

**Что делать, если вы получили дамп при такой ошибке?**  
Скорее всего, вы настроили `-XX:+HeapDumpOnOutOfMemoryError`, и он сработал на другой причине, а не на этой. Для `Unable to create new native thread` heap dump не создаётся. Поэтому если у вас есть дамп – это НЕ этот тип.

**Как понять без дампа:** посмотреть в логах сообщение `java.lang.OutOfMemoryError: unable to create new native thread`.

## 5. `Requested array size exceeds VM limit`

**Что вы увидите в дампе:**
- Ошибка возникает при попытке создать массив недопустимого размера. Дамп создаётся, но в нём нет такого огромного массива (JVM упала до его создания).
- Однако вы можете найти след в стеке потока (если дамп снят с помощью `jstack` или у вас есть thread dump). В heap dump'е нет стека, но в MAT есть вкладка Threads. Откройте её и найдите красный (или подозрительный) поток. Его стек покажет метод, который пытался создать массив, например:
   ```java
   at java.util.Arrays.copyOf(...)
   at java.lang.AbstractStringBuilder.ensureCapacityInternal(...)
   at java.lang.StringBuilder.append(...)
   ```
- Размер запрашиваемого массива может быть > `Integer.MAX_VALUE - 8`.

**Как понять:** Смотрим потоки в дампе (MAT -> Threads -> ищем поток с исключением). Там может не быть явного `OutOfMemoryError`, но будет неудачная попытка аллокации.

## Пошаговая инструкция: по дампу определить тип OOME

1. **Откройте дамп в Eclipse MAT**.
2. **Сразу посмотрите на вкладку Overview**:
   - **Total Heap Size** – сколько занято в куче.
   - **Max Heap** (можно узнать из аргументов JVM, но MAT может показать не всегда). Оцените заполнение: если >90% – вероятно `Java heap space`. Если <50% – возможно, другая причина.
3. **Сгенерируйте Leak Suspects Report**:
   - Если отчёт говорит «No leak suspects found» или указывает на `ClassLoader` – думайте в сторону Metaspace.
   - Если указывает на огромный массив или коллекцию – `Java heap space`.
4. **Откройте Histogram** и найдите классы:
   - Много `java.lang.Class` (тысячи) и мало других объектов → **Metaspace**.
   - Много `java.nio.DirectByteBuffer` (сотни/тысячи) → подозревайте **Direct buffer memory** (но также проверьте кучу на заполнение).
   - Много `byte[]`, `char[]`, `int[]` и объектов приложения → Java heap space.
5. **Посмотрите Threads** (MAT: Window > Threads):
   - Найдите потоки в состоянии RUNNABLE или с большим количеством аллокаций. Стеки могут показать, что было в момент дампа – например, `ByteBuffer.allocateDirect` или `new byte[Integer.MAX_VALUE]`.
6. **Если дамп небольшой, а OOME был** – часто это Metaspace или Direct buffer, потому что куча не переполнена.

## Пример из жизни

**Ситуация:** Получили дамп размером 50 МБ (при `-Xmx=2g`). Histogram показывает 200 000 объектов `java.lang.Class` и 1500 `WebappClassLoader`. Кучи занято всего 100 МБ.  
**Вывод:** Произошёл `OutOfMemoryError: Metaspace`, потому что куча почти пуста, а память под метаданные классов переполнена.

**Ситуация:** Дамп 3.5 ГБ (при `-Xmx=4g`). Histogram: 12 млн `byte[]`. Leak Suspects указывает на кеш изображений.  
**Вывод:** `Java heap space`.

**Ситуация:** Дамп 200 МБ при `-Xmx=1g`. Histogram: 50 000 `DirectByteBuffer`, остальные объекты занимают 50 МБ. В логах перед падением было много операций с `FileChannel`.  
**Вывод:** `Direct buffer memory`.

## Но самый надёжный способ – всё-таки читать логи JVM

Дамп – это дополнение, а не замена лога. **Перед анализом дампа всегда смотрите на сообщение об ошибке в stdout/stderr или в файле `hs_err_pid.log`**:

```
java.lang.OutOfMemoryError: Java heap space
java.lang.OutOfMemoryError: Metaspace
java.lang.OutOfMemoryError: Direct buffer memory
java.lang.OutOfMemoryError: unable to create new native thread
java.lang.OutOfMemoryError: Requested array size exceeds VM limit
```

Эти строки не в дампе, но они есть в логах. Используйте дамп, чтобы узнать *почему* (какой код, какие объекты), а тип ошибки устанавливайте по логу.

Если логов нет (например, приложение запущено без сохранения вывода), то следуйте описанным выше признакам в дампе – они дадут правильный диагноз в 95% случаев.

**Резюме:**  
- Heap dump не хранит тип ошибки как строку.  
- Но по состоянию кучи и содержимому можно определить, была ли проблема в heap, metaspace или direct memory.  
- Для `unable to create new native thread` дампа не будет – это косвенный признак, что если дамп есть, то это другой тип.  


# Как исправить OOME

Ключевое понимание: `OutOfMemoryError` (OOME) — не один, а целое семейство ошибок. От того, *какой именно* OOME вы получили, зависит способ исправления. Разберем детально.

## Типы OutOfMemoryError и их исправление

### 1. `java.lang.OutOfMemoryError: Java heap space`
Самая частая. Закончилась память в куче для объектов.

**Причины:**
- Утечка памяти (объекты не освобождаются)
- Реальная нехватка памяти под нагрузку
- Неэффективные структуры данных

#### Исправление в зависимости от первопричины:

| Симптом (из анализа дампа) | Диагноз | Исправление |
|-----------------------------|---------|--------------|
| Огромный `ArrayList` / `HashMap` с миллионами записей | Нет ограничения размера коллекции | Добавить эвикшен, `LinkedHashMap.removeEldestEntry`, `Caffeine` кеш с maxSize |
| Множество одинаковых объектов из-за отсутствия `equals`/`hashCode` | Ошибка в модели данных | Переопределить `equals`/`hashCode`, использовать `Set` вместо `List` |
| Статическое поле держит ссылку на большой объект всю жизнь приложения | Глобальный кеш без очистки | Пересмотреть архитектуру: использовать `WeakHashMap`, `SoftReference` или очищать при переходе между состояниями |
| Много временных объектов (стринги, байт-буферы) в цикле | Неэффективный код | Вынести создание объектов за цикл, использовать пулы, `StringBuilder` |
| Картинки / большие блобы загружаются в память целиком | Неподходящий формат хранения | Использовать потоковую обработку, `FileChannel`, `MappedByteBuffer` |
| Session-данные не удаляются | Проблема с жизненным циклом сессии | Настроить таймауты сессий, явно инвалидировать в `logout`, использовать `@SessionScope` с осторожностью |

#### Пример исправления утечки через статическую коллекцию:

**До (плохо):**
```java
public class SessionCache {
    private static Map<String, UserSession> activeSessions = new HashMap<>();
    
    public void addSession(String token, UserSession session) {
        activeSessions.put(token, session); // Бесконечный рост
    }
}
```

**После (хорошо):**
```java
public class SessionCache {
    // Используем Guava или Caffeine
    private static Cache<String, UserSession> activeSessions = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build();
    
    // Или старый добрый LinkedHashMap с автоматическим удалением старых
    private static final int MAX_ENTRIES = 10_000;
    private static Map<String, UserSession> sessions = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, UserSession> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
}
```

#### Если утечки нет, но памяти реально не хватает:

**Увеличьте Heap с умом:**
```bash
# Для JVM до 8u191 (без контейнерной поддержки):
-Xms4g -Xmx4g

# Для современных JVM в контейнере (K8s, Docker):
-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0
```

**Оптимизируйте GC:**
```bash
# Для больших куч (>4GB) - G1GC
-XX:+UseG1GC -XX:MaxGCPauseMillis=200

# Для маленьких (<4GB) - Parallel GC
-XX:+UseParallelGC
```

### 2. `OutOfMemoryError: PermGen space` (Java 7 и ниже)  
### 3. `OutOfMemoryError: Metaspace` (Java 8+)

Закончилась память для метаданных классов (загруженных классов, методов, пулов констант).

**Причины:**
- Неограниченное создание классов (динамические прокси, рефлексия, Groovy-скрипты, JSP при перекомпиляции)
- Утечка ClassLoader'ов (особенно в серверах приложений и OSGi)

#### Исправление:

**А) Увеличить Metaspace (временное решение):**
```bash
-XX:MaxMetaspaceSize=256m       # По умолчанию практически неограничен
-XX:MetaspaceSize=128m          # Начальный размер, при котором вызовется GC
```

**Б) Устранить утечку ClassLoader'ов** (основная причина в Java EE / Spring):

```java
// Проблемный код: загрузчик классов не отпускается
public void reloadPlugin(URL jarUrl) {
    URLClassLoader oldLoader = currentLoader;
    currentLoader = new URLClassLoader(new URL[]{jarUrl});
    // Нет вызова oldLoader.close() и нет обнуления ссылок
}

// Исправление (Java 7+ с try-with-resources)
public void reloadPlugin(URL jarUrl) {
    try (URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl})) {
        // используем loader
    } // loader автоматически закрыт, классы могут быть выгружены
    // Но на самом деле выгрузка класса возможна только если:
    // - Нет ссылок на загруженные классы снаружи
}

// Более надежно: использовать один ClassLoader и пересоздавать контекст
```

**В) Отключите лишнюю генерацию metaclass в фреймворках:**
```yaml
# Spring: не создавайте тысячи прокси @Scope("prototype")
spring.aop.proxy-target-class=false  # используйте JDK динамические прокси вместо CGLIB

# Hibernate: не используйте энтити с динамическими атрибутами без меры
```

### 4. `OutOfMemoryError: Direct buffer memory`

Закончилась память вне кучи (native memory), выделенная через `ByteBuffer.allocateDirect` или NIO.

**Причины:** 
- Не освобождаются прямые буферы (не вызван `cleaner`, старые JDK)
- Слишком много маленьких прямых буферов
- Очень большие буферы (сотни МБ)

#### Исправление:

**А) Увеличить лимит:**
```bash
-XX:MaxDirectMemorySize=512m   # По умолчанию = -Xmx
```

**Б) Явно освобождать буферы:**
```java
// Плохо: полагаемся на GC
ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
// работа
// buffer пропал, но память не освобождена мгновенно

// Хорошо: используем сущности с close()
try (BufferResource resource = new BufferResource(1024 * 1024)) {
    // работа
}
// В close() вызываем чистку через sun.misc.Cleaner (рефлексия) или используйте Netty's PooledByteBufAllocator

// Альтернатива: использовать MemorySegment (Java 14+, Panama)
MemorySegment segment = MemorySegment.allocateNative(1024 * 1024, ResourceScope.newImplicitScope());
// Автоматически очищается через ResourceScope
```

**В) Использовать пулы буферов (Netty, Apache Mina):**
```java
// Netty
PooledByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;
ByteBuf buf = allocator.directBuffer(1024);
// ...
buf.release(); // Явное освобождение
```

### 5. `OutOfMemoryError: Unable to create new native thread`

Нельзя создать новый поток, так как достигнут лимит операционной системы на количество потоков или закончилась адресная память на стек потоков.

**Причины:**
- Создание тысяч потоков (например, пулы без ограничений)
- Очень большой размер стека потока (`-Xss`)
- Ограничения ОС (`ulimit -u`)

#### Исправление:

**А) Уменьшить размер стека потока:**
```bash
-Xss256k   # вместо стандартных 1M (для 64-bit JVM)
```

**Б) Использовать пул потоков с фиксированным размером:**
```java
// Плохо: new Thread() в цикле
for (int i = 0; i < 10000; i++) {
    new Thread(() -> { /* work */ }).start();
}

// Хорошо: пул
ExecutorService executor = Executors.newFixedThreadPool(100);
for (int i = 0; i < 10000; i++) {
    executor.submit(() -> { /* work */ });
}
```

**В) Проверить лимиты ОС:**
```bash
ulimit -u          # максимальное число процессов/потоков user
cat /proc/sys/kernel/threads-max   # системный лимит
cat /proc/sys/kernel/pid_max
```

**Г) Использовать асинхронную модель (Project Loom виртуальные потоки в Java 21+):**
```java
// Java 21+ с виртуальными потоками
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 1_000_000; i++) {
        executor.submit(() -> { /* work */ });
    }
} // Очень мало нативных потоков
```

### 6. `OutOfMemoryError: Requested array size exceeds VM limit`

Попытка создать массив, размер которого больше максимального (чуть меньше 2^31 - 1, около 2.147 млрд элементов).

**Причины:** ошибка в логике, попытка загрузить огромный файл целиком в `byte[]`.

**Исправление:**
```java
// Плохо: читаем весь файл в память
byte[] data = Files.readAllBytes(Paths.get("huge.dat"));

// Хорошо: потоковая обработка
try (InputStream is = Files.newInputStream(Paths.get("huge.dat"))) {
    byte[] buffer = new byte[8192];
    int bytesRead;
    while ((bytesRead = is.read(buffer)) != -1) {
        processChunk(buffer, bytesRead);
    }
}

// Если нужен список больше 2^31 -1, используйте MemorySegment или фреймворк BigArray
```

## Общие стратегии исправления OOME (по шагам)

### Шаг 1. Воспроизведите проблему локально с меньшим хипом
```bash
java -Xmx128m -XX:+HeapDumpOnOutOfMemoryError -jar app.jar
# Или используйте параметр -XX:+UseG1GC -XX:G1HeapRegionSize=1m для быстрейшего OOME
```
Чем меньше куча, тем быстрее проявится ошибка при утечке.

### Шаг 2. Получите heap dump и проанализируйте (как ранее)
Используйте Eclipse MAT, найдите biggest retainers.

### Шаг 3. Примените "золотое правило" исправления утечек: удалите неожиданную ссылку

**Для статических полей:** обнулите или используйте `WeakReference`.
**Для коллекций:** добавьте ограничение размера или удаляйте по LRU.
**Для слушателей:** используйте `WeakHashMap` или отписывайтесь явно.

### Шаг 4. Рефакторинг кода с большими объектами

**Разбивайте на части:**
```java
// До: один гигантский список
List<Transaction> allTransactions = transactionDao.getAll(); // 10M записей

// После: пейджинг
PageRequest page = PageRequest.of(0, 1000);
Page<Transaction> pageResult;
do {
    pageResult = transactionDao.findPage(page);
    process(pageResult.getContent());
    page = pageResult.nextPageable();
} while (pageResult.hasNext());
```

**Используйте примитивные коллекции (для больших данных):**
```java
// Вместо ArrayList<Integer> используйте IntArrayList (Eclipse Collections) или int[]
IntArrayList list = new IntArrayList();
```

**Кэшируйте с умом:**
```java
// Неэффективно: кэш без вытеснения
Map<String, byte[]> cache = new HashMap<>();

// Эффективно: мягкие ссылки
Map<String, SoftReference<byte[]>> cache = new HashMap<>();
```

### Шаг 5. Настройте GC и параметры памяти под ваш паттерн

| Паттерн приложения | Рекомендация |
|--------------------|---------------|
| Много временных объектов | Увеличить young gen `-XX:NewRatio=1 -XX:SurvivorRatio=8` |
| Сервер обработки данных (долгоживущие объекты) | Увеличить old gen `-XX:NewRatio=3` |
| Большая куча (>32GB) | Использовать ZGC или Shenandoah `-XX:+UseZGC` |
| Low-latency трейдинг | `-XX:+UseZGC -XX:ZCollectionInterval=60` |
| Веб-сервер со средним хипом | `-XX:+UseG1GC -XX:MaxGCPauseMillis=100` |

### Шаг 6. Напишите тест на утечку памяти (чтобы не вернулась)

**Используйте MemoryLeakDetector (JUnit):**
```java
@Test
public void testMemoryLeak() {
    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    long before = memoryBean.getHeapMemoryUsage().getUsed();
    
    // Вызываем код много раз
    for (int i = 0; i < 10_000; i++) {
        myClassUnderTest.doWork();
    }
    
    System.gc(); // попросим (не гарантия)
    long after = memoryBean.getHeapMemoryUsage().getUsed();
    
    assertTrue("Memory leak suspected", after - before < 5_000_000); // 5MB запас
}
```

**Лучший подход: библиотека **TempusFugit** или **JMockit** с проверкой ссылок:
```java
import static com.google.common.truth.Truth.assertThat;

@Test
public void noLeak() {
    WeakReference<Object> ref = new WeakReference<>(new LargeObject());
    // после использования объекта
    ref.clear();
    // проверяем, что объект доступен сборщику
    assertThat(ref.get()).isNull();
}
```

### Шаг 7. Внедрите мониторинг и проактивную очистку

```java
@Component
public class MemoryGuardian {
    @Scheduled(fixedDelay = 60000)
    public void checkMemory() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long used = heap.getUsed();
        long max = heap.getMax();
        
        if (used > max * 0.8) {
            log.warn("Heap usage high: {}%", (used * 100 / max));
            // Очищаем кэши, пулы
            cache.clear();
            // Можем предложить JVM собрать мусор
            System.gc(); // только как сигнал
        }
    }
}
```

## Специфические случаи (как исправить)

### Утечка через ThreadLocal в веб-приложении

**Проблема:** Tomcat, Spring Boot при передеплое — старые `ThreadLocal` не очищаются → утечка ClassLoader.

**Решение:**
```java
// Используйте слушатель для очистки
@WebListener
public class ThreadLocalCleanupListener implements ServletRequestListener {
    @Override
    public void requestDestroyed(ServletRequestEvent sre) {
        // Очищаем все кастомные ThreadLocal, которые могли быть установлены
        MyThreadLocalHolder.clean();
    }
}

// Или в конфигурации Spring:
@Bean
public ServletListenerRegistrationBean<ThreadLocalCleanupListener> cleanupListener() {
    return new ServletListenerRegistrationBean<>(new ThreadLocalCleanupListener());
}
```

### Утечка из-за java.util.logging в WebLogic/Tomcat

**Симптом:** Огромное количество `LogRecord`, удерживаемых `Logger` кешем.

**Решение:** Отключить кеш логов:
```java
System.setProperty("java.util.logging.manager", "org.apache.juli.ClassLoaderLogManager");
// или
java.util.logging.LogManager.getLogManager().reset();
```

### Утечка из-за JPA EntityManager без clear()

**Проблема:** Выборка миллионов энтити без открепления (detach).

**Исправление:**
```java
EntityManager em = entityManagerFactory.createEntityManager();
try {
    for (int i = 0; i < 100000; i++) {
        MyEntity entity = em.find(MyEntity.class, i);
        process(entity);
        em.detach(entity); // Важно! Освобождаем из persistence context
        if (i % 1000 == 0) {
            em.clear();    // Принудительно сбросить контекст
        }
    }
} finally { em.close(); }
```

Лучше вообще использовать проекции и `@SqlResultSetMapping`, чтобы не грузить целые энтити.

## Чеклист перед деплоем фикса

- [ ] Причина OOME подтверждена анализом дампа (не предположениями)
- [ ] Исправление воспроизводит сценарий, который раньше падал (уменьшенный Xmx)
- [ ] Новый код не содержит схожих рисков (ревью)
- [ ] Добавлены интеграционные тесты с `-Xmx128m`
- [ ] Настроен мониторинг на `MXBean` и оповещение при 70% heap
- [ ] Обновлена документация по лимитам памяти в зависимости от нагрузки
- [ ] В контейнерах установлен `memory.request` и `memory.limit` с правильным `MaxRAMPercentage`

## Если ничего не помогает (экстренные меры)

**Перезапуск процесса:** Добавьте в систему оркестрации автоматический перезапуск при OOME:
```bash
# systemd сервис
[Service]
Restart=on-failure
SuccessExitStatus=143

# или Docker + restart policy
docker run --restart=on-failure:5 myapp
```

**Автоматический рестарт внутри кода (НЕ РЕКОМЕНДУЕТСЯ, но для legacy):**
```java
Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
    if (throwable instanceof OutOfMemoryError) {
        System.exit(1); // пусть внешний скрипт перезапустит
    }
});
```

Никогда, повторяю, **никогда** не пытайтесь перехватить OOME в приложении и продолжать работу. Состояние повреждено (потоки могут быть в неконсистентном состоянии, очистка не гарантирована). Единственное правильное действие — завершиться, чтобы оркестратор перезапустил процесс.


# Мониторинг JVM в микросервисах

**Отслеживание состояния памяти в Java-микросервисах** — критически важная часть observability, особенно в Kubernetes/Docker, где память ограничена и OOMKill происходит быстро.

Вот современный (2026 год) подход к мониторингу памяти в микросервисах.

### 1. Что именно нужно мониторить (ключевые метрики памяти)

| Уровень              | Метрики                                      | Почему важно                              |
|----------------------|----------------------------------------------|-------------------------------------------|
| **JVM (Heap)**      | Heap Used / Committed / Max<br>Eden, Survivor, Old Gen | Основная причина OOM (Java heap space)   |
| **JVM (Non-Heap)**  | Metaspace, Compressed Class Space, Code Cache | Утечки классов, много динамических прокси |
| **GC**              | GC count, GC time/pause, Full GC frequency   | "GC overhead limit exceeded", стопы       |
| **Native / Direct** | Direct Buffer Memory, Native Memory (NMT)    | ByteBuffer.allocateDirect(), JNI          |
| **Процесс / Контейнер** | RSS (Resident Set Size), Working Set, Container Memory | Реальное потребление в K8s                |
| **Приложение**      | Кэши, Connection Pools, Thread Pools         | Логика утечек                             |

### 2. Рекомендуемая архитектура мониторинга (самая популярная)

**Prometheus + Grafana + Micrometer** — золотой стандарт для Java-микросервисов (особенно Spring Boot).

#### Для Spring Boot (рекомендуется)

**Зависимости** (pom.xml или build.gradle):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<!-- Опционально для дополнительных JVM метрик -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-jvm-extras</artifactId>
</dependency>
```

**application.yml**:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

Эндпоинт для Prometheus: `http://your-service:8080/actuator/prometheus`

#### Для любых Java-приложений (не только Spring)

Используйте **JMX Prometheus Exporter** (javaagent) или OpenTelemetry Java Agent.

### 3. Настройка в Kubernetes

1. **ServiceMonitor** (Prometheus Operator) — автоматически собирает метрики со всех микросервисов.
2. Добавьте labels к подам:
   ```yaml
   annotations:
     prometheus.io/scrape: "true"
     prometheus.io/port: "8080"
     prometheus.io/path: "/actuator/prometheus"
   ```
3. В ресурсах подов обязательно указывайте:
   ```yaml
   resources:
     requests:
       memory: "1Gi"
     limits:
       memory: "2Gi"   # ← очень важно!
   ```

**Важно для JVM в контейнерах**:
- Используйте флаги:
  ```bash
  -XX:MaxRAMPercentage=75
  -XX:InitialRAMPercentage=50
  -XX:+UseContainerSupport   # Java 10+
  -XX:+HeapDumpOnOutOfMemoryError
  ```
- Не задавайте жёстко `-Xmx`, если используете процентные настройки.

4. Лучшие Grafana дашборды (импортируйте по ID)

- **JVM (Micrometer)** — ID: **4701** (очень хороший)
- **JVM Dashboard** — ID: **8563**
- **Spring Boot 3.x Statistics** 
- **Kubernetes Pod & Node** (для сравнения container memory vs JVM heap)

5. Полезные PromQL-запросы для памяти

```promql
# Процент использования Heap
sum(jvm_memory_used_bytes{area="heap"}) / sum(jvm_memory_max_bytes{area="heap"}) * 100

# Heap usage по сервисам
jvm_memory_used_bytes{area="heap"} / 1024 / 1024

# GC паузы (время в секундах)
rate(jvm_gc_pause_seconds_sum[5m]) / rate(jvm_gc_pause_seconds_count[5m])

# Metaspace
jvm_memory_used_bytes{area="nonheap", id="Metaspace"}

# Предупреждение: heap > 85% больше 5 минут
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
```

6. Дополнительные уровни мониторинга

- **Native Memory Tracking** (для сложных случаев):
  ```bash
  -XX:NativeMemoryTracking=detail
  jcmd <pid> VM.native_memory summary
  ```

- **Java Flight Recorder (JFR)** — для глубокого профилирования памяти под нагрузкой:
  ```bash
  -XX:StartFlightRecording=duration=60s,filename=recording.jfr
  ```

- **Alerting** (Alertmanager):
  - Heap usage > 80% — warning
  - Heap usage > 90% — critical
  - GC pause > 1 секунда несколько раз
  - Container memory usage接近 limit

- **Distributed Tracing** (OpenTelemetry + Jaeger/Tempo) — помогает понять, какой запрос вызывает всплеск памяти.

7. Практические советы

- Следите за **трендом** использования памяти, а не только текущим значением.
- Сравнивайте **JVM Heap** с **Container RSS** — большая разница часто указывает на native memory leak.
- Регулярно снимайте heap dumps при росте памяти и анализируйте в **Eclipse MAT**.
- В продакшене включайте **-XX:+HeapDumpOnOutOfMemoryError** + указывайте путь в volume.
- Используйте **Caffeine** вместо Guava Cache — он лучше управляет памятью.
