package com.chapchap.subscription.domain.terms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 구독 계약을 만들 때 실제 적용된 고객 약관 동의 기록을 고정한다. */
@Getter
@Entity
@Table(
        name = "subscription_contract_terms_agreements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subscription_contract_terms_agreements",
                columnNames = {"subscription_id", "user_terms_agreement_id"}
        ),
        indexes = {
                @Index(name = "idx_subscription_contract_terms_agreements_subscription", columnList = "subscription_id"),
                @Index(name = "idx_subscription_contract_terms_agreements_agreement", columnList = "user_terms_agreement_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionContractTermsAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "subscription_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long subscriptionId;

    @Column(name = "user_terms_agreement_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long userTermsAgreementId;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)"
    )
    private LocalDateTime createdAt;

    public static SubscriptionContractTermsAgreement create(
            Long subscriptionId,
            Long userTermsAgreementId
    ) {
        SubscriptionContractTermsAgreement relation = new SubscriptionContractTermsAgreement();
        relation.subscriptionId = subscriptionId;
        relation.userTermsAgreementId = userTermsAgreementId;
        return relation;
    }
}
