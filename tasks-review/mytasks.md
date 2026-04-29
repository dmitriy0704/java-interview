# Задача на ревью по SQL

Сценарий: Списание товара со склада при покупке

Представь, что ты ревьюишь код коллеги. Он написал процедуру, которая
вызывается, когда пользователь нажимает «Купить».

```text

// Почитать:

// Запрос который покажет проблемы повторяющегося чтения
// PgBouncer, Connection Pool

// Описать 5 часто встречающиеся проблемы в Java и их решение:
// ООМ, проблемы исчерпывающихся потоков,   

-- Код на ревью
BEGIN TRANSACTION;

-- 1. Проверяем остаток
SELECT quantity
FROM inventory
WHERE product_id = 123;

-- 2. (Логика приложения: если quantity > 0, идем дальше)

-- 3. Обновляем остаток
UPDATE inventory
SET quantity = quantity - 1
WHERE product_id = 123;

/*
-- Для пессимистической блокировки: Выбираем товар и "бронируем" строку для себя
SELECT id, quantity, price 
FROM inventory 
WHERE product_id = 123 
FOR UPDATE;
*/


-- 4. Записываем лог операции
INSERT INTO order_logs (product_id, message)
VALUES (123, 'Product purchased');

COMMIT;

```

### Какие здесь проблемы? (Твое объяснение)

1. Уровни изоляции и «Потерянное обновление» (Lost Update)

    - Проблема: По умолчанию во многих БД (PostgreSQL, MS SQL) используется
      уровень Read Committed. Если два пользователя одновременно запустят этот
      код, они оба могут прочитать quantity = 1 на шаге 1. Оба пройдут проверку,
      и оба сделают UPDATE. В итоге остаток станет -1, хотя товара была одна
      штука.
    - Решение:
      Использовать Pessimistic Locking (блокировку при чтении): SELECT ... FOR
      UPDATE. Это заставит вторую транзакцию ждать, пока первая не завершится.
      Либо использовать Optimistic Locking:
      `UPDATE inventory SET quantity = quantity - 1 
       WHERE product_id = 123 AND quantity > 0.`

2. Проблема с индексами (Deadlocks)

    - Проблема: Если в таблице inventory нет индекса по product_id, СУБД может
      применить Lock Escalation (блокировку всей таблицы вместо одной строки).
      Если параллельно идет другой процесс (например, пересчет всех остатков),
      это легко приведет к Deadlock (взаимной блокировке).
    - Решение: Убедиться, что product_id — это Primary Key или имеет UNIQUE
      INDEX. Это гарантирует Row-level lock (блокировку только одной строки).

3. Избыточные блокировки и порядок действий

    - Проблема: Шаг 4 (запись в лог) находится внутри транзакции. Если запись в
      лог тормозит или таблица логов заблокирована, мы удерживаем блокировку на
      строку товара дольше, чем нужно.
    - Решение: Правило «Short and Sweet». Транзакции должны быть максимально
      короткими. Если лог не критичен для консистентности остатка, его можно
      вынести за пределы основной транзакции или делать в самом конце.

```text
-- Исправленный вариант
BEGIN TRANSACTION;

-- Блокируем конкретную строку сразу при чтении
-- Проверка наличия и уменьшение одним запросом (атомарно)
UPDATE inventory
SET quantity = quantity - 1
WHERE product_id = 123
  AND quantity > 0;

-- Проверяем, затронута ли хоть одна строка (ROW_COUNT)
-- Если 0 — значит товара нет, делаем ROLLBACK

INSERT INTO order_logs (product_id, message)
VALUES (123, 'Product purchased');

COMMIT;

```

Резюме для собеседования:

1. Индексы: Нужны, чтобы блокировки были точечными (Row-level), а не на всю
   таблицу.
2. Уровни изоляции: Read Committed не защищает от race condition без
   дополнительных хинтов (FOR UPDATE).
3. Блокировки: Важен порядок обновлений. Если два процесса обновляют таблицы А и
   Б в разном порядке — это 100% Deadlock.

----

# 5 часто встречающихся проблем в Java и их решение

Вот **5 часто встречающихся проблем в Java**, с учётом твоего уточнения. Я
включил **OutOfMemoryError** и **проблему исчерпания потоков** (thread
exhaustion) как обязательные пункты, а остальные три — самые распространённые в
реальной разработке.

## 1. OutOfMemoryError: Java heap space (самая частая форма OOM)

Вот подробный разбор **OutOfMemoryError** (OOM) в Java — одной из самых
неприятных и частых критических ошибок.

### Что такое OutOfMemoryError?

`OutOfMemoryError` — это ошибка (Error, а не Exception), которую выбрасывает
JVM, когда не может выделить память под новый объект. В отличие от обычных
исключений, её сложно «поймать» и продолжить работу — приложение обычно падает.

Существует **несколько типов** OOM, и каждый указывает на разную проблему:

### 1. Java heap space (самый частый)

**Сообщение:** `java.lang.OutOfMemoryError: Java heap space`

**Причины:**

- **Memory Leak** — объекты продолжают жить в памяти, хотя уже не нужны (самая
  частая реальная причина).
- Слишком маленький размер кучи (`-Xmx`).
- Загрузка больших объёмов данных целиком (результаты SQL-запросов на сотни
  тысяч строк, большие файлы, JSON).
- Огромные коллекции (`ArrayList`, `HashMap`) без ограничения размера.
- Кэши без политики вытеснения (eviction).
- В Spring Boot: обработка больших сущностей с множеством связей (lazy loading +
  eager fetch), сессии с большим количеством данных.

**Как диагностировать:**

- Добавь JVM-флаги:
  ```bash
  -XX:+HeapDumpOnOutOfMemoryError 
  -XX:HeapDumpPath=/path/to/heapdump.hprof
  ```
  Это создаст дамп памяти при ошибке.
- Инструменты анализа:
    - **Eclipse MAT** (Memory Analyzer Tool) — лучший для поиска утечек.
    - VisualVM, JProfiler, YourKit, Async Profiler.
    - `jcmd <pid> GC.heap_info`, `jmap`, `jstat`.
