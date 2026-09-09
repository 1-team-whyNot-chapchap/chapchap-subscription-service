package com.chapchap.subscription.domain.terms.repository;

import com.chapchap.subscription.domain.terms.entity.Terms;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TermsRepository extends JpaRepository<Terms, Long> {

    // Optional 사용 이유: 값의 부재 가능성을 '명시적'으로 모델링한다, 이를 보고 Service에서 예외처리 할 수 있음
    Optional<Terms> findByTermsTypeAndIsCurrentTrueAndIsRequiredTrue(
            String termsType
    );
}
