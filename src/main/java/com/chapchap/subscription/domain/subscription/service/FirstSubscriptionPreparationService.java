package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.service.AddressService;
import com.chapchap.subscription.domain.holiday.entity.Holiday;
import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;
import com.chapchap.subscription.domain.order.service.FirstOrderAmountCalculator;
import com.chapchap.subscription.domain.order.service.FirstOrderPreparationCommand;
import com.chapchap.subscription.domain.order.service.FirstOrderPreparationResult;
import com.chapchap.subscription.domain.order.service.FirstOrderService;
import com.chapchap.subscription.domain.payment.entity.PaymentMethodStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentMethodRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.service.FirstPaymentPreparationService;
import com.chapchap.subscription.domain.payment.service.command.FirstPaymentPrepareCommand;
import com.chapchap.subscription.domain.payment.service.exception.CurrentPaymentMethodUnavailableException;
import com.chapchap.subscription.domain.payment.service.result.PreparedFirstPayment;
import com.chapchap.subscription.domain.payment.support.PaymentBusinessKeyGenerator;
import com.chapchap.subscription.domain.subscription.entity.*;
import com.chapchap.subscription.domain.subscription.repository.*;
import com.chapchap.subscription.domain.subscription.request.FirstSubscriptionRequest;
import com.chapchap.subscription.domain.subscription.response.FirstSubscriptionPreviewResponse;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.service.TermsService;
import com.chapchap.subscription.global.exception.subscription.PlanNotFoundException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionAlreadyActiveException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 첫 결제 전에 필요한 선행 검증과 구독·주문·결제 거래 생성을 하나의 로컬 트랜잭션으로 처리한다.
 */
@Service
public class FirstSubscriptionPreparationService {
    private static final String ACTOR = "CUSTOMER";
    private static final String INITIAL_REASON = "FIRST_SUBSCRIPTION_REQUESTED";
    private static final String REAPPLICATION_REASON = "SUBSCRIPTION_REAPPLIED";

    private final TermsService termsService;
    private final AddressService addressService;
    private final PlanRepository planRepository;
    private final MenuRepository menuRepository;
    private final HolidayRepository holidayRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPeriodRepository periodRepository;
    private final SubscriptionSettingRepository settingRepository;
    private final SubscriptionDeliveryConditionRepository conditionRepository;
    private final SubscriptionStatusHistoryRepository historyRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionScheduleCalculator scheduleCalculator;
    private final KstReferenceTimeProvider timeProvider;
    private final FirstOrderService firstOrderService;
    private final FirstPaymentPreparationService firstPaymentPreparationService;

    /** 첫 구독 prepare에 참여하는 도메인 조회·저장·계산 서비스를 구성한다. */
    public FirstSubscriptionPreparationService(
        TermsService termsService,
        AddressService addressService,
        PlanRepository planRepository,
        MenuRepository menuRepository,
        HolidayRepository holidayRepository,
        SubscriptionRepository subscriptionRepository,
        SubscriptionPeriodRepository periodRepository,
        SubscriptionSettingRepository settingRepository,
        SubscriptionDeliveryConditionRepository conditionRepository,
        SubscriptionStatusHistoryRepository historyRepository,
        PaymentMethodRepository paymentMethodRepository,
        PaymentTransactionRepository paymentTransactionRepository,
        SubscriptionScheduleCalculator scheduleCalculator,
        KstReferenceTimeProvider timeProvider,
        FirstOrderService firstOrderService,
        FirstPaymentPreparationService firstPaymentPreparationService
    ) {
        this.termsService = termsService;
        this.addressService = addressService;
        this.planRepository = planRepository;
        this.menuRepository = menuRepository;
        this.holidayRepository = holidayRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.periodRepository = periodRepository;
        this.settingRepository = settingRepository;
        this.conditionRepository = conditionRepository;
        this.historyRepository = historyRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.scheduleCalculator = scheduleCalculator;
        this.timeProvider = timeProvider;
        this.firstOrderService = firstOrderService;
        this.firstPaymentPreparationService = firstPaymentPreparationService;
    }