- Смотри, какие классы занимают больше всего памяти (обычно это `byte[]`,
  `String`, твои Entity, HashMap.Entry и т.д.).

**Решения:**

- Увеличить `-Xmx` (временно) — например, `-Xmx4g` или `-Xmx8g`.
- **Исправить утечку** — найти объекты, которые держат ссылки (static поля,
  listeners, ThreadLocal без remove(), кэши).
- Обрабатывать данные потоково: pagination, streaming результатов из БД,
  `Stream` вместо `collect(toList())`.
- Использовать правильные кэши: **Caffeine** с `maximumSize` и
  `expireAfterAccess`.
- В Spring: `@Transactional(readOnly = true)` + Streamable, избегать загрузки
  всего в память.

### 2. GC Overhead limit exceeded

**Сообщение:** `java.lang.OutOfMemoryError: GC overhead limit exceeded`

**Причина:** JVM тратит больше 98% времени на Garbage Collection, а полезной
работы почти нет (GC thrashing). Куча заполнена, но GC почти ничего не может
очистить.

**Решения:**

- Найти и исправить memory leak (то же, что и для heap space).
- Попробовать другой GC: G1 (по умолчанию), ZGC или Shenandoah (для низких
  пауз).
- Отключить лимит (не рекомендуется надолго): `-XX:-UseGCOverheadLimit`.

### Полезные JVM-флаги для продакшена

```bash
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/logs/heapdump.hprof
-XX:+ExitOnOutOfMemoryError          # автоматически убивать процесс
-XX:MaxMetaspaceSize=512m
-Xmx4g -Xms2g
-XX:+UseG1GC
-XX:+PrintGCDetails -Xlog:gc*        # для анализа GC
```

### Лучшие практики предотвращения OOM

1. **Не загружай всё в память** — используй streaming/pagination.
2. **Закрывай ресурсы** — `try-with-resources`.
3. **Мониторь** в продакшене (Prometheus + Grafana, Micrometer, New Relic,
   Datadog).
4. **Пиши тесты** под нагрузку (Gatling, JMeter).
5. Используй современные инструменты: Caffeine вместо HashMap как кэш, Virtual
   Threads.
6. Регулярно анализируй heap dumps при росте памяти.

### Примеры кода

### Пример 1. Утечка памяти

Вот **самый простой и классический** пример утечки памяти в Java:

Плохой код (с утечкой памяти)

```java
public class BadCacheExample {

    // Статическая коллекция — живёт всё время работы программы
    private static final Map<Integer, byte[]> cache = new HashMap<>();

    public byte[] getData(int id) {
        // Если данных нет — загружаем и кладём в кэш
        return cache.computeIfAbsent(id, key -> loadHeavyData(key));
    }

    // Симулируем загрузку "тяжёлых" данных (например, большой отчёт или файл)
    private byte[] loadHeavyData(int id) {
        // Создаём массив в 10 МБ
        byte[] data = new byte[10 * 1024 * 1024];
        Arrays.fill(data, (byte) 1);
        return data;
    }
}
```

**Почему здесь утечка памяти?**

- `cache` — это `static final` поле. Он никогда не очищается.
- Каждый вызов `getData()` с новым `id` добавляет в карту **10 МБ** данных.
- Через 300–400 разных вызовов память кучи будет исчерпана →
  `OutOfMemoryError: Java heap space`.

**Исправленный код (без утечки)**

```java
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

public class GoodCacheExample {

    // Правильный кэш с ограничением размера и временем жизни
    private final Cache<Integer, byte[]> cache = Caffeine.newBuilder()
            .maximumSize(100)                    // максимум 100 записей
            .expireAfterAccess(10, TimeUnit.MINUTES)  // удалять через 10 мин без доступа
            .build();

    public byte[] getData(int id) {
        return cache.get(id, this::loadHeavyData);
    }

    private byte[] loadHeavyData(int id) {
        byte[] data = new byte[10 * 1024 * 1024];
        Arrays.fill(data, (byte) 1);
        return data;
    }
}
```

**Ключевые исправления:**

1. Убрали `static` — кэш теперь принадлежит экземпляру класса.
2. Заменили `HashMap` на **Caffeine Cache**.
3. Добавили `maximumSize()` — жёсткое ограничение количества элементов.
4. Добавили `expireAfterAccess()` — автоматическое удаление старых данных.

**Ещё более простой вариант** (без Caffeine):

```java
// Простое исправление без внешних библиотек
private final Map<Integer, byte[]> cache =
        Collections.synchronizedMap(new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> eldest) {
                return size() > 100;   // оставляем максимум 100 элементов
            }
        });
```

### Пример 2. ThreadLocal

**Плохой код (с утечкой памяти)**

```java
public class UserContext {

    // ThreadLocal, который никогда не очищается
    private static final ThreadLocal<String> currentUser = new ThreadLocal<>();

    public static void setUser(String username) {
        currentUser.set(username);
    }

    public static String getUser() {
        return currentUser.get();
    }

    // Метод очистки существует, но его НЕ вызывают!
    public static void clear() {
        currentUser.remove();
    }
}
```

### Почему здесь утечка памяти?

- Веб-сервер (Tomcat, Jetty и т.д.) использует **пул потоков**.
- После обработки запроса поток возвращается обратно в пул.
- Если `ThreadLocal` не был очищен, то ссылка на объект (в данном случае
  `String`) остаётся "привязанной" к потоку.
- Со временем **все потоки в пуле** будут держать ненужные объекты → память не
  освобождается → растёт потребление heap → `OutOfMemoryError`.

### Исправленный код (правильный вариант)

```java
public class UserContext {

    private static final ThreadLocal<String> currentUser = new ThreadLocal<>();

    public static void setUser(String username) {
        currentUser.set(username);
    }

    public static String getUser() {
        return currentUser.get();
    }

    public static void clear() {
        currentUser.remove();        // важно!
    }
}
```

**Главное правило:**
Если используешь `ThreadLocal` — **всегда** очищай его в блоке `finally`.




