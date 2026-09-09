package com.chapchap.subscription.domain.address.repository;

import com.chapchap.subscription.domain.address.entity.DeliveryMethod;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryMethodRepository extends JpaRepository<DeliveryMethod, String> {
}
