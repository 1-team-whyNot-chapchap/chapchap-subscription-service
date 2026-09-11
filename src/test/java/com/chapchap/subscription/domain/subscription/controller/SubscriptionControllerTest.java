package com.chapchap.subscription.domain.subscription.controller;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.request.FirstSubscriptionRequest;
import com.chapchap.subscription.domain.subscription.response.CurrentSubscriptionResponse;
import com.chapchap.subscription.domain.subscription.response.FirstSubscriptionPreviewResponse;
import com.chapchap.subscription.domain.subscription.response.SubscriptionCancellationResponse;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.service.SubscriptionCancellationType;
import java.time.LocalDateTime;
import com.chapchap.subscription.domain.subscription.service.CurrentSubscriptionQueryService;
import com.chapchap.subscription.domain.subscription.service.FirstSubscriptionPreparationService;
import com.chapchap.subscription.domain.subscription.service.FirstSubscriptionService;
import com.chapchap.subscription.domain.subscription.service.SubscriptionCancellationService;
import com.chapchap.subscription.domain.subscription.service.SettingChangeService;
import com.chapchap.subscription.domain.subscription.service.SettingChangePreviewService;
import com.chapchap.subscription.global.response.GlobalResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionControllerTest {

    @Test
    void 인증_사용자_ID로_첫_구독_예상금액을_조회한다() {
        FirstSubscriptionPreparationService preparationService = mock(FirstSubscriptionPreparationService.class);
        SubscriptionController controller = new SubscriptionController(
            mock(FirstSubscriptionService.class), preparationService,
            mock(CurrentSubscriptionQueryService.class), mock(SubscriptionCancellationService.class),
            mock(SettingChangeService.class), mock(SettingChangePreviewService.class)
        );
        Authentication authentication = mock(Authentication.class);
        FirstSubscriptionRequest request = mock(FirstSubscriptionRequest.class);
        FirstSubscriptionPreviewResponse expected = new FirstSubscriptionPreviewResponse(
            java.time.LocalDate.of(2026, 9, 14), java.time.LocalDate.of(2026, 10, 11),
            71_200L, 12_000L, 10_680L, 72_520L
        );
        when(authentication.getName()).thenReturn("10");
        when(preparationService.preview(10L, request)).thenReturn(expected);

        GlobalResponse<FirstSubscriptionPreviewResponse> response = controller.preview(authentication, request);

        assertThat(response.code()).isEqualTo("00");
        assertThat(response.data()).isSameAs(expected);
        verify(preparationService).preview(10L, request);
    }

    @Test
    void 인증_사용자_ID로_구독_해지를_요청한다() {
        SubscriptionCancellationService cancellationService = mock(SubscriptionCancellationService.class);
        SubscriptionController controller = new SubscriptionController(
            mock(FirstSubscriptionService.class), mock(FirstSubscriptionPreparationService.class),
            mock(CurrentSubscriptionQueryService.class), cancellationService,
            mock(SettingChangeService.class), mock(SettingChangePreviewService.class)
        );
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("10");
        SubscriptionCancellationResponse expected = new SubscriptionCancellationResponse(
            SubscriptionCancellationType.REGULAR_CANCELLATION,
            SubscriptionStatus.CANCELLATION_SCHEDULED,
            SubscriptionPeriodStatus.IN_PROGRESS,
            LocalDateTime.of(2026, 9, 6, 15, 0),
            null
        );
        when(cancellationService.cancel(10L)).thenReturn(expected);

        GlobalResponse<SubscriptionCancellationResponse> response = controller.cancel(authentication);

        assertThat(response.data()).isSameAs(expected);
        verify(cancellationService).cancel(10L);
    }

    @Test
    void 인증_사용자_ID로_현재_구독을_조회한다() {
        FirstSubscriptionService firstSubscriptionService = mock(FirstSubscriptionService.class);
        CurrentSubscriptionQueryService queryService = mock(CurrentSubscriptionQueryService.class);
        SubscriptionController controller = new SubscriptionController(
                firstSubscriptionService,
                mock(FirstSubscriptionPreparationService.class),
                queryService,
                 mock(SubscriptionCancellationService.class),
                 mock(SettingChangeService.class), mock(SettingChangePreviewService.class)
        );
        Authentication authentication = mock(Authentication.class);
        CurrentSubscriptionResponse current = new CurrentSubscriptionResponse(
                "11111111-1111-4111-8111-111111111111",
                SubscriptionStatus.ENDED,
                null,
                null,
                null,
                null,
                List.of()
        );
        when(authentication.getName()).thenReturn("10");
        when(queryService.getCurrentSubscription(10L)).thenReturn(current);

        GlobalResponse<CurrentSubscriptionResponse> response =
                controller.getCurrentSubscription(authentication);

        assertThat(response.code()).isEqualTo("00");
        assertThat(response.data()).isSameAs(current);
        verify(queryService).getCurrentSubscription(10L);
    }

    @Test
    void 구독이_없으면_성공_응답의_data가_null이다() {
        FirstSubscriptionService firstSubscriptionService = mock(FirstSubscriptionService.class);
        CurrentSubscriptionQueryService queryService = mock(CurrentSubscriptionQueryService.class);
        SubscriptionController controller = new SubscriptionController(
                firstSubscriptionService,
                mock(FirstSubscriptionPreparationService.class),
                queryService,
                 mock(SubscriptionCancellationService.class),
                 mock(SettingChangeService.class), mock(SettingChangePreviewService.class)
        );
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("10");
        when(queryService.getCurrentSubscription(10L)).thenReturn(null);

        GlobalResponse<CurrentSubscriptionResponse> response =
                controller.getCurrentSubscription(authentication);

        assertThat(response.code()).isEqualTo("00");
        assertThat(response.data()).isNull();
    }
}