    /**
     * 기존 PROCESSING 요청은 멱등 결과를 반환하고, 신규 요청은 모든 사전 데이터를 함께 저장한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PreparedFirstSubscription prepare(Long userId, FirstSubscriptionRequest request) {
        LocalDateTime referenceAt = timeProvider.now();
        Subscription existing = findExistingWithLock(userId);
        PreparedFirstSubscription processing = findExistingProcessing(existing);
        if (processing != null) {
            return processing;
        }
        rejectActive(existing);

        UserTermsAgreement agreement = termsService.requireCurrentAgreement(userId);
        Plan plan = planRepository.findByPublicId(request.planId()).orElseThrow(PlanNotFoundException::new);
        Map<DeliveryWeekday, ValidatedCondition> conditions = validateConditions(userId, request);
        requireCurrentPaymentMethod(userId);

        Subscription subscription = prepareSubscription(userId, existing, referenceAt);
        int periodSequence = nextPeriodSequence(subscription.getId());
        int settingSequence = nextSettingSequence(subscription.getId());
        SubscriptionSchedule schedule = calculateSchedule(referenceAt, conditions.keySet());

        SubscriptionPeriod period = periodRepository.save(SubscriptionPeriod.createAwaitingConfirmation(
            subscription.getId(), periodSequence, schedule.periodStartDate(), referenceAt
        ));
        SubscriptionSetting setting = settingRepository.save(
            settingSequence == 1
                ? SubscriptionSetting.createFirstAwaitingConfirmation(
                    subscription.getId(), plan.getId(), schedule.periodStartDate())
                : SubscriptionSetting.createAwaitingConfirmation(
                    subscription.getId(), plan.getId(), settingSequence, referenceAt, schedule.periodStartDate())
        );
        conditionRepository.saveAll(conditions.values().stream()
            .map(condition -> SubscriptionDeliveryCondition.create(
                setting.getId(), condition.request().weekday(), condition.request().mealQuantity(),
                condition.address().getId(), condition.request().deliveryTimeSlot()
            ))
            .toList());

        boolean applyFirstDiscount = !subscription.isFirstSubscriptionDiscountUsed();
        FirstOrderPreparationResult orderResult = firstOrderService.prepare(new FirstOrderPreparationCommand(
            userId,
            subscription.getId(),
            period.getId(),
            setting.getId(),
            agreement.getId(),
            new FirstOrderPreparationCommand.PlanSnapshot(plan.getId(), plan.getName(), plan.getUnitPrice()),
            applyFirstDiscount,
            schedule.periodStartDate(),
            schedule.periodEndDate(),
            createDeliveries(plan, schedule.deliveryDates(), conditions)
        ));
        PreparedFirstPayment payment = firstPaymentPreparationService.prepare(new FirstPaymentPrepareCommand(
            userId, subscription.getId(), period.getId(), orderResult.totalPaymentAmount(), referenceAt,
            schedule.periodStartDate(), schedule.periodEndDate()
        ));
        if (!payment.newlyCreated() || payment.status() != PaymentTransactionStatus.PROCESSING) {
            throw new IllegalStateException("A newly prepared subscription requires a new processing payment");
        }
        return new PreparedFirstSubscription(
            subscription.getId(), subscription.getPublicId(), period.getId(), setting.getId(),
            subscription.getStatus(), schedule.periodStartDate(), schedule.periodEndDate(),
            payment.paymentTransactionId(), orderResult, true
        );
    }

    /**
     * 첫 구독 신청 조건으로 예상 이용 기간과 결제금액을 조회한다.
     *
     * <p>실제 첫 결제와 같은 입력 검증·일정·메뉴·주문 단위 금액 계산을 사용하지만, 구독·주문·결제
     * 데이터를 만들거나 외부 결제·Kafka를 호출하지 않는다. Preview 결과는 실제 결제 금액을 고정하지
     * 않으며, 실제 요청은 별도의 처리 기준 시각으로 다시 계산한다.</p>
     */
    @Transactional(readOnly = true)
    public FirstSubscriptionPreviewResponse preview(Long userId, FirstSubscriptionRequest request) {
        LocalDateTime referenceAt = timeProvider.now();
        Subscription existing = subscriptionRepository.findByUserId(userId).orElse(null);
        rejectActive(existing);

        termsService.requireCurrentAgreement(userId);
        Plan plan = planRepository.findByPublicId(request.planId()).orElseThrow(PlanNotFoundException::new);
        Map<DeliveryWeekday, ValidatedCondition> conditions = validateConditions(userId, request);
        requireCurrentPaymentMethod(userId);

        SubscriptionSchedule schedule = calculateSchedule(referenceAt, conditions.keySet());
        boolean applyFirstDiscount = existing == null || !existing.isFirstSubscriptionDiscountUsed();
        List<FirstOrderPreparationCommand.Delivery> deliveries = createDeliveries(
            plan, schedule.deliveryDates(), conditions
        );
        return createPreviewResponse(schedule, plan, deliveries, applyFirstDiscount);
    }

