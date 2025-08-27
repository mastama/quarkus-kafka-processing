package com.yolifay.api;

import com.yolifay.domain.OrderIn;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.*;

@Path("/api/ingest")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class IngestResource {
    private static final Logger LOG = Logger.getLogger(IngestResource.class);

    @Inject
    @Channel("orders-raw")
    Emitter<OrderIn> emitter;

    @POST
    public Response ingest(OrderIn in) {
        var meta = OutgoingKafkaRecordMetadata.<String>builder()
                .withKey(in.orderId())
                .build();

        // future utk menunggu ACK/NACK dari Kafka connector
        CompletableFuture<Void> done = new CompletableFuture<>();

        Message<OrderIn> msg = Message.of(in)
                .addMetadata(meta)
                .withAck(() -> {
                    LOG.infof("Kafka ACK topic=orders.raw key=%s", in.orderId());
                    done.complete(null);
                    return CompletableFuture.completedFuture(null);
                })
                .withNack(t -> {
                    LOG.errorf(t, "Kafka NACK topic=orders.raw key=%s", in.orderId());
                    done.completeExceptionally(t);
                    return CompletableFuture.completedFuture(null);
                });

        LOG.infof("Publishing to Kafka topic=orders.raw key=%s", in.orderId());
        emitter.send(msg); // <- tidak mengembalikan future

        try {
            // tunggu ACK maksimal 3 detik
            done.get(3, TimeUnit.SECONDS);
            return Response.ok(Map.of(
                    "status", "SENT",
                    "topic", "orders.raw",
                    "key", in.orderId()
            )).build(); // 200 OK kalau sudah benar2 di-ACK Kafka
        } catch (TimeoutException te) {
            LOG.warnf("Kafka ACK timeout (<=3s) topic=orders.raw key=%s", in.orderId());
            return Response.status(202).entity(Map.of(
                    "status", "PENDING",
                    "topic", "orders.raw",
                    "key", in.orderId(),
                    "note", "Kafka ack not received within 3s"
            )).build(); // 202 Accepted kalau kita belum terima ACK
        } catch (Exception e) {
            LOG.error("Send to Kafka failed", e);
            return Response.status(502).entity(Map.of(
                    "status", "FAILED",
                    "topic", "orders.raw",
                    "key", in.orderId(),
                    "error", e.getMessage()
            )).build();
        }
    }
}