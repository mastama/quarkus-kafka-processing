package com.yolifay.kafka;

import com.yolifay.domain.OrderIn;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class OrderInDeserializer extends ObjectMapperDeserializer<OrderIn> {
    public OrderInDeserializer() {
        super(OrderIn.class);
    }
}
