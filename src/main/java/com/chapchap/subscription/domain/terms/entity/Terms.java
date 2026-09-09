package com.chapchap.subscription.domain.terms.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "terms",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_terms_type_version",
                        columnNames = {
                                "terms_type",
                                "version_number"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_terms_current_terms_type",
                        columnNames = "current_terms_type"
                )
        },
        check = {
                @CheckConstraint(
                        name = "chk_terms_version_number",
                        constraint = "version_number >= 1"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(
            name = "id",
            nullable = false,
            columnDefinition = "BIGINT UNSIGNED"
    )
    private Long id;

    @Column(
            name = "terms_type",
            nullable = false,
            length = 50
    )
    private String termsType;

    @Column(
            name = "version_number",
            nullable = false,
            columnDefinition = "INT UNSIGNED"
    )
    private Integer versionNumber;

    @Column(
            name = "title",
            nullable = false,
            length = 200
    )
    private String title;

    @Column(
            name = "content",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String content;

    @Column(
            name = "is_required",
            nullable = false,
            options = "DEFAULT 1"
    )
    private boolean isRequired;

    @Column(
            name = "is_current",
            nullable = false,
            options = "DEFAULT 1"
    )
    private boolean isCurrent;

    @Column(
            name = "current_terms_type",
            insertable = false,
            updatable = false,
            columnDefinition = """
                    VARCHAR(50) GENERATED ALWAYS AS (
                        CASE
                            WHEN is_current = TRUE
                            THEN terms_type
                            ELSE NULL
                        END
                    ) STORED
                    """
    )
    private String currentTermsType;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)"
    )
    private LocalDateTime createdAt;
}