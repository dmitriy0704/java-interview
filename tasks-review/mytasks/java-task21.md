# надежность(пулы потоков)

## Пулы потоков в Java: подробный разбор

Пулы потоков (`ExecutorService`, `ThreadPoolExecutor`) — один из ключевых инструментов для управления параллелизмом. Неправильное использование может привести к `OutOfMemoryError` (в частности, `unable to create new native thread`), утечкам памяти и деградации производительности.

## 1. Зачем нужны пулы потоков?

- **Ограничение количества одновременно работающих потоков** (избегаем создания тысяч потоков, которые съедают память стека).
- **Переиспользование потоков** (снижаем накладные расходы на создание/уничтожение).
- **Управление очередью задач** (контролируем backlog).

## 2. Основные типы пулов из фабрики `Executors`

| Метод | Характеристика | Риски |
|-------|----------------|--------|
| `newFixedThreadPool(int n)` | Фиксированное число потоков, очередь без лимита (`LinkedBlockingQueue`) | **Очередь может бесконечно расти → OOME heap space** |
| `newCachedThreadPool()` | Потоки создаются по требованию, простаивающие удаляются через 60 сек. | **Неограниченный рост потоков → OOME unable to create native thread** |
| `newSingleThreadExecutor()` | Один поток, очередь без лимита | Очередь может бесконечно расти |
| `newScheduledThreadPool(int n)` | Фиксированное число потоков для отложенных задач | Аналогичен fixed, но поддерживает расписание |
| `newWorkStealingPool()` (Java 8+) | Пул с работой «на кражу», число потоков = ядрам процессора | Безопасен по числу потоков, но возможен рост очередей |


-  `newFixedThreadPool(int n)`:
    Фиксированное число потоков, очередь без лимита (`LinkedBlockingQueue`)  :
    **Очередь может бесконечно расти → OOME heap space**
- `newCachedThreadPool()` :
  Потоки создаются по требованию, простаивающие удаляются через 60 сек. :
  **Неограниченный рост потоков → OOME unable to create native thread** 
- `newSingleThreadExecutor()`:
  Один поток, очередь без лимита  :
  Очередь может бесконечно расти
- `newScheduledThreadPool(int n)`: 
  Фиксированное число потоков для отложенных задач :
  Аналогичен fixed, но поддерживает расписание 
- `newWorkStealingPool()` (Java 8+): 
  Пул с работой «на кражу», число потоков = ядрам процессора :
  Безопасен по числу потоков, но возможен рост очередей


## 3. Связь пулов потоков с OutOfMemoryError

### Тип A: `OutOfMemoryError: unable to create new native thread`

**Причина:** 
Попытка создать больше потоков, чем позволяет ОС (лимиты `ulimit -u`, память на стеки). Чаще всего вызвана `newCachedThreadPool()` при высокой нагрузке.

**Пример опасного кода:**
```java
ExecutorService pool = Executors.newCachedThreadPool();
for (int i = 0; i < 1_000_000; i++) {
    pool.submit(() -> heavyTask()); // Каждая задача создаёт новый поток, если все заняты
}
```

Поскольку `CachedThreadPool` не ограничивает количество потоков, при интенсивной подаче задач их число вырастет до лимита ОС (обычно несколько тысяч), после чего последует OOME.

### Тип B: `OutOfMemoryError: Java heap space`

**Причина:** 
Неограниченная очередь задач в `FixedThreadPool` или `SingleThreadExecutor`. Если задачи поступают быстрее, чем обрабатываются, очередь растёт и заполняет кучу.

**Пример:**
```java
ExecutorService pool = Executors.newFixedThreadPool(10); // очередь без лимита
while (true) {
    pool.submit(() -> {
        byte[] data = new byte[10_000_000]; // большая задача
        process(data);
    });
}
// Через некоторое время очередь будет содержать миллионы задач → OOME heap space
```

## 4. Как правильно создавать пулы? (ручное конфигурирование)

Используйте конструктор `ThreadPoolExecutor` с явным заданием очереди ограниченного размера и политики отклонения.

```java
int corePoolSize = 10;
int maxPoolSize = 50;
long keepAliveTime = 60L;
TimeUnit unit = TimeUnit.SECONDS;
BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(1000); // Ограниченная очередь
RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();

ExecutorService pool = new ThreadPoolExecutor(
    corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue, handler
);
```

#### Выбор очереди и политики:

| Очередь | Поведение | Риски |
|---------|-----------|-------|
| `LinkedBlockingQueue` (без лимита) | Может расти бесконечно | OOME heap space |
| `ArrayBlockingQueue` (фикс. размер) | Отклоняет задачи при переполнении | Задачи будут отклонены, нужно обработать |
| `SynchronousQueue` | Не хранит задачи, передаёт сразу потоку | Требует `maxPoolSize = Integer.MAX_VALUE` (риск роста потоков) |

