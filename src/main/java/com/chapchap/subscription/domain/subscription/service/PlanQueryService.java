package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.Menu;
import com.chapchap.subscription.domain.subscription.entity.Plan;
import com.chapchap.subscription.domain.subscription.repository.MenuRepository;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.response.PlanDetailResponse;
import com.chapchap.subscription.domain.subscription.response.PlanListResponse;
import com.chapchap.subscription.global.exception.subscription.PlanNotFoundException;
import com.chapchap.subscription.global.validation.PublicIdFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 로그인 전에도 사용할 수 있는 플랜·고정 메뉴 기준정보를 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanQueryService {
    private static final int REQUIRED_MENU_COUNT = 31;
    private final PlanRepository planRepository;
    private final MenuRepository menuRepository;

    public PlanListResponse getPlans() {
        List<PlanListResponse.PlanItemResponse> plans = planRepository
            .findAllByOrderByUnitPriceAscPublicIdAsc()
            .stream()
            .map(this::toListItem)
            .toList();
        return new PlanListResponse(plans);
    }

    public PlanDetailResponse getPlan(String planId) {
        validatePlanId(planId);
        Plan plan = planRepository.findByPublicId(planId)
            .orElseThrow(PlanNotFoundException::new);
        Long internalPlanId = requirePositive(plan.getId(), "플랜");
        List<Menu> menus = menuRepository.findAllByPlanIdOrderByMenuSequenceAsc(internalPlanId);
        validateMenus(menus, internalPlanId);

        return new PlanDetailResponse(
            plan.getPublicId(),
            plan.getName(),
            plan.getDescription(),
            plan.getUnitPrice(),
            menus.stream().map(this::toMenu).toList()
        );
    }

    private PlanListResponse.PlanItemResponse toListItem(Plan plan) {
        return new PlanListResponse.PlanItemResponse(
            plan.getPublicId(), plan.getName(), plan.getDescription(), plan.getUnitPrice()
        );
    }

    private PlanDetailResponse.MenuResponse toMenu(Menu menu) {
        return new PlanDetailResponse.MenuResponse(
            menu.getMenuSequence(),
            menu.getName(),
            menu.getDescription(),
            menu.getImageUrl(),
            menu.getAllergenInfo(),
            menu.getNutritionInfo(),
            menu.getIngredientInfo()
        );
    }

    private void validateMenus(List<Menu> menus, Long planId) {
        if (menus.size() != REQUIRED_MENU_COUNT) {
            throw inconsistentMenuData();
        }
        for (int index = 0; index < REQUIRED_MENU_COUNT; index++) {
            Menu menu = menus.get(index);
            if (!planId.equals(menu.getPlanId())
                || !Integer.valueOf(index + 1).equals(menu.getMenuSequence())) {
                throw inconsistentMenuData();
            }
        }
    }

    private void validatePlanId(String planId) {
        if (!PublicIdFormat.isUuidV4(planId)) {
            throw new IllegalArgumentException("유효하지 않은 플랜 공개 식별자입니다.");
        }
    }

    private Long requirePositive(Long value, String target) {
        if (value == null || value <= 0) {
            throw new IllegalStateException(target + " 기준 데이터가 올바르지 않습니다.");
        }
        return value;
    }

    private IllegalStateException inconsistentMenuData() {
        return new IllegalStateException("플랜의 1~31번 메뉴 기준 데이터가 올바르지 않습니다.");
    }
}
