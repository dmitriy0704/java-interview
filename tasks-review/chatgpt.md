# Задача от ChatGpt

```java


@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    public ResponseEntity createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.ok(order);
    }
}

@Service
public class OrderService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getStock() < request.getQuantity()) {
            throw new RuntimeException("Not enough stock");
        }

        product.setStock(product.getStock() - request.getQuantity());
        productRepository.save(product);

        Order order = new Order();
        order.setProductId(product.getId());
        order.setQuantity(request.getQuantity());
        order.setUserId(request.getUserId());
        order.setStatus("NEW");

        orderRepository.save(order);

        String paymentResponse = restTemplate.postForObject(
                "http://payment-service/pay",
                new PaymentRequest(order.getId(), request.getUserId(), product.getPrice()),
                String.class
        );

        if (!"SUCCESS".equals(paymentResponse)) {
            order.setStatus("FAILED");
            orderRepository.save(order);
            throw new RuntimeException("Payment failed");
        }

        order.setStatus("PAID");
        orderRepository.save(order);

        return order;
    }
}

public interface ProductRepository extends JpaRepository<Product, Long> {
}

public interface OrderRepository extends JpaRepository<Order, Long> {
}

public class CreateOrderRequest {
    private Long productId;
    private int quantity;
    private Long userId;
}

```