---

## 2. Исчерпание потоков (Thread Exhaustion / Too many threads)

Отлично! Давай подробно и по делу разберём тему **«Исчерпание потоков»** (Thread
Exhaustion) — одну из самых частых причин падения Java-приложений под нагрузкой.

### Что такое "исчерпание потоков"?

**Thread Exhaustion** — это ситуация, когда приложение не может создать новый
поток (thread), потому что достигнут лимит.

Самое частое сообщение об ошибке:

```java
java.lang.OutOfMemoryError:
unable to
create new native thread
```

Хотя ошибка называется `OutOfMemoryError`, на самом деле проблема **не в
Java-куче**, а в **операционной системе**.

### Почему это происходит?

1. **Каждый платформенный поток (platform thread)** стоит дорого:
    - По умолчанию ~1 МБ стека (можно уменьшить до 512 КБ или 256 КБ).
    - Плюс структуры данных в ядре ОС.
    - Лимит на количество потоков на пользователя в Linux обычно от 1024 до
      4096 (зависит от `ulimit -u`).

2. **Классическая модель thread-per-request**:
    - Каждый HTTP-запрос занимает один поток из пула Tomcat/Jetty.
    - При 500–1000+ одновременных запросов пул потоков быстро исчерпывается.

### Типичные причины исчерпания потоков

| Причина                                  | Когда чаще всего происходит              | Опасность    |
|------------------------------------------|------------------------------------------|--------------|
| Слишком маленький пул потоков            | `server.tomcat.threads.max=200`          | Высокая      |
| Блокирующие операции (JDBC, REST, файлы) | Долгие запросы к БД или внешним сервисам | Высокая      |
| Thread Leak (потоки не завершаются)      | Забытые `new Thread()` или пулы          | Критично     |
| Высокая нагрузка + thread-per-request    | Продакшен без Virtual Threads            | Очень частая |
| Вызов внешних блокирующих библиотек      | Legacy код, старые JDBC драйверы         | Высокая      |

### Пример кода, который приводит к исчерпанию потоков

```java
// Плохой подход (классика)
@RestController
public class OrderController {

    @GetMapping("/orders")
    public List<Order> getAllOrders() {
        return orderService.getAllOrders();   // блокирующий вызов JDBC
    }
}
```

Если БД начнёт тормозить, все потоки Tomcat будут висеть в ожидании → новые
запросы не смогут обработаться → ошибка `unable to create new native thread`.

### Современные решения (2025–2026)

#### 1. Лучшее решение — Virtual Threads (рекомендуется)

С Java 21+ это **основной способ** борьбы с исчерпанием потоков.

**Самый простой способ в Spring Boot 3.2+:**

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

Или программно:

```java

@Configuration
public class ThreadConfig {
    @Bean
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

**Преимущества Virtual Threads:**

- Очень лёгкие (несколько килобайт вместо 1 МБ).
- Можно создавать десятки и сотни тысяч потоков.
- Код остаётся императивным (не нужно писать реактивный код).
- Отлично работают с блокирующими вызовами (JDBC, RestTemplate и т.д.).

#### 2. Увеличение лимитов ОС (временное решение)

```bash
# Linux
ulimit -u 65535          # увеличить максимальное количество процессов/потоков
```

#### 3. Настройка пула платформенных потоков (если не используешь Virtual Threads)

```yaml
server:
  tomcat:
    threads:
      max: 400
      min-spare: 50
