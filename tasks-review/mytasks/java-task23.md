# Актуатор и health check

## Spring Boot Actuator и Health Checks в микросервисах с Kubernetes

Actuator — это подпроект Spring Boot, который добавляет production-ready endpoints для мониторинга и управления приложением. В микросервисной архитектуре, особенно под управлением Kubernetes (K8s), он становится незаменимым инструментом для обеспечения надёжности, самодиагностики и автоматического восстановления.

### 1. Что такое Spring Boot Actuator?

Actuator предоставляет REST-эндпоинты, JMX-бина, а также интеграцию с метриками, логированием, health checks, информацией о приложении и т.д.

Основные эндпоинты (адрес относительно контекста `/actuator`):

| Эндпоинт | Описание |
|----------|-----------|
| `/health` | Состояние здоровья приложения (агрегирует статусы всех индикаторов) |
| `/info` | Произвольная информация (версия, описание, и т.п.) |
| `/metrics` | Метрики приложения (память, процессор, http-запросы, JVM, БД) |
| `/prometheus` | Метрики в формате, понятном Prometheus (если добавить зависимость) |
| `/loggers` | Просмотр и изменение уровня логирования в рантайме |
| `/env` | Переменные окружения и свойства конфигурации (осторожно – чувствительные данные) |
| `/configprops` | Все `@ConfigurationProperties` бины |
| `/beans` | Список всех Spring-бинов |
| `/conditions` | Условия автоконфигурации (откуда взялись бины) |
| `/shutdown` | (отключен по умолчанию) – корректное завершение приложения |
| `/threaddump` | Дамп потоков JVM |

По умолчанию открыты только `/health` и `/info` (для безопасности). Остальные нужно явно включать.

### 2. Настройка Actuator в Spring Boot

#### Зависимость (Maven):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

#### Включение всех эндпоинтов через HTTP (application.yml):
```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"   # включаем все эндпоинты (или перечислить: health,info,metrics,prometheus)
      base-path: /actuator
  endpoint:
    health:
      show-details: always   # показывать детали (в K8s лучше "when_authorized" или "never")
      show-components: always
    prometheus:
      enabled: true
    metrics:
      enabled: true
```

### 3. Health Check в деталях

`/actuator/health` возвращает общий статус: `UP`, `DOWN`, `OUT_OF_SERVICE`, `UNKNOWN`. Статус вычисляется на основе нескольких `HealthIndicator` (компонентов, проверяющих работоспособность различных систем).

#### Стандартные HealthIndicator'ы (автоматически подключаются при наличии зависимостей):
- **DataSourceHealthIndicator** – проверяет соединение с БД (выполняет `validationQuery`).
- **RedisHealthIndicator** – проверяет Redis.
- **MongoHealthIndicator** – MongoDB.
- **RabbitHealthIndicator** – RabbitMQ.
- **DiskSpaceHealthIndicator** – свободное место на диске.
- **ElasticsearchRestHealthIndicator** – Elasticsearch.
- **CassandraHealthIndicator** – Cassandra.
- **Neo4jHealthIndicator** – Neo4j.
- **KafkaHealthIndicator** – Kafka.
- **MailHealthIndicator** – SMTP-сервер.
- **LivenessStateHealthIndicator** и **ReadinessStateHealthIndicator** (для K8s, подробно ниже).

#### Кастомный HealthIndicator:

```java
@Component
public class CustomServiceHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // Проверка какого-то внешнего сервиса
        boolean serviceHealthy = checkExternalService();
        if (serviceHealthy) {
            return Health.up()
                .withDetail("service", "available")
                .withDetail("timestamp", System.currentTimeMillis())
                .build();
        } else {
            return Health.down()
                .withDetail("service", "unavailable")
                .withDetail("reason", "timeout")
                .build();
        }
    }
}
```

Также можно использовать `HealthContributorRegistry` для динамической регистрации.

### 4. Интеграция Actuator с Kubernetes

Kubernetes использует **probes** для управления жизненным циклом контейнера:
- **livenessProbe** – жива ли JVM? Если probe завершается с ошибкой, K8s перезапускает контейнер.
- **readinessProbe** – готов ли контейнер принимать трафик? Если нет, K8s удаляет контейнер из балансировщика (Service).
- **startupProbe** – используется для приложений, которым требуется долгий старт (обычно отключает liveness на время инициализации).

