# Задачи на ревью

## Задача #1. Сбер

```java
// @Service -> не указана аннотация @Service
class UserService { //-> Класс, не объявлен как public 
//-> TODO: Написать юнит тест для класса
    
//-> FIXME: Нельзя внедряемые бины создавать через new(), теряется контроль управления и прокси
    private UserRepository repo = new UserRepository();
    
    //-> Внедрение через конструктор лучше потому что:
    // 1. Гарантирует, что зависимость будет установлена один раз при запуске приложения 
    // и не изменится (случайно или намеренно) во время работы. 
    // 2. При использовании конструктора объект не может быть создан без его зависимостей. 
    // Spring просто не запустит приложение, если не найдет нужный бин.
    private  RegionService regionService; //-> Не указан final
    
    public UserService(final ApplicationContext appCtx) {
        //-> Строки не используются. Не использовать магические константы
        // 1. Имя бина может измениться
        // 2. Сложно тестировать: придется мокировать весь контекст
        // 3. При внедрении через конструктор Spring обнаруживает циклические зависимости 
        regionService = appCtx.getBean("regionService", RegionService.class);
    }

    //-> TODO: regionName заменить на Enum
    public void processNewUsers(final List<User> users, String regionName) {
        // …
        
        //-> FIXME: Замечания:
        //1.  Вызов транзакционного метода из нетранзакционного, Лучше использовать Self Injection
        //2.  Перезапись параметров: users = createUsers(users); — параметр users помечен как final, 
        // этот код просто не скомпилируется, т.к. createUsers() переназначает значение переменной
        users = createUsers(users);
        // …
        users.stream()
                //-> FIXME: Замечания
                //1. forEach() а не foreach()
                //2. Побочные эффекты в Stream: плохой тон, проблема производительности: 
                // для 1000 пользователей будет выполнено 1000 запросов.
            .foreach(u -> regionService.updateRegionLink(u.getId(), regionName));
            // если в RegionService сложная логика - можно обрабатывать по одному пользователю
            // forEach(u -> regionService.updateRegionLink(u, regionName));
    }

    @Transactional
    public List<User> createUsers(final List<User> users) { //-> Написать один инсерт
        
        //-> FIXME: Замечание:
        // Проблема N+1 в БД: В методе createUsers вызывается save в цикле. 
        // Это порождает множество мелких запросов. У Spring Data JPA есть метод saveAll(), 
        // который работает гораздо эффективнее.
        return users.stream()
                .map(u -> repo.saveUser(u))
                .collect(Collectors.toList());
    }

//-> FIXME: Неиспользуемый метод
    private User getUser(final int userId) {
        return repo.getUserById(userId);
    }
}

///-> Исправлено:

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {
    
    //-> Один запрос сохраняет все сразу
    @Modifying(clearAutomatically = true, flushAutomatically = true)// Обязательно для запросов UPDATE/DELETE
    @Transactional // Нужно, если метод вызывается вне другой транзакции
    @Query("UPDATE User u SET u.regionName = :regionName WHERE u.id IN :ids")
    void updateRegionForUsers(@Param("ids") List<Long> ids, @Param("regionName") String regionName);
}


/**
 * // Батчинг:
 * # Включаем батчинг (стандарт — от 20 до 50)
 * spring.jpa.properties.hibernate.jdbc.batch_size=50
 * 
 * # Заставляем Hibernate группировать похожие запросы
 * spring.jpa.properties.hibernate.order_inserts=true
 * spring.jpa.properties.hibernate.order_updates=true
 * 
 * # (Опционально) Для статистики, чтобы увидеть батчи в логах
 * spring.jpa.properties.hibernate.generate_statistics=true
 * 
 * Генерация ID у сущности User не IDENTITY (лучше SEQUENCE),
 * иначе батчинг на вставку не включится.
 */


@Service
@RequaredArgsConstructor
public class UserService{
    
    public void processNewUsers(List<User> users, String regionName) {
        /// -> Если небольшое количество пользователей:
        // 1. Сохраняем новых пользователей (используем saveAll для батчинга)
        List<User> savedUsers = repo.saveAll(users);
        // 2. Собираем ID сохраненных пользователей
        List<Long> ids = savedUsers.stream()
                .map(User::getId)
                .toList();
        // 3. Один запрос к БД вместо цикла! 
        // Напрямую через репозиторий, без лишних сервисов.
        repo.updateRegionForUsers(ids, regionName);

        
        //-> Или сразу проставляем регион и сохраняем:
        // 1. Прямо в памяти проставляем регион каждому пользователю
        users.forEach(u -> u.setRegionName(regionName));
        // 2. Сохраняем всех одним махом (Hibernate использует JDBC Batching)
        repo.saveAll(users);
        
        
        /// -> Если пользователей будет миллион:
        int batchSize = 50; // Тот же размер, что в настройках hibernate.jdbc.batch_size
        AtomicInteger counter = new AtomicInteger();
        userStream.forEach(user -> {
            user.setRegionName(regionName);
            repo.save(user); // Пока только складываем в очередь Hibernate

            // Каждые 50 записей сбрасываем данные в БД и чистим память
            if (counter.incrementAndGet() % batchSize == 0) {
                entityManager.flush(); // Отправить батч в базу
                entityManager.clear(); // Очистить кэш Hibernate, чтобы память не кончалась
            }
        });
        
    }
}


```


