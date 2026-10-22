package io.littlehorse.streamsense.diffuser;

import io.littlehorse.streamsense.diffuser.config.AppConfig;
import io.littlehorse.streamsense.diffuser.config.KafkaStreamsConfig;
import io.littlehorse.streamsense.diffuser.config.PrefixConfig;
import io.littlehorse.streamsense.diffuser.topology.LogAndContinueDeserializationExceptionHandler;
import io.littlehorse.streamsense.diffuser.topology.LogAndContinueProductionExceptionHandler;
import io.littlehorse.streamsense.diffuser.topology.TopologyFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics;
import io.smallrye.config.SmallRyeConfig;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;

/** Owns one diffuser's Streams instance and metrics, but never its host's registry or HTTP server. */
public final class WorkflowDiffuserRuntime implements AutoCloseable {
    private final KafkaStreams streams;
    private KafkaStreamsMetrics metrics;
    private boolean started;
    private boolean closed;

    public WorkflowDiffuserRuntime(KafkaStreams streams) {
        this.streams = Objects.requireNonNull(streams);
    }

    public static WorkflowDiffuserRuntime create(SmallRyeConfig config) {
        AppConfig app = config.getConfigMapping(AppConfig.class);
        if (app.tenants().isEmpty()) {
            throw new IllegalArgumentException("Workflow Diffuser requires at least one configured app.tenants entry");
        }
        KafkaStreamsConfig kafka = new KafkaStreamsConfig(config);
        Map<String, Object> apicurio = new PrefixConfig(config, "apicurio.registry.")
                .asPrefixedMap("apicurio.registry.");
        if (!apicurio.containsKey("apicurio.registry.url") && kafka.schemaRegistryUrl() != null) {
            apicurio.put("apicurio.registry.url", kafka.schemaRegistryUrl());
        }
        Properties properties = kafka.asProperties();
        properties.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueDeserializationExceptionHandler.class);
        properties.put(StreamsConfig.PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueProductionExceptionHandler.class);
        return new WorkflowDiffuserRuntime(new KafkaStreams(
                TopologyFactory.build(app, kafka.applicationId(), apicurio), properties));
    }

    public synchronized void start(MeterRegistry registry) {
        if (closed) throw new IllegalStateException("Workflow Diffuser runtime is closed");
        if (started) return;
        try {
            metrics = new KafkaStreamsMetrics(streams);
            metrics.bindTo(registry);
            streams.start();
            started = true;
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public KafkaStreams streams() {
        return streams;
    }

    public KafkaStreams.State state() {
        return streams.state();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            streams.close(Duration.ofSeconds(30));
        } finally {
            if (metrics != null) metrics.close();
            started = false;
        }
    }
}