#### Рекомендуемый подход: отдельные эндпоинты для liveness и readiness

Spring Boot 2.3+ предоставляет **LivenessState** и **ReadinessState**, а также специальные группы health-индикаторов.

**Настройка:**

```yaml
management:
  endpoint:
    health:
      # делаем группу для liveness
      group:
        liveness:
          include: "livenessState,diskSpace"   # только те индикаторы, которые критичны для работы (!)
        readiness:
          include: "readinessState,db,redis,kafka"  # проверки зависимостей, без которых нельзя принимать запросы
```

**Объяснение:**
- `livenessState` – встроенный индикатор, который переводится в `DOWN` только при фатальных ошибках (например, когда приложение вызвало `ApplicationAvailability.setLivenessState(LivenessState.BROKEN)`).
- `readinessState` – аналогично для готовности.

В коде можно принудительно пометить приложение как непригодное к работе:

```java
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class AvailabilityService {
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    public void markLivenessBroken() {
        AvailabilityChangeEvent.publish(eventPublisher, this, LivenessState.BROKEN);
    }
    
    public void markReadinessAccepted() {
        AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.ACCEPTING_TRAFFIC);
    }
}
```

**В K8s Deployment используем:**

```yaml
spec:
  containers:
  - name: my-app
    image: my-app:latest
    ports:
    - containerPort: 8080
    livenessProbe:
      httpGet:
        path: /actuator/health/liveness   # группа liveness
        port: 8080
      initialDelaySeconds: 30   # после старта ждём
      periodSeconds: 10
      timeoutSeconds: 2
      failureThreshold: 3       # 3 неудачи → рестарт
    readinessProbe:
      httpGet:
        path: /actuator/health/readiness  # группа readiness
        port: 8080
      initialDelaySeconds: 5
      periodSeconds: 5
      timeoutSeconds: 2
      failureThreshold: 3
    startupProbe:
      httpGet:
        path: /actuator/health/readiness
        port: 8080
      initialDelaySeconds: 0
      periodSeconds: 5
      failureThreshold: 30      # даём приложению до 30*5=150 сек на старт
```

**Важный принцип:**
- **liveness** должна **НЕ** зависеть от внешних ресурсов (БД, Redis, etc.). Если приложение не может соединиться с БД, это не значит, что его нужно убивать и перезапускать – оно может восстановиться позже. Liveness должна сигнализировать только о мёртвом приложении (deadlock, бесконечный цикл, внутренней неконсистентности, OutOfMemoryError).
- **readiness** должна проверять все необходимые зависимости. Если БД недоступна, приложение не должно принимать запросы.

### 5. Как K8s использует health-эндоинты внутри микросервисной сетки

Типичный сценарий в Kubernetes (без service mesh):

1. **Подача Deployment** – K8s создаёт Pod.
2. **startupProbe** даёт время на старт (если настроен). Пока он не пройдёт, liveness не работает.
3. **readinessProbe** начинает проверяться. Когда она возвращает успех, Pod помечается как `Ready` и его IP добавляется в Endpoints сервиса.
4. **livenessProbe** периодически проверяет, жив ли процесс. Если он падает трижды, Pod перезапускается (но не пересоздаётся, если не задана иная стратегия).

### 6. Что происходит, когда health check падает в разных ситуациях

| Probe | Статус | Действие K8s |
|-------|--------|--------------|
| readinessProbe → `DOWN` (503) | Не готов | Pod удаляется из балансировки сервиса, но продолжает работать (может догрузить кеши, подождать восстановление БД). |
| livenessProbe → `DOWN` (503) | Мёртв | K8s убивает контейнер и создаёт новый (restartPolicy: Always). |
| startupProbe → failure | Не стартовал | Если не пройдёт `failureThreshold` раз, K8s убивает контейнер – считает, что приложение никогда не запустится. |

### 7. Лучшие практики для микросервисов

