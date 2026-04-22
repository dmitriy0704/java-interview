# Задачи на ревью

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



------------
## Задача sber#2

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


------------
## Задача sber#3

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


---------------
## Задача sber#4

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

