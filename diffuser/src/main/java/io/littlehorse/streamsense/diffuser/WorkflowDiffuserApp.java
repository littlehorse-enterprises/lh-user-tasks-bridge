package io.littlehorse.streamsense.diffuser;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.sun.net.httpserver.HttpServer;
import io.littlehorse.streamsense.diffuser.config.AppConfig;
import io.littlehorse.streamsense.diffuser.config.ConfigFactory;
import io.littlehorse.streamsense.diffuser.server.HttpServerFactory;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.smallrye.config.SmallRyeConfig;
import org.slf4j.LoggerFactory;

/** Standalone host; the Business Console embeds only {@link WorkflowDiffuserRuntime}. */
public class WorkflowDiffuserApp {
    public static void main(String[] args) {
        // Standalone logging defaults must not be auto-discovered by an embedding host.
        if (System.getProperty("logback.configurationFile") == null) {
            System.setProperty("logback.configurationFile", "diffuser-logback.xml");
        }
        SmallRyeConfig config = ConfigFactory.build();
        AppConfig app = config.getConfigMapping(AppConfig.class);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (String name : config.getPropertyNames()) {
            if (name.startsWith("logging.level.")) {
                context.getLogger(name.substring("logging.level.".length()))
                        .setLevel(Level.toLevel(config.getValue(name, String.class)));
            }
        }

        WorkflowDiffuserRuntime runtime = WorkflowDiffuserRuntime.create(config);
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        JvmGcMetrics gcMetrics = new JvmGcMetrics();
        HttpServer server = null;
        try {
            new JvmMemoryMetrics().bindTo(registry);
            gcMetrics.bindTo(registry);
            new JvmThreadMetrics().bindTo(registry);
            server = HttpServerFactory.create(runtime.streams(), registry, app);
            server.start();
            runtime.start(registry);
            HttpServer ownedServer = server;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    runtime.close();
                } finally {
                    ownedServer.stop(0);
                    gcMetrics.close();
                    registry.close();
                }
            }, "workflow-diffuser-shutdown"));
        } catch (RuntimeException | Error failure) {
            try {
                runtime.close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            } finally {
                if (server != null) server.stop(0);
                gcMetrics.close();
                registry.close();
            }
            throw failure;
        }
    }
}
