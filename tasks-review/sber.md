# Задачи на ревью

## Задача sber#1 (разобрано, см. task-review-success.md)

```java

class UserService { //-> Класс, не объявлен как public 
    private UserRepository repo = new UserRepository();
    private  RegionService regionService; //-> Не указан final

    public UserService(final ApplicationContext appCtx) {
        regionService = appCtx.getBean("regionService", RegionService.class);
    }

    public void processNewUsers(final List<User> users, String regionName) {
        // …
        users = createUsers(users);
        // …
        users.stream()
            .foreach(u -> regionService.updateRegionLink(u.getId(), regionName);
    }

    @Transactional
    public List<User> createUsers(final List<User> users) { 
        return users.stream()
                .map(u -> repo.saveUser(u))
                .collect(Collectors.toList());
    }

    private User getUser(final int userId) {
        return repo.getUserById(userId);
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


## Задача sber#5

```java
// Сбер

//Сделать рефакторинг

@RestController
@Transactional
@RequiredArgsConstructor
public class ProcessActionController {
    private final PushHandler pushHandler;
    private final SmsHandler smsHandler;

    
    @PostMapping(value = "/doAction", consumes = {MediaType.ALL_VALUE})
    public ResponseEntity<Object> doIt(@ResponseBody @Valid ActionRequ request) {
        Action a = request.getaction();
        if (a == Action.SEND_PUSH)) {
            return pushHandler.process(request);
        } 
        if (a == Action.SEND_SMS) {
            return smsHandler.process(request);
        }
        return ResponseEntity.ok().body(UserRs.getDefaultResponce("It's default response. "));
    }
}

@Data
class ActionRequ {
    @NotNull
    private Integer id;
    @NotNull
    private Action action;
    //...
}  
```


## Задача sber#6

```java
// Сбер
// Сервис определения типа мяча по ID мяча
// Известно, что:
// * Вызываемый сервис BallPropertiesService возвращает список свойств (List<BallProperty>) для одного мяча. Размер списка от 0 до 2^30.
// * Значения BallProperty.code от 0 до 9 описывают размер мяча (т.е. 0 - микроскопический, 9 - гигантский).
// * Значения BallProperty.code от 100 до 129 описывают материал мяча.
// * Значения BallProperty.code в других диапазонах существуют, но бизнес ценности в данном случае не несут.
// * Если в списке:
//    - пришел проперти с кодом 7, то считаем, что мяч баскетбольный
//    - пришел проперти с кодом 6, то считаем, что мяч футбольный
//    - пришел проперти с кодом 5, то считаем, что мяч тенисный
//    - пришел проперти с кодом 5 и еще проперти 102, то считаем, что это ядро
//    - не пришло что-то из выше описанного, то считаем, что это мяч для пингпонга
// * Гарантируется, что если в списке присутствует BallProperty с кодом из какого-то диапазона, то не может
//   быть в этом же списке BallProperty с другим кодом из этого диапазона (т.е. в одном списке не будет 
//   одновременно кодов 1 и 2)
// Что не так?

@Service
@RequiredArgsConstructor
public class BallTypeService {
    private final BallPropertiesService ballPropertiesService;

    public BallType getType(Long ballId) {
        List<BallProperty> ballProperties = ballPropertiesService.getBallProperties(ballId);
        ballProperties.sort(Comparator.comparing(BallProperty::getCode).reversed());
        return resolveType(ballProperties);

    }

    private BallType resolveType(List<BallProperty> ballProperties) {
        long propertyNum = ballProperties.get(0).getCode();
        long propertyNext = ballProperties.get(1).getCode();
        if (propertyNum == 102 && propertyNext == 5) {
            return BallType.CANNON_BALL;
        }
        if (propertyNum == 7) {
            return BallType.BASKET_BALL;
        }
        if (propertyNum == 6) {
            return BallType.FOOT_BALL;
        } 
        if (propertyNum == 5) {
            return BallType.TENNIS_BALL;
        }
        return BallType.PING_PONG_BALL;
    }
}

public enum BallType {
    BASKET_BALL,
    FOOT_BALL,
    CANNON_BALL,
    TENNIS_BALL,
    PING_PONG_BALL;
}

// библиотечный код
@Data
public class BallProperty {
    private Integer group;
    private Integer code;
    private String description;
}

public interface BallPropertiesService {
    List<BallProperty> getBallProperties(Long itemId);
}#sber
  Прислать задачу | Подписаться
```



## Задача sber#7

```java
// СБЕР


@Service
@RequiredArgsConstructor
@Sl4j
class ProductServiceImpl implements ProductService {
    private final ProductRepository repository;
    private OtherService otherService;
    private Map<String, String> dictionary;

    @PostConstruct
    @Transactional
    public void postConstruct(){
        dictionary = otherService.getDictionary();
    }

    @Autowired
    public void setOtherService(OtherService otherService) {
        this.otherService = otherService;
    }


    /** Метод возвращает список обработанных Продуктов
     *
     * @param productIds
     * @return
     */
    @Transactional
    public List<ProductProcessResult> processProducts(List<Long> productIds){
        productIds.stream().map(productIds.mapToObject)
        //TODO
    }

    @Transactional
    public Product getProduct(Long id){

        if(id == null){
            throw new UnsupportedOperationException("Не поддерживается");
        }
        return repository.findById(id);
    }

    public ProductProcessResult process(Product product) throws IOException
        if(product.getProductStatus() == PROCESSED){
        throw new IOException("нельзя обрабатывать продукт в состоянии PROCESSED");
    }
        otherService.process(product);
        product.setProductState(PROCESSED);
        return product;
}
}

@Getter
@HashcodeAndEquals
public class Product {
    enum ProductStatus{
        NEW, PROCESSED
    }

    private int id;
    private ProductStatus productStatus;
    private String name;
    private Double price;
    private List<String> tags;

    Product(int id, ProductStatus productStatus, String name, Double price, List<String> tags){
        this.id = id;
        this.productStatus = productStatus;
        this.name = name;
        this.price = price;
        this.tags = tags;
    }

}#sber | Прислать задачу | Подписаться
```
