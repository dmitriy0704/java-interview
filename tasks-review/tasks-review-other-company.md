# Задачи на ревью из других компаний





## Задача Alfa Bank

```java
// Сделать рефакторинг (задача из одного красного банка)
@RestController
@RequestMapping(path = "/contract")
public class ContractController {

    private final ContractRepository repository;
    private final ContractService contractService;

    public ContractController(ContractRepository repository, ContractService contractService) {
        this.repository = repository;
        this.contractService = contractService;
    }

    @PostMapping(path = "/{contractId}/price")
    public void updatePrice(@PathVariable Long contractId) {
        Contract contract = repository.findById(contractId).orElseThrow();
        contractService.setContract(contract);
        contractService.updatePrice();
    }
}

@Service
public class ContractService {
    
    private final PriceService priceService;
    
    private Contract contract;
    
    public ContractService(PriceService priceService) {
        this.priceService = priceService;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public void updatePrice() {
        if (contract != null) {
            BigDecimal price = priceService.calcPrice(contract);
            contract.setPrice(price);
        }
    }
}

class Contract {

    private BigDecimal price;

    public BigDecimal getPrice() {
        return this.price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}

// Решение:
/

@RestController
@RequestMapping(path = "/contract")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @PostMapping(path = "/{contractId}/price")
    public void updatePrice(@PathVariable Long contractId) {
        contractService.updatePrice(contractId);
    }
}
// Убрали логику из мтеода updatePrice

@Service
public class ContractService {
    
    private final PriceService priceService;
    private final ContractRepository repository;
    
    public ContractService(PriceService priceService, ContractRepository repository) {
        this.priceService = priceService;
        this.repository = repository;
    }

    public void updatePrice(Long contractId) {
        Contract contract = repository.findById(contractId).orElseThrow(() -> new IllegalArgumentException("Contract not found"));
        BigDecimal price = priceService.calcPrice(contract);
        contract.setPrice(price);
        repository.save(contract); 
    }
}

// Убрали состояние Contract из сервиса (некорректная работа в многопоточной среде)
// Перенесли поиск контракта из контроллера в сервис
// При необходимости еще можно добавить логи

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


---
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


## Задача Axbit Group

```java

@Service
@RequiredArgsConstructor
public class DocumentProcessService {

    private final DocumentRepo repo;

    @Async
    @Transactional
    void revokeDocument(List<Document> docs) {
        List<Integer> id_list = docs.stream().map(Document::getId).collect(Collectors.toList());
        List<Document> list = repo.findAllById(id_list);

        Optional<Document> doc = Optional.empty();
        for (int i = 0; i < list.size(); i++) {
            Integer statusId = list.get(i).getStatusId();
            if (statusId == 4) {
                doc = Optional.of(list.get(i));
                break;
            }
        }

        if (doc.isPresent()) return;

        System.out.println("Processing document id: " + doc.get().getId());

        int processed = process(doc.get());

        if (processed == 1) {
            // Сохраняем документ, если он был обработан
            repo.save(doc.get());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int process(Document doc) {
        Integer result = 0;
        doc.getAssistants().forEach(a -> a.setFullName("dismissed"));
        // some logic:
        // if document is processed returns 1, else 0
        return result;
    }
}
```


## Иннотех

```java


// Ниже приведен код обновления статуса заказа. 
// Необходимо найти проблемы и внести изменения в приведенный код.

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    private final NotificationService notificationService;

    @Autowired
    public OrderService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void updateOrderStatus(Long orderId, String newStatus) {
        Order order = orderRepository.findById(orderId).get();

        if (newStatus.equals("COMPLETED")) {
            order.setStatus("COMPLETED");
            orderRepository.save(order);
            notificationService.notifyUser(order.getUserId(), "Your order is completed");
        } else if (newStatus.equals("CANCELLED")) {
            order.setStatus("CANCELLED");
            orderRepository.save(order);
            notificationService.notifyUser(order.getUserId(), "Your order is cancelled");
        } else if (newStatus.equals("PENDING")) {
            order.setStatus("PENDING");
            orderRepository.save(order);
        } else if (newStatus.equals("IN_PROGRESS")) {
            order.setStatus("IN_PROGRESS");
            orderRepository.save(order);
        } else {
            throw new IllegalArgumentException("Unsupported status: " + newStatus);
        }
    }
}


// Решение



@Service
@RequiredArgsConstructor
public class OrderService {

    private static final List<String> SUPPORTED_STATUSES = List.of("COMPLETED", "CANCELLED", "PENDING", "IN_PROGRESS");

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    public void updateOrderStatus(Long orderId, String newStatus) {
        if (!SUPPORTED_STATUSES.contains(newStatus)) {
            throw new UnsupportedOrderStatusException(newStatus);
        }

        Order order = getOrder(orderId);

        order.setStatus(newStatus);
        orderRepository.save(order);

        if (newStatus.equals("COMPLETED") || newStatus.equals("CANCELLED")) {
            notificationService.notifyUser(order.getUserId(), "Your order is completed");
        }
    }

    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order with id '%s' not found".formatted(orderId)));
    }

}
```
