package com.chapchap.subscription.domain.terms.service;

import com.chapchap.subscription.domain.terms.entity.Terms;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.entity.SubscriptionContractTermsAgreement;
import com.chapchap.subscription.domain.terms.repository.SubscriptionContractTermsAgreementRepository;
import com.chapchap.subscription.domain.terms.repository.TermsRepository;
import com.chapchap.subscription.domain.terms.repository.UserTermsAgreementRepository;
import com.chapchap.subscription.domain.terms.request.RequiredTermsAgreementRequest;
import com.chapchap.subscription.domain.terms.request.TermsAgreementRequest;
import com.chapchap.subscription.domain.terms.response.RequiredTermsAgreementResponse;
import com.chapchap.subscription.domain.terms.response.RequiredTermsResponse;
import com.chapchap.subscription.domain.terms.response.TermsAgreementResponse;
import com.chapchap.subscription.domain.terms.response.TermsCurrentResponse;
import com.chapchap.subscription.global.exception.terms.CurrentRequiredTermsNotFoundException;
import com.chapchap.subscription.global.exception.terms.TermsAgreementRequiredException;
import com.chapchap.subscription.global.exception.terms.TermsVersionMismatchException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsService {

    // 상수 선언
    private static final String NON_FACE_TO_FACE_STORAGE =
            "NON_FACE_TO_FACE_STORAGE";

    private static final ZoneId KST_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private final TermsRepository termsRepository;
    private final UserTermsAgreementRepository userTermsAgreementRepository;
    private final SubscriptionContractTermsAgreementRepository contractTermsAgreementRepository;
    private final PlatformTransactionManager transactionManager;

    // 현재 적용 중인 약관의 데이터 받아오기
    public TermsCurrentResponse getCurrentTerms() {
        Terms terms = getCurrentNonFaceToFaceRequiredTerms();

        return new TermsCurrentResponse(
                terms.getTitle(),
                terms.getContent(),
                terms.getVersionNumber()
        );
    }

    /** 현재 적용 중인 필수 약관의 고객 동의 기록을 조회하고, 없으면 TERMS_003을 발생시킨다. */
    public UserTermsAgreement requireCurrentAgreement(Long userId) {
        Terms terms = getCurrentNonFaceToFaceRequiredTerms();

        return userTermsAgreementRepository
                .findByUserIdAndTermsId(userId, terms.getId())
                .orElseThrow(TermsAgreementRequiredException::new);
    }

    /** 첫 구독과 첫 결제 전, 현재 적용되는 모든 필수 약관의 동의를 요구한다. */
    public List<UserTermsAgreement> requireAllCurrentRequiredAgreements(Long userId) {
        List<Terms> terms = getAllCurrentRequiredTerms();

        return terms.stream()
                .map(currentTerms -> userTermsAgreementRepository
                        .findByUserIdAndTermsId(userId, currentTerms.getId())
                        .orElseThrow(TermsAgreementRequiredException::new))
                .toList();
    }

    /** 구독 계약 시점에 실제 적용된 고객 약관 동의 기록을 관계 테이블에 보존한다. */
    @Transactional
    public void preserveContractTermsAgreements(
            Long subscriptionId,
            List<UserTermsAgreement> agreements
    ) {
        agreements.forEach(agreement -> {
            if (!contractTermsAgreementRepository.existsBySubscriptionIdAndUserTermsAgreementId(
                    subscriptionId, agreement.getId()
            )) {
                contractTermsAgreementRepository.save(
                        SubscriptionContractTermsAgreement.create(subscriptionId, agreement.getId())
                );
            }
        });
    }

    /** 현재 적용 중인 모든 필수 약관을 고객 화면에 제공한다. */
    public List<RequiredTermsResponse> getCurrentRequiredTerms() {
        return getAllCurrentRequiredTerms().stream()
                .map(terms -> new RequiredTermsResponse(
                        terms.getTermsType(), terms.getTitle(), terms.getContent(), terms.getVersionNumber()
                ))
                .toList();
    }

    // 동의한 내역이 있는지 판단하고 처리
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TermsAgreementResponse agreeTerms(
            Long userId,
            TermsAgreementRequest request
    ) {
        Terms terms = getCurrentNonFaceToFaceRequiredTerms();

        // 요정 정보에 담긴 버전과 현재 약관의 버전 비교
        validateVersion(
                terms,
                request.version()
        );

        // 동의 내역이 존재하는지 여부 파악하고, 각각에 맞는 처리
        UserTermsAgreement existingAgreement =
                userTermsAgreementRepository
                        .findByUserIdAndTermsId(
                                userId,
                                terms.getId()
                        )
                        .orElse(null);

        // 존재하면 동의한 버전과 시간 반환
        if (existingAgreement != null) {
            return toAgreementResponse(
                    terms,
                    existingAgreement
            );
        }

        // 내역 없으면 새로운 데이터(고객이 동의함) 생성해 반환, 중복 오류가 나면 기존의 동의내역 반환
            // 동의한 시간
        LocalDateTime agreedAt =
                LocalDateTime.now(KST_ZONE_ID)
                        .truncatedTo(ChronoUnit.MICROS);

            // 동의 내역이 없으면 새 동의 데이터 생성 및 반환
        try {
            UserTermsAgreement savedAgreement =
                    createAgreementInNewTransaction(
                            userId,
                            terms.getId(),
                            agreedAt
                    );

            return toAgreementResponse(
                    terms,
                    savedAgreement
            );

            // 중복 INSERT 오류나면 기존의 동의내역 반환
        } catch (DataIntegrityViolationException e) {
            UserTermsAgreement concurrentAgreement =
                    userTermsAgreementRepository
                            .findByUserIdAndTermsId(
                                    userId,
                                    terms.getId()
                            )
                            .orElseThrow(() -> e);

            return toAgreementResponse(
                    terms,
                    concurrentAgreement
            );
        }
    }

    /** 현재 적용 중인 특정 필수 약관에 동의한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RequiredTermsAgreementResponse agreeRequiredTerms(
            Long userId,
            RequiredTermsAgreementRequest request
    ) {
        Terms terms = getAllCurrentRequiredTerms().stream()
                .filter(currentTerms -> currentTerms.getTermsType().equals(request.termsType()))
                .findFirst()
                .orElseThrow(CurrentRequiredTermsNotFoundException::new);
        validateVersion(terms, request.version());
        UserTermsAgreement agreement = findOrCreateAgreement(userId, terms);

        return new RequiredTermsAgreementResponse(
                terms.getTermsType(), terms.getVersionNumber(), toOffsetDateTime(agreement)
        );
    }

    // ----------------------------------------------------------------------------------------
    // 현재 약관 불러오기
    private Terms getCurrentNonFaceToFaceRequiredTerms() {
        return termsRepository
                .findByTermsTypeAndIsCurrentTrueAndIsRequiredTrue(
                        NON_FACE_TO_FACE_STORAGE
                )
                .orElseThrow(
                        CurrentRequiredTermsNotFoundException::new
                );
    }

    private List<Terms> getAllCurrentRequiredTerms() {
        List<Terms> terms = termsRepository.findAllByIsCurrentTrueAndIsRequiredTrueOrderByTermsTypeAsc();
        if (terms.isEmpty()) {
            throw new CurrentRequiredTermsNotFoundException();
        }
        return terms;
    }

    // 요정 정보에 담긴 버전과 현재 약관의 버전 비교
    private void validateVersion(
            Terms terms,
            Integer requestedVersion
    ) {
        if (!Objects.equals(
                terms.getVersionNumber(),
                requestedVersion
        )) {
            throw new TermsVersionMismatchException();
        }
    }

    // TermsAgreementResponse: agreement는 OffsetDateTime 타입이므로 타입 변경
    private TermsAgreementResponse toAgreementResponse(
            Terms terms,
            UserTermsAgreement agreement
    ) {
        return new TermsAgreementResponse(
                terms.getVersionNumber(),
                toOffsetDateTime(agreement)
        );
    }

    private java.time.OffsetDateTime toOffsetDateTime(UserTermsAgreement agreement) {
        return agreement.getAgreedAt().atZone(KST_ZONE_ID).toOffsetDateTime();
    }

    private UserTermsAgreement findOrCreateAgreement(Long userId, Terms terms) {
        UserTermsAgreement existingAgreement = userTermsAgreementRepository
                .findByUserIdAndTermsId(userId, terms.getId())
                .orElse(null);
        if (existingAgreement != null) {
            return existingAgreement;
        }

        LocalDateTime agreedAt = LocalDateTime.now(KST_ZONE_ID).truncatedTo(ChronoUnit.MICROS);
        try {
            return createAgreementInNewTransaction(userId, terms.getId(), agreedAt);
        } catch (DataIntegrityViolationException e) {
            return userTermsAgreementRepository.findByUserIdAndTermsId(userId, terms.getId())
                    .orElseThrow(() -> e);
        }
    }

    // `새로 동의한 내역 데이터를 생성하는 처리`(INSERT)를 하나의 transaction 단위로 묶음
        // 동시성 처리: 동시에 인서트 요청 왔을 때 대비함
    private UserTermsAgreement createAgreementInNewTransaction(
            Long userId,
            Long termsId,
            LocalDateTime agreedAt
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );

        return transactionTemplate.execute(status -> {
            UserTermsAgreement agreement =
                    UserTermsAgreement.create(
                            userId,
                            termsId,
                            agreedAt
                    );

            return userTermsAgreementRepository
                    .saveAndFlush(agreement);
        });
    }
}
