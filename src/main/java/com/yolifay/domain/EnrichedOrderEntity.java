package com.yolifay.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "enriched_orders")
public class EnrichedOrderEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "order_id", nullable = false)
    public String orderId;

    @Column(name = "customer_id", nullable = false)
    public String customerId;

    @Column(nullable = false)
    public double amount;

    @Column(nullable = false)
    public String currency;

    @Column(name = "amount_idr", nullable = false)
    public double amountIdr;

    @Column(name = "high_value", nullable = false)
    public boolean highValue;

    @Column(name = "processed_at", nullable = false)
    public OffsetDateTime processedAt = OffsetDateTime.now();

    @PrePersist
    public void prePersist(){
        if (id == null) id = UUID.randomUUID();
    }
}