    /**
     * 동시 prepare 충돌로 현재 요청의 트랜잭션이 끝난 뒤 먼저 확정된 처리 중 신청을 조회한다.
     *
     * <p>구독·최신 기간·첫 결제 거래가 모두 처리 대기 상태로 일치할 때만 복구 결과를 반환한다.
     * 일부 데이터만 남았거나 이미 실패로 확정된 상태는 새 결제 실행 권한으로 바꾸지 않는다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<PreparedFirstSubscription> recoverConcurrentProcessing(Long userId) {
        Optional<Subscription> found = subscriptionRepository.findByUserId(userId);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        Subscription subscription = found.get();
        rejectActive(subscription);
        if (subscription.getStatus() != SubscriptionStatus.AWAITING_CONFIRMATION) {
            return Optional.empty();
        }

        return periodRepository.findTopBySubscriptionIdOrderByPeriodSequenceDesc(subscription.getId())
            .filter(period -> period.getStatus() == SubscriptionPeriodStatus.AWAITING_CONFIRMATION)
            .flatMap(period -> paymentTransactionRepository
                .findByBusinessDeduplicationKey(PaymentBusinessKeyGenerator.firstPayment(period.getId()))
                .filter(transaction -> transaction.getStatus() == PaymentTransactionStatus.PROCESSING)
                .map(transaction -> PreparedFirstSubscription.processing(
                    subscription.getId(), subscription.getPublicId(), period.getId(),
                    period.getPeriodStartDate(), period.getPeriodEndDate(), transaction.getId()
                ))
            );
    }

    private PreparedFirstSubscription findExistingProcessing(Subscription subscription) {
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.AWAITING_CONFIRMATION) {
            return null;
        }
        SubscriptionPeriod period = periodRepository.findTopBySubscriptionIdOrderByPeriodSequenceDesc(subscription.getId())
            .orElseThrow(() -> new IllegalStateException("Awaiting subscription has no period"));
        PaymentTransaction payment = paymentTransactionRepository
            .findByBusinessDeduplicationKey(PaymentBusinessKeyGenerator.firstPayment(period.getId()))
            .filter(transaction -> transaction.getStatus() == PaymentTransactionStatus.PROCESSING)
            .orElseThrow(() -> new IllegalStateException("Awaiting subscription has no processing payment"));
        return PreparedFirstSubscription.processing(
            subscription.getId(), subscription.getPublicId(), period.getId(),
            period.getPeriodStartDate(), period.getPeriodEndDate(), payment.getId()
        );
    }

    private Subscription findExistingWithLock(Long userId) {
        if (!subscriptionRepository.existsByUserId(userId)) {
            return null;
        }
        return subscriptionRepository.findWithLockByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("Existing subscription disappeared before lock acquisition"));
    }

    private void rejectActive(Subscription subscription) {
        if (subscription == null) {
            return;
        }
        if (subscription.getStatus() == SubscriptionStatus.SCHEDULED
            || subscription.getStatus() == SubscriptionStatus.IN_PROGRESS
            || subscription.getStatus() == SubscriptionStatus.CANCELLATION_SCHEDULED) {
            throw new SubscriptionAlreadyActiveException();
        }
    }

    private Subscription prepareSubscription(Long userId, Subscription existing, LocalDateTime changedAt) {
        if (existing == null) {
            Subscription created = subscriptionRepository.save(Subscription.create(userId));
            historyRepository.save(SubscriptionStatusHistory.create(
                created.getId(), null, SubscriptionStatus.AWAITING_CONFIRMATION,
                ACTOR, INITIAL_REASON, changedAt
            ));
            return created;
        }
        SubscriptionStatus previous = existing.prepareReapplication();
        historyRepository.save(SubscriptionStatusHistory.create(
            existing.getId(), previous, SubscriptionStatus.AWAITING_CONFIRMATION,
            ACTOR, REAPPLICATION_REASON, changedAt
        ));
        return existing;
    }

    private Map<DeliveryWeekday, ValidatedCondition> validateConditions(
        Long userId,
        FirstSubscriptionRequest request
    ) {
        Map<DeliveryWeekday, ValidatedCondition> validated = new EnumMap<>(DeliveryWeekday.class);
        for (FirstSubscriptionRequest.DeliveryCondition condition : request.deliveryConditions()) {
            Address address = addressService.requireActiveAddress(userId, condition.addressId());
            if (validated.put(condition.weekday(), new ValidatedCondition(condition, address)) != null) {
                throw new IllegalArgumentException("delivery weekday must not be duplicated");
            }
        }
        return validated;
    }

    private void requireCurrentPaymentMethod(Long userId) {
        boolean exists = paymentMethodRepository.existsByUserIdAndStatusAndIsCurrentTrueAndDeletedAtIsNull(
            userId, PaymentMethodStatus.AVAILABLE
        );
        if (!exists) {
            throw new CurrentPaymentMethodUnavailableException();
        }
    }

    private SubscriptionSchedule calculateSchedule(LocalDateTime referenceAt, Set<DeliveryWeekday> weekdays) {
        LocalDate from = referenceAt.toLocalDate();
        LocalDate to = from.plusDays(400);
        Set<LocalDate> holidays = holidayRepository.findAllByHolidayDateBetween(from, to).stream()
            .map(Holiday::getHolidayDate)
            .collect(java.util.stream.Collectors.toSet());
        return scheduleCalculator.calculate(referenceAt, new HashSet<>(weekdays), holidays);
    }

    private List<FirstOrderPreparationCommand.Delivery> createDeliveries(
        Plan plan,
        List<LocalDate> deliveryDates,
        Map<DeliveryWeekday, ValidatedCondition> conditions
    ) {
        return deliveryDates.stream().map(date -> {
            ValidatedCondition condition = conditions.values().stream()
                .filter(value -> value.request().weekday().toDayOfWeek() == date.getDayOfWeek())
                .findFirst()
                .orElseThrow();
            Menu menu = menuRepository.findByPlanIdAndMenuSequence(plan.getId(), scheduleCalculator.menuSequence(date))
                .orElseThrow(() -> new IllegalStateException("Plan menu is missing for delivery date"));
            Address address = condition.address();
            return new FirstOrderPreparationCommand.Delivery(
                date, menu.getId(), menu.getPlanId(), menu.getMenuSequence(), menu.getName(),
                condition.request().mealQuantity(),
                new FirstOrderPreparationCommand.AddressSnapshot(
                    address.getId(), address.getRecipientName(), address.getRecipientPhone(),
                    address.getPostalCode(), address.getAddressLine1(), address.getAddressLine2(),
                    address.getDeliveryMethodCode(), address.getOtherDeliveryRequest(), address.getEntrancePassword()
                ),
                toOrderTimeSlot(condition.request().deliveryTimeSlot())
            );
        }).toList();
    }

    private FirstSubscriptionPreviewResponse createPreviewResponse(
        SubscriptionSchedule schedule,
        Plan plan,
        List<FirstOrderPreparationCommand.Delivery> deliveries,
        boolean applyFirstDiscount
    ) {
        long totalMealAmount = 0L;
        long totalDeliveryFee = 0L;
        long totalDiscountAmount = 0L;
        long paymentAmount = 0L;

        for (FirstOrderPreparationCommand.Delivery delivery : deliveries) {
            FirstOrderAmountCalculator.FirstOrderAmount amount = FirstOrderAmountCalculator.calculate(
                plan.getUnitPrice(), delivery.mealQuantity(), applyFirstDiscount
            );
            totalMealAmount = Math.addExact(totalMealAmount, amount.mealAmount());
            totalDeliveryFee = Math.addExact(totalDeliveryFee, amount.deliveryFee());
            totalDiscountAmount = Math.addExact(totalDiscountAmount, amount.discountAmount());
            paymentAmount = Math.addExact(paymentAmount, amount.actualAllocatedAmount());
        }

        return new FirstSubscriptionPreviewResponse(
            schedule.periodStartDate(), schedule.periodEndDate(),
            totalMealAmount, totalDeliveryFee, totalDiscountAmount, paymentAmount
        );
    }

    private OrderDeliveryTimeSlot toOrderTimeSlot(DeliveryTimeSlot slot) {
        return switch (slot) {
            case TIME_1100_1300 -> OrderDeliveryTimeSlot.TIME_1100_1300;
            case TIME_1700_1900 -> OrderDeliveryTimeSlot.TIME_1700_1900;
        };
    }

    private int nextPeriodSequence(Long subscriptionId) {
        return periodRepository.findTopBySubscriptionIdOrderByPeriodSequenceDesc(subscriptionId)
            .map(period -> period.getPeriodSequence() + 1).orElse(1);
    }

    private int nextSettingSequence(Long subscriptionId) {
        return settingRepository.findTopBySubscriptionIdOrderBySettingSequenceDesc(subscriptionId)
            .map(setting -> setting.getSettingSequence() + 1).orElse(1);
    }

    private record ValidatedCondition(
        FirstSubscriptionRequest.DeliveryCondition request,
        Address address
    ) {
    }
}
