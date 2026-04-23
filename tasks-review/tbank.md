# Задачи на ревью


## Задача tbank#1  (разобрано, см. task-review-success.md)

```java
// Сделать код ревью 

import java.util.UIID;

/**
 * Сервис бронирования места в самолете.
 * Клиент с купленным билетом может за дополнительную плату выбрать конкретное место.
 * Базовая цена мест определяется тарифами (внешним сервисом).
 * Для клиентов с определенными тарифами (PREMIUM, ULTRA) необходимо сделать скидку при оплате.
 * При бронировании клиенту выставляется инвойс на оплату. Управление оплатой осуществляется в стороннем сервисе.
 */
@Service
public class SeatBookingService {
    

    @Autowired private SeatBookingRepository seatBookingRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TariffClient tariffClient;
    @Autowired private CustomerClient customerClient;
    @Autowired private PaymentClient paymentClient;

    /**
     * Бронирование.
     * @param seatCode код места (например 19A)
     * @param ticketId ид билета
     */
    @Transactional
    public void bookSeat(String seatCode, UIID ticketId) { //-> FIXME: UUID
        
         var ticket = ticketRepository.findById(ticketId);
        
        // бронируем: 
        var seatBooking = new SeatBooking(seatCode, ticket.get().getFlightId(), ticketId, BookingStatus.BOOKED);
        // сохраняем бронь
        seatBookingRepository.save(seatBooking);

        // ищем базовый тариф для выбранного места в самолете
        var basePrice = tariffClient.getBasePrice(
            ticket.get().getPlaneModel(), seatCode
        );
        
        // ищем данные о клиенте
        long userId = (long) SecurityContextHolder
        .getContext().getAuthentication().getPrincipal();
        
    
        var userData = customerClient.getCustomer(userId);
        
        System.out.println("Найден пользователь " + userData.getFio() + " номер документа " + userData.getDocument());
        
        var price = basePrice;
        
        if (userData.getTariff() == "PREMIUM") {
            // скидка 50%
            price = basePrice * 0.5d;
        }
        if (userData.getTariff() == "ULTRA") {
            // скидка 20%
            price = basePrice * 0.8d;
        }
        // Новый инвойс на оплату:
        var invoice = new Invoice(price, ticketId, userId);
        // выставляем платежку
        paymentClient.sendInvoice(invoice);
    }
}

@Data // FIXME: Для сущности БД использовать @Entity убрать @Data
@Table("seat_booking")
public class SeatBooking {
    
    // Добавить поле id и геттеры/сеттеры

    @Column
    private String seatCode;

    @Column
    private UUID flightId;

    @Column
    private UUID ticketId;

    @Column
    private BookingStatus status;
}

public enum BookingStatus {
    BOOKED, PAID
}

/** 
 1. Использовать constructor-inject, а не field-inject (проще отлаживать будет и тестировать)
 2. Использование double для денег, лучше использовать BigDecimal.
 3. Можно использовать SAGA, а не 2PC для распределенной транзакции, 
    чтобы был не просто откат событий, а компенсирующие действия, например если забронирован, 
    но не удалось выставить платеж, то отмена брони. Это дает отказоустойчивость и масштабируемость.
 4. Магические слова на if.
 5. Сравнение строк с помощью ==, нужно сделать через equals.
 6. Импорты неправильные.
 7. SeatBooking должен быть Entity, а не Data.
 8. ticketRepository возвращает Optional, но используется ticket.get() без проверки на null.


 Еще:
 1. В SeatBooking не определена колонка с PrimaryKey. Непонятно какой PK (простой или составной).
 2. В SeatBooking хорошо бы добавить @AllArgsConstructor (не уверен, что в текущей конфигурации @Data включает в себя @AllArgsConstructor).
 3. BookingStatus я бы переименовал в BookingStatusEnum, чтобы сразу по имени класса видеть, что это перечисление.
 4. public void bookSeat(String seatCode, UIID ticketId)  - "а что будет, если на вход подадутся значения null" ? Что будет, если seatCode не будет отвечать какому-то шаблону?
 На входные параметры нужен валидатор. Прежде проверить всегда дешевле чем получить в итоге откат ТА в результате выполнения операций на бд.
 Не забудьте и о том, что этот метод вам надо будет потестировать, а значит на тест придут самые разные данные.
 5. Сам класс SeatBookingService - он, судя по выполняемой работе, имеет тип "coarseGrained". Coarse-grained классы не должны (в моём понимании, хотя и могут), напрямую работать
 с классами репозиториев. Я бы заиспользовал SeatBookingService вместо SeatBookingRepository и TicketService вместо TicketRepository с соответствющими методами,
 скрыл бы от SeatBookingService детали работы с SeatBookingRepository  и TicketRepository.
 6. В локальной переменной seatBooking нет неоходимости. Можно сделать так:   seatBookingService.save(new SeatBooking(seatCode, flightId, ticketId, BookingStatus.BOOKED));
 (в случае, если объект ticket был найден).
 7. Нет проверки на то, что будет если basePrice = null ?
 8.  long userId = (long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
 - не нравится мне использование "потрохов системы безопасности" внутри бизнес-методов. Опять же, при интеграционном тестировании вам понадобится
 поднимать контекст безопасности. Я бы изменил метод bookSeat, добавив в него идентификатор userId. Пусть он приходит извне.
 При неправильной настройке контекста безопасности предвижу, что будет постоянно возникать исключение в цепочке вызовов
 "SecurityContextHolder.getContext().getAuthentication().getPrincipal();"
 9. Хорошо бы сразу прологировать входные параметры метода перед вызовом "var ticket = ticketRepository.findById(ticketId);".
 10. Что будет если в "var userData" придёт null ?
 11. Как видим, SeatBookingService использует в работе 3 сторонних сервиса (через их клиенты tariffClient, customerClient, paymentClient).
 Что будет, если сторонние сервисы 1-2-3 через свои клиенты в любой конфигурации откажут в обслуживании?
 Я бы, для начала и прежде всего, в начале бизнес-метода опросил бы  tariffClient и customerClient на данные с них, и в случае отказа любого
 не выполнял бы бизнес-операцию. В случае отказа paymentClient уже бы проконсультировался с аналитиком: как быть? Может быть, отправить данные
 в какую-либо очередь, чтобы сторонний процесс брал из неё данные , и периодически запрашивал paymentClient на выпуск платёжки?
 12. Timeouts. Бизнес-метод использует 3 сторонних сервиса для своей работы. В какое время каждый из них обязан ответить? Что делать, если в выделенное время не ответит?
 В функционал бизнес-метода надо включить обработку данных с учётом timeout'ов сторонних сервисов. Возможно, метод  надо будет переписать с реактивщиной.
 13. По поводу того, что можно и что нельзя выводить в лог, нужна консультация со службой безопасности организации.
 8а. Метод похож на часто используемый, поэтому получение идентификатора пользователя я бы сдвинул в базовый класс coarse-grained классов, и, наверное изменил бы тип вывода на Optional<Integer>, чтобы в клиентском классе решать что делать если пришел null.
 
 
*/
```


