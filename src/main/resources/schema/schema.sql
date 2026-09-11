-- Subscription Service schema
-- Basis: Subscription_Service_DB_ERD.md (2026-09-06)
-- Target: MySQL 8.4.x / utf8mb4 / utf8mb4_0900_ai_ci
-- Time policy: domain DATETIME(6) values are written/read as Asia/Seoul (KST) by the application.
-- IMPORTANT: This service intentionally uses logical references only. No physical FOREIGN KEY constraints are created.
-- IMPORTANT: Database name is intentionally not hard-coded. Connect to/select the target database before running this file.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 1. Reference/master tables
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS delivery_methods (
    code VARCHAR(20) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plans (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255) NOT NULL,
    unit_price BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_plans_public_id UNIQUE (public_id),
    CONSTRAINT uq_plans_name UNIQUE (name),
    CONSTRAINT ck_plans_name_nonblank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_plans_description_nonblank CHECK (CHAR_LENGTH(TRIM(description)) > 0),
    CONSTRAINT ck_plans_unit_price CHECK (unit_price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS menus (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    plan_id BIGINT UNSIGNED NOT NULL,
    menu_sequence TINYINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    image_url VARCHAR(200) NOT NULL,
    allergen_info TEXT NOT NULL,
    nutrition_info TEXT NOT NULL,
    ingredient_info TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_menus_public_id UNIQUE (public_id),
    CONSTRAINT uq_menus_plan_sequence UNIQUE (plan_id, menu_sequence),
    CONSTRAINT uq_menus_name UNIQUE (name),
    CONSTRAINT ck_menus_sequence CHECK (menu_sequence BETWEEN 1 AND 31),
    CONSTRAINT ck_menus_name_nonblank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_menus_description_nonblank CHECK (CHAR_LENGTH(TRIM(description)) > 0),
    CONSTRAINT ck_menus_allergen_nonblank CHECK (CHAR_LENGTH(TRIM(allergen_info)) > 0),
    CONSTRAINT ck_menus_nutrition_nonblank CHECK (CHAR_LENGTH(TRIM(nutrition_info)) > 0),
    CONSTRAINT ck_menus_ingredient_nonblank CHECK (CHAR_LENGTH(TRIM(ingredient_info)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS terms (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    terms_type VARCHAR(50) NOT NULL,
    version_number INT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    current_terms_type VARCHAR(50)
        GENERATED ALWAYS AS (CASE WHEN is_current = TRUE THEN terms_type ELSE NULL END) STORED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_terms_type_version UNIQUE (terms_type, version_number),
    CONSTRAINT uq_terms_current_type UNIQUE (current_terms_type),
    CONSTRAINT ck_terms_version CHECK (version_number >= 1),
    CONSTRAINT ck_terms_type_nonblank CHECK (CHAR_LENGTH(TRIM(terms_type)) > 0),
    CONSTRAINT ck_terms_title_nonblank CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_terms_content_nonblank CHECK (CHAR_LENGTH(TRIM(content)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS holidays (
    holiday_date DATE NOT NULL,
    holiday_name VARCHAR(100) NOT NULL,
    is_substitute_holiday BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (holiday_date),
    CONSTRAINT ck_holidays_name_nonblank CHECK (CHAR_LENGTH(TRIM(holiday_name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 2. Subscription core
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS subscriptions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    auth_subscription_version INT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    is_first_subscription_discount_used BOOLEAN NOT NULL DEFAULT FALSE,
    cancellation_requested_at DATETIME(6) NULL DEFAULT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_subscriptions_public_id UNIQUE (public_id),
    CONSTRAINT uq_subscriptions_user_id UNIQUE (user_id),
    CONSTRAINT ck_subscriptions_auth_version CHECK (auth_subscription_version >= 0),
    CONSTRAINT ck_subscriptions_status CHECK (status IN (
        'AWAITING_CONFIRMATION', 'SCHEDULED', 'IN_PROGRESS',
        'CANCELLATION_SCHEDULED', 'PAYMENT_FAILED',
        'CANCELED_BEFORE_START', 'ENDED'
    )),
    CONSTRAINT ck_subscriptions_cancel_requested CHECK (
        (status = 'CANCELLATION_SCHEDULED' AND cancellation_requested_at IS NOT NULL)
        OR
        (status <> 'CANCELLATION_SCHEDULED' AND cancellation_requested_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS subscription_status_histories (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT UNSIGNED NOT NULL,
    previous_status VARCHAR(30) NULL DEFAULT NULL,
    next_status VARCHAR(30) NOT NULL,
    change_actor VARCHAR(30) NOT NULL,
    change_reason VARCHAR(100) NOT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_sub_hist_prev_status CHECK (
        previous_status IS NULL OR previous_status IN (
            'AWAITING_CONFIRMATION', 'SCHEDULED', 'IN_PROGRESS',
            'CANCELLATION_SCHEDULED', 'PAYMENT_FAILED',
            'CANCELED_BEFORE_START', 'ENDED'
        )
    ),
    CONSTRAINT ck_sub_hist_next_status CHECK (next_status IN (
        'AWAITING_CONFIRMATION', 'SCHEDULED', 'IN_PROGRESS',
        'CANCELLATION_SCHEDULED', 'PAYMENT_FAILED',
        'CANCELED_BEFORE_START', 'ENDED'
    )),
    CONSTRAINT ck_sub_hist_actual_change CHECK (previous_status IS NULL OR previous_status <> next_status),
    INDEX idx_sub_hist_subscription_changed (subscription_id, changed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS subscription_periods (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT UNSIGNED NOT NULL,
    period_sequence INT UNSIGNED NOT NULL,
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    calculation_reference_at DATETIME(6) NOT NULL,
    start_canceled_at DATETIME(6) NULL DEFAULT NULL,
    start_cancel_reason VARCHAR(40) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_sub_periods_sequence UNIQUE (subscription_id, period_sequence),
    CONSTRAINT ck_sub_periods_sequence CHECK (period_sequence >= 1),
    CONSTRAINT ck_sub_periods_dates CHECK (period_start_date <= period_end_date),
    CONSTRAINT ck_sub_periods_status CHECK (status IN (
        'AWAITING_CONFIRMATION', 'SCHEDULED', 'IN_PROGRESS',
        'ENDED', 'CANCELED_BEFORE_START', 'PAYMENT_FAILED'
    )),
    CONSTRAINT ck_sub_periods_cancel_fields CHECK (
        (
            status = 'CANCELED_BEFORE_START'
            AND start_canceled_at IS NOT NULL
            AND start_cancel_reason IN (
                'FIRST_SUBSCRIPTION_CANCELLATION',
                'REGULAR_PAYMENT_RETRY_CANCELLATION',
                'NEXT_PERIOD_FULL_CANCELLATION'
            )
        )
        OR
        (
            status <> 'CANCELED_BEFORE_START'
            AND start_canceled_at IS NULL
            AND start_cancel_reason IS NULL
        )
    ),
    INDEX idx_sub_periods_status_start (status, period_start_date),
    INDEX idx_sub_periods_status_end (status, period_end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 3. Address / subscription settings / agreements
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS addresses (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    delivery_address_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    name VARCHAR(50) NOT NULL,
    recipient_name VARCHAR(50) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    postal_code VARCHAR(10) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255) NULL DEFAULT NULL,
    delivery_method_code VARCHAR(20) NOT NULL,
    other_delivery_request VARCHAR(255) NULL DEFAULT NULL,
    entrance_password VARCHAR(100) NULL DEFAULT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    active_default_user_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE
                WHEN deleted_at IS NULL AND is_default = TRUE THEN user_id
                ELSE NULL
            END
        ) STORED NULL,
    deleted_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_addresses_public_id UNIQUE (public_id),
    CONSTRAINT uq_addresses_active_default UNIQUE (active_default_user_id),
    CONSTRAINT ck_addresses_version CHECK (delivery_address_version >= 0),
    CONSTRAINT ck_addresses_other_request CHECK (
        (delivery_method_code = 'OTHER' AND other_delivery_request IS NOT NULL AND CHAR_LENGTH(TRIM(other_delivery_request)) > 0)
        OR
        (delivery_method_code <> 'OTHER' AND other_delivery_request IS NULL)
    ),
    INDEX idx_addresses_user_active (user_id, deleted_at, is_default, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS subscription_settings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT UNSIGNED NOT NULL,
    plan_id BIGINT UNSIGNED NOT NULL,
    setting_sequence INT UNSIGNED NOT NULL,
    status VARCHAR(30) NOT NULL,
    processing_reference_at DATETIME(6) NULL DEFAULT NULL,
    effective_start_date DATE NOT NULL,
    effective_end_exclusive_date DATE NULL DEFAULT NULL,
    confirmed_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_sub_settings_sequence UNIQUE (subscription_id, setting_sequence),
    CONSTRAINT ck_sub_settings_sequence CHECK (setting_sequence >= 1),
    CONSTRAINT ck_sub_settings_status CHECK (status IN (
        'AWAITING_CONFIRMATION', 'CHANGE_PENDING', 'ACTIVE',
        'PAYMENT_FAILED', 'CHANGE_NOT_APPLIED', 'ENDED'
    )),
    CONSTRAINT ck_sub_settings_processing_ref CHECK (
        (setting_sequence = 1 AND processing_reference_at IS NULL)
        OR
        (setting_sequence > 1 AND processing_reference_at IS NOT NULL)
    ),
    CONSTRAINT ck_sub_settings_effective_range CHECK (
        effective_end_exclusive_date IS NULL OR effective_end_exclusive_date >= effective_start_date
    ),
    INDEX idx_sub_settings_lookup (subscription_id, status, effective_start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS subscription_delivery_conditions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subscription_setting_id BIGINT UNSIGNED NOT NULL,
    delivery_weekday VARCHAR(10) NOT NULL,
    meal_quantity INT UNSIGNED NOT NULL,
    address_id BIGINT UNSIGNED NOT NULL,
    delivery_time_slot VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_sub_delivery_cond_weekday UNIQUE (subscription_setting_id, delivery_weekday),
    CONSTRAINT ck_sub_delivery_cond_weekday CHECK (delivery_weekday IN (
        'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'
    )),
    CONSTRAINT ck_sub_delivery_cond_quantity CHECK (meal_quantity BETWEEN 1 AND 6),
    CONSTRAINT ck_sub_delivery_cond_slot CHECK (delivery_time_slot IN (
        'TIME_1100_1300', 'TIME_1700_1900'
    )),
    INDEX idx_sub_delivery_cond_address (address_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_terms_agreements (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    terms_id BIGINT UNSIGNED NOT NULL,
    agreed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_user_terms_agreements UNIQUE (user_id, terms_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS subscription_contract_terms_agreements (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT UNSIGNED NOT NULL,
    user_terms_agreement_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_subscription_contract_terms_agreements
        UNIQUE (subscription_id, user_terms_agreement_id),
    INDEX idx_subscription_contract_terms_agreements_subscription (subscription_id),
    INDEX idx_subscription_contract_terms_agreements_agreement (user_terms_agreement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 4. Orders / Kafka delivery history
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    subscription_id BIGINT UNSIGNED NOT NULL,
    subscription_period_id BIGINT UNSIGNED NOT NULL,
    subscription_setting_id BIGINT UNSIGNED NOT NULL,
    non_face_to_face_terms_agreement_id BIGINT UNSIGNED NOT NULL,
    plan_id BIGINT UNSIGNED NOT NULL,
    address_id BIGINT UNSIGNED NOT NULL,
    menu_id BIGINT UNSIGNED NOT NULL,
    replacement_target_order_id BIGINT UNSIGNED NULL DEFAULT NULL,
    delivery_date DATE NOT NULL,
    revision_sequence INT UNSIGNED NOT NULL,
    status VARCHAR(30) NOT NULL,
    kafka_delivery_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SENT',
    active_subscription_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN subscription_id ELSE NULL END) STORED NULL,
    active_delivery_date DATE
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN delivery_date ELSE NULL END) STORED NULL,
    plan_name VARCHAR(50) NOT NULL,
    menu_name VARCHAR(100) NOT NULL,
    meal_unit_price BIGINT UNSIGNED NOT NULL,
    meal_quantity INT UNSIGNED NOT NULL,
    meal_amount BIGINT UNSIGNED NOT NULL,
    delivery_fee BIGINT UNSIGNED NOT NULL,
    discount_amount BIGINT UNSIGNED NOT NULL DEFAULT 0,
    actual_allocated_amount BIGINT UNSIGNED NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    postal_code VARCHAR(10) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255) NULL DEFAULT NULL,
    delivery_method_code VARCHAR(20) NOT NULL,
    other_delivery_request VARCHAR(255) NULL DEFAULT NULL,
    entrance_password VARCHAR(100) NULL DEFAULT NULL,
    delivery_time_slot VARCHAR(20) NOT NULL,
    kafka_stored_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_orders_public_id UNIQUE (public_id),
    CONSTRAINT uq_orders_revision UNIQUE (subscription_id, delivery_date, revision_sequence),
    CONSTRAINT uq_orders_active_delivery UNIQUE (active_subscription_id, active_delivery_date),
    CONSTRAINT ck_orders_revision CHECK (revision_sequence >= 1),
    CONSTRAINT ck_orders_status CHECK (status IN (
        'AWAITING_CONFIRMATION', 'CHANGE_PENDING', 'ACTIVE', 'INACTIVE',
        'PAYMENT_FAILED', 'CHANGE_NOT_APPLIED', 'CANCELED_BEFORE_START'
    )),
    CONSTRAINT ck_orders_kafka_status CHECK (kafka_delivery_status IN (
        'NOT_SENT', 'FAILED', 'COMPLETED', 'FINAL_FAILED'
    )),
    CONSTRAINT ck_orders_kafka_stored_at CHECK (
        (kafka_delivery_status = 'COMPLETED' AND kafka_stored_at IS NOT NULL)
        OR
        (kafka_delivery_status <> 'COMPLETED' AND kafka_stored_at IS NULL)
    ),
    CONSTRAINT ck_orders_unit_price CHECK (meal_unit_price > 0),
    CONSTRAINT ck_orders_quantity CHECK (meal_quantity BETWEEN 1 AND 6),
    CONSTRAINT ck_orders_meal_amount CHECK (meal_amount = meal_unit_price * meal_quantity),
    CONSTRAINT ck_orders_discount CHECK (discount_amount <= meal_amount),
    CONSTRAINT ck_orders_allocated_amount CHECK (
        actual_allocated_amount = meal_amount + delivery_fee - discount_amount
    ),
    CONSTRAINT ck_orders_other_request CHECK (
        (delivery_method_code = 'OTHER' AND other_delivery_request IS NOT NULL AND CHAR_LENGTH(TRIM(other_delivery_request)) > 0)
        OR
        (delivery_method_code <> 'OTHER' AND other_delivery_request IS NULL)
    ),
    CONSTRAINT ck_orders_delivery_slot CHECK (delivery_time_slot IN (
        'TIME_1100_1300', 'TIME_1700_1900'
    )),
    INDEX idx_orders_user_delivery (user_id, delivery_date DESC, id DESC),
    INDEX idx_orders_publish_target (delivery_date, status, kafka_delivery_status, id),
    INDEX idx_orders_setting_change (
        subscription_id, subscription_period_id, delivery_date, status, kafka_delivery_status, id
    ),
    INDEX idx_orders_address_active (address_id, status, delivery_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_delivery_attempts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id BIGINT UNSIGNED NOT NULL,
    attempt_sequence TINYINT UNSIGNED NOT NULL,
    execution_type VARCHAR(20) NOT NULL,
    attempted_at DATETIME(6) NOT NULL,
    result VARCHAR(10) NOT NULL,
    kafka_topic VARCHAR(255) NULL DEFAULT NULL,
    kafka_partition INT UNSIGNED NULL DEFAULT NULL,
    kafka_offset BIGINT UNSIGNED NULL DEFAULT NULL,
    failure_code VARCHAR(100) NULL DEFAULT NULL,
    failure_reason TEXT NULL,
    resolved_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_order_delivery_attempt_seq UNIQUE (order_id, attempt_sequence),
    CONSTRAINT uq_order_delivery_attempt_type UNIQUE (order_id, execution_type),
    CONSTRAINT ck_order_delivery_attempt_seq CHECK (attempt_sequence BETWEEN 1 AND 2),
    CONSTRAINT ck_order_delivery_attempt_type CHECK (execution_type IN ('INITIAL_1500', 'RETRY_1600')),
    CONSTRAINT ck_order_delivery_attempt_pair CHECK (
        (attempt_sequence = 1 AND execution_type = 'INITIAL_1500')
        OR
        (attempt_sequence = 2 AND execution_type = 'RETRY_1600')
    ),
    CONSTRAINT ck_order_delivery_attempt_result CHECK (result IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT ck_order_delivery_attempt_payload CHECK (
        (
            result = 'SUCCESS'
            AND kafka_topic IS NOT NULL
            AND kafka_partition IS NOT NULL
            AND kafka_offset IS NOT NULL
            AND failure_code IS NULL
            AND failure_reason IS NULL
        )
        OR
        (
            result = 'FAILURE'
            AND kafka_topic IS NULL
            AND kafka_partition IS NULL
            AND kafka_offset IS NULL
            AND failure_code IS NOT NULL
            AND failure_reason IS NOT NULL
        )
    ),
    CONSTRAINT ck_order_delivery_attempt_time CHECK (resolved_at >= attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS kafka_delivery_failures (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id BIGINT UNSIGNED NOT NULL,
    order_delivery_attempt_id BIGINT UNSIGNED NOT NULL,
    failure_code VARCHAR(100) NOT NULL,
    failure_reason TEXT NOT NULL,
    failed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_kafka_delivery_failures_order UNIQUE (order_id),
    CONSTRAINT uq_kafka_delivery_failures_attempt UNIQUE (order_delivery_attempt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 5. Payment / refund
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS payment_methods (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    provider_code VARCHAR(30) NOT NULL,
    protected_external_method_ref VARCHAR(512) NOT NULL,
    card_company VARCHAR(50) NULL DEFAULT NULL,
    masked_card_number VARCHAR(30) NULL DEFAULT NULL,
    is_current BOOLEAN NOT NULL,
    status ENUM('AVAILABLE', 'DELETED', 'DISCARDED') NOT NULL,
    current_user_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE WHEN status = 'AVAILABLE' AND is_current = TRUE THEN user_id ELSE NULL END
        ) STORED NULL,
    registered_at DATETIME(6) NOT NULL,
    last_selected_at DATETIME(6) NULL DEFAULT NULL,
    retirement_at DATETIME(6) NULL DEFAULT NULL,
    deleted_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_payment_methods_public_id UNIQUE (public_id),
    CONSTRAINT uq_payment_methods_current_user UNIQUE (current_user_id),
    CONSTRAINT ck_payment_methods_current_status CHECK (
        is_current = FALSE OR status = 'AVAILABLE'
    ),
    CONSTRAINT ck_payment_methods_available_times CHECK (
        status <> 'AVAILABLE' OR (retirement_at IS NULL AND deleted_at IS NULL)
    ),
    CONSTRAINT ck_payment_methods_terminal_retirement CHECK (
        status = 'AVAILABLE' OR retirement_at IS NOT NULL
    ),
    CONSTRAINT ck_payment_methods_deleted_status CHECK (
        deleted_at IS NULL OR status IN ('DELETED', 'DISCARDED')
    ),
    INDEX idx_payment_methods_user_status (user_id, status, is_current, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS refunds (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    subscription_id BIGINT UNSIGNED NOT NULL,
    refund_type VARCHAR(40) NOT NULL,
    subscription_period_id BIGINT UNSIGNED NULL DEFAULT NULL,
    subscription_setting_id BIGINT UNSIGNED NULL DEFAULT NULL,
    order_id BIGINT UNSIGNED NULL DEFAULT NULL,
    external_delivery_id CHAR(36) NULL DEFAULT NULL,
    refund_amount BIGINT UNSIGNED NOT NULL,
    successful_refund_amount BIGINT UNSIGNED NOT NULL DEFAULT 0,
    unprocessed_amount BIGINT UNSIGNED
        GENERATED ALWAYS AS (refund_amount - successful_refund_amount) STORED NOT NULL,
    business_deduplication_key VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(500) NULL DEFAULT NULL,
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_refunds_public_id UNIQUE (public_id),
    CONSTRAINT uq_refunds_period UNIQUE (subscription_period_id),
    CONSTRAINT uq_refunds_setting UNIQUE (subscription_setting_id),
    CONSTRAINT uq_refunds_order UNIQUE (order_id),
    CONSTRAINT uq_refunds_external_delivery UNIQUE (external_delivery_id),
    CONSTRAINT uq_refunds_business_dedup UNIQUE (business_deduplication_key),
    CONSTRAINT ck_refunds_type CHECK (refund_type IN (
        'SETTING_CHANGE_REDUCTION', 'CANCELLATION_BEFORE_START',
        'NEXT_PERIOD_FULL_CANCELLATION', 'DELIVERY_PARTIAL_CANCELLATION'
    )),
    CONSTRAINT ck_refunds_target CHECK (
        (
            refund_type = 'SETTING_CHANGE_REDUCTION'
            AND subscription_period_id IS NULL
            AND subscription_setting_id IS NOT NULL
            AND order_id IS NULL
            AND external_delivery_id IS NULL
        )
        OR
        (
            refund_type IN ('CANCELLATION_BEFORE_START', 'NEXT_PERIOD_FULL_CANCELLATION')
            AND subscription_period_id IS NOT NULL
            AND subscription_setting_id IS NULL
            AND order_id IS NULL
            AND external_delivery_id IS NULL
        )
        OR
        (
            refund_type = 'DELIVERY_PARTIAL_CANCELLATION'
            AND subscription_period_id IS NULL
            AND subscription_setting_id IS NULL
            AND order_id IS NOT NULL
            AND external_delivery_id IS NOT NULL
        )
    ),
    CONSTRAINT ck_refunds_amount CHECK (
        refund_amount > 0 AND successful_refund_amount <= refund_amount
    ),
    CONSTRAINT ck_refunds_status CHECK (status IN (
        'PENDING', 'COMPLETED', 'FAILED', 'REVIEW_REQUIRED'
    )),
    CONSTRAINT ck_refunds_status_fields CHECK (
        (status = 'PENDING' AND completed_at IS NULL)
        OR
        (
            status = 'COMPLETED'
            AND successful_refund_amount = refund_amount
            AND completed_at IS NOT NULL
        )
        OR
        (
            status = 'FAILED'
            AND successful_refund_amount = 0
            AND completed_at IS NULL
            AND failure_reason IS NOT NULL
            AND CHAR_LENGTH(TRIM(failure_reason)) > 0
        )
        OR
        (
            status = 'REVIEW_REQUIRED'
            AND successful_refund_amount > 0
            AND successful_refund_amount < refund_amount
            AND completed_at IS NULL
            AND failure_reason IS NOT NULL
            AND CHAR_LENGTH(TRIM(failure_reason)) > 0
        )
    ),
    INDEX idx_refunds_subscription_requested (subscription_id, requested_at),
    INDEX idx_refunds_status_requested (status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    subscription_id BIGINT UNSIGNED NOT NULL,
    subscription_period_id BIGINT UNSIGNED NOT NULL,
    subscription_setting_id BIGINT UNSIGNED NULL DEFAULT NULL,
    refund_id BIGINT UNSIGNED NULL DEFAULT NULL,
    original_payment_transaction_id BIGINT UNSIGNED NULL DEFAULT NULL,
    transaction_type VARCHAR(40) NOT NULL,
    original_payment_amount BIGINT UNSIGNED NULL DEFAULT NULL,
    transaction_amount BIGINT UNSIGNED NOT NULL,
    cumulative_cancel_amount BIGINT UNSIGNED NULL DEFAULT NULL,
    cancelable_amount BIGINT UNSIGNED NULL DEFAULT NULL,
    processing_reference_at DATETIME(6) NOT NULL,
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    setting_effective_date DATE NULL DEFAULT NULL,
    business_deduplication_key VARCHAR(255) NOT NULL,
    external_request_idempotency_key VARCHAR(255) NULL DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    payment_state_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_payment_tx_public_id UNIQUE (public_id),
    CONSTRAINT uq_payment_tx_business_dedup UNIQUE (business_deduplication_key),
    CONSTRAINT uq_payment_tx_external_idem UNIQUE (external_request_idempotency_key),
    CONSTRAINT ck_payment_tx_type CHECK (transaction_type IN (
        'FIRST_SUBSCRIPTION_PAYMENT', 'REGULAR_PAYMENT', 'SETTING_CHANGE_PAYMENT',
        'SETTING_CHANGE_PARTIAL_CANCELLATION', 'CANCELLATION_BEFORE_START',
        'NEXT_PERIOD_FULL_CANCELLATION', 'DELIVERY_PARTIAL_CANCELLATION'
    )),
    CONSTRAINT ck_payment_tx_status CHECK (status IN (
        'PROCESSING', 'SUCCESS', 'FAILED', 'RETRY_WAITING', 'RETRY_STOPPED'
    )),
    CONSTRAINT ck_payment_tx_amount CHECK (transaction_amount > 0),
    CONSTRAINT ck_payment_tx_version CHECK (payment_state_version >= 0),
    CONSTRAINT ck_payment_tx_period_dates CHECK (period_start_date <= period_end_date),
    CONSTRAINT ck_payment_tx_setting_ref CHECK (
        (
            transaction_type IN ('SETTING_CHANGE_PAYMENT', 'SETTING_CHANGE_PARTIAL_CANCELLATION')
            AND subscription_setting_id IS NOT NULL
            AND setting_effective_date IS NOT NULL
        )
        OR
        (
            transaction_type NOT IN ('SETTING_CHANGE_PAYMENT', 'SETTING_CHANGE_PARTIAL_CANCELLATION')
            AND subscription_setting_id IS NULL
            AND setting_effective_date IS NULL
        )
    ),
    CONSTRAINT ck_payment_tx_refund_ref CHECK (
        (
            transaction_type IN (
                'SETTING_CHANGE_PARTIAL_CANCELLATION', 'CANCELLATION_BEFORE_START',
                'NEXT_PERIOD_FULL_CANCELLATION', 'DELIVERY_PARTIAL_CANCELLATION'
            )
            AND refund_id IS NOT NULL
            AND original_payment_transaction_id IS NOT NULL
        )
        OR
        (
            transaction_type IN (
                'FIRST_SUBSCRIPTION_PAYMENT', 'REGULAR_PAYMENT', 'SETTING_CHANGE_PAYMENT'
            )
            AND refund_id IS NULL
            AND original_payment_transaction_id IS NULL
        )
    ),
    CONSTRAINT ck_payment_tx_original_amounts CHECK (
        (
            transaction_type IN (
                'FIRST_SUBSCRIPTION_PAYMENT', 'REGULAR_PAYMENT', 'SETTING_CHANGE_PAYMENT'
            )
            AND status = 'SUCCESS'
            AND original_payment_amount = transaction_amount
            AND cumulative_cancel_amount IS NOT NULL
            AND cancelable_amount IS NOT NULL
            AND original_payment_amount = cumulative_cancel_amount + cancelable_amount
        )
        OR
        (
            NOT (
                transaction_type IN (
                    'FIRST_SUBSCRIPTION_PAYMENT', 'REGULAR_PAYMENT', 'SETTING_CHANGE_PAYMENT'
                )
                AND status = 'SUCCESS'
            )
            AND original_payment_amount IS NULL
            AND cumulative_cancel_amount IS NULL
            AND cancelable_amount IS NULL
        )
    ),
    CONSTRAINT ck_payment_tx_external_idem_state CHECK (
        (status = 'PROCESSING' AND external_request_idempotency_key IS NOT NULL)
        OR
        (status <> 'PROCESSING' AND external_request_idempotency_key IS NULL)
    ),
    INDEX idx_payment_tx_user_occurred (user_id, occurred_at DESC, id DESC),
    INDEX idx_payment_tx_subscription (
        subscription_id, subscription_period_id, transaction_type, status, id
    ),
    INDEX idx_payment_tx_refund (refund_id, status, id),
    INDEX idx_payment_tx_original (original_payment_transaction_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS payment_attempts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_transaction_id BIGINT UNSIGNED NOT NULL,
    payment_method_id BIGINT UNSIGNED NULL DEFAULT NULL,
    provider_code VARCHAR(30) NOT NULL,
    attempt_sequence INT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    requested_amount BIGINT UNSIGNED NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    responded_at DATETIME(6) NOT NULL,
    result VARCHAR(10) NOT NULL,
    external_payment_id VARCHAR(255) NULL DEFAULT NULL,
    external_transaction_ref VARCHAR(255) NULL DEFAULT NULL,
    external_result_code VARCHAR(100) NULL DEFAULT NULL,
    failure_reason TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_payment_attempts_sequence UNIQUE (payment_transaction_id, attempt_sequence),
    CONSTRAINT uq_payment_attempts_idem UNIQUE (idempotency_key),
    CONSTRAINT ck_payment_attempts_sequence CHECK (attempt_sequence >= 1),
    CONSTRAINT ck_payment_attempts_amount CHECK (requested_amount > 0),
    CONSTRAINT ck_payment_attempts_time CHECK (responded_at >= requested_at),
    CONSTRAINT ck_payment_attempts_result CHECK (result IN ('SUCCESS', 'FAILURE')),
    INDEX idx_payment_attempts_external_payment (external_payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS payment_allocations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id BIGINT UNSIGNED NOT NULL,
    original_payment_transaction_id BIGINT UNSIGNED NOT NULL,
    allocation_type VARCHAR(40) NOT NULL,
    allocated_amount BIGINT UNSIGNED NOT NULL,
    cumulative_cancelled_amount BIGINT UNSIGNED NOT NULL DEFAULT 0,
    cancelable_amount BIGINT UNSIGNED
        GENERATED ALWAYS AS (allocated_amount - cumulative_cancelled_amount) STORED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_payment_allocations_source UNIQUE (order_id, original_payment_transaction_id),
    CONSTRAINT ck_payment_allocations_type CHECK (allocation_type IN (
        'FIRST_SUBSCRIPTION_PAYMENT', 'REGULAR_PAYMENT', 'SETTING_CHANGE_PAYMENT'
    )),
    CONSTRAINT ck_payment_allocations_amount CHECK (allocated_amount > 0),
    CONSTRAINT ck_payment_allocations_cancelled CHECK (cumulative_cancelled_amount <= allocated_amount),
    INDEX idx_payment_allocations_original (original_payment_transaction_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- End of schema.sql
