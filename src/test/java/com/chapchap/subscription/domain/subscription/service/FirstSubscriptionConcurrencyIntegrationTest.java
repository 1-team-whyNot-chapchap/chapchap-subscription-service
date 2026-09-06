package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentClient;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentResult;
import com.chapchap.subscription.domain.payment.entity.PaymentMethod;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.repository.PaymentMethodRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.security.BillingKeyProtector;
import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.domain.subscription.entity.Menu;
import com.chapchap.subscription.domain.subscription.entity.Plan;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.repository.MenuRepository;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.request.FirstSubscriptionRequest;
import com.chapchap.subscription.domain.subscription.response.FirstSubscriptionResponse;
import com.chapchap.subscription.domain.terms.entity.Terms;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.repository.TermsRepository;
import com.chapchap.subscription.domain.terms.repository.UserTermsAgreementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("local")
class FirstSubscriptionConcurrencyIntegrationTest {
    private static final String TERMS_TYPE = "NON_FACE_TO_FACE_STORAGE";
    private static final int CONCURRENCY_TIMEOUT_SECONDS = 10;

    @Autowired private FirstSubscriptionService firstSubscriptionService;
    @Autowired private FirstSubscriptionPreparationService firstSubscriptionPreparationService;
    @Autowired private PlanRepository planRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private TermsRepository termsRepository;
    @Autowired private UserTermsAgreementRepository userTermsAgreementRepository;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionPeriodRepository subscriptionPeriodRepository;
    @Autowired private BillingKeyProtector billingKeyProtector;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AutomaticPaymentClient automaticPaymentClient;

    private long userId;
    private String planPublicId;
    private String addressPublicId;

    @BeforeEach
    void setUp() {
        String fixtureSuffix = UUID.randomUUID().toString().substring(0, 8);
        userId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);

        Plan plan = plan(fixtureSuffix);
        planRepository.saveAndFlush(plan);
        menuRepository.saveAllAndFlush(menus(plan.getId(), fixtureSuffix));

        Address address = addressRepository.saveAndFlush(Address.create(
            userId,
            "동시성 통합 테스트 배송지",
            "테스트 수령인",
            "010-0000-0000",
            "00000",
            "테스트 주소",
            null,
            "DIRECT",
            null,
            null,
            true
        ));
        addressPublicId = address.getPublicId();

        Terms terms = termsRepository
            .findByTermsTypeAndIsCurrentTrueAndIsRequiredTrue(TERMS_TYPE)
            .orElseThrow();
        userTermsAgreementRepository.saveAndFlush(UserTermsAgreement.create(
            userId,
            terms.getId(),
            LocalDateTime.now()
        ));