## Задача tbank#2

```java

/**
  * API поиска авторов и их книг по имени автора (полное ФИО или часть имени 
    в любом регистре).
  * Также компонент при каждом поиске обновляет статистику по частоте использования поисковой строки (сбрасывается раз в сутки другой системой)
  * При обнаружении популярного запроса (> 1000 запросов в сутки), по которому 
    находится много авторов, отправляется алерт.
  * Алерт должен отправляться не более 1 раза за сутки для каждого запроса
  * Все классы на самом деле находятся в разных файлах, однако здесь представлены в одном месте для удобства
*/

@RestController
public class AuthorController {
    @Autowired
    private AuthorSearchService service;

    @GetMapping("/authors")
    public List<AuthorDto> readAllAuthors(@RequestParam String query) {
        List<Author> authors = service.search(query);
        return authors.stream().map(el -> {
            return new Mapper().map(el);
        }).collect(Collectors.toList());
    }
}

@Component
public class AuthorSearchService {

    @Autowired
    private AuthorsRepository authorsRepository;
    @Autowired
    private StatisticsRepository statisticsRepository;

    private AlertRestClient arc = new AlertRestClient();

    // В query может быть как полностью ФИО, так и часть имени, например "Вадим Панов" или "панов"
    @Transactional
    public List<Author> search(String query) {
        List<Author> authors = authorsRepository
        .findByNameContainingIgnoreCase(query);
        Statistics s = statisticsRepository.findById(query).orElse(null);
        if (s == null) s = new Statistics(query);
        s.setNumbers(s.getNumbers() + 1);
        statisticsRepository.save(s);
        if (s.getNumbers() > 1000 && authors.size() > 1000) {
            System.out.println("too popular search with too much data, sending an alert...");
            arc.send(query, s.getNumbers(), authors.size());
        }

        return authors;
    }
}


@Entity
@Data
public class Author {
    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @OneToMany(mappedBy = "author")
    private List<Book> books;

    public Author(String name) {
        this.name = name;
    }
}

@Entity
@Data
public class Statistics {
    @Id
    private String query;
    private int numbers;

    public Statistics(String query) {
        this.query = query;
    }
}


@Data
public class AuthorDto {
    private Long id;
    private String name;
    private List<Book> books;
}


@Entity
@Data
public class Book {
    private Long id;
    private String name;
}
```



