package com.yolifay.domain;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EnrichedOrderRepository implements PanacheRepository<EnrichedOrderEntity> {
}
