# Задачи на ревью

## Сбер

```java
// @Service -> не указана аннотация @Service
class UserService { //-> Класс, не объявлен как public 
// TODO: Написать юнит тест для класса
    
// Нельзя внедряемые бины создавать через new(),
// теряется контроль управления и прокси
    private UserRepository repo = new UserRepository();
    
    // Внедрение через конструктор лучше потому что:
    // 1. Гарантирует, что зависимость будет установлена один раз при запуске приложения 
    // и не изменится (случайно или намеренно) во время работы. 
    // 2. При использовании конструктора объект не может быть создан без его зависимостей. 
    // Spring просто не запустит приложение, если не найдет нужный бин.
    private  RegionService regionService; //-> Не указан final
    
    
    public UserService(final ApplicationContext appCtx) {
        // Строки не используются. Не использовать магические константы
        // 1. Имя бина может измениться
        // 2. Сложно тестировать: придется мокировать весь контекст
        // 3. При внедрении через конструктор Spring обнаруживает циклические зависимости 
        regionService = appCtx.getBean("regionService", RegionService.class);
    }

    //-> regionName заменить на Enum
    public void processNewUsers(final List<User> users, String regionName) {
        // …
        
        //1.  Вызов транзакционного метода из нетранзакционного, Лучше использовать Self Injection
        //2.  Перезапись параметров: users = createUsers(users); — параметр users помечен как final, 
        // этот код просто не скомпилируется, т.к. createUsers() переназначает значение переменной
        users = createUsers(users);
        // …
        users.stream()
                //1. forEach() а не foreach()
                //2. Побочные эффекты в Stream: плохой тон, проблема производительности: 
                // для 1000 пользователей будет выполнено 1000 запросов.
            .foreach(u -> regionService.updateRegionLink(u.getId(), regionName));
            // при сложной логике в RegionService обрабатывать по одному пользователю
            // forEach(u -> regionService.updateRegionLink(u, regionName));


    }

    @Transactional
    public List<User> createUsers(final List<User> users) { //-> Написать один инсерт
        
        // Проблема N+1 в БД: В методе createUsers вы вызываете save в цикле. 
        // Это порождает множество мелких запросов. У Spring Data JPA есть метод saveAll(), 
        // который работает гораздо эффективнее.
        return users.stream()
                .map(u -> repo.saveUser(u))
                .collect(Collectors.toList());
    }

// Неиспользуемый метод
    private User getUser(final int userId) {
        return repo.getUserById(userId);
    }
}

///-> Исправлено:

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)// Обязательно для запросов UPDATE/DELETE
    @Transactional // Нужно, если метод вызывается вне другой транзакции
    @Query("UPDATE User u SET u.regionName = :regionName WHERE u.id IN :ids")
    void updateRegionForUsers(@Param("ids") List<Long> ids, @Param("regionName") String regionName);
}

// Батчинг:
// # Включаем батчинг (стандарт — от 20 до 50)
// spring.jpa.properties.hibernate.jdbc.batch_size=50

// # Заставляем Hibernate группировать похожие запросы
// spring.jpa.properties.hibernate.order_inserts=true
// spring.jpa.properties.hibernate.order_updates=true

// # (Опционально) Для статистики, чтобы увидеть батчи в логах
// spring.jpa.properties.hibernate.generate_statistics=true
// Генерация ID у сущности User не IDENTITY (лучше SEQUENCE), 
// иначе батчинг на вставку не включится.


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





------------
## Сбер

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
## Райффайзен

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
## Альфа банк

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
## Сбер

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
## Тбанк

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
## Альфа банк

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
## WB
``` java

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
## Газпромбанк

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





------------
## web tech

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





-----------
## Магнит

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
## Точка банк (стажировка)

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
## СБЕР

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
