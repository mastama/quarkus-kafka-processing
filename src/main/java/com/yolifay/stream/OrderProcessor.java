// OrderProcessor.java
package com.yolifay.stream;

import com.yolifay.domain.EnrichedOrder;
import com.yolifay.domain.OrderIn;
import com.yolifay.service.OrderEnrichmentService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OrderProcessor {
    private static final Logger LOG = Logger.getLogger(OrderProcessor.class);

    @Inject OrderEnrichmentService service;

    @Incoming("orders-in")
    @Outgoing("orders-out")
    @Blocking
    public EnrichedOrder process(OrderIn in){
        LOG.infof("Consuming orderId=%s amount=%.2f ccy=%s", in.orderId(), in.amount(), in.currency());
        EnrichedOrder out = service.enrichAndMaybePersist(in);
        LOG.infof("Produced enriched orderId=%s high=%s amountIdr=%.2f", out.orderId(), out.highValue(), out.amountIdr());
        return out;
    }
}