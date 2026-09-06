package com.chapchap.subscription.domain.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GeneratedColumn;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "payment_methods"
    , uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_payment_methods_current_user_id"
            , columnNames = "current_user_id"
        )
    }
    , indexes = {
        @Index(columnList = "user_id, status, is_current, id")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_code", nullable = false, length = 30)
    private PaymentProviderCode providerCode;

    @Column(name = "protected_external_method_ref", nullable = false, length = 512)
    private String protectedExternalMethodRef;

    @Column(name = "card_company", length = 50)
    private String cardCompany;

    @Column(name = "masked_card_number", length = 30)
    private String maskedCardNumber;

    @GeneratedColumn("CASE WHEN status = 'AVAILABLE' AND is_current = TRUE THEN user_id ELSE NULL END")
    @Column(name = "current_user_id", columnDefinition = "BIGINT UNSIGNED")
    private Long currentUserId;

    @Column(name = "is_current", nullable = false)
    private boolean isCurrent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentMethodStatus status;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "last_selected_at")
    private LocalDateTime lastSelectedAt;

    @Column(name = "retirement_at")
    private LocalDateTime retirementAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private PaymentMethod(
            String publicId
            , Long userId
            , PaymentProviderCode providerCode
            , String protectedExternalMethodRef
            , String cardCompany
            , String maskedCardNumber
            , boolean isCurrent // 첫 사용 가능 결제수단이면 현재 수단으로 생성하고, 추가 등록이면 기존 현재 수단을 유지한다.
            , LocalDateTime registeredAt
    ) {
        this.publicId = publicId;
        this.userId = userId;
        this.providerCode = providerCode;
        this.protectedExternalMethodRef = protectedExternalMethodRef;
        this.cardCompany = cardCompany;
        this.maskedCardNumber = maskedCardNumber;

        // 신규 등록 수단은 검증 완료 후 AVAILABLE 상태로 생성한다.
        this.status = PaymentMethodStatus.AVAILABLE;

        this.isCurrent = isCurrent;
        this.registeredAt = registeredAt;

        // 현재 수단으로 생성된 경우 등록 시각을 최초 선택 시각으로 사용한다.
        this.lastSelectedAt = isCurrent ? registeredAt : null;

        // 신규 AVAILABLE 수단은 아직 업무상 종료·논리 삭제되지 않은 상태다.
        this.retirementAt = null;
        this.deletedAt = null;
    }

    // 첫 사용 가능 결제수단 등록
    public static PaymentMethod createAsCurrent(
        Long userId
        , PaymentProviderCode providerCode
        , String protectedExternalMethodRef
        , String cardCompany
        , String maskedCardNumber
        , LocalDateTime registeredAt
    ) {
        return new PaymentMethod(
            generatePublicId()
            , userId
            , providerCode
            , protectedExternalMethodRef
            , cardCompany
            , maskedCardNumber
            , true
            , registeredAt
        );
    }

    // 기존 사용 가능 수단이 있는 상태의 추가 등록
    public static PaymentMethod createAsAdditional(
        Long userId
        , PaymentProviderCode providerCode
        , String protectedExternalMethodRef
        , String cardCompany
        , String maskedCardNumber
        , LocalDateTime registeredAt
    ) {
        return new PaymentMethod(
            generatePublicId()
            , userId
            , providerCode
            , protectedExternalMethodRef
            , cardCompany
            , maskedCardNumber
            , false
            , registeredAt
        );
    }

    public void unsetCurrent() {
        this.isCurrent = false;
    }

    public void selectAsCurrent(LocalDateTime selectedAt) {
        this.isCurrent = true;
        this.lastSelectedAt = selectedAt;
    }

    public void markAsDeleted(LocalDateTime retirementAt) {
        this.status = PaymentMethodStatus.DELETED;
        this.isCurrent = false;
        this.retirementAt = retirementAt;
    }

    private static String generatePublicId() {
        return UUID.randomUUID().toString();
    }
}
