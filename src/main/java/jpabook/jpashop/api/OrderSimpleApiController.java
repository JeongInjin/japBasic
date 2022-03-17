package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
    private final OrderSimpleQueryRepository orderSimpleQueryRepository;

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
    /**
     * 다시 공부겸 보니 좀 더 와닿네..
     * 엔티티를 반환하지말고..필요데이터만 뽑아서 반환하자.
     * EAGER 사용으로인해 피해를 실제로 받고있어서 고통스럽다..
     */
    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName(); //lazy 강제 초기화
            order.getDelivery().getAddress(); //lazy 강제 초기화
        }

        return all;
    }

    /**
     * V2. 엔티티를 조회해서 DTO로 변환(fetch join 사용X)
     * - 단점: 지연로딩으로 쿼리 N번 호출
     */
    @GetMapping("/api/v2/simple-orders")
    public List<SimpleOrderDto> orderV2() {
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());
//        orders.stream()
//                .map(o -> new SimpleOrderDto(o))
//                .collect(Collectors.toList());

        List<SimpleOrderDto> result = orders.stream()
                .map(SimpleOrderDto::new)
                .collect(toList());

        return result;
    }

    /**
     * V3. 엔티티를 조회해서 DTO로 변환(fetch join 사용O)
     * - fetch join으로 쿼리 1번 호출
     * 참고: fetch join에 대한 자세한 내용은 JPA 기본편 참고(정말 중요함)
     * 이방법이 좋긴한데, 우리회사 테이블 컬럼은 왜이렇게 많지...고민이네 허매..
     */
    @GetMapping("/api/v3/simple-orders")
    public List<SimpleOrderDto> orderV3() {
        List<Order> orders = orderRepository.findAllWithMemberDelivery();
        List<SimpleOrderDto> result = orders.stream()
                .map(SimpleOrderDto::new)
                .collect(toList());

        return result;
    }

    /*
     * 성능이 좋을지는 모르으나, 재활용성이 힘들다.
     * DTO 를 조회하였기 때문에 데이터 변경이 힘들다.
     * ---
     * 쿼리방식 선택 권장 순서
     * 1.우선 엔티티를 DTO 로 변환하는 방법을 선택한다. -> V2
     * 2.필요하면 페치 조인으로 성능을 최적화 한다. -> 대부분의 성능 이슈가 해결된다. -> V3
     * 그래도 안되면 DTO 로 직접 조회하는 방법을 사용한다. -> V4
     * 최후의 방법은 JPA가 제공하는 네이티브 SQL 이나 스프링 JDBC Template 를 사용하여 직접 SQL 을 직접 사용한다.
     * */
    @GetMapping("/api/v4/simple-orders")
    public List<OrderSimpleQueryDto> ordersV4() {
        return orderSimpleQueryRepository.findOrderDtos();
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