```

### Рекомендации по архитектуре

- **Java 21+** → Используй **Virtual Threads** + обычный блокирующий код.
- Если нагрузка очень высокая и нужна максимальная эффективность → рассматривай
  **WebFlux** (реактивный стек).
- Избегай создания потоков вручную через `new Thread()` или `ThreadPoolExecutor`
  с большим количеством потоков.
- Всегда используй `try-with-resources` или `StructuredTaskScope` при работе с
  задачами.

### Как диагностировать исчерпание потоков в продакшене

Исчерпание потоков обычно проявляется как:

- java.lang.OutOfMemoryError: unable to create new native thread
- Резкий рост времени отклика (latency)
- Приложение «зависает» или перестаёт отвечать на новые запросы
- CPU часто низкий или средний, память в норме
- Много ошибок 502/503 или таймаутов у клиентов


1. Быстрая проверка в момент проблемы:

   Выполняйте эти команды на сервере:

    ```shell
    # 1. Сколько потоков у процесса Java сейчас?
    ps -eLf | grep java | wc -l
    
    # Или более точно:
    jcmd <PID> Thread.print | wc -l
    
    # 2. Лимиты ОС (очень важно!)
    ulimit -a | grep processes
    # или
    cat /proc/<PID>/limits | grep "Max processes"
    
    # 3. Количество потоков в системе
    ps -eLf | wc -l
    ```

   Если количество потоков у Java-процесса приближается к лимиту Max user
   processes (обычно 1024–4096), это явный признак проблемы.

2. Основные инструменты диагностики

   A. Thread Dump (самый важный артефакт)

   Снимите несколько thread dump’ов с интервалом 5–10 секунд:

    ```shell
    # Рекомендуемый способ
    jcmd <PID> Thread.print > thread_dump_$(date +%s).txt
    
    # Альтернативы
    jstack -l <PID> > thread_dump.txt
    kill -3 <PID>  # dump уходит в stdout приложения (обычно в catalina.out или nohup)
    ```

   На что смотреть в thread dump:

    - Общее количество потоков ("Total threads:" или посчитайте).
        - Состояние пула Tomcat (или вашего Executor):
            - Много потоков в состоянии WAITING или TIMED_WAITING на сокетах,
              JDBC, RestTemplate, Feign и т.д.
            - Потоки с названием http-nio-8080-exec-* (Tomcat worker threads).
        - Thread Leak: растущее количество потоков с похожими именами (например,
          много pool-*-thread-*).
        - Deadlocks (в конце дампа JVM обычно пишет, если они есть).

   B. Мониторинг метрик (лучше всего настроить заранее)

   В Spring Boot с Actuator:

    ```yaml
    management:
      endpoints:
        web:
          exposure:
            include: "*"
      metrics:
        enable:
          tomcat: true
    ```
   Полезные метрики:
    - tomcat.threads.busy
    - tomcat.threads.current
    - tomcat.threads.config.max
    - http.server.requests (latency + active requests)

   Если tomcat.threads.busy ≈ tomcat.threads.config.max долгое время — это
   классическое thread pool exhaustion.

   C. JVM и OS инструменты

    - jcmd (современный инструмент):Bashjcmd <PID> Thread.print -l

    ```shell
    jcmd <PID> Thread.print -l
    jcmd <PID> VM.native_memory summary   # если нужно посмотреть native memory
    ```

- top / htop:
    - Нажмите H в htop → показывает потоки.
    - Ищите процесс с очень большим количеством потоков (NLWP).

- VisualVM или JDK Mission Control (JMC) — если есть возможность подключиться (
  не всегда возможно в проде).


3. Специфика диагностики при Virtual Threads

   Если вы уже используете **Virtual Threads** (
   `spring.threads.virtual.enabled=true`):

    - Проблема исчерпания платформенных потоков почти исчезает.
        - Но может возникнуть **pinning** (приклеивание виртуального потока к
          carrier thread).

   **Как диагностировать pinning:**

    ```bash
    # В Java 21–23
    java -Djdk.tracePinnedThreads=full -jar app.jar
    
    # С Java 24+ лучше использовать JFR
    java -XX:StartFlightRecording=duration=60s,filename=pinning.jfr -jar app.jar
    ```

   Ищите событие `VirtualThreadPinned` в Java Flight Recorder.

4. Пошаговый план диагностики в продакшене

1. Увидели симптомы (медленные ответы / ошибки) → сразу снимите **thread dump**.
2. Проверьте `ulimit -u` и количество потоков процесса.
3. Посмотрите метрики Tomcat / Executor (Actuator + Prometheus/Grafana).
4. Снимите 3–5 thread dump’ов подряд.
5. Проанализируйте:
    - Растёт ли количество потоков со временем? → Thread Leak.
    - Все worker threads заблокированы на внешних вызовах? → Блокирующий код +
      недостаточный размер пула.
6. Если используете Virtual Threads — проверьте pinning через JFR.

### Полезные рекомендации

- Добавьте в JVM-флаги:
  ```bash
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:OnOutOfMemoryError="jstack -l %p > /var/log/thread_dump_%p.log"
  ```

- Ведите мониторинг количества потоков (`jvm.threads.live` в Micrometer).

---

## 3. Memory Leaks + неправильное использование коллекций и кэшей

Отлично! Вот подробный, практический и актуальный разбор темы:

### Memory Leaks в Java + неправильное использование коллекций и кэшей

**Memory Leak (утечка памяти)** — это ситуация, когда объекты, которые уже не
нужны приложению, продолжают удерживаться в памяти, потому что на них остаются
ссылки (references). Со временем это приводит к росту потребления heap и в итоге
к `OutOfMemoryError: Java heap space`.

Самая частая причина утечек в Java — **неправильная работа с коллекциями и
кэшами**.

---

### 1. Самые частые виды утечек через коллекции

#### Пример №1 — Статический кэш без ограничений (самая популярная утечка)

```java

@Service
public class UserService {

    // КЛАССИЧЕСКАЯ УТЕЧКА ПАМЯТИ
    private static final Map<Long, User> userCache = new HashMap<>();

    public User getUser(Long id) {
        return userCache.computeIfAbsent(id, this::loadFromDatabase);
    }

    private User loadFromDatabase(Long id) { ...}
}
```

**Почему утекает?**

- `static` коллекция живёт столько же, сколько живет ClassLoader (т.е. пока
  работает приложение).
- Нет механизма удаления старых записей.
- Со временем в карте может накопиться сотни тысяч объектов.

---

#### Пример №2 — Кэш в обычном HashMap внутри обычного бина

```java

@Service
public class ReportService {

    private final Map<String, ReportData> cache = new HashMap<>(); // тоже плохо

    public ReportData generateReport(String key) {
        return cache.computeIfAbsent(key, this::generateHeavyReport);
    }
}
```

Даже без `static` это может привести к утечке, если сервис является singleton (
по умолчанию в Spring).

---

#### Пример №3 — Список, который постоянно растёт

```java
private final List<Event> allEvents = new ArrayList<>();

public void onEvent(Event event) {
    allEvents.add(event);        // никогда не очищается
}
```

---

### 2. Почему коллекции становятся причиной утечек?

- `HashMap`, `ArrayList`, `HashSet` **не имеют** автоматического механизма
  очистки.
- Они продолжают держать сильные ссылки (`Strong Reference`) на объекты.
- Garbage Collector не может удалить объект, пока на него есть хотя бы одна
  сильная ссылка.

### 3. Правильные способы работы с кэшами

#### Лучшее решение в 2026 году — **Caffeine Cache**

```java

@Service
public class UserService {

    private final Cache<Long, User> userCache = Caffeine.newBuilder()
            .maximumSize(10_000)                    // максимум записей
            .expireAfterAccess(30, TimeUnit.MINUTES) // удалять после 30 мин без обращения
            .expireAfterWrite(2, TimeUnit.HOURS)     // или через 2 часа после создания
            .recordStats()                           // важно для мониторинга
            .build();

