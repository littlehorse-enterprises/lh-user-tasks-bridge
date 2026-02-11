package io.littlehorse.streamsense.diffuser.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.kafka.streams.KafkaStreams;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * HTTP handler that exposes Kafka Streams health status as JSON.
 * Supports liveness, readiness, and combined probe types — returns 200 when healthy,
 * 503 when degraded.
 */
public class HealthHandler implements HttpHandler {

    private static final Set<KafkaStreams.State> READY_STATES = Set.of(
            KafkaStreams.State.RUNNING,
            KafkaStreams.State.REBALANCING
    );

    private static final Set<KafkaStreams.State> LIVE_STATES = Set.of(
            KafkaStreams.State.CREATED,
            KafkaStreams.State.RUNNING,
            KafkaStreams.State.REBALANCING,
            KafkaStreams.State.PENDING_SHUTDOWN
    );

    public enum ProbeType {
        LIVENESS, READINESS, COMBINED
    }

    private final KafkaStreams streams;
    private final ProbeType probeType;

    public HealthHandler(KafkaStreams streams, ProbeType probeType) {
        this.streams = streams;
        this.probeType = probeType;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        KafkaStreams.State state = streams.state();
        boolean healthy = isHealthy(state);
        int statusCode = healthy ? 200 : 503;
        String status = healthy ? "UP" : "DOWN";

        String body = """
                {"status":"%s","kafkaStreams":{"state":"%s"}}""".formatted(status, state);

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private boolean isHealthy(KafkaStreams.State state) {
        return switch (probeType) {
            case LIVENESS -> LIVE_STATES.contains(state);
            case READINESS -> READY_STATES.contains(state);
            case COMBINED -> LIVE_STATES.contains(state) && READY_STATES.contains(state);
        };
    }

    public static boolean isReady(KafkaStreams.State state) {
        return READY_STATES.contains(state);
    }

    public static boolean isLive(KafkaStreams.State state) {
        return LIVE_STATES.contains(state);
    }
}
