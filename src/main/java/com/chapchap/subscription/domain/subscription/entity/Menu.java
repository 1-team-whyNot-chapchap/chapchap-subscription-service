package com.chapchap.subscription.domain.subscription.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "menus",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_menus_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_menus_plan_sequence", columnNames = {"plan_id", "menu_sequence"}),
                @UniqueConstraint(name = "uk_menus_name", columnNames = "name")
        },
        check = {
                @CheckConstraint(name = "chk_menus_sequence", constraint = "menu_sequence BETWEEN 1 AND 31"),
                @CheckConstraint(name = "chk_menus_name", constraint = "TRIM(name) <> ''"),
                @CheckConstraint(name = "chk_menus_description", constraint = "TRIM(description) <> ''"),
                @CheckConstraint(name = "chk_menus_allergen_info", constraint = "TRIM(allergen_info) <> ''"),
                @CheckConstraint(name = "chk_menus_nutrition_info", constraint = "TRIM(nutrition_info) <> ''"),
                @CheckConstraint(name = "chk_menus_ingredient_info", constraint = "TRIM(ingredient_info) <> ''")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "plan_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long planId;

    @Column(name = "menu_sequence", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer menuSequence;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "allergen_info", nullable = false, columnDefinition = "TEXT")
    private String allergenInfo;

    @Column(name = "nutrition_info", nullable = false, columnDefinition = "TEXT")
    private String nutritionInfo;

    @Column(name = "ingredient_info", nullable = false, columnDefinition = "TEXT")
    private String ingredientInfo;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;
}