#### Политики отклонения (`RejectedExecutionHandler`):

- **`AbortPolicy`** (по умолчанию) — бросает `RejectedExecutionException`.
- **`CallerRunsPolicy`** — задача выполняется в потоке, который вызвал `submit` (это давит на вызывающий код и снижает скорость подачи).
- **`DiscardPolicy`** — молча отбрасывает задачу.
- **`DiscardOldestPolicy`** — удаляет самую старую задачу из очереди и повторяет попытку.

**Самый безопасный вариант для продакшена:** `CallerRunsPolicy` + ограниченная `ArrayBlockingQueue`. Это обеспечивает обратное давление (backpressure) и предотвращает переполнение ресурсов.

## 5. Мониторинг пулов потоков (чтобы вовремя заметить проблемы)

Используйте JMX или встроенные методы:

```java
ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;
int active = tpe.getActiveCount();
long completed = tpe.getCompletedTaskCount();
int queueSize = tpe.getQueue().size();
int poolSize = tpe.getPoolSize();
int largestPoolSize = tpe.getLargestPoolSize(); // пиковое число потоков

logger.info("Pool: active={}, queue={}, total threads={}, completed={}", 
            active, queueSize, poolSize, completed);
```

Установите алерт, если `queueSize` превышает порог (например, 80% от максимального) или `largestPoolSize` приближается к лимиту ОС.

## 6. Как исправить уже существующий OOME, связанный с пулами потоков

### Случай 1: `unable to create new native thread` из-за `CachedThreadPool`

**Исправление:** заменить на `FixedThreadPool` с разумным числом потоков или на `ThreadPoolExecutor` с ограничением максимального числа.

```java
// Плохо
ExecutorService bad = Executors.newCachedThreadPool();

// Хорошо (максимум 100 потоков, очередь ограничена)
ExecutorService good = new ThreadPoolExecutor(
    10, 100, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000)
);
```

### Случай 2: `Java heap space` из-за бесконечной очереди

**Исправление:** заменить очередь на ограниченную и добавить политику отклонения.

```java
// Плохо
ExecutorService bad = Executors.newFixedThreadPool(10);

// Хорошо
BlockingQueue<Runnable> boundedQueue = new ArrayBlockingQueue<>(500);
RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();
ExecutorService good = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS, boundedQueue, handler);
```

### Случай 3: Утечка памяти из-за того, что пул не закрыт

Если пул потоков создаётся, но никогда не вызывается `shutdown()`, потоки остаются живыми и удерживают ссылки на объекты (например, в `ThreadLocal`), препятствуя сборке мусора.

**Исправление:** всегда закрывайте пул при завершении приложения или использовании.

```java
// В веб-приложении: через @PreDestroy или слушатель контекста
@PreDestroy
public void cleanup() {
    executorService.shutdown();
    try {
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }
    } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

## 7. Как не получить OOME из-за неправильного размера стека (Thread Stack)

Каждый поток имеет свой стек (размер задаётся `-Xss`). Если вы создаёте 5000 потоков со стеком 1 МБ, это 5 ГБ только на стеки, не считая кучи. В 32-битных системах лимит адресного пространства ещё жёстче.

**Исправление:**
- Ограничьте максимальное число потоков в пулах (например, до 200).
- Уменьшите размер стека, если это допустимо: `-Xss256k` (особенно для приложений с тысячами потоков, где рекурсии мало).

## 8. Лучшие практики по пулам потоков

1. **Никогда не используйте `Executors.newCachedThreadPool()` на продакшене** под нагрузкой, где количество задач может быть неограниченным.
2. **Никогда не используйте `Executors.newFixedThreadPool()` без контроля размера очереди** в системах с неравномерной нагрузкой.
3. **Всегда называйте потоки** (через `ThreadFactory`) для простоты отладки:
   ```java
   ThreadFactory namedFactory = new ThreadFactoryBuilder()
       .setNameFormat("my-pool-%d")
       .build();
   ExecutorService pool = new ThreadPoolExecutor(..., namedFactory);
   ```
4. **Используть `Future` и обрабатывать `RejectedExecutionException`**.
5. **Для фоновых/некритичных задач рассмотривате `ScheduledExecutorService` с ограниченной очередью**.
6. **В микросервисах/контейнерах** число потоков следует привязывать к количеству ядер:
   ```java
   int poolSize = Runtime.getRuntime().availableProcessors();
   ExecutorService pool = new ThreadPoolExecutor(poolSize, poolSize * 2, ...);
   ```
7. **При использовании виртуальных потоков (Java 21+)** большие пулы не нужны, но очереди всё равно нужно контролировать:
   ```java
   try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
       // Безопасен для миллионов задач, но не забывайте про ограничение других ресурсов
   }
   ```

## 9. Пример комплексного решения с обработкой OOME

```java
public class SafeThreadPool {
    private final ThreadPoolExecutor executor;
    
