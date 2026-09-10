package com.chapchap.subscription.domain.terms.controller;

import com.chapchap.subscription.domain.terms.response.RequiredTermsAgreementResponse;
import com.chapchap.subscription.domain.terms.response.RequiredTermsResponse;
import com.chapchap.subscription.domain.terms.service.TermsService;
import com.chapchap.subscription.global.exception.GlobalExceptionHandler;
import com.chapchap.subscription.global.security.filter.HeaderAuthenticationFilter;
import com.chapchap.subscription.global.security.filter.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RequiredTermsController.class)
@Import({SecurityConfiguration.class, HeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class RequiredTermsControllerTest {

    private static final String BASE_PATH = "/api/subscription/terms";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TermsService termsService;

    @Test
    void 인증_사용자는_현재_필수_약관_전체를_조회한다() throws Exception {
        when(termsService.getCurrentRequiredTerms()).thenReturn(List.of(
                new RequiredTermsResponse("NON_FACE_TO_FACE_STORAGE", "비대면 보관 약관", "본문", 1),
                new RequiredTermsResponse("SUBSCRIPTION_SERVICE_TERMS", "구독 서비스 이용 약관", "본문", 1)
        ));

        mockMvc.perform(get(BASE_PATH + "/required").with(user("10").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].termsType").value("NON_FACE_TO_FACE_STORAGE"))
                .andExpect(jsonPath("$.data[1].termsType").value("SUBSCRIPTION_SERVICE_TERMS"));
    }

    @Test
    void 인증_사용자는_약관_유형과_버전으로_동의한다() throws Exception {
        when(termsService.agreeRequiredTerms(
                argThat(userId -> userId == 10L),
                argThat(request -> request.termsType().equals("SUBSCRIPTION_SERVICE_TERMS") && request.version() == 1)
        )).thenReturn(new RequiredTermsAgreementResponse(
                "SUBSCRIPTION_SERVICE_TERMS", 1, OffsetDateTime.parse("2026-09-10T10:30:15+09:00")
        ));

        mockMvc.perform(post(BASE_PATH + "/agreements")
                        .with(user("10").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsType\":\"SUBSCRIPTION_SERVICE_TERMS\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.termsType").value("SUBSCRIPTION_SERVICE_TERMS"))
                .andExpect(jsonPath("$.data.version").value(1));

        verify(termsService).agreeRequiredTerms(
                argThat(userId -> userId == 10L),
                argThat(request -> request.termsType().equals("SUBSCRIPTION_SERVICE_TERMS") && request.version() == 1)
        );
    }
}
