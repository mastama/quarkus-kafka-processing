package com.yolifay.service;

import com.yolifay.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;

@ApplicationScoped
public class OrderEnrichmentService {
    private static final Logger LOG = Logger.getLogger(OrderEnrichmentService.class);

    @ConfigProperty(name = "app.enrich.idr-threshold", defaultValue = "1000000")
    long idrThreshold;

    @Inject EnrichedOrderRepository repo;

    @ConfigProperty(name = "app.enrich.fx.USD", defaultValue = "16000") double fxUsd;
    @ConfigProperty(name = "app.enrich.fx.IDR", defaultValue = "1")     double fxIdr;
    @ConfigProperty(name = "app.enrich.fx.EUR", defaultValue = "17500") double fxEur;

    @Transactional // <-- ini yang mengaktifkan EntityManager/Session
    public EnrichedOrder enrichAndMaybePersist(OrderIn in){
        String ccy = in.currency() == null ? "IDR" : in.currency().toUpperCase();
        double rate = switch (ccy) { case "USD" -> fxUsd; case "EUR" -> fxEur; default -> fxIdr; };
        double amountIdr = in.amount() * rate;
        boolean high = amountIdr >= idrThreshold;

        if (high) {
            EnrichedOrderEntity e = new EnrichedOrderEntity();
            e.orderId    = in.orderId();
            e.customerId = in.customerId();
            e.amount     = in.amount();
            e.currency   = ccy;
            e.amountIdr  = amountIdr;
            e.highValue  = true;
            e.processedAt = OffsetDateTime.now();
            repo.persist(e);
            LOG.infof("Persisted high-value orderId=%s amountIdr=%.2f", in.orderId(), amountIdr);
        } else {
            LOG.debugf("Skip persist (not high) orderId=%s amountIdr=%.2f", in.orderId(), amountIdr);
        }

        return new EnrichedOrder(in.orderId(), in.customerId(), in.amount(), ccy, amountIdr, high, OffsetDateTime.now().toString());
    }
}