package com.chapchap.subscription.domain.subscription.repository;

import com.chapchap.subscription.domain.subscription.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    // 플랜ID와 메뉴 순번으로 메뉴 찾기
    Optional<Menu> findByPlanIdAndMenuSequence(Long planId, Integer menuSequence);

    List<Menu> findAllByPlanIdOrderByMenuSequenceAsc(Long planId);
}
