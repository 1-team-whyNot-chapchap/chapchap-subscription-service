package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.Menu;
import com.chapchap.subscription.domain.subscription.entity.Plan;
import com.chapchap.subscription.domain.subscription.repository.MenuRepository;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.response.PlanDetailResponse;
import com.chapchap.subscription.domain.subscription.response.PlanListResponse;
import com.chapchap.subscription.global.exception.subscription.PlanNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanQueryServiceTest {
    private static final String PLAN_PUBLIC_ID = "550e8400-e29b-41d4-a716-446655440000";

    @Mock private PlanRepository planRepository;
    @Mock private MenuRepository menuRepository;

    private PlanQueryService service;

    @BeforeEach
    void setUp() {
        service = new PlanQueryService(planRepository, menuRepository);
    }

    @Test
    void 플랜_목록을_Repository의_안정적인_정렬_순서대로_반환한다() {
        Plan first = plan("11111111-1111-4111-8111-111111111111", "간편식", 7_900L);
        Plan second = plan("22222222-2222-4222-8222-222222222222", "가정식", 8_900L);
        when(planRepository.findAllByOrderByUnitPriceAscPublicIdAsc())
            .thenReturn(List.of(first, second));

        PlanListResponse response = service.getPlans();

        assertThat(response.plans())
            .extracting(PlanListResponse.PlanItemResponse::planId)
            .containsExactly(
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222"
            );
    }

    @Test
    void 플랜이_없으면_빈_목록을_반환한다() {
        when(planRepository.findAllByOrderByUnitPriceAscPublicIdAsc()).thenReturn(List.of());

        assertThat(service.getPlans().plans()).isEmpty();
    }

    @Test
    void 플랜_상세는_1번부터_31번_메뉴를_반환한다() {
        Plan plan = plan(PLAN_PUBLIC_ID, "가정식", 8_900L);
        when(plan.getId()).thenReturn(10L);
        List<Menu> menus = menus(10L, 31);
        when(planRepository.findByPublicId(PLAN_PUBLIC_ID)).thenReturn(Optional.of(plan));
        when(menuRepository.findAllByPlanIdOrderByMenuSequenceAsc(10L)).thenReturn(menus);

        PlanDetailResponse response = service.getPlan(PLAN_PUBLIC_ID);

        assertThat(response.planId()).isEqualTo(PLAN_PUBLIC_ID);
        assertThat(response.menus()).hasSize(31);
        assertThat(response.menus().getFirst().menuSequence()).isEqualTo(1);
        assertThat(response.menus().getLast().menuSequence()).isEqualTo(31);
        assertThat(response.menus().getFirst().imageUrl()).isEqualTo("https://example.com/menu-1.jpg");
    }

    @Test
    void 잘못된_플랜_공개_식별자는_DB를_조회하지_않는다() {
        assertThatThrownBy(() -> service.getPlan("PLN-invalid"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(planRepository, menuRepository);
    }

    @Test
    void 없는_플랜은_플랜_없음_오류로_처리한다() {
        when(planRepository.findByPublicId(PLAN_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlan(PLAN_PUBLIC_ID))
            .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void 메뉴가_31개보다_적으면_기준정보_불일치로_처리한다() {
        Plan plan = plan(PLAN_PUBLIC_ID, "가정식", 8_900L);
        when(plan.getId()).thenReturn(10L);
        when(planRepository.findByPublicId(PLAN_PUBLIC_ID)).thenReturn(Optional.of(plan));
        List<Menu> menus = menus(10L, 30);
        when(menuRepository.findAllByPlanIdOrderByMenuSequenceAsc(10L)).thenReturn(menus);

        assertThatThrownBy(() -> service.getPlan(PLAN_PUBLIC_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("1~31번 메뉴");
    }

    @Test
    void 메뉴_순번이_연속되지_않으면_기준정보_불일치로_처리한다() {
        Plan plan = plan(PLAN_PUBLIC_ID, "가정식", 8_900L);
        when(plan.getId()).thenReturn(10L);
        List<Menu> menus = menus(10L, 31);
        when(menus.get(10).getMenuSequence()).thenReturn(20);
        when(planRepository.findByPublicId(PLAN_PUBLIC_ID)).thenReturn(Optional.of(plan));
        when(menuRepository.findAllByPlanIdOrderByMenuSequenceAsc(10L)).thenReturn(menus);

        assertThatThrownBy(() -> service.getPlan(PLAN_PUBLIC_ID))
            .isInstanceOf(IllegalStateException.class);
    }

    private Plan plan(String publicId, String name, Long unitPrice) {
        Plan plan = mock(Plan.class);
        lenient().when(plan.getPublicId()).thenReturn(publicId);
        lenient().when(plan.getName()).thenReturn(name);
        lenient().when(plan.getDescription()).thenReturn(name + " 설명");
        lenient().when(plan.getUnitPrice()).thenReturn(unitPrice);
        return plan;
    }

    private List<Menu> menus(Long planId, int count) {
        List<Menu> menus = new ArrayList<>();
        for (int sequence = 1; sequence <= count; sequence++) {
            Menu menu = mock(Menu.class);
            lenient().when(menu.getPlanId()).thenReturn(planId);
            lenient().when(menu.getMenuSequence()).thenReturn(sequence);
            lenient().when(menu.getName()).thenReturn(sequence + "일 메뉴");
            lenient().when(menu.getDescription()).thenReturn("메뉴 설명");
            lenient().when(menu.getImageUrl()).thenReturn("https://example.com/menu-" + sequence + ".jpg");
            lenient().when(menu.getAllergenInfo()).thenReturn("알레르기 정보");
            lenient().when(menu.getNutritionInfo()).thenReturn("영양 정보");
            lenient().when(menu.getIngredientInfo()).thenReturn("원재료 정보");
            menus.add(menu);
        }
        return menus;
    }
}