    public SafeThreadPool(int maxThreads, int queueCapacity) {
        executor = new ThreadPoolExecutor(
            maxThreads / 2,     // core
            maxThreads,         // max
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            new NamedThreadFactory("safe-pool"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // Мониторинг через отдельный поток
        startMonitoring();
    }
    
    private void startMonitoring() {
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            int queueSize = executor.getQueue().size();
            int activeCount = executor.getActiveCount();
            if (queueSize > 0.8 * queueCapacity) {
                log.warn("Pool queue is filling up: {}/{}", queueSize, queueCapacity);
            }
            if (activeCount == executor.getMaximumPoolSize()) {
                log.warn("Pool is saturated, active threads = {}", activeCount);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
    
    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
```

## 10. Что делать, если OOME уже произошёл, а у вас `CachedThreadPool`?

1. **Временно снизьте нагрузку** (ограничьте прием запросов на уровне балансировщика).
2. **Перезапустите приложение** (лишние потоки не исчезнут сами).
3. **Исправьте код** на ограниченный пул.
4. **Добавьте метрики** в Grafana/Prometheus: `thread_pool_size`, `queue_size`, `rejected_tasks`.
5. **Установите `-Xss256k`** для уменьшения расхода памяти на стеки, если потоки всё ещё многочисленны.

### Резюме

| Риск | Причина | Исправление |
|------|---------|--------------|
| `unable to create new native thread` | Неограниченный рост потоков (`newCachedThreadPool`) | Использовать `FixedThreadPool` или `ThreadPoolExecutor` с явным `maxPoolSize` |
| `Java heap space` | Бесконечная очередь задач | Использовать `ArrayBlockingQueue` с ограничением + `CallerRunsPolicy` |
| Утечка памяти | Пул не закрыт, потоки висят | `shutdown()` / `shutdownNow()` при завершении |
| Снижение производительности | Размер пула слишком мал или слишком велик | Настройка под тип задач (IO-bound: больше потоков, CPU-bound: ≈ ядрам) |

Контролируйте пулы — и OOME, связанные с потоками, вас покинут.


---

Конечно, объясню простыми словами.

Представьте, что каждое Java-приложение — это ресторан, а потоки — это официанты. У каждого официанта есть свой «рабочий инвентарь» (в Java это **стек потока** — память, где хранятся локальные переменные и информация о вызовах). В Java по умолчанию на каждого официанта выделяется около 1 мегабайта такой памяти.

## Почему пул потоков может привести к OutOfMemoryError?

### 1. Слишком много официантов (потоков)

Если вы создаёте неограниченный пул (например, `Executors.newCachedThreadPool()`), то при большом наплыве посетителей ресторан нанимает всё новых и новых официантов. Каждый новый официант съедает свой мегабайт памяти. Когда их становится слишком много (например, 10 000), только на их стеки уходит **10 гигабайт**! Даже если сами заказы (задачи) маленькие, память под стеки кончается, и Java вылетает с ошибкой `java.lang.OutOfMemoryError: unable to create new native thread`. Операционная система тоже не может создавать бесконечные потоки.

### 2. Бесконечная очередь заказов (задач)

Другой тип пула (например, `Executors.newFixedThreadPool(10)`) держит всего 10 официантов, но очередь заказов у них безразмерная. Если заказы поступают быстрее, чем 10 официантов успевают их выполнять, очередь растёт. Каждый невыполненный заказ — это объект в памяти. Когда очередь разрастается до миллионов задач, они заполняют всю кучу (heap), и возникает другая ошибка: `java.lang.OutOfMemoryError: Java heap space`.

### Суть в двух словах

- **Потоки** сами по себе требуют памяти (стеки). Слишком много потоков → память на стеки кончается.
- **Задачи**, ждущие в очереди, тоже занимают память. Если очередь не ограничена, она может переполнить кучу.

## Как избежать?

Ограничивать и потоки, и очередь:
- Не использовать `newCachedThreadPool()` бездумно.
- Всегда задавать максимальный размер очереди (`ArrayBlockingQueue` с лимитом).
- Выбрать политику, что делать, когда очередь переполнена (например, `CallerRunsPolicy` — пусть тот, кто вызывает, сам выполняет задачу, это давит на вход и не даёт расти очереди).

Вот почему в Java, если неправильно настроить пул потоков, очень легко получить `OutOfMemoryError`. Это как ресторан: нельзя нанять миллион официантов (не хватит места), и нельзя поставить бесконечную стойку ожидания заказов (она заполнит весь зал).
