package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.query.OrderFlatDto;
import jpabook.jpashop.repository.order.query.OrderItemQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.*;
/* oneToMany 관겨시 조회*/
/*
 * 엔티티를 조회해서 그대로 반환 : V1
 * 엔티티를 조회 후 DTO 로 변환 : V2
 * 페치 조인으로 쿼리 수 최적화 : V3
 * 컬렉션 페이징과 한계 돌파 : V3.1
 *      컬렉션은 페치 조인시 페이징 불가능
 *      ToOne 관계는 페치 조인으로 쿼리 수 최적화
 *      컬렉션은 페치 조인 대신에 지연 로딩을 유지하고, `default_batch_fetch_size`, `@BatchSize` 로 최적화
 * DTO 직접 조회 :
 * JPA 에서 DTO 를 직접 조회 : V4
 * 컬렉션 조회 최적화 : 일대다 관계인 컬렉션은 IN 절을 활용해서 메모리에 미리 조회해서 최적화 : V5
 * 플랫 데이터 최적화 : JOIN 결과를 그대로 조회 후 애플리케이션에서 원하는 모양으로 직접 변환 : V6
 * */

/*
 * 권장 순서
 * 1.엔티티 조회 방식으로 우선 접근
 *      1.1 페치조인으로 쿼리 수를 최적화
 *      1.2 컬렉션 최적화
 *          1.2.1 패아징 필요 `default_batch_fetch_size`, `@BatchSize` 로 최적화
 *          1.2.2 페이징 필요 X -> 페치 조인 사용
 * 2.엔티티 조회 방식으로 해결이 안되면 DTO 조회 방식 사용
 * 3.DTO 조회 방식으로 해결이 안되면 NativeSQL or 스프링 jdbcTemplate
 ---
     * 쿼리방식 선택 권장 순서
     * 1.우선 엔티티를 DTO 로 변환하는 방법을 선택한다. -> V2
     * 2.필요하면 페치 조인으로 성능을 최적화 한다. -> 대부분의 성능 이슈가 해결된다. -> V3
     * 그래도 안되면 DTO 로 직접 조회하는 방법을 사용한다. -> V4, V5
     * 최후의 방법은 JPA 가 제공하는 네이티브 SQL 이나 스프링 JDBC Template 를 사용하여 직접 SQL 을 직접 사용한다.
 * ---
 * 참고 : 엔티티 조회 방식을 추천하는 이유는 `default_batch_fetch_size`, `@BatchSize`
 * 같이 코드를 거의 수정하지 않고, 옵션만 약간 변경해서, 다양한 성능 최적화를 시도할 수 있다.
 * 반면에 DTO 를 직접 조회하는 방식은 성능 최적화, 최적화 방식을 변경할 때 많은 코드를 변경해야 한다.
 * ---
 * => 엔티티 조회만으로 성능이 느리다면 redis 나 local cache 를 써야하지 않을까?
 */

/*
 * DTO 조회 방식의 선택지 - > 결론 : V5를 추천, 단건이면 V4로..
 * DTO 로 조회하는 방법도 각각 장단점이 있다. V4, V , V6 에서 단순하게 쿼리라 1번 실행된다고 V6 가 항상 좋은 방법인 것은 아니다.
 * V4는 코드가 단순하다.특정 주문 한건만 조회하며 이 방식을 사용해도 성능이 잘 나온다. 예를 들어서 조회한 Order 데이터가 1건이면 OrderItem 을 찾기 위한 쿼리도 1번만 실행하면 된다.
 * V5는 코드가 복잡하다. 여러 주문을 한꺼번에 조회하는 경우에는 V4 대신에 이것을 최적화한 V5 방식을 사용해야 한다.
 *      예를 들어서 조회한 Order 데이터가 1000건인데, V4 방식을 그대로 사용하면, 쿼리가 총 1 + 1000번 실행 된다.
 *      여기서 1은 Order 를 조회한 쿼리고, 1000은 조회된 Order 의 row 수다.
 *      V5 방식으로 최적화 하면 쿼리가 총 1 + 1 번만 실행된다.상황에 따라 다르겠지만 운영 환경에서 100배 이상의 성능 차이가 날 수 있다.
 * V6는 완전히 다른 접근 방식이다. 쿼리 한번으로 최적화 되어서 좋아 보이지만, Order 를 기준으로 페이징이 불가능 하다.
 *      실무에서 이정도 데이터면 수백, 수천건 단위로 페이징 처리가 꼭 필요하므로, 이경우 선택하기는 어려운 방법이다.
 *      그리고 데이터가 많으면 중복 전송이 증가해서 V5 와 비교해서 성능 차이도 미비하다.
 */


