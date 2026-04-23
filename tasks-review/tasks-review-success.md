# Задачи с проведенным ревью

## Задача sber#1

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
        users.forEach(user -> {
            user.setRegionName(regionName);
            repo.save(user); // Пока только складываем в очередь Hibernate

            // Каждые 50 записей сбрасываем данные в БД и чистим память
            if (counter.incrementAndGet() % batchSize == 0) {
                entityManager.flush(); // Отправить батч в базу
                entityManager.clear(); // Очистить кэш Hibernate, чтобы память не кончалась
            }
        });


        /**
         * Использование AtomicInteger здесь обусловлено тем, как работают лямбда-выражения в Java.
         Внутри forEach (лямбды) вы можете использовать переменные извне только в том случае, если они final или effectively final (то есть их значение не меняется после инициализации).
         ## Почему нельзя обычный int?
         Если вы объявите int counter = 0;, а внутри лямбды попытаетесь сделать counter++, компилятор выдаст ошибку:
         "Variable used in lambda expression should be final or effectively final".
         Java запрещает изменять простые переменные внутри лямбд, чтобы избежать проблем с потокобезопасностью и областью видимости.
         ## Почему AtomicInteger решает проблему?

         1. Обход ограничения final: Сама ссылка на объект AtomicInteger остается неизменной (final), а вот внутреннее состояние объекта (число внутри него) мы можем менять с помощью метода incrementAndGet().
         2. Потокобезопасность: Если вы решите сделать стрим параллельным (userStream.parallel()), обычный int начал бы «врать» из-за состояния гонки (race condition). AtomicInteger гарантирует, что инкремент будет выполнен корректно даже в несколько потоков.

         Лайфхак:
         Если вы на 100% уверены, что поток будет только один, вместо AtomicInteger иногда используют массив из одного элемента: int[] counter = {0};, но это считается «грязным» кодом. AtomicInteger — более стандартный и понятный путь.

         */
        
        
    }
}


```


## Задача tbank#1

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
        
        var ticket = ticketRepository.findById(ticketId);
        
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
