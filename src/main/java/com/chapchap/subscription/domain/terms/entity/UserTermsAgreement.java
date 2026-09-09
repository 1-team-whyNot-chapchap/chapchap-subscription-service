package com.chapchap.subscription.domain.terms.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "user_terms_agreements",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_terms_agreements_user_terms",
                        columnNames = {
                                "user_id",
                                "terms_id"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTermsAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(
            name = "id",
            nullable = false,
            columnDefinition = "BIGINT UNSIGNED"
    )
    private Long id;

    @Column(
            name = "user_id",
            nullable = false,
            columnDefinition = "BIGINT UNSIGNED"
    )
    private Long userId;

    @Column(
            name = "terms_id",
            nullable = false,
            columnDefinition = "BIGINT UNSIGNED"
    )
    private Long termsId;

    @Column(
            name = "agreed_at",
            nullable = false,
            columnDefinition = "DATETIME(6)"
    )
    private LocalDateTime agreedAt;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)"
    )
    private LocalDateTime createdAt;

    public static UserTermsAgreement create(
            Long userId,
            Long termsId,
            LocalDateTime agreedAt
    ) {
        UserTermsAgreement agreement = new UserTermsAgreement();

        agreement.userId = userId;
        agreement.termsId = termsId;
        agreement.agreedAt = agreedAt;

        return agreement;
    }
}