/**
 * V1. 엔티티 직접 노출
 * - 엔티티가 변하면 API 스펙이 변한다.
 * - 트랜잭션 안에서 지연 로딩 필요
 * - 양방향 연관관계 문제
 * V2. 엔티티를 조회해서 DTO로 변환(fetch join 사용X)
 * - 트랜잭션 안에서 지연 로딩 필요
 * V3. 엔티티를 조회해서 DTO로 변환(fetch join 사용O)
 * - 페이징 시에는 N 부분을 포기해야함(대신에 batch fetch size? 옵션 주면 N -> 1 쿼리로 변경 가능)
 * V4. JPA에서 DTO로 바로 조회, 컬렉션 N 조회 (1 + N Query)
 * - 페이징 가능
 * V5. JPA에서 DTO로 바로 조회, 컬렉션 1 조회 최적화 버전 (1 + 1 Query)
 * - 페이징 가능
 * V6. JPA에서 DTO로 바로 조회, 플랫 데이터(1Query) (1 Query)
 * - 페이징 불가능...
 */
@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    /**
     * V1. 엔티티 직접 노출
     * - Hibernate5Module 모듈 등록, LAZY=null 처리
     * - 양방향 관계 문제 발생 -> @JsonIgnore
     */
    @GetMapping("/api/v1/orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName();
            order.getDelivery().getAddress();
            List<OrderItem> orderItems = order.getOrderItems();
//            for (OrderItem orderItem : orderItems) {
//                orderItem.getItem().getName();
//            }
            orderItems.stream().forEach(o -> o.getItem().getName());
        }
        return all;
    }

    @GetMapping("/api/v2/orders")
    public List<OrderDto> ordersV2() {
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());
//        for (Order order : orders) {
//            for (OrderItem item : order.getOrderItems()) {
//                System.out.println("count = " + item.getCount());
//            }
//        }

