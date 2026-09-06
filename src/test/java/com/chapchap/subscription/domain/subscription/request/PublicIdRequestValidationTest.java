package com.chapchap.subscription.domain.subscription.request;

import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicIdRequestValidationTest {

    @Test
    void 첫_구독_요청은_Prefix_없는_소문자_UUID_v4를_허용한다() {
        FirstSubscriptionRequest request = new FirstSubscriptionRequest(
            "11111111-1111-4111-8111-111111111111",
            List.of(new FirstSubscriptionRequest.DeliveryCondition(
                DeliveryWeekday.MONDAY,
                2,
                "21111111-1111-4111-8111-111111111111",
                DeliveryTimeSlot.TIME_1100_1300
            ))
        );

        assertThat(validator().validate(request)).isEmpty();
    }

    @Test
    void 설정_변경_요청은_Prefix와_대문자_UUID를_거절한다() {
        SettingChangeRequest request = new SettingChangeRequest(
            "PLN-11111111-1111-4111-8111-111111111111",
            List.of(new SettingChangeRequest.DeliveryCondition(
                DeliveryWeekday.MONDAY,
                2,
                "21111111-1111-4111-8111-11111111111A",
                DeliveryTimeSlot.TIME_1100_1300
            ))
        );

        assertThat(validator().validate(request)).hasSize(2);
    }

    private Validator validator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        return factory.getValidator();
    }
}
