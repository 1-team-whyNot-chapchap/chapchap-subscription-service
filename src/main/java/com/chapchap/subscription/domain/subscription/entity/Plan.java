package com.chapchap.subscription.domain.subscription.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "plans",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_plans_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_plans_name", columnNames = "name")
        },
        check = {
                @CheckConstraint(name = "chk_plans_name", constraint = "TRIM(name) <> ''"),
                @CheckConstraint(name = "chk_plans_description", constraint = "TRIM(description) <> ''"),
                @CheckConstraint(name = "chk_plans_unit_price", constraint = "unit_price >= 1")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "unit_price", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long unitPrice;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;
}