## Задача tbank#3

```java
import java.util.Date;
import java.util.List;

/**
Сервис тарификации вознаграждений сотрудникам за дополнительную работу.
Каждый сотрудник может выполнять что-либо помимо основной работы - проводить лекции, выступать на конференциях и т.д.
Такие действия оплачиваются согласно тарифам, с учетом заслуг сотрудника (личного бонусного коэффициента).
Оплата проходит через внешний сервис, вызываемый по REST.
*/
@Service
public class RewardBillingService {
  @Autowired
  private RewardRepository rewardRepository;

  @Autowired
  private TariffRepository tariffRepository;

  @Autowired
  private RewardRestClient rewardRestClient;

  @Transactional
  public void handleRewards(List<Employee> employees) {
    for (Employee employee : employees) {
      List<Reward> rewards = rewardRepository.findByEmployeeId(employee.getId();
      for (Reward reward : rewards) {
        if (List.of("speech", "lesson", "help").contains(reward.getType())) {
          Tariff tariff = tariffRepository.findByTypeAndDate(reward.getType(), new Date()).get();

          double amount = (1 + employee.getBonusCoefficient()) * tariff.getAmount();

          rewardRestClient.payReward(employee.getId(), amount);
          System.out.println("Отправлен платеж");

          reward.setStatus("paid");
          rewardRepository.sav e(reward);
        }
      }
    }
  }
}

```




--------------
## Задача tbank#4

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//Отработает ли CountDownLatch нужное количество раз?
public class Increment {
  private static int counter1 = 0;
  private static int counter2 = 0;
  Lock lock = new ReentranLock();

  public static void main(String[] args) throws InterruptedException {
    int tasksCount = 100_000;
    CountDownLatch latch = new CountDownLatch(tasksCount);
    ExecutorService executor = Executors.newFixedThreadPool(100);

    for (int i = 0; i < tasksCount; i++) {
      executor.submit(() -> {
          counter1++;
          counter2++; 
        latch.countDown();
      });
    }

    latch.await();
//сколько будет выведено?
    System.out.println(counter1);
    System.out.println(counter2);
    System.exit(0);
  }
}
```


## Задача tbank#5

```java

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Increment {
    private static int counter1 = 0;
    private static int counter2 = 0;

    public static void main(String[] args) throws InterruptedException {
        int tasksCount = 100_000;
        CountDownLatch latch = new CountDownLatch(tasksCount);
        ExecutorService executor = Executors.newFixedThreadPool(100);

        for (int i = 0; i < tasksCount; i++) {
            executor.submit(() -> {
                counter1++;
                counter2++;
                latch.countDown();
            });
        }

        latch.await();

        System.out.println(counter1);
        System.out.println(counter2);
        System.exit(0);
    }
}
```
