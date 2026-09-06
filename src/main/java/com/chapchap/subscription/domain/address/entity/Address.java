package com.chapchap.subscription.domain.address.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// @NoArgsConstructor (빈 생성자 작성) //@RequiredArgsConstructor (new User("홍길동"))
// @CheckConstraint (조건 맞지 않는 데이터는 DB 저장 안되도록 막음)
@Getter
@Entity
@Table(
        name = "addresses",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_addresses_public_id",
                        columnNames = "public_id"
                ),
                @UniqueConstraint(
                        name = "uk_addresses_active_default_user_id",
                        columnNames = "active_default_user_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_addresses_user_deleted_default_id",
                        columnList = "user_id, deleted_at, is_default, id"
                )
        },
        check = {
                @CheckConstraint(
                        name = "chk_addresses_delivery_address_version",
                        constraint = "delivery_address_version >= 0"
                ),
                @CheckConstraint(
                        name = "chk_addresses_delivery_request",
                        constraint = """
                                (
                                    delivery_method_code = 'OTHER'
                                    AND other_delivery_request IS NOT NULL
                                    AND TRIM(other_delivery_request) <> ''
                                )
                                OR
                                (
                                    delivery_method_code IN ('DIRECT', 'DOORSTEP')
                                    AND other_delivery_request IS NULL
                                )
                                """
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(
            name = "id",
            nullable = false,
            columnDefinition = "BIGINT UNSIGNED"
    )
    private Long id;

    @Column(
            name = "public_id",
            nullable = false,
            length = 36,
            columnDefinition = "CHAR(36)"
    )
    private String publicId;

    @Column(
            name = "user_id",
            nullable = false,
            columnDefinition = "BIGINT UNSIGNED"
    )
    private Long userId;

    @Column(
            name = "delivery_address_version",
            nullable = false,
            columnDefinition = "BIGINT UNSIGNED DEFAULT 0"
    )
    private Long deliveryAddressVersion;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "delivery_method_code", nullable = false, length = 20)
    private String deliveryMethodCode;

    @Column(name = "other_delivery_request", length = 255)
    private String otherDeliveryRequest;

    @Column(name = "entrance_password", length = 100)
    private String entrancePassword;

    @Column(
            name = "is_default",
            nullable = false,
            options = "DEFAULT 0"
    )
    private boolean isDefault;

    @Column(
            name = "active_default_user_id",
            insertable = false,
            updatable = false,
            columnDefinition = """
                    BIGINT UNSIGNED GENERATED ALWAYS AS (
                        CASE
                            WHEN deleted_at IS NULL AND is_default = TRUE
                            THEN user_id
                            ELSE NULL
                        END
                    ) STORED
                    """
    )
    private Long activeDefaultUserId;

    @Column(
            name = "deleted_at",
            columnDefinition = "DATETIME(6)"
    )
    private LocalDateTime deletedAt;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)"
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition =
                    "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)"
    )
    private LocalDateTime updatedAt;

    public static Address create(
            Long userId,
            String name,
            String recipientName,
            String recipientPhone,
            String postalCode,
            String addressLine1,
            String addressLine2,
            String deliveryMethodCode,
            String otherDeliveryRequest,
            String entrancePassword,
            boolean isDefault
    ) {
        Address address = new Address();

        address.publicId = UUID.randomUUID().toString();
        address.userId = userId;
        address.deliveryAddressVersion = 0L;
        address.name = name;
        address.recipientName = recipientName;
        address.recipientPhone = recipientPhone;
        address.postalCode = postalCode;
        address.addressLine1 = addressLine1;
        address.addressLine2 = addressLine2;
        address.deliveryMethodCode = deliveryMethodCode;
        address.otherDeliveryRequest = otherDeliveryRequest;
        address.entrancePassword = entrancePassword;
        address.isDefault = isDefault;

        return address;
    }

    public void changeDetails(
            String name,
            String recipientName,
            String recipientPhone,
            String postalCode,
            String addressLine1,
            String addressLine2,
            String deliveryMethodCode,
            String otherDeliveryRequest,
            String entrancePassword
    ) {
        this.name = name;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.postalCode = postalCode;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.deliveryMethodCode = deliveryMethodCode;
        this.otherDeliveryRequest = otherDeliveryRequest;
        this.entrancePassword = entrancePassword;
    }

    public void setAsDefault() {
        this.isDefault = true;
    }

    public void unsetDefault() {
        this.isDefault = false;
    }

    public void softDelete(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void increaseDeliveryAddressVersion() {
        this.deliveryAddressVersion++;
    }
}
