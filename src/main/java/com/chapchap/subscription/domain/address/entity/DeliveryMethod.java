package com.chapchap.subscription.domain.address.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// @NoArgsConstructor
@Getter
@Entity
@Table(name = "delivery_methods")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryMethod {

    @Id
    @Column(name = "code", length = 20, nullable = false)
    private String code;

    @Column(name = "display_name", length = 50, nullable = false)
    private String displayName;

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
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)"
    )
    private LocalDateTime updatedAt;
}
