package com.chapchap.subscription.domain.terms.controller;

import com.chapchap.subscription.domain.terms.response.TermsAgreementResponse;
import com.chapchap.subscription.domain.terms.response.TermsCurrentResponse;
import com.chapchap.subscription.domain.terms.service.TermsService;
import com.chapchap.subscription.global.exception.GlobalExceptionHandler;
import com.chapchap.subscription.global.exception.terms.CurrentRequiredTermsNotFoundException;
import com.chapchap.subscription.global.exception.terms.TermsVersionMismatchException;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TermsController.class)
@Import({SecurityConfiguration.class, HeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class TermsControllerTest {

    private static final String BASE_PATH = "/api/subscription/terms/non-face-to-face";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TermsService termsService;

    @Test
    void 인증_사용자는_현재_필수_약관을_조회한다() throws Exception {
        when(termsService.getCurrentTerms()).thenReturn(
                new TermsCurrentResponse("비대면 보관 약관", "문 앞 보관에 동의합니다.", 1)
        );

        mockMvc.perform(get(BASE_PATH)
                        .with(user("10").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.title").value("비대면 보관 약관"))
                .andExpect(jsonPath("$.data.content").value("문 앞 보관에 동의합니다."))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.termsId").doesNotExist());
    }

    @Test
    void 인증_사용자는_현재_버전의_약관에_동의한다() throws Exception {
        when(termsService.agreeTerms(argThat(userId -> userId == 10L), argThat(request -> request.version() == 1)))
                .thenReturn(new TermsAgreementResponse(1, OffsetDateTime.parse("2026-09-02T10:30:15.123456+09:00")));

        mockMvc.perform(post(BASE_PATH + "/agreements")
                        .with(user("10").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.agreedAt").value("2026-09-02T10:30:15.123456+09:00"))
                .andExpect(jsonPath("$.data.termsId").doesNotExist())
                .andExpect(jsonPath("$.data.agreementId").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist());

        verify(termsService).agreeTerms(argThat(userId -> userId == 10L), argThat(request -> request.version() == 1));
    }

    @Test
    void 인증하지_않으면_약관_조회와_동의를_AUTH_001로_거절한다() throws Exception {
        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));

        mockMvc.perform(post(BASE_PATH + "/agreements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void 누락_null_0_버전은_COMMON_001로_거절한다() throws Exception {
        assertInvalidVersion("{}");
        assertInvalidVersion("{\"version\":null}");
        assertInvalidVersion("{\"version\":0}");
    }

    @Test
    void 현재_필수_약관이_없으면_TERMS_001과_500을_반환한다() throws Exception {
        when(termsService.getCurrentTerms()).thenThrow(new CurrentRequiredTermsNotFoundException());

        mockMvc.perform(get(BASE_PATH)
                        .with(user("10").roles("USER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("TERMS_001"));
    }

    @Test
    void 약관_버전이_다르면_TERMS_002와_409를_반환한다() throws Exception {
        when(termsService.agreeTerms(argThat(userId -> userId == 10L), argThat(request -> request.version() == 2)))
                .thenThrow(new TermsVersionMismatchException());

        mockMvc.perform(post(BASE_PATH + "/agreements")
                        .with(user("10").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TERMS_002"));
    }

    private void assertInvalidVersion(String content) throws Exception {
        mockMvc.perform(post(BASE_PATH + "/agreements")
                        .with(user("10").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }
}
