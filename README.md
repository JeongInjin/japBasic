---
* 엔티티에는 가급적 Setter 를 사용하지 말자.
  * Setter 가 모두 열려있다면, 변경 포인트가 너무 많아서 유지보수가 어렵다.
  

* 모든 연관관계는 지연로딩으로 설정!
  * 즉시로딩(EAGER)는 예측이 어렵고, 어떤 SQL 이 실행될지 추적하기가 어렵다. 특히 JPQL 을 실행 할때 N + 1 문제가 발생한다.
  * 실무에서 모든 연관관계는 지연로딩(LAZY)으로 설정해야 한다.
    * 연관된 Entity 를 함께 DB 에서 조회해야 하면, fetch join 또는 Entity Graph 기능을 사용한다.
    * @XToOne(OneToOne, ManyToOne) 관계는 기본이 즉시로딩이므로 직접 지연로딩으로 설정해야 한다.
      * XToMany -> default : LAZY
      * @ManyToOne(fetch = FetchType.LAZY) -> static import -> fetch = LAZY


* 컬렉션은 필드에서 초기화 하자.
  * 컬렉션은 필드에서 바로 초기화 하는 것이 안전하다
    * NULL 문제에서 안전하다.
    * 하이버네이트는 인테테를 영속화 할 때, 컬렉션을 감싸서 하이버네이트가 제공하는 내장 컬렉션으로 변경한다.
    * 만약 getOrders() 처럼 임의의 메서드에서 컬렉션을 잘못 생성하면 하이버네이트 내부 메커니즘에 문제가 발행할 수 있다.
    * 따라서 필드레벨에서 생성하는 것이 가장 안전하며, 코드도 간결하다.


* 테이블, 컬럼
  * 설정 관련 - SpringPhysicalNamingStrategy
  * 스프링 부트 신규 설정
    * 1 - 카멜케이스 -> 언더스코어(orderItem -> order_item)
    * 2 -.(점) -> _(언더스코어)
    * 3 - 대문자 -> 소문자

* 쿼리 &Jpql 
  * 쿼리는 데이터베이스를 대상으로 쿼리조회, jpql 은 Entity 객체 를 대상으로 조회한다.

