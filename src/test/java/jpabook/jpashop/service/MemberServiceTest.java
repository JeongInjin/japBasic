package jpabook.jpashop.service;

import jpabook.jpashop.domain.Member;
import jpabook.jpashop.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.*;
/*
    JUnit5 부터는 @RunWith 가 아닌 Extension 이라는 일관된 방법을 사용한다.
    @SpringBootTest 어노테이셔 안에 @ExtendWith(SpringExtension.class) 을 포함한다.
 */
//----> @ExtendWith(SpringExtension.class)

@SpringBootTest
@Transactional
class MemberServiceTest {

    @Autowired
    MemberService memberService;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    EntityManager em;

    @Test
    public void 회원가입() throws Exception {
        //given
        Member member = new Member();
        member.setName("jeong");

        //when
        Long saveId = memberService.join(member);
        System.out.println("saveId : " + saveId);

        //then
        em.flush();
        assertEquals(member, memberRepository.findOne(saveId));
    }

    @Test
    public void 중복_회원_예외() throws Exception {
        //given
        Member memberA = new Member();
        memberA.setName("jeong");

        Member memberB = new Member();
        memberB.setName("jeong");

        //when
        memberService.join(memberA);

        //then
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> memberService.join(memberB));
        //assertEquals("이미 존재하는 회원입니다.", thrown.getMessage());
    }

}