    public User getUser(Long id) {
        return userCache.get(id, this::loadFromDatabase);
    }
}
```

**Преимущества Caffeine:**

- Ограничение по размеру (`maximumSize`)
- Автоматическое истечение по времени (`expireAfter...`)
- Поддержка веса объектов (`weigher`)
- Статистика использования
- Thread-safe

---

### 4. Другие опасные паттерны

| Паттерн                               | Почему опасно                | Как исправить                  |
|---------------------------------------|------------------------------|--------------------------------|
| `static Map<..., ...>`                | Никогда не очищается         | Caffeine или Guava Cache       |
| `static List` / `static Set`          | Постоянный рост              | Убрать `static` + лимит        |
| Хранение данных в `HttpSession`       | Сессия может жить долго      | Хранить только ID или ничего   |
| `WeakHashMap` без понимания           | Ключи удаляются слишком рано | Использовать осторожно         |
| `ThreadLocal` без `remove()`          | Привязывается к потокам      | Всегда очищать в `finally`     |
| Коллекции в singleton-бине без лимита | Растёт бесконтрольно         | Caffeine / bounded collections |

---

### 5. Полезные техники предотвращения утечек

1. **Никогда не используйте `HashMap` как кэш** в долгоживущих объектах.
2. **Всегда ставьте лимит** на размер коллекции.
3. Предпочитайте `expireAfterAccess` + `maximumSize`.
4. Используйте `try-with-resources` для ресурсов.
5. Очищайте `ThreadLocal` в `finally`.
6. Будьте осторожны с **listeners** и **observers** — они должны отписываться.
7. Регулярно анализируйте heap dump’ы с помощью **Eclipse MAT**.

### 6. Как находить такие утечки?

- **Eclipse Memory Analyzer Tool (MAT)** — лучший инструмент.
- Ищите accumulation points (точки накопления).
- Смотрите, какие классы занимают больше всего памяти (`byte[]`, `String`, ваши
  Entity, `HashMap$Node`).
- Анализируйте **Retained Heap** — сколько памяти будет освобождено, если
  удалить этот объект.

---

## 4. Race Conditions (Состояние гонки)

Отличный вопрос!

**Race Condition (состояние гонки)** и **проблемы с памятью** в Java — это две
связанные, но **разные** вещи. Давай разберём это чётко и по делу.

1. Что такое Race Condition?

**Race Condition** — это ситуация, когда **результат работы программы зависит от
того, в каком порядке выполняются потоки**, и этот порядок непредсказуем.

Простыми словами: несколько потоков одновременно работают с одной и той же
переменной/объектом, и хотя бы один из них **изменяет** состояние.

2. Как Race Condition связана с проблемами памяти?

Race Condition **сама по себе не вызывает OutOfMemoryError**, но она очень часто
**приводит** к утечкам памяти или другим тяжёлым проблемам с памятью косвенным
образом.

Вот как это происходит:

3. Самые частые примеры Race Condition, которые приводят к проблемам с памятью

Пример №1 — Неправильный "ленивый" кеш (очень частый!)

```java
public class UserService {

    private static Map<Long, User> cache;   // не volatile + не synchronized

    public User getUser(Long id) {
        if (cache == null) {                    // Race Condition!
            cache = new HashMap<>();
        }

        return cache.computeIfAbsent(id, this::loadUser);
    }
}
```

**Что происходит при Race Condition:**

- Несколько потоков одновременно видят `cache == null`
- Каждый создаёт свой `HashMap`
- В итоге вместо одного кеша появляется несколько, и старые карты продолжают
  жить в памяти → **утечка памяти**.

Пример №2 — Добавление в коллекцию без синхронизации

```java
public class EventService {

    private final List<Event> events = new ArrayList<>();   // НЕ thread-safe

    public void addEvent(Event event) {
        events.add(event);        // Race Condition!
    }

    public List<Event> getAllEvents() {
        return events;            // может вернуть повреждённое состояние
    }
}
```

**Последствия:**

- `ConcurrentModificationException`
- Повреждение внутренней структуры `ArrayList`
- В худшем случае — утечка памяти (объекты остаются в "битой" коллекции и не
  удаляются)

Пример №3 — Double-Checked Locking (старый антипаттерн)

```java
public class Singleton {
    private static Singleton instance;

    public static Singleton getInstance() {
        if (instance == null) {                    // Race Condition
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();    // опасно до Java 5
                }
            }
        }
        return instance;
    }
}
```

До Java 5 это могло приводить к тому, что другие потоки видели **частично
созданный** объект (из-за проблем с видимостью памяти).

4. Как Race Condition приводит к Memory Leak?

Вот основные механизмы:

1. **Создание лишних объектов**
    - Несколько потоков создают "свои" кеши, пулы, буферы и т.д.

2. **Потеря ссылок на нужные объекты**
    - Из-за гонки объект может быть добавлен в коллекцию, но ссылка потеряна.

3. **Неправильное использование Weak/SoftReference**
    - Гонки при работе со слабыми ссылками часто приводят к неожиданному
      поведению.

4. **ThreadLocal + Race Condition**
    - Неправильная инициализация ThreadLocal в многопоточной среде.

5. **Некорректная работа с кэшами**
    - `computeIfAbsent` без должной синхронизации может привести к многократному
      вычислению тяжёлых операций и созданию дублирующихся объектов в памяти.


5. Как защищаться от Race Condition?

| Проблема           | Правильное решение                               |
|--------------------|--------------------------------------------------|
| Простая переменная | `volatile` или `Atomic*` классы                  |
| Коллекция          | `ConcurrentHashMap`, `CopyOnWriteArrayList`      |
| Сложная логика     | `synchronized`, `ReentrantLock`                  |
| Кеш                | Caffeine / Guava Cache (они thread-safe)         |
| Singleton          | Enum или Bill Pugh Singleton (с `static holder`) |
| Инициализация      | `static` блок или `final` поля                   |

**Современный рекомендуемый подход** (Java 21+):

- Использовать **immutable** объекты + `ConcurrentHashMap`
- Предпочитать высокоуровневые конструкции (`Caffeine`, `CompletableFuture`,
  `StructuredTaskScope`)
- Избегать низкоуровневого `synchronized`, где возможно

### Короткое резюме:

- **Race Condition** ≠ Memory Leak напрямую.
- Но Race Condition **очень часто становится причиной** создания лишних объектов
  и утечек памяти.
- Самые опасные места — **ленивая инициализация** кешей, singleton’ов и общих
  коллекций.

---

## 5. Deadlocks (Взаимная блокировка)

Вот подробный и честный рассказ про **Deadlocks** (взаимные блокировки) в
контексте проблем с памятью в Java.

### Что такое Deadlock?

**Deadlock** — это ситуация, когда два или более потоков **навсегда**
заблокированы, ожидая друг друга. Каждый поток держит блокировку (lock), которую
ждёт другой поток, и при этом ждёт блокировку, которую держит первый.

Классические условия возникновения deadlock (условия Коффмана):

1. **Mutual Exclusion** — ресурс может быть занят только одним потоком.
2. **Hold and Wait** — поток держит ресурс и ждёт другой.
3. **No Preemption** — ресурс нельзя принудительно забрать.
4. **Circular Wait** — существует циклическое ожидание.

### Пример классического Deadlock

```java
public class DeadlockExample {