- **Не включайте `/env`, `/configprops`, `/beans`** в открытых эндпоинтах (могут содержать секреты). Если нужно – используйте авторизацию (`spring.security`) или открывайте только `/health` и `/metrics`.
- **Используйте разные порты для административных эндпоинтов** (например, 8081), чтобы отделить внешний трафик от внутренних checks.
```yaml
management.server.port: 8081
management.endpoints.web.base-path: /
```
- **Настройте таймауты чувствительно** – не ждите 30 секунд на ответ health endpoint (обычно 1-2 секунды).
- **Не делайте в health check тяжёлых операций** – не обходите все строки таблиц, не выполняйте сложные запросы.
- **Агрегируйте health status верхнеуровнево** – например, для сервиса, который зависит от downstream сервисов, можно проверить их health через `RestTemplate` (но осторожно – это может создавать каскадные проблемы).

### 8. Как работает health check в микросервисной архитектуре с Service Mesh (Istio, Linkerd)

При использовании service mesh, часто не используются `livenessProbe` и `readinessProbe` самого K8s, а полагаются на sidecar-контейнер (Envoy). Но health endpoint'ы всё равно важны, потому что:
- Sidecar использует readinessProbe K8s для управления введением трафика.
- Метрики из `/metrics` или `/prometheus` собираются Prometheus и отображаются в Grafana.
- Для автоматического восстановления посредством `istio` используются OutlierDetection на стороне Envoy.

Тем не менее, базовые K8s probes остаются основным механизмом.

### 9. Пример полной конфигурации микросервиса с Actuator и K8s

**application.yml:**
```yaml
spring:
  application:
    name: order-service
  datasource:
    url: jdbc:postgresql://postgres:5432/orders
    hikari:
      maximum-pool-size: 10
      
management:
  server:
    port: 8081
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
      base-path: /
  endpoint:
    health:
      show-details: when_authorized
      group:
        liveness:
          include: livenessState,diskSpace
        readiness:
          include: readinessState,db
  metrics:
    export:
      prometheus:
        enabled: true
```

**Deployment.yaml (фрагмент):**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: app
        image: order-service:latest
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 8081
          name: management
        env:
        - name: SPRING_DATASOURCE_URL
          value: jdbc:postgresql://postgres-svc/orders
        livenessProbe:
          httpGet:
            path: /health/liveness
            port: 8081
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health/readiness
            port: 8081
          periodSeconds: 5
```

### 10. Troubleshooting: что делать, если health check падает

| Симптом | Возможная причина | Действие |
|---------|------------------|----------|
| liveness периодически падает | OutOfMemoryError, deadlock, бесконечный цикл | Посмотреть дампы потоков, heap dump, увеличить память или исправить код. |
| readiness падает на старте | БД недоступна, кэш не запущен | Проверить зависимости, настроить startupProbe. |
| readiness падает после некоторого времени | Исчерпание пула соединений, утечка | Увеличить pool, проверить connection leak, перезапустить зависимый сервис. |
| Оба probes падают | Приложение полностью остановлено | Проверить логи, возможно внешнее убийство OOM Killer. |

### 11. Альтернативы Actuator для health checks

- **Micronaut** – аналогичный health endpoint.
- **Quarkus** – `smallrye-health` (MicroProfile Health).
- **Go + Kubernetes** – ручная реализация `/health` на HTTP.
- **Node.js** – библиотека `express-healthcheck`.

Но для Spring Boot **Actuator** – самый удобный и мощный вариант.

### Итог

В микросервисах с Kubernetes Spring Boot Actuator предоставляет:
- `/health/liveness` – для K8s livenessProbe (определяет, жив ли контейнер).
- `/health/readiness` – для readinessProbe (готов принимать трафик).
- `/metrics` и `/prometheus` – для сбора метрик в Prometheus + Grafana.
- `/info` – для отображения версии приложения, git commit, etc.
- Возможность расширения через `HealthIndicator` и `AvailabilityChangeEvent`.

Правильная настройка probes – критически важна для самовосстановления микросервисной системы. Без них вы рискуете, что K8s будет перезапускать приложение при временных проблемах с БД или, наоборот, оставлять в работе неготовые поды, направляя на них трафик.

**Главный принцип:** Liveness – для проверки неустранимых внутренних ошибок, Readiness – для проверки доступности зависимостей.
