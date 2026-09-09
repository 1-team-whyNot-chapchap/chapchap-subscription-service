package com.chapchap.subscription.domain.terms.repository;

import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTermsAgreementRepository
        extends JpaRepository<UserTermsAgreement, Long> {

    // Optional: 동의내역이 존재하는지 여부 파악, 유무에 따라 서로 다른 처리
    Optional <UserTermsAgreement> findByUserIdAndTermsId(
            Long userId,
            Long termsId
    );
}