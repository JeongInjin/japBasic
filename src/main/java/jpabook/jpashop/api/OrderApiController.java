package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * V1. 엔티티 직접 노출
 * - 엔티티가 변하면 API 스펙이 변한다.
 * - 트랜잭션 안에서 지연 로딩 필요
 * - 양방향 연관관계 문제
 * <p>
 * V2. 엔티티를 조회해서 DTO로 변환(fetch join 사용X)
 * - 트랜잭션 안에서 지연 로딩 필요
 * V3. 엔티티를 조회해서 DTO로 변환(fetch join 사용O)
 * - 페이징 시에는 N 부분을 포기해야함(대신에 batch fetch size? 옵션 주면 N -> 1 쿼리로 변경 가능)
 * <p>
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