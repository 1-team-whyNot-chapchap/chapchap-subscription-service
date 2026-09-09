package com.chapchap.subscription.domain.address.repository;

import com.chapchap.subscription.domain.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    // 내 활성 배송지 목록 조회
    List<Address> findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(
            Long userId
    );

    // → 수정 / 기본 지정 / 삭제 대상 조회
    // → 다른 고객 배송지나 삭제된 배송지도 자연스럽게 제외
    Optional<Address> findByPublicIdAndUserIdAndDeletedAtIsNull(
            String publicId,
            Long userId
    );

    // 현재 기본 배송지 조회
    Optional<Address> findByUserIdAndIsDefaultTrueAndDeletedAtIsNull(
            Long userId
    );

    // → 첫 배송지인지 확인
    // → 첫 배송지면 자동 기본 배송지 지정
    boolean existsByUserIdAndDeletedAtIsNull(
            Long userId
    );
}