    private final Object lockA = new Object();
    private final Object lockB = new Object();

    public void method1() {
        synchronized (lockA) {
            synchronized (lockB) {   // может ждать lockB
                // критическая секция
            }
        }
    }

    public void method2() {
        synchronized (lockB) {
            synchronized (lockA) {   // может ждать lockA
                // критическая секция
            }
        }
    }
}
```

Если один поток вызовет `method1()`, а другой одновременно `method2()` —
возникнет deadlock.

### Связь Deadlock с проблемами памяти

**Прямой связи нет.** Deadlock **сам по себе не вызывает** `OutOfMemoryError`.

Однако **косвенная связь** очень сильная и важная:

#### 1. Deadlock приводит к "зависанию" потоков

- Потоки остаются живыми, но ничего не делают (заблокированы).
- Они продолжают удерживать все объекты, на которые у них есть ссылки (включая
  локальные переменные, захваченные объекты и т.д.).
- Если эти потоки держали большие объекты в памяти — они **не могут быть собраны
  GC**, пока потоки живы.

#### 2. Постепенное накопление проблем с памятью

- Новые запросы продолжают приходить → создаются новые потоки (в
  thread-per-request модели).
- Старые потоки в deadlock продолжают потреблять память.
- Со временем может возникнуть:
    - **Thread Exhaustion** (`unable to create new native thread`)
    - **OutOfMemoryError: Java heap space** (из-за накопления объектов в
      заблокированных потоках)
    - **GC Overhead limit exceeded** (GC постоянно работает, но не может
      очистить память из-за живых заблокированных потоков)

#### 3. Реальные сценарии из практики

- Deadlock в пуле соединений к базе данных → все соединения "висят" → новые
  запросы создают новые объекты → рост памяти.
- Deadlock при работе с кэшами или очередями → объекты остаются в памяти.
- В приложениях с большим количеством потоков deadlock часто приводит к
  медленному, но неумолимому росту потребления памяти и потоков.

### Как диагностировать Deadlock в продакшене?

1. **Thread Dump** — основной инструмент:
   ```bash
   jcmd <PID> Thread.print
   jstack -l <PID>
   kill -3 <PID>
   ```

   В дампе JVM сама часто пишет в конце:
   ```
   Found one Java-level deadlock:
   =============================
   "Thread-1":
     waiting to lock monitor ...
   "Thread-2":
     waiting to lock monitor ...
   ```

2. **Java Flight Recorder (JFR)** — очень удобно в современных JDK.
3. **VisualVM**, **JMC (JDK Mission Control)**, **JProfiler**.

### Как предотвратить Deadlock?

- **Самое надёжное**: Всегда захватывать блокировки **в одном и том же порядке
  ** (глобальный порядок).
- Использовать `ReentrantLock` с `tryLock()` и таймаутами.
- Предпочитать высокоуровневые конструкции:
    - `ConcurrentHashMap`
    - `Caffeine Cache`
    - `java.util.concurrent` пакеты
- С Java 21+ — **Virtual Threads** + **StructuredTaskScope** (меньше
  классических synchronized-блокировок).
- Избегать вызова чужого кода внутри `synchronized` блока.

### Важный вывод

- **Deadlock** — это в первую очередь **проблема доступности** (availability) и
  производительности. Приложение "зависает", хотя JVM продолжает работать.
- **Memory Leak** — это проблема **потребления памяти**.
- Они часто идут вместе: deadlock → заблокированные потоки → удерживаемые
  объекты → рост памяти → OOM или деградация.

---

# Как Virtual Threads решают проблемы с памятью

**Виртуальные потоки (Virtual Threads)** в Java (Project Loom) радикально решают
**две ключевые проблемы с памятью**, которые долго мучили разработчиков:

1. **Исчерпание потоков** (
   `OutOfMemoryError: unable to create new native thread`)
2. **Высокое потребление памяти** на потоки в thread-per-request архитектуре

### Как именно Virtual Threads решают проблемы с памятью?

#### 1. Разница в потреблении памяти (самое важное)

| Параметр                     | Платформенный поток (Platform Thread)       | Виртуальный поток (Virtual Thread)                     | Выигрыш  |
|------------------------------|---------------------------------------------|--------------------------------------------------------|----------|
| Стек (Stack)                 | ~1 МБ (фиксированный, зарезервирован сразу) | Несколько сотен байт в начале, растёт по необходимости | ~1000x   |
| Управление                   | ОС (ядро)                                   | JVM                                                    | —        |
| Создание                     | Дорогое                                     | Очень дешёвое                                          | —        |
| Максимальное разумное кол-во | 1000–5000 (часто лимит ОС)                  | Сотни тысяч — миллионы (ограничено heap)               | Огромный |

- **Платформенный поток** — это тяжёлый объект ОС. Каждый поток сразу
  резервирует большой непрерывный блок памяти под стек (по умолчанию 1 МБ).
- **Виртуальный поток** — это обычный объект в **Java heap**. Его стек хранится
  в куче JVM и растёт динамически (как обычный массив). Поэтому он начинается с
  очень малого размера (~ few hundred bytes) и потребляет память только по мере
  необходимости.

**Вывод**: вместо того чтобы тратить ~1 ГБ на 1000 потоков, ты можешь иметь 100
000–500 000 виртуальных потоков и потратить на них всего несколько сотен МБ
heap.

### 2. Как Virtual Threads решают проблему `unable to create new native thread`

Раньше при высокой нагрузке (особенно I/O-bound: ожидание БД, HTTP-запросов,
файлов) все worker-потоки Tomcat/Jetty блокировались. Когда пул потоков
заканчивался — новые запросы не могли быть обработаны →
`OutOfMemoryError: unable to create new native thread`.

**Virtual Threads решают это так**:

- Виртуальные потоки **монтируются** (mount) на небольшое количество **carrier
  threads** (обычных платформенных потоков).
- Когда виртуальный поток выполняет блокирующую операцию (например,
  `socket.read()` или JDBC запрос), JVM **размонтирует** его с carrier thread.
- Carrier thread сразу освобождается и может подхватить другой виртуальный
  поток.
- Сам заблокированный виртуальный поток просто "спит" в heap, почти не потребляя
  ресурсов процессора.

Благодаря этому можно обрабатывать **десятки тысяч** одновременных запросов,
используя всего **несколько десятков** реальных ОС-потоков.

### 3. Включение Virtual Threads в Spring Boot (самый простой способ)

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

После этого все `@Async`, HTTP-запросы, TaskExecutor и т.д. по умолчанию
используют виртуальные потоки.

### Важные нюансы и ограничения (чтобы не было сюрпризов)

Хотя виртуальные потоки сильно помогают с памятью, они **не волшебная таблетка
**:

- **Потребление heap растёт** — потому что стек виртуальных потоков лежит в Java
  heap. При очень большом количестве одновременно активных виртуальных потоков
  можно получить обычный `OutOfMemoryError: Java heap space`.
- **Pinning (приклеивание)** — в Java 21–23 виртуальный поток мог "приклеиться"
  к carrier thread при использовании `synchronized` + блокирующей операции
  внутри. Это сводило на нет преимущества.  
  **С Java 24+** (JEP 491) проблема с `synchronized` в большинстве случаев
  решена.
- **Нет встроенного лимита** — в отличие от `ThreadPoolExecutor`, виртуальные
  потоки можно создавать миллионами. Без back-pressure можно легко "засорить"
  память.

### Когда Virtual Threads лучше всего помогают с памятью?

- I/O-bound приложения (веб-сервисы, микросервисы, работа с БД, внешними API)
- Высокая степень параллелизма (много одновременных запросов с ожиданием)
- Приложения, где раньше приходилось искусственно ограничивать размер пула
  потоков

**Меньше помогают** (или даже могут ухудшить):

- CPU-bound задачи (вычисления)
- Приложения с очень большим объёмом данных в памяти




---
---

# ScopeValue

**ScopedValue** — это современная альтернатива `ThreadLocal`, которая появилась
в рамках **Project Loom**. Она предназначена для безопасной и эффективной
передачи **неизменяемых** (immutable) данных внутри потока и между дочерними
задачами.

### Зачем нужен ScopedValue?

`ThreadLocal` имеет несколько серьёзных проблем, особенно в эпоху **Virtual
Threads**:

- Значение можно менять в любом месте (`set()`), что делает код сложным для
  понимания.
- Легко забыть вызвать `remove()` → **утечка памяти** (особенно опасно с пулом
  потоков).
- Плохо работает с большим количеством виртуальных потоков (дополнительные
  overhead).
- Неудобно передавать данные в дочерние потоки.

**ScopedValue** решает эти проблемы:

- Значение **неизменяемое** (immutable).
- Автоматически уничтожается после выхода из области видимости (scope).
- Отлично работает с **Structured Concurrency** (`StructuredTaskScope`).
- Быстрее и экономичнее по памяти при большом количестве виртуальных потоков.
- Наследуется дочерними задачами автоматически.

### Основной API

```java
// 1. Объявляем ScopedValue (статическое финальное поле)
private static final ScopedValue<String> USER_NAME = ScopedValue.newInstance();
private static final ScopedValue<User> CURRENT_USER = ScopedValue.newInstance();
```

### Как использовать (самый простой пример)

```java
public void processRequest(String username) {

    // Привязываем значение к области выполнения
    ScopedValue.where(USER_NAME, username)
            .run(() -> {
                // Здесь и во всех вызываемых методах можно читать значение
                System.out.println("User: " + USER_NAME.get());

                doBusinessLogic();   // значение доступно внутри
            });
}
```

Метод `get()` можно вызывать в любом глубоком методе — значение "видно" как
неявный параметр.

### Пример с возвратом результата

```java
String result = ScopedValue.where(USER_NAME, "Alice")
        .call(() -> {
            return service.getUserData();   // внутри можно читать USER_NAME.get()
        });
