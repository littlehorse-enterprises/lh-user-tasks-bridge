package io.littlehorse.streamsense.diffuser;

import com.sun.net.httpserver.HttpServer;
import io.littlehorse.streamsense.diffuser.config.AppConfig;
import io.littlehorse.streamsense.diffuser.config.ConfigFactory;
import io.littlehorse.streamsense.diffuser.config.KafkaStreamsConfig;
import io.littlehorse.streamsense.diffuser.config.PrefixConfig;
import io.littlehorse.streamsense.diffuser.server.HttpServerFactory;
import io.littlehorse.streamsense.diffuser.topology.LogAndContinueDeserializationExceptionHandler;
import io.littlehorse.streamsense.diffuser.topology.LogAndContinueProductionExceptionHandler;
import io.littlehorse.streamsense.diffuser.topology.TopologyFactory;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.smallrye.config.SmallRyeConfig;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

/**
 * Entry point for the Workflow Diffuser application.
 *
 * <p>Bootstraps configuration, builds the Kafka Streams topology, starts an HTTP server
 * for health checks and Prometheus metrics, and runs the Kafka Streams instance.
 */
public class WorkflowDiffuserApp {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDiffuserApp.class);

    public static void main(String[] args) {
        // 1. Build configuration
        SmallRyeConfig config = ConfigFactory.build();
        AppConfig appConfig = config.getConfigMapping(AppConfig.class);
        KafkaStreamsConfig ksConfig = new KafkaStreamsConfig(config);

        // 2. Apply log-level overrides from config (logging.level.{package}={LEVEL})
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (String name : config.getPropertyNames()) {
            if (name.startsWith("logging.level.")) {
                String loggerName = name.substring("logging.level.".length());
                String levelStr = config.getValue(name, String.class);
                loggerContext.getLogger(loggerName).setLevel(Level.toLevel(levelStr));
            }
        }

        log.info("Starting Workflow Diffuser with cluster={}, tenants={}",
                appConfig.lhClusterId(), appConfig.tenants().size());

        // 3. Build Apicurio config from apicurio.registry.* properties, with fallback
        PrefixConfig apicurioPrefixConfig = new PrefixConfig(config, "apicurio.registry.");
        Map<String, Object> apicurioConfig = apicurioPrefixConfig.asPrefixedMap("apicurio.registry.");
        if (!apicurioConfig.containsKey("apicurio.registry.url") && ksConfig.schemaRegistryUrl() != null) {
            apicurioConfig.put("apicurio.registry.url", ksConfig.schemaRegistryUrl());
        }

        // 4. Build Kafka Streams topology
        Topology topology = TopologyFactory.build(appConfig, ksConfig.applicationId(), apicurioConfig);
        log.info("Topology built:\n{}", topology.describe());

        // 5. Create KafkaStreams instance
        Properties streamsProps = ksConfig.asProperties();
        streamsProps.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueDeserializationExceptionHandler.class);
        streamsProps.put(StreamsConfig.PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueProductionExceptionHandler.class);
        KafkaStreams streams = new KafkaStreams(topology, streamsProps);

        // 6. Set up Micrometer metrics
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new KafkaStreamsMetrics(streams).bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);

        // 7. Start HTTP server (metrics + health)
        HttpServer server = HttpServerFactory.create(streams, registry, appConfig);
        server.start();
        log.info("HTTP server started on port {}", appConfig.server().port());

        // 8. Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Workflow Diffuser...");
            streams.close(Duration.ofSeconds(30));
            server.stop(5);
            log.info("Workflow Diffuser stopped.");
        }));

        // 9. Start Kafka Streams
        streams.start();
        log.info("Kafka Streams started.");
    }
}
