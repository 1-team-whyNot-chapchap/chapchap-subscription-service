package com.chapchap.subscription.domain.terms.service;

import com.chapchap.subscription.domain.terms.entity.Terms;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.repository.TermsRepository;
import com.chapchap.subscription.domain.terms.repository.UserTermsAgreementRepository;
import com.chapchap.subscription.domain.terms.request.TermsAgreementRequest;
import com.chapchap.subscription.domain.terms.response.TermsAgreementResponse;
import com.chapchap.subscription.domain.terms.response.TermsCurrentResponse;
import com.chapchap.subscription.global.exception.ErrorCode;
import com.chapchap.subscription.global.exception.terms.CurrentRequiredTermsNotFoundException;
import com.chapchap.subscription.global.exception.terms.TermsAgreementRequiredException;
import com.chapchap.subscription.global.exception.terms.TermsVersionMismatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermsServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long TERMS_ID = 1L;
    private static final Integer CURRENT_VERSION = 1;
    private static final LocalDateTime AGREED_AT = LocalDateTime.of(2026, 9, 2, 10, 30, 15, 123_456_000);

    @Mock private TermsRepository termsRepository;
    @Mock private UserTermsAgreementRepository agreementRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    private TermsService termsService;

    @BeforeEach
    void setUp() {
        termsService = new TermsService(termsRepository, agreementRepository, transactionManager);
    }

    @Test
    void 현재_필수_약관의_제목_내용_버전을_조회한다() {
        Terms terms = currentTerms();
        when(termsRepository.findByTermsTypeAndIsCurrentTrueAndIsRequiredTrue(
                "NON_FACE_TO_FACE_STORAGE"
        )).thenReturn(Optional.of(terms));

        TermsCurrentResponse response = termsService.getCurrentTerms();

        assertThat(response.title()).isEqualTo("비대면 보관 약관");
        assertThat(response.content()).isEqualTo("문 앞 보관에 동의합니다.");
        assertThat(response.version()).isEqualTo(CURRENT_VERSION);
    }

    @Test
    void 현재_필수_약관이_없으면_TERMS_001을_발생시킨다() {
        when(termsRepository.findByTermsTypeAndIsCurrentTrueAndIsRequiredTrue(
                "NON_FACE_TO_FACE_STORAGE"
        )).thenReturn(Optional.empty());

        assertThatThrownBy(termsService::getCurrentTerms)
                .isInstanceOf(CurrentRequiredTermsNotFoundException.class)
                .satisfies(exception -> assertThat(((CurrentRequiredTermsNotFoundException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CURRENT_REQUIRED_TERMS_NOT_FOUND));
    }

    @Test
    void 현재_약관에_처음_동의하면_새_기록을_저장하고_KST_응답을_반환한다() {
        Terms terms = currentTerms();
        UserTermsAgreement saved = agreement(AGREED_AT);
        stubCurrentTerms(terms);
        when(agreementRepository.findByUserIdAndTermsId(USER_ID, TERMS_ID)).thenReturn(Optional.empty());
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(agreementRepository.saveAndFlush(any(UserTermsAgreement.class))).thenReturn(saved);

        TermsAgreementResponse response = termsService.agreeTerms(USER_ID, new TermsAgreementRequest(CURRENT_VERSION));

        assertThat(response.version()).isEqualTo(CURRENT_VERSION);
        assertThat(response.agreedAt().toLocalDateTime()).isEqualTo(AGREED_AT);
        assertThat(response.agreedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        verify(transactionManager).commit(transactionStatus);
        verify(agreementRepository).saveAndFlush(any(UserTermsAgreement.class));
    }

    @Test
    void 이미_동의했다면_새로_저장하지_않고_최초_동의_시각을_재사용한다() {
        Terms terms = currentTerms();
        UserTermsAgreement existing = agreement(AGREED_AT);
        stubCurrentTerms(terms);
        when(agreementRepository.findByUserIdAndTermsId(USER_ID, TERMS_ID)).thenReturn(Optional.of(existing));

        TermsAgreementResponse response = termsService.agreeTerms(USER_ID, new TermsAgreementRequest(CURRENT_VERSION));

        assertThat(response.agreedAt().toLocalDateTime()).isEqualTo(AGREED_AT);
        verify(agreementRepository, never()).saveAndFlush(any());
        verify(transactionManager, never()).getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void 요청_버전이_현재_버전과_다르면_TERMS_002이고_저장하지_않는다() {
        stubCurrentTerms(currentTerms());

        assertThatThrownBy(() -> termsService.agreeTerms(USER_ID, new TermsAgreementRequest(2)))
                .isInstanceOf(TermsVersionMismatchException.class)
                .satisfies(exception -> assertThat(((TermsVersionMismatchException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.TERMS_VERSION_MISMATCH));

        verify(agreementRepository, never()).findByUserIdAndTermsId(any(), any());
        verify(agreementRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_INSERT_UNIQUE_충돌이면_다른_요청이_저장한_동의를_반환한다() {
        Terms terms = currentTerms();
        UserTermsAgreement concurrent = agreement(AGREED_AT);
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("concurrent agreement");
        stubCurrentTerms(terms);
        when(agreementRepository.findByUserIdAndTermsId(USER_ID, TERMS_ID))
                .thenReturn(Optional.empty(), Optional.of(concurrent));
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(agreementRepository.saveAndFlush(any(UserTermsAgreement.class))).thenThrow(conflict);

        TermsAgreementResponse response = termsService.agreeTerms(USER_ID, new TermsAgreementRequest(CURRENT_VERSION));

        assertThat(response.agreedAt().toLocalDateTime()).isEqualTo(AGREED_AT);
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void UNIQUE_충돌_후에도_동의가_없으면_원래_DB_예외를_숨기지_않는다() {
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("unrelated constraint");
        stubCurrentTerms(currentTerms());
        when(agreementRepository.findByUserIdAndTermsId(USER_ID, TERMS_ID))
                .thenReturn(Optional.empty(), Optional.empty());
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(agreementRepository.saveAndFlush(any(UserTermsAgreement.class))).thenThrow(conflict);

        assertThatThrownBy(() -> termsService.agreeTerms(USER_ID, new TermsAgreementRequest(CURRENT_VERSION)))
                .isSameAs(conflict);

        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void 현재_필수_약관_동의가_있으면_해당_기록을_반환한다() {
        Terms terms = currentTerms();
        UserTermsAgreement agreement = agreement(AGREED_AT);
        stubCurrentTerms(terms);
        when(agreementRepository.findByUserIdAndTermsId(USER_ID, TERMS_ID)).thenReturn(Optional.of(agreement));

        assertThat(termsService.requireCurrentAgreement(USER_ID)).isSameAs(agreement);
    }

    @Test
    void 현재_필수_약관_동의가_없으면_TERMS_003을_발생시킨다() {
        stubCurrentTerms(currentTerms());
        when(agreementRepository.findByUserIdAndTermsId(USER_ID, TERMS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> termsService.requireCurrentAgreement(USER_ID))
                .isInstanceOf(TermsAgreementRequiredException.class)
                .satisfies(exception -> assertThat(((TermsAgreementRequiredException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.TERMS_AGREEMENT_REQUIRED));
    }

    private void stubCurrentTerms(Terms terms) {
        when(termsRepository.findByTermsTypeAndIsCurrentTrueAndIsRequiredTrue(
                "NON_FACE_TO_FACE_STORAGE"
        )).thenReturn(Optional.of(terms));
    }

    private Terms currentTerms() {
        Terms terms = mock(Terms.class);
        lenient().when(terms.getId()).thenReturn(TERMS_ID);
        lenient().when(terms.getVersionNumber()).thenReturn(CURRENT_VERSION);
        lenient().when(terms.getTitle()).thenReturn("비대면 보관 약관");
        lenient().when(terms.getContent()).thenReturn("문 앞 보관에 동의합니다.");
        return terms;
    }

    private UserTermsAgreement agreement(LocalDateTime agreedAt) {
        return UserTermsAgreement.create(USER_ID, TERMS_ID, agreedAt);
    }
}