```

### Наследование в Structured Concurrency (самое мощное использование)

```java
private static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

public void handleRequest() {
    String reqId = UUID.randomUUID().toString();

    ScopedValue.where(REQUEST_ID, reqId)
            .run(() -> {
                try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

                    scope.fork(() -> serviceA.process());   // автоматически наследует REQUEST_ID
                    scope.fork(() -> serviceB.process());
                    scope.fork(() -> serviceC.process());

                    scope.join();
                    scope.throwIfFailed();
                } catch (Exception e) {
                    // обработка
                }
            });
}
```

Внутри методов `serviceA.process()`, `serviceB.process()` и т.д. можно просто
писать:

```java
String id = REQUEST_ID.get();
```

### Сравнение ScopedValue и ThreadLocal

| Характеристика                       | ThreadLocal              | ScopedValue                          |
|--------------------------------------|--------------------------|--------------------------------------|
| Изменяемость                         | Можно менять (`set()`)   | Только immutable                     |
| Очистка                              | Нужно вручную `remove()` | Автоматическая (по выходу из scope)  |
| Утечки памяти                        | Легко допустить          | Практически невозможны               |
| Наследование в дочерние потоки       | Сложно и дорого          | Автоматическое и эффективное         |
| Производительность с Virtual Threads | Хуже                     | Лучше (оптимизировано)               |
| Стиль кода                           | Неявный и опасный        | Более структурированный и безопасный |

### Важные правила и лучшие практики

1. **Используй только для неизменяемых данных** (UserContext, RequestId,
   TenantId, токены и т.д.).
2. Объявляй `ScopedValue` как `private static final`.
3. Не используй для данных, которые должны меняться во время выполнения.
4. Хорошо сочетается с **Virtual Threads** + **StructuredTaskScope**.
5. В новых проектах на Java 21+ предпочтительнее `ScopedValue`, чем
   `ThreadLocal`.

### Текущее состояние (апрель 2026)

ScopedValue прошёл путь:

- Incubator — Java 20
- Preview — Java 21, 22, 23, 24
- **Финализирован** в **Java 25** (JEP 506)

В Java 25+ можно использовать без флага `--enable-preview`.


--- 

# Кеш

Представь ситуацию: у тебя есть сервис, который ходит в базу данных за профилем
пользователя. Запрос в базу долгий (например, 1 секунда). Чтобы не нагружать
базу, ты решаешь хранить профили в памяти (в Map). Это и есть кеш.
В многопоточной среде (когда приходят тысячи запросов одновременно) начинаются
проблемы.

## Проблема «Гонки за вычислением» (Cache Stampede)

Допустим, кеш пустой. Одновременно прилетают 100 потоков с запросом профиля
одного и того же юзера (ID=1).

1. Поток №1 лезет в Map, видит, что там пусто.
2. Поток №2 лезет в Map, тоже видит «пусто».
3. ...и так все 100 потоков.
4. В итоге все 100 потоков одновременно отправляют тяжелый запрос в базу данных.
5. База «ложится» от нагрузки, хотя мы хотели её защитить кешем.

------------------------------

## Решение через ConcurrentHashMap и computeIfAbsent

Метод computeIfAbsent в ConcurrentHashMap работает магически: он блокирует
вычисление только для конкретного ключа.

// Кеш
Map<Integer, User> cache = new ConcurrentHashMap<>();
public User getUser(Integer id) {
// Если 100 потоков вызовут это одновременно для id=1:
return cache.computeIfAbsent(id, key -> {
// ЭТОТ БЛОК выполнится ТОЛЬКО ОДИН РАЗ
return db.findUserById(key); // Тяжелый запрос в базу
});
// Остальные 99 потоков "подождут" на этой строке,
// пока первый поток получит ответ от базы, и сразу возьмут готовый результат.
}

## Почему это круто?

1. Потокобезопасность: Тебе не нужно писать synchronized, который замедлил бы
   всё приложение.
2. Атомарность: Гарантируется, что тяжелая функция выполнится единожды для
   одного ключа.
3. Производительность: Если придут запросы для разных юзеров (ID=1 и ID=2), они
   не будут мешать друг другу, так как ConcurrentHashMap блокирует только одну
   «корзину» (bucket) с данными, а не всю карту целиком.

## Резюме для интервью

«Кеш в многопоточной среде должен быть не просто Map, а ConcurrentHashMap.
Использование computeIfAbsent позволяет избежать ситуации, когда несколько
потоков одновременно пытаются вычислить (загрузить из БД) одно и то же
отсутствующее значение, создавая лишнюю нагрузку».



--------

Отличный вопрос! Давай разберём **статический кеш** максимально понятно и
честно.

### Что такое статический кеш и почему он так часто вызывает утечку памяти?

**Статический кеш** — это когда ты объявляешь коллекцию (Map, List, Set) с
модификатором `static`.

#### Простой пример статического кеша:

```java

