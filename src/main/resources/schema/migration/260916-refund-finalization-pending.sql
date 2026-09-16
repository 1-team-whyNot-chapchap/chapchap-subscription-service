-- Production migration for the FINALIZATION_PENDING refund state.
-- Run once before deploying the application change. The production profile validates
-- the schema and does not run schema.sql automatically.

ALTER TABLE refunds
    DROP CHECK ck_refunds_status,
    DROP CHECK ck_refunds_status_fields,
    ADD CONSTRAINT ck_refunds_status CHECK (status IN (
        'PENDING', 'FINALIZATION_PENDING', 'COMPLETED', 'FAILED', 'REVIEW_REQUIRED'
    )),
    ADD CONSTRAINT ck_refunds_status_fields CHECK (
        (status = 'PENDING' AND completed_at IS NULL)
        OR
        (
            status = 'FINALIZATION_PENDING'
            AND successful_refund_amount = refund_amount
            AND completed_at IS NOT NULL
        )
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
    );
