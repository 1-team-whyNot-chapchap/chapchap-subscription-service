package com.chapchap.subscription.domain.payment.response;

import com.chapchap.subscription.domain.payment.entity.PaymentAttemptResult;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 결제 거래 당시 금액·기간과 외부 처리 시도의 비민감 정보를 제공한다. */
@Schema(description = "결제 거래 상세와 처리 시도 이력")
public record PaymentDetailResponse(
    @Schema(description = "결제 거래의 공개 UUID", format = "uuid", example = "ce03468a-6eb1-4e55-9f23-68a3404f0fca")
    String paymentId,
    @Schema(description = "결제 거래 유형")
    PaymentTransactionType paymentType,
    @Schema(description = "결제 거래 처리 상태")
    PaymentTransactionStatus status,
    @Schema(description = "해당 결제 거래 금액(원)", example = "32700")
    Long amount,
    @Schema(description = "결제 거래 발생 시각", example = "2026-09-10T15:30:00")
    LocalDateTime occurredAt,
    @Schema(description = "원 결제 금액(원)", example = "98100")
    Long originalPaymentAmount,
    @Schema(description = "누적 취소 금액(원)", example = "32700")
    Long cumulativeCancelAmount,
    @Schema(description = "추가 취소 가능한 금액(원)", example = "65400")
    Long cancelableAmount,
    @Schema(description = "결제 대상 이용 기간 시작일", example = "2026-09-14")
    LocalDate periodStartDate,
    @Schema(description = "결제 대상 이용 기간 종료일", example = "2026-10-11")
    LocalDate periodEndDate,
    @Schema(description = "외부 결제 처리 시도 이력. 시도 순번 오름차순")
    List<PaymentAttemptResponse> attempts
) {
    public PaymentDetailResponse {
        attempts = List.copyOf(attempts);
    }

    @Schema(description = "외부 결제 처리 시도 항목")
    public record PaymentAttemptResponse(
        @Schema(description = "결제 처리 시도 순번", example = "1")
        Integer attemptSequence,
        @Schema(description = "외부 결제사에 요청한 금액(원)", example = "32700")
        Long requestedAmount,
        @Schema(description = "결제 처리 요청 시각", example = "2026-09-10T15:30:00")
        LocalDateTime requestedAt,
        @Schema(description = "외부 결제사 응답 시각", nullable = true, example = "2026-09-10T15:30:01")
        LocalDateTime respondedAt,
        @Schema(description = "결제 처리 시도 결과")
        PaymentAttemptResult result,
        @Schema(description = "카드사명", nullable = true, example = "현대카드")
        String cardCompany,
        @Schema(description = "고객 표시용 마스킹 카드번호", nullable = true, example = "****-****-****-1234")
        String maskedCardNumber
    ) {
    }
}
