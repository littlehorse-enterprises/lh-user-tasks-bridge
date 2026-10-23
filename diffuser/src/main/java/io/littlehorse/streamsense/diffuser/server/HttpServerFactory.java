package io.littlehorse.streamsense.diffuser.server;

import com.sun.net.httpserver.HttpServer;
import io.littlehorse.streamsense.diffuser.config.AppConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.kafka.streams.KafkaStreams;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;

/**
 * Creates the HTTP server with health check and Prometheus metrics endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code /health} — combined liveness + readiness</li>
 *   <li>{@code /health/liveness} — liveness only (Kafka Streams not in ERROR/NOT_RUNNING)</li>
 *   <li>{@code /health/readiness} — readiness only (Kafka Streams RUNNING or REBALANCING)</li>
 *   <li>{@code /metrics} — Prometheus scrape endpoint</li>
 * </ul>
 */
public class HttpServerFactory {

    public static HttpServer create(KafkaStreams streams, PrometheusMeterRegistry registry, AppConfig appConfig) {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(appConfig.server().port()), 0);

            server.createContext("/health",
                    new HealthHandler(streams, HealthHandler.ProbeType.COMBINED));
            server.createContext("/health/liveness",
                    new HealthHandler(streams, HealthHandler.ProbeType.LIVENESS));
            server.createContext("/health/readiness",
                    new HealthHandler(streams, HealthHandler.ProbeType.READINESS));
            server.createContext("/metrics",
                    new MetricsHandler(registry));

            return server;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create HTTP server", e);
        }
    }
}
