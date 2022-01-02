package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * xToOne(ManyToOne, OneToOne) 관계 최적화
 * Order
 * Order -> Member
 * Order -> Delivery
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;

    /**
     * 무한 루프테 빠진다.
     * Order Entity 애서 Member 참조 Member Entity 에서 Order 참조......
     * 해결 방법 => 양방향 걸리는 것 중 하나에 @JsonIgnore 를 해주어야 한다.
     * => Type definition error : [simple type class ... bytebuddy ... 등 에러가 나타나는데 이는 Order Entity 에 Member 객체에 fetch = LAZY 로 되어있는데
     * 이는 지연로딩으로 써 지연로딩은 진짜 new 해서 Member 객체를 가져오지 않고, (지연로딩 - DB 에서 가져오지 않는다. null 로 넣어두기진 못해서 가짜 프록시 맴버를 상속받아 집어 넣어논다 -> bytebuddy)
     * 가짜 객체를 넣은 상태에서 실제 Member 객체를 필요 시 sql 을 보내어 데이터를 가져온다.
     * json 으로 치환시 Member 객체가 byteBuddy 식으로 파싱이 되지 않기때문에 에러가 나타난다.
     * 해결책 -> 	implementation 'com.fasterxml.jackson.datatype:jackson-datatype-hibernate5' 추가 하고 @Bean 등록한다 -> Hibernate5Module
     * 해당 방법은 쓰지 않는다. Entity 는 노출 하지 않느다.
     */
    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName();
            order.getDelivery().getAddress();
        }

        return all;
    }

    @GetMapping("/api/v2/simple-orders")
    public List<SimpleOrderDto> orderV2() {
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());

        List<SimpleOrderDto> result = orders.stream()
                .map(SimpleOrderDto::new)
                .collect(toList());

        return result;
    }

    @Data
    static class SimpleOrderDto {

        private Long orderId;
        private String name;
        private LocalDateTime orderDate; //주문시간
        private OrderStatus orderStatus;
        private Address address;

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName(); //LAZY 초기화
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress(); //LAZY 초기화
        }
    }
}