        String protectedBillingKey = billingKeyProtector.protect(userId, "test-billing-key");
        paymentMethodRepository.saveAndFlush(PaymentMethod.createAsCurrent(
            userId,
            PaymentProviderCode.PORTONE,
            protectedBillingKey,
            "테스트 카드사",
            "****-****-****-0000",
            LocalDateTime.now()
        ));
    }

    @AfterEach
    void cleanUp() {
        if (userId > 0L) {
            deleteSubscriptionData(userId);
            jdbcTemplate.update("DELETE FROM payment_methods WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM addresses WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_terms_agreements WHERE user_id = ?", userId);
            assertThat(count("SELECT COUNT(*) FROM subscriptions WHERE user_id = ?", userId)).isZero();
            assertThat(count("SELECT COUNT(*) FROM payment_methods WHERE user_id = ?", userId)).isZero();
            assertThat(count("SELECT COUNT(*) FROM addresses WHERE user_id = ?", userId)).isZero();
            assertThat(count("SELECT COUNT(*) FROM user_terms_agreements WHERE user_id = ?", userId)).isZero();
        }
        if (planPublicId != null) {
            List<Long> planIds = jdbcTemplate.queryForList(
                "SELECT id FROM plans WHERE public_id = ?",
                Long.class,
                planPublicId
            );
            for (Long planId : planIds) {
                jdbcTemplate.update("DELETE FROM menus WHERE plan_id = ?", planId);
                jdbcTemplate.update("DELETE FROM plans WHERE id = ?", planId);
            }
            assertThat(count("SELECT COUNT(*) FROM plans WHERE public_id = ?", planPublicId)).isZero();
        }
    }

    @Test
    void 같은_고객의_첫_구독_동시_신청은_PG를_한_번만_호출하고_데이터를_한_세트만_생성한다() throws Exception {
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        CountDownLatch processingResponseReturned = new CountDownLatch(1);
        AtomicInteger providerCallCount = new AtomicInteger();

        when(automaticPaymentClient.pay(any())).thenAnswer(invocation -> {
            providerCallCount.incrementAndGet();
            providerEntered.countDown();
            assertThat(releaseProvider.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            String externalPaymentId = invocation.getArgument(
                0,
                com.chapchap.subscription.domain.payment.client.AutomaticPaymentRequest.class
            ).externalPaymentId();
            return AutomaticPaymentResult.success(externalPaymentId, "test-transaction", "PAID");
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<FirstSubscriptionResponse> first = executor.submit(() -> subscribeConcurrently(
                bothReady, start, processingResponseReturned
            ));
            Future<FirstSubscriptionResponse> second = executor.submit(() -> subscribeConcurrently(
                bothReady, start, processingResponseReturned
            ));

            assertThat(bothReady.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(providerEntered.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(processingResponseReturned.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            releaseProvider.countDown();

            List<FirstSubscriptionResponse> responses = List.of(
                first.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                second.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );

            assertThat(responses)
                .extracting(FirstSubscriptionResponse::subscriptionStatus)
                .containsExactlyInAnyOrder(
                    SubscriptionStatus.SCHEDULED,
                    SubscriptionStatus.AWAITING_CONFIRMATION
                );
            assertThat(responses)
                .extracting(FirstSubscriptionResponse::subscriptionId)
                .containsOnly(responses.getFirst().subscriptionId());
            assertThat(providerCallCount).hasValue(1);

            assertPersistedDataIsNotDuplicated();
        } finally {
            start.countDown();
            releaseProvider.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 이용_기간이_확정_대기가_아니면_PROCESSING_결제_거래가_있어도_복구하지_않는다() {
        LocalDateTime referenceAt = LocalDateTime.now();
        Subscription subscription = subscriptionRepository.saveAndFlush(Subscription.create(userId));
        SubscriptionPeriod period = SubscriptionPeriod.createAwaitingConfirmation(
            subscription.getId(),
            1,
            referenceAt.toLocalDate().plusDays(3),
            referenceAt
        );
        period.markPaymentFailed();
        subscriptionPeriodRepository.saveAndFlush(period);
        paymentTransactionRepository.saveAndFlush(PaymentTransaction.createFirstSubscriptionPayment(
            userId,
            subscription.getId(),
            period.getId(),
            10_000L,
            referenceAt,
            period.getPeriodStartDate(),
            period.getPeriodEndDate(),
            "FIRST-PAYMENT-" + UUID.randomUUID(),
            referenceAt
        ));

        assertThat(firstSubscriptionPreparationService.recoverConcurrentProcessing(userId)).isEmpty();
    }

    private FirstSubscriptionResponse subscribeConcurrently(
        CountDownLatch bothReady,
        CountDownLatch start,
        CountDownLatch processingResponseReturned
    ) throws InterruptedException {
        bothReady.countDown();
        assertThat(start.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        FirstSubscriptionResponse response = firstSubscriptionService.subscribe(userId, request());
        if (response.subscriptionStatus() == SubscriptionStatus.AWAITING_CONFIRMATION) {
            processingResponseReturned.countDown();
        }
        return response;
    }

    private void assertPersistedDataIsNotDuplicated() {
        assertThat(count("SELECT COUNT(*) FROM subscriptions WHERE user_id = ?", userId)).isEqualTo(1L);
        Long subscriptionId = jdbcTemplate.queryForObject(
            "SELECT id FROM subscriptions WHERE user_id = ?",
            Long.class,
            userId
        );
        assertThat(subscriptionId).isNotNull();

        assertThat(count("SELECT COUNT(*) FROM subscription_periods WHERE subscription_id = ?", subscriptionId))
            .isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM subscription_settings WHERE subscription_id = ?", subscriptionId))
            .isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM subscription_status_histories WHERE subscription_id = ?", subscriptionId))
            .isEqualTo(2L);
        assertThat(count("SELECT COUNT(*) FROM payment_transactions WHERE subscription_id = ?", subscriptionId))
            .isEqualTo(1L);

        Long periodId = jdbcTemplate.queryForObject(
            "SELECT id FROM subscription_periods WHERE subscription_id = ?",
            Long.class,
            subscriptionId
        );
        Long settingId = jdbcTemplate.queryForObject(
            "SELECT id FROM subscription_settings WHERE subscription_id = ?",
            Long.class,
            subscriptionId
        );
        Long transactionId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_transactions WHERE subscription_id = ?",
            Long.class,
            subscriptionId
        );

        assertThat(count(
            "SELECT COUNT(*) FROM subscription_delivery_conditions WHERE subscription_setting_id = ?",
            settingId
        )).isEqualTo(1L);
        long orderCount = count("SELECT COUNT(*) FROM orders WHERE subscription_period_id = ?", periodId);
        assertThat(orderCount).isPositive();
        assertThat(count("SELECT COUNT(*) FROM payment_attempts WHERE payment_transaction_id = ?", transactionId))
            .isEqualTo(1L);
        assertThat(count(
            "SELECT COUNT(*) FROM payment_allocations WHERE original_payment_transaction_id = ?",
            transactionId
        )).isEqualTo(orderCount);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM subscriptions WHERE id = ?",
            String.class,
            subscriptionId
        )).isEqualTo("SCHEDULED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM payment_transactions WHERE id = ?",
            String.class,
            transactionId
        )).isEqualTo("SUCCESS");
    }

    private FirstSubscriptionRequest request() {
        return new FirstSubscriptionRequest(
            planPublicId,
            List.of(new FirstSubscriptionRequest.DeliveryCondition(
                DeliveryWeekday.MONDAY,
                2,
                addressPublicId,
                DeliveryTimeSlot.TIME_1100_1300
            ))
        );
    }

    private Plan plan(String fixtureSuffix) {
        Plan plan = BeanUtils.instantiateClass(Plan.class);
        planPublicId = UUID.randomUUID().toString();
        ReflectionTestUtils.setField(plan, "publicId", planPublicId);
        ReflectionTestUtils.setField(plan, "name", "동시성 통합 테스트 플랜 " + fixtureSuffix);
        ReflectionTestUtils.setField(plan, "description", "동시 신청 통합 테스트 전용 플랜");
        ReflectionTestUtils.setField(plan, "unitPrice", 10_000L);
        return plan;
    }

    private List<Menu> menus(Long planId, String fixtureSuffix) {
        List<Menu> menus = new ArrayList<>();
        for (int sequence = 1; sequence <= 31; sequence++) {
            Menu menu = BeanUtils.instantiateClass(Menu.class);
            ReflectionTestUtils.setField(menu, "publicId", UUID.randomUUID().toString());
            ReflectionTestUtils.setField(menu, "planId", planId);
            ReflectionTestUtils.setField(menu, "menuSequence", sequence);
            ReflectionTestUtils.setField(menu, "name", "동시성 테스트 메뉴 " + fixtureSuffix + "-" + sequence);
            ReflectionTestUtils.setField(menu, "description", "동시 신청 통합 테스트 메뉴");
            ReflectionTestUtils.setField(menu, "allergenInfo", "없음");
            ReflectionTestUtils.setField(menu, "nutritionInfo", "테스트 영양정보");
            ReflectionTestUtils.setField(menu, "ingredientInfo", "테스트 원재료정보");
            menus.add(menu);
        }
        return menus;
    }

    private long count(String sql, Object parameter) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, parameter);
        return value == null ? 0L : value;
    }

    private void deleteSubscriptionData(long fixtureUserId) {
        List<Long> subscriptionIds = jdbcTemplate.queryForList(
            "SELECT id FROM subscriptions WHERE user_id = ?",
            Long.class,
            fixtureUserId
        );
        for (Long subscriptionId : subscriptionIds) {
            List<Long> transactionIds = jdbcTemplate.queryForList(
                "SELECT id FROM payment_transactions WHERE subscription_id = ?",
                Long.class,
                subscriptionId
            );
            for (Long transactionId : transactionIds) {
                jdbcTemplate.update(
                    "DELETE FROM payment_allocations WHERE original_payment_transaction_id = ?",
                    transactionId
                );
                jdbcTemplate.update("DELETE FROM payment_attempts WHERE payment_transaction_id = ?", transactionId);
            }
            jdbcTemplate.update("DELETE FROM payment_transactions WHERE subscription_id = ?", subscriptionId);
            jdbcTemplate.update("DELETE FROM orders WHERE subscription_id = ?", subscriptionId);

            List<Long> settingIds = jdbcTemplate.queryForList(
                "SELECT id FROM subscription_settings WHERE subscription_id = ?",
                Long.class,
                subscriptionId
            );
            for (Long settingId : settingIds) {
                jdbcTemplate.update(
                    "DELETE FROM subscription_delivery_conditions WHERE subscription_setting_id = ?",
                    settingId
                );
            }
            jdbcTemplate.update("DELETE FROM subscription_status_histories WHERE subscription_id = ?", subscriptionId);
            jdbcTemplate.update("DELETE FROM subscription_settings WHERE subscription_id = ?", subscriptionId);
            jdbcTemplate.update("DELETE FROM subscription_periods WHERE subscription_id = ?", subscriptionId);
            jdbcTemplate.update("DELETE FROM subscriptions WHERE id = ?", subscriptionId);
        }
    }
}
