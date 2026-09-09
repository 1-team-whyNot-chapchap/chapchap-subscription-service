package com.chapchap.subscription.domain.payment.entity;

import com.chapchap.subscription.global.validation.PublicIdFormat;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundCancellationTest {
    @Test
    void 모든_원결제_취소금액이_누적되면_환불을_완료한다() {
        Refund refund = Refund.createPeriodCancellation(
            1L, 2L, RefundType.CANCELLATION_BEFORE_START, 10_000L
        );
        assertThat(PublicIdFormat.isUuidV4(refund.getPublicId())).isTrue();
        refund.addSuccessfulAmount(4_000L, LocalDateTime.of(2026, 9, 6, 12, 0));
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);

        LocalDateTime completedAt = LocalDateTime.of(2026, 9, 6, 12, 1);
        refund.addSuccessfulAmount(6_000L, completedAt);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getSuccessfulRefundAmount()).isEqualTo(10_000L);
        assertThat(refund.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void 첫실패와_부분성공뒤실패를_서로_다르게_보존한다() {
        Refund failed = Refund.createPeriodCancellation(
            1L, 2L, RefundType.CANCELLATION_BEFORE_START, 10_000L
        );
        failed.markFailed("declined");
        assertThat(failed.getStatus()).isEqualTo(RefundStatus.FAILED);
        failed.retry();
        assertThat(failed.getStatus()).isEqualTo(RefundStatus.PENDING);

        Refund review = Refund.createPeriodCancellation(
            1L, 3L, RefundType.NEXT_PERIOD_FULL_CANCELLATION, 10_000L
        );
        review.addSuccessfulAmount(4_000L, LocalDateTime.now());
        review.markFailed("second cancellation declined");
        assertThat(review.getStatus()).isEqualTo(RefundStatus.REVIEW_REQUIRED);
        assertThatThrownBy(review::retry).isInstanceOf(IllegalStateException.class);
    }
}
