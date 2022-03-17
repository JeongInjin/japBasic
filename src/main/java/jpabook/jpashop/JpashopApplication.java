package jpabook.jpashop;

import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class JpashopApplication {

    public static void main(String[] args) {
        SpringApplication.run(JpashopApplication.class, args);
    }

    /**
     * json 파싱시 지연로딩으로 인하여 프록시객체를 josn 화 하지못하는 문제가 발생시 만드는 부분.
     * 다시 보니, 완전 잊고 있었는데..어짜피 별 쓸일 없을듯 하다. 왜냐하면, entity 를 그대로 반환하는 부분은 없을듯 하다.
     */
    @Bean
    Hibernate5Module hibernate5Module() {
        Hibernate5Module hibernate5Module = new Hibernate5Module();
        //강제 지연 로딩 설정
        hibernate5Module.configure(Hibernate5Module.Feature.FORCE_LAZY_LOADING, true);
        return hibernate5Module;
    }
}