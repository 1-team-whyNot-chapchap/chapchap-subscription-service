package com.chapchap.subscription.domain.subscription.controller;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.request.SettingChangeRequest;
import com.chapchap.subscription.domain.subscription.response.SettingChangeResponse;
import com.chapchap.subscription.domain.subscription.service.CurrentSubscriptionQueryService;
import com.chapchap.subscription.domain.subscription.service.FirstSubscriptionService;
import com.chapchap.subscription.domain.subscription.service.SettingChangeService;
import com.chapchap.subscription.domain.subscription.service.SubscriptionCancellationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingChangeControllerTest {
    @Test
    void 인증고객의_설정변경을_요청한다() {
        SettingChangeService service = mock(SettingChangeService.class);
        SubscriptionController controller = controller(service);
        Authentication authentication = mock(Authentication.class);
        SettingChangeRequest request = mock(SettingChangeRequest.class);
        SettingChangeResponse expected = response(true);
        when(authentication.getName()).thenReturn("10");
        when(service.change(10L, request)).thenReturn(expected);

        var result = controller.changeSetting(authentication, request);

        assertThat(result.data()).isSameAs(expected);
        verify(service).change(10L, request);
    }

    @Test
    void 인증고객의_증액결제확인을_요청한다() {
        SettingChangeService service = mock(SettingChangeService.class);
        SubscriptionController controller = controller(service);
        Authentication authentication = mock(Authentication.class);
        SettingChangeResponse expected = response(false);
        when(authentication.getName()).thenReturn("10");
        when(service.confirm(10L)).thenReturn(expected);

        var result = controller.confirmSettingChange(authentication);

        assertThat(result.data()).isSameAs(expected);
        verify(service).confirm(10L);
    }

    private SubscriptionController controller(SettingChangeService service) {
        return new SubscriptionController(mock(FirstSubscriptionService.class),
            mock(CurrentSubscriptionQueryService.class), mock(SubscriptionCancellationService.class), service);
    }

    private SettingChangeResponse response(boolean confirmationRequired) {
        return new SettingChangeResponse(confirmationRequired ? SubscriptionSettingStatus.CHANGE_PENDING
            : SubscriptionSettingStatus.ACTIVE, SettingChangeResponse.DifferenceType.INCREASE, 1_000L,
            LocalDate.of(2026, 9, 8), confirmationRequired, null, null);
    }
}
