package com.chapchap.subscription.domain.terms.repository;

import com.chapchap.subscription.domain.terms.entity.SubscriptionContractTermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionContractTermsAgreementRepository
        extends JpaRepository<SubscriptionContractTermsAgreement, Long> {

    boolean existsBySubscriptionIdAndUserTermsAgreementId(
            Long subscriptionId,
            Long userTermsAgreementId
    );
}