//        List<OrderDto> collect = orders.stream()
//                .map(o -> new OrderDto(o))
//                .collect(Collectors.toList());

        List<OrderDto> collect = orders.stream()
                .map(OrderDto::new)
                .collect(toList());

        return collect;
    }

    /*
     * fetch join 으로 안하여 sql 이 1번 실행
     * `distinct` 를 사용하는 이유
     *   ㄴ 1:N 조인 으로 인한 row 수 증가로 distinct 로 중복을 걸러준다. sql 로 조회시에는 N 개의 row 가 조회되지만 jpa 에서는 Key 같아 ref 가 같은걸로 인식하여 중복을 걸러준다.
     * 치명적인 단점 : 페이징 불가능.
     * collection fetch 조인은 1개만 사용할 수 있다. collection 둘 이상에 fetch 조인을 사용할 시 데이터가 부정합 할 수 있다.
     * */
    @GetMapping("/api/v3/orders")
    public List<OrderDto> ordersV3() {
        List<Order> orders = orderRepository.findAllWithItem();

        //repository 에 distinct 가 없으면 2 * 2 개가 중복없이 노출 된다.
        for (Order order : orders) {
            System.out.println("order ref = " + order + " id = " + order.getId());
        }
        List<OrderDto> result = orders.stream()
                .map(OrderDto::new)
                .collect(toList());

        return result;
    }

    /**
     * V3.1 엔티티를 조회해서 DTO로 변환 페이징 고려
     * - ToOne 관계만 우선 모두 페치 조인으로 최적화
     * - 컬렉션 관계는 hibernate.default_batch_fetch_size, @BatchSize로 최적화
     * -----------------------------------------------------------------------------------------------------
     * - hibernate.default_batch_fetch_size 의 크기 정할시 참조
     * - 100 ~ 1000 사이를 권장.
     * - DB 에 따라 in 절 파라미터를 1000개로 제한하도 한다.
     * - 100 이나, 1000 이나 결국 데이터를 로딩해야 하므로 메모리 사용량은 같다
     * - 1000으로 설정하는 것이 성능상 가장 좋으나, DB 혹은 애플리케이션 등이 순간 부하를 어디까지 견딜 수 있는지로 결정하면 된다.
     * -----------------------------------------------------------------------------------------------------
     */
    @GetMapping("/api/v3.1/orders")
    public List<OrderDto> ordersV3_page(@RequestParam(value = "offset", defaultValue = "0") int offset,
                                        @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<Order> orders = orderRepository.findAllWithMemberDelivery(offset, limit);

        //repository 에 distinct 가 없으면 2 * 2 개가 중복없이 노출 된다.
        for (Order order : orders) {
            System.out.println("order ref = " + order + " id = " + order.getId());
        }
        List<OrderDto> result = orders.stream()
                .map(OrderDto::new)
                .collect(toList());

        return result;
    }

    /**
     * query : 루트 1번, 컬렉션 N 번 실행
     * ToOne(N:1, 1:1) 관계들을 먼저 조회하고, ToMany(1:N) 관계는 각각 별도로 처리한다.
     * ToMany 관계는 조인하면 row 수가 증가하기 때문에.
     * row 수가 증가하지 않는 ToOne 관계는 조인으로 최적하기 쉬우므로 한번에 조회, ToMany 관계는 최적화 하기 어려워 findOderItems() 같은 별도 메서드로 조회한다.
     */
    @GetMapping("/api/v4/orders")
    public List<OrderQueryDto> ordersV4() {
        return orderQueryRepository.findOrderQueryDtos();
    }

    @GetMapping("/api/v5/orders")
    public List<OrderQueryDto> ordersV5() {
        return orderQueryRepository.findAllByDto_optimization();
    }

    /**
     * 쿼리는 1번이 조회되지만 디비에서 애플리케이션에 전달하는 데이터에 중복 데이터가 추가되므로 v5 보다 느릴 수 있다.
     * 애플리케이션에서 추가 작업이 크다..
     * 페이징 처리가 불가하다..데이터 중복이로 인하여 원하는 페이징 불가.
     */
    @GetMapping("/api/v6/orders")
    public List<OrderQueryDto> ordersV6() {
        List<OrderFlatDto> flats = orderQueryRepository.findAllByDto_flat();

        //OrderQueryDto parse...
        return flats.stream()
                .collect(groupingBy(o -> new OrderQueryDto(o.getOrderId(), o.getName(), o.getOrderDate(), o.getOrderStatus(), o.getAddress()),
                        mapping(o -> new OrderItemQueryDto(o.getOrderId(), o.getItemName(), o.getOrderPrice(), o.getCount()), toList())
                )).entrySet().stream()
                .map(e -> new OrderQueryDto(e.getKey().getOrderId(), e.getKey().getName(), e.getKey().getOrderDate(), e.getKey().getOrderStatus(), e.getKey().getAddress(), e.getValue()))
                .collect(toList());
    }

    @Data
    static class OrderDto {

        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;
        private List<OrderItemDto> orderItems;

        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
            orderItems = order.getOrderItems().stream()
                    .map(orderItem -> new OrderItemDto(orderItem))
                    .collect(toList());
        }
    }

    @Data
    static class OrderItemDto {

        private String itemName; //상품 명
        private int orderPrice;  //주문 가격
        private int count;       //주문 수량

        public OrderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }
    }
}