@Service
public class UserService {

    // Вот этот кеш и является проблемой
    private static final Map<Long, User> userCache = new HashMap<>();

    public User getUserById(Long id) {
        // Если пользователя нет в кеше — загружаем из базы и кладём в кеш
        return userCache.computeIfAbsent(id, this::loadUserFromDb);
    }

    private User loadUserFromDb(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}
```

### Почему из-за этого утекает память?

1. **Статическое поле живёт очень долго**
    - `static` переменная создаётся один раз при загрузке класса.
    - Она существует **всё время**, пока работает JVM (пока живёт ClassLoader).

2. **HashMap никогда сам не очищается**
    - Каждый новый пользователь, которого ты запросил, добавляется в
      `userCache`.
    - Старые пользователи **не удаляются**.
    - Со временем в этой мапе может накопиться 100 000, 500 000 или даже
      миллионы объектов.

3. **Каждый объект User держит другие объекты**
    - Один `User` может держать в себе: заказы, адреса, документы, роли,
      настройки и т.д.
    - В итоге 100 тысяч пользователей в кеше могут занимать несколько гигабайт
      памяти.

4. **Garbage Collector не может ничего удалить**
    - Пока на объект есть ссылка из `userCache` (а она есть, потому что мапа
      статическая), GC не может его собрать.

**Результат**: память постоянно растёт → через несколько часов или дней
появляется `OutOfMemoryError: Java heap space`.

### Почему разработчики так часто делают?

Потому что на первый взгляд это выглядит логично:

- "Хочу быстро получать пользователей по id"
- "Сделаю простой кеш"
- `computeIfAbsent` — удобно

Но забывают про **ограничение размера** и **время жизни** данных.

### Как правильно делать кеш? (Сравнение)

**Плохо (статический HashMap):**

```java
private static final Map<Long, User> cache = new HashMap<>();   // утечка!
```

**Хорошо (современный подход):**

```java
private final Cache<Long, User> cache = Caffeine.newBuilder()
        .maximumSize(5000)                    // максимум 5000 пользователей
        .expireAfterAccess(15, TimeUnit.MINUTES) // удалять, если не обращались 15 минут
        .build();
```

Или даже проще (если не хочешь добавлять Caffeine):

```java
private final Map<Long, User> cache =
        Collections.synchronizedMap(new LinkedHashMap<Long, User>(5000, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, User> eldest) {
                return size() > 5000;   // автоматически удаляем самые старые
            }
        });
```

---

### Главное правило:

> **Никогда не используй `static HashMap`, `static ArrayList`
или `static HashSet` как кеш в долгоживущих приложениях (Spring Boot,
микросервисы и т.д.).**

Это один из самых быстрых способов получить утечку памяти.

