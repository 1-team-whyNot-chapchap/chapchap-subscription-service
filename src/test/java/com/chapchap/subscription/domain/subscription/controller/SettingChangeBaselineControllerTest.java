package com.chapchap.subscription.domain.subscription.controller;

import com.chapchap.subscription.domain.subscription.service.SettingChangeBaselineQueryService;
import com.chapchap.subscription.global.config.openapi.CustomApiResponse;
import com.chapchap.subscription.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingChangeBaselineControllerTest {
    @Test
    void 인증사용자만_읽기전용_서비스로_전달하고_문서화한다() throws Exception {
        var service = mock(SettingChangeBaselineQueryService.class);
        var auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("12");
        var response = new SettingChangeBaselineController(service).getBaseline(auth);
        verify(service).getBaseline(12L);
        assertThat(response.code()).isEqualTo("00");
        var method = SettingChangeBaselineController.class.getMethod("getBaseline", Authentication.class);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(
            "/api/subscription/subscriptions/setting-changes/baseline");
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
        assertThat(method.getAnnotation(CustomApiResponse.class).value())
            .contains(ErrorCode.SUBSCRIPTION_NOT_FOUND, ErrorCode.SUBSCRIPTION_CHANGE_IN_PROGRESS)
            .doesNotContain(ErrorCode.CURRENT_PAYMENT_METHOD_REQUIRED);
        assertThat(SettingChangeBaselineQueryService.class.getAnnotation(Transactional.class).readOnly()).isTrue();
    }
}
