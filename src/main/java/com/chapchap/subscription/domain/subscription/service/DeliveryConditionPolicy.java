package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionDeliveryCondition;
import org.springframework.stereotype.Component;

import java.util.List;

// 요일 별 배송조건 유효성 체크
@Component
public class DeliveryConditionPolicy {

    public void validate(List<SubscriptionDeliveryCondition> conditions) {
        if (conditions == null || conditions.isEmpty() || conditions.size() > 6) {
            throw new IllegalArgumentException("요일별 배송 조건은 1개부터 6개까지 설정해야 합니다.");
        }

        long uniqueWeekdayCount = conditions.stream()
                .map(SubscriptionDeliveryCondition::getDeliveryWeekday)
                .distinct()
                .count();
        if (uniqueWeekdayCount != conditions.size()) {
            throw new IllegalArgumentException("같은 배송 요일을 중복해서 설정할 수 없습니다.");
        }
    }
}