## Задача #2. Т-банк (стажировка)

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
    
    //-> FIXME: Сделать внедрение зависимостей через конструктор 
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
        
        // Ищем билет по id
        //-> FIXME: добавить обработку исключения "Билет не найден"
        
        // var ticket = ticketRepository.findById(ticketId);
        
        // бронируем: 
        // Новая бронь места: код места, рейс, билет, статус "Забронировано" 
        var seatBooking = new SeatBooking(seatCode, ticket.get().getFlightId(), ticketId, BookingStatus.BOOKED);
        // сохраняем бронь
        seatBookingRepository.save(seatBooking);

        // ищем базовый тариф для выбранного места в самолете
        //-> FIXME: "Рест в транзакции"
        var basePrice = tariffClient.getBasePrice(ticket.get().getPlaneModel(), seatCode);
        
        // ищем данные о клиенте
        //-> FIXME: 1) getPrincipal() может возвращать объект; 
        //        2) cервис привязывается к контексту безопасности, поэтому его 
        //           будет сложно протестировать, если контекст не задан; 
        //        3) пользователя следует передавать из контроллера
        long userId = (long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        
        //-> получение данных клиента из внешнего сервиса
        //-> FIXME: 1) "Рест в транзакции"; 
        //        2) Добавить или обработать исключение "Если клиент не найден"
        var userData = customerClient.getCustomer(userId);
        
        //-> FIXME: проблема: "println вместо логов"
        System.out.println("Найден пользователь " + userData.getFio() + " номер документа " + userData.getDocument());
        
        var price = basePrice;
        
        //-> FIXME: проблема: "магические константы"
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


## Задача Тбанк:

```java

/**
  * API поиска авторов и их книг по имени автора (полное ФИО или часть имени 
    в любом регистре).
  * Также компонент при каждом поиске обновляет статистику по частоте использования 
    поисковой строки (сбрасывается раз в сутки другой системой)
  * При обнаружении популярного запроса (> 1000 запросов в сутки), по которому 
    находится много авторов, отправляется алерт.
  * Алерт должен отправляться не более 1 раза за сутки для каждого запроса
  * Все классы на самом деле находятся в разных файлах, однако здесь представлены 
    в одном месте для удобства
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
        List<Author> authors = authorsRepository.findByNameContainingIgnoreCase(query);
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



## Задача Тбанк

```java
import java.util.Date;
import java.util.List;

/**
 
Сервис тарификации вознаграждений сотрудникам за дополнительную работу.*
Каждый сотрудник может выполнять что-либо помимо основной работы - проводить лекции,
 выступать на конференциях и т.д.
Такие действия оплачиваются согласно тарифам, с учетом заслуг сотрудника (личного бонусного коэффициента).
Оплата проходит через внешний сервис, вызываемый по REST.*/
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
      List<Reward> rewards = rewardRepository.findByEmployeeId(employee.getId());
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


------------
## Задача web tech

```java

// Сделать ревью

public class UserUpdater {
	
    @Autowired
	private CompanyRepository companyRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RemoteUserInfoProvider remoteUserInfoProvider;

	private Logger logger = Logger.getLogger(UserUpdater.class.getName());

	public void fillUsersData() {
		 try {
				companyRepository.findAll().forEach(company ->
					 updateUsersData(company.getUsers())
				);
			} catch (Exception e) {
				logger.error("Error");
			}

	}

	@Transactional
	private void updateUsersData(List<User> users) {
		// tx.begin
		userRepository.lockUsers(users); 
		users.parallelStream().forEach(user -> {
				UserInfo userInfo = remoteUserInfoProvider.getUserInfo(user.getId());
				user.updateData(userRepository); // update user fields
				userRepository.save(user);
			}
		);
	}
}
```


------------
## Задача Сбер

```java
class CodeProcessor {

    public void process(List<Code> codes) {
        for (Code code : codes) {

// 1. Множественные if можно заменить на switch:

            if (CodeType.ITCP == code.getCodeType()) {
                doSmthngITCP();
            } 
            else if (CodeType.TLS == code.getCodeType()) {
                doSmthngTLS();
            } 
            else if (CodeType.OTHER == code.getCodeType()) {
                doSmthngOther();
            } 
            else {
                doDefault();
            }

/*  3. Замена: 
            switch(code.getCodeType()){
                case CodeType.ITCP -> System.out.println("Handling ITCP");
                case CodeType.TLS -> System.out.println("Handling TLS");
                case CodeType.OTHER -> System.out.println("Handling Other");
                default -> System.out.println("Handling Default Case");
           } */
        }
    }

// 2. Текст, выводимый в методах можно выводить в switch  

    private void doSmthngITCP() {
        System.out.println("Handling ITCP");
    }

    private void doSmthngTLS() {
        System.out.println("Handling TLS");
    }

    private void doSmthngOther() {
        System.out.println("Handling Other");
    }

    private void doDefault() {
        System.out.println("Handling Default Case");
    }
}
 

enum CodeType {
    ITCP, TLS, OTHER
}

class Code {
    private final CodeType codeType;

    public Code(CodeType codeType) {
        this.codeType = codeType;
    }

    public CodeType getCodeType() {
        return codeType;
    }
}

public class CodeProcessingApp {
    public static void main(String[] args) {
        List<Code> codes = Arrays.asList(
                new Code(CodeType.ITCP),
                new Code(CodeType.TLS),
                new Code(CodeType.OTHER)
        );

        CodeProcessor processor = new CodeProcessor();
        processor.process(codes);
    }
}
```


---------------
## Задача Райффайзен

```java

// Что произойдет с изменениями в бд после блока catch ?

@Service
public class A {

    @Autowired
    B b;

    @Transactional
    public void doStuff() {
        try {
            b.doStuff();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // do some stuff
    }
}

@Service
public class B {

    @Transactional
    public void doStuff() {
        // do some stuff
        throw new RuntimeException();
    }
}

/* 
1. Одна транзакция: По умолчанию @Transactional имеет уровень распространения REQUIRED. 
Это значит, что метод B.doStuff() не создает новую транзакцию, а присоединяется к уже существующей 
транзакции метода A.doStuff().

2. Пометка на откат: Когда внутри метода B выбрасывается RuntimeException, Spring перехватывает его 
(через прокси-объект) и помечает текущую транзакцию как rollback-only (только для отката).

3. Игнорирование catch: То, что вы в методе A обернули вызов в try-catch, предотвращает немедленный 
вылет ошибки из метода A, но статус транзакции уже изменен на "испорчена".

4. Финал: Когда метод A.doStuff() успешно завершается, Spring пытается зафиксировать транзакцию (commit). 
Но, видя метку rollback-only, он понимает, что целостность данных нарушена, делает rollback и выбрасывает UnexpectedRollbackException.
*/

```


---------------
## Задача Альфа банк

```java

// Сделать ревью, найти проблемы

@RequiredArgsConstructor
@RestController("/resize/v1") // -> Адрес тут не пишется
//@RequestMapping("/resize/v1") -> Указание адреса в @RequestMapping
public class Controller {
    
    private final CachedPhotosService cachedPhotosService;
    
    @GetMapping("/resized-photo/{photo-id}")
    public PhootoDTO getResizedPhoto(@PathVariable("photo-id") String photoId) {
        return cachedPhotosService.iconifiedPhoto(photoId);
    }
}

@Component
@RequiredArgsConstructor
public class CachedPhotosService {
   private static final String RESIZED_PHOTO_CACHE_NAME = "RESIZED_PHOTO_CACHE_NAME";

   private final PhotoRepository photoRepository;
   private final PhotoValidationService photoValidationService;
   private final PhotoOperations photoOperations;

   @Cacheable(cacheNames = RESIZED_PHOTO_CACHE_NAME)
   public PhotoDTO resizedPhoto(String photoId, int width, int height) {
       photoValidationService.validateSize(width, height);

       Photo photo = photoRepository.findById(photoId); // ->

        //-> С Optional обрабатываем исключение, "если фото не найдено"
        //   Optional<Photo> photo = photoRepository.findById(photoId).orElseThrow(
        //        () -> new EntityNotFoundException("Photo not found with id: " + photoId)
        //   );

        //-> Я бы обработал ситуацию "некорректной конвертации/ресайза/некорректных размеров"
       PhotoDTO photoDto = ConversionUtils.convert(photo);
       var resizedPhoto = photoOperations.resize(photoDto, width, height);

       return resizedPhoto; //-> return photoOperations.resize(photoDto, width, height);
   }

   public PhotoDTO iconifiedPhoto(String photoId) {
       return resizedPhoto(photoId, 100, 100);
   }
}
```


------------
## Задача Сбер

```java

// Сделать рефакторинг

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    public ResponseEntity<String> createOrder(@RequestBody OrderRequest request) {
        orderService.processOrder(request);
        return ResponseEntity.ok("ok");
    }
}

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void processOrder(OrderRequest request) {
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setTotal(request.getTotal());
        order.setStatus("NEW");
        orderRepository.save(order);

        kafkaTemplate.send("orders", String.valueOf(order.getId()), "order_created");
    }
}

@Entity
@Data
public class Order {
    @Id
    @GeneratedValue
    private long id;

    private String userId;
    private String status;
    private BigDecimal total;
}

@Data
public class OrderRequest {
    private String userId;
    private BigDecimal total;
}
```


--------------
## Задача Тбанк

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


-------------
## Задача Альфа банк

```java

// провести ревью - всё ли здесь хорошо?
class Scratch {
    private static final Logger log = getLogger(Scratch.class);
    private static volatile boolean ready = false;
    private static final Lock rLock = new ReentrantLock();
    private static final Condition readyCondition = rLock.newCondition();

    private static void waitAndLog() {
        
        try {
            rLock.lock();
            log.info("rLock acquired, ready: {}", ready);
            if(!ready)
                readyCondition.await();
            log.info("ready was awaited: {}", ready);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        finally {
            rLock.unlock();
        }
    }

    public static void main(String[] args) {
        new Thread(Scratch::waitAndLog).start();
        
        try {
            rLock.lock();
            ready = true;
            log.info("signal about ready");
            readyCondition.signal();
        }
        finally {
            rLock.unlock();
        }
    }
}
```


---------------
## Задача WB
```java

// 1. Сделать ревью
// 2. Что будет если упадет сеть в строке "//упала сеть" (и что делать)

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class InterviewService {

    private final ScoreRepository scoreRepository;
    private final TransactionTemplate transactionTemplate;
    private final InterviewScoreMLService interviewScoreMLService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InterviewService(ScoreRepository scoreRepository,
                            TransactionTemplate transactionTemplate,
                            InterviewScoreMLService interviewScoreMLService) {
        this.scoreRepository = scoreRepository;
        this.transactionTemplate = transactionTemplate;
        this.interviewScoreMLService = interviewScoreMLService;
    }

/**
 * Метод считает сколько очков заработал кандидат,
 * сохраняет результат в базу и кидает callback об этом во внешний сервис
 */
    public void process(Candidate c) {
        transactionTemplate.executeWithoutResult(status -> {
            Score s = interviewScoreMLService.compute(c);
                String body = objectMapper.writeValueAsString(Map.of(c.getName(), s));

            Mono<ResponseEntity<Void>> request = WebClient.create()
                    .post()
                    .body(BodyInserters.fromValue(body))
                    .retrieve()
                    .toBodilessEntity();

            scoreRepository.saveScore(s);
        });
        //упала сеть
    }
}

class Candidate {
    private final String name;
    private final List<Integer> tasksSolvedId;

    public Candidate(String name, List<Integer> tasksSolvedId) {
        this.name = name;
        this.tasksSolvedId = tasksSolvedId;
    }

    public String getName() {
        return name;
    }

    public List<Integer> getTasksSolvedId() {
        return tasksSolvedId;
    }
}

class Score {
    private final String name;
    private final int score;

    public Score(String name, int score) {
        this.name = name;
        this.score = score;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }
}
```


----------------
## Задача Газпромбанк

```java
// Сделать ревью
/**
* Метод работает не всегда корректно.
* Как его можно отрефакторить или переписать, и как проверить, что ничего не сломалось?
*
* Метод возвращает индекс элемента в последовательности чисел, который соответствует дубликату.
*
* @param numbers
* @return
* 
* 1,2,3,4,4,5,6
* 4
* 
*/
public int findDuplicateIndex(int... numbers) {

    int[] countArray = new int[nubmers.length];
    for (int i = 0; i < numbers.length; i++) {
        int current = numbers[i];
        if (countArray[current] > 0) {
            return i;
        } else {
            countArray[current] += 1;
        }
    }
    throw new CustomException("Duplicate not found!");
}
```


-----------
## Задача Магнит

```java
// Сделать рефакторинг

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public final class Cat4 {
    
    private static final int jumpsCount = 0;

    private final DataSource dataSource;
    private final String name;

    public Cat4(String name, DataSource dataSource) {
        this.name = name;
        this.dataSource = dataSource;
    }

    public void doJumps(int jumpsCount) {
        for (int i = 0; i < jumpsCount; i++) {
            new Thread(new Runnable() {
                public void run() {

                    doJump();
                
                }
            }).start();
        }
    }

    public void doJump() {
      
        jumpsCount++;
        Logger.getLogger(Cat4.class.getName()).fine("Jump!");
    }

    public void doMeow() {
        Logger.getLogger(Cat4.class.getName()).fine("Meow!");
    }

    public void doQuery(byte[] parameters) throws SQLException {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = dataSource.getConnection();
            stmt = conn.createStatement();
            stmt.execute("insert into cats (name) values(" + new String(parameters) + ")");
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
      
    }

    protected int getJumpsCount() {
        int result = jumpsCount;
        jumpsCount = 0;
        return result;
    }

    public void finalize() {
        jumpsCount = 0;
    }

    @Override
    public boolean equals(Object otherCat) {
        if (otherCat == this) {
            return true;
        }
        if (!(otherCat instanceof Cat4)) {
            return false;
        }
        Cat4 cat4 = (Cat4) otherCat;
        return name.equals(cat4.name);
    }

    @Override
    public String toString() {
        try {
            return "Cat4{" +
                "name='" + name + '\'' +
                ", url=" + dataSource.getConnection().getMetaData().getURL() +
                '}';
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
```


--------------------------
## Задача Точка банк (стажировка)

```java

// Провести код-ревью

boolean containsStringInData(String csvFile, String str) throws IOException {

 	BufferedReader reader = new BufferedReader(new FileReader(csvFile));
	ArrayList<String> list = new ArrayList();

	String line;
	while ((line = br.readLine()) != null) {
			 list.add(line);
	}

	boolean result;
	for (String s : list) {
		if (s == str) {
			result = true;
		}
	}

	return result;
}

```


---------------
## Задача СБЕР

```java
// Сделать рефакторинг кода

@Transactional
 public void process(String oldName, String newName) { 
     Long id = exec("select id from file where name='" + oldName + "'"); //выполнение запроса к БД 
     insert 
     processFile(oldName, newName); //переименование файла на диске
    exec("update file set name='" + newName + "' where id = " + id);  
 }

```