package io.littlehorse.usertasks.diffuser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.littlehorse.streamsense.diffuser.WorkflowDiffuserRuntime;
import io.littlehorse.streamsense.diffuser.config.ConfigFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DiffuserLifecycleTest {
    @Test
    void embeddedDiffuserDoesNotReadTheHostApplicationYaml() {
        var config = ConfigFactory.build();
        assertThat(config.getOptionalValue("spring.application.name", String.class))
                .isEmpty();
        assertThat(config.getValue("app.server.port", Integer.class)).isEqualTo(8080);
    }

    @Test
    void disabledConfigurationDoesNotRequireKafkaOrTenantSettings() {
        new ApplicationContextRunner()
                .withUserConfiguration(DiffuserConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DiffuserLifecycle.class);
                    assertThat(context).doesNotHaveBean("diffuserHealthIndicator");
                });
    }

    @Test
    void enabledConfigurationRejectsEmptyTenantListBeforeConnectingToKafka() {
        new ApplicationContextRunner()
                .withUserConfiguration(DiffuserConfiguration.class)
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("business-console.diffuser.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "Workflow Diffuser requires at least one configured app.tenants entry");
                });
    }

    @Test
    void lifecycleSupportsRestartAndKeepsHostRegistryAvailable() {
        KafkaStreams streams = streams();
        AtomicInteger instances = new AtomicInteger();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DiffuserLifecycle lifecycle = new DiffuserLifecycle(
                () -> {
                    instances.incrementAndGet();
                    return new WorkflowDiffuserRuntime(streams);
                },
                registry);
        lifecycle.start();
        lifecycle.start();
        assertThat(instances).hasValue(1);
        assertThat(lifecycle.isRunning()).isTrue();
        lifecycle.stop();
        lifecycle.stop();
        assertThat(lifecycle.isRunning()).isFalse();
        lifecycle.start();
        lifecycle.stop();
        assertThat(instances).hasValue(2);
        verify(streams, times(2)).start();
        verify(streams, times(2)).close(Duration.ofSeconds(30));
        registry.counter("host.still.available").increment();
        assertThat(registry.get("host.still.available").counter().count()).isEqualTo(1);
        registry.close();
    }

    @Test
    void startupFailureClosesStreamsAndLeavesLifecycleStopped() {
        KafkaStreams streams = streams();
        doThrow(new IllegalStateException("broker setup failed")).when(streams).start();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            DiffuserLifecycle lifecycle = new DiffuserLifecycle(() -> new WorkflowDiffuserRuntime(streams), registry);
            assertThatThrownBy(lifecycle::start).hasMessage("broker setup failed");
            assertThat(lifecycle.isRunning()).isFalse();
            verify(streams).close(Duration.ofSeconds(30));
            assertThat(registry.getMeters()).isEmpty();
        } finally {
            registry.close();
        }
    }

    @Test
    void healthTracksStreamStateIncludingAsynchronousFailures() {
        KafkaStreams streams = streams();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            DiffuserLifecycle lifecycle = new DiffuserLifecycle(() -> new WorkflowDiffuserRuntime(streams), registry);
            HealthIndicator health = new DiffuserConfiguration().diffuserHealthIndicator(lifecycle);
            assertThat(health.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
            lifecycle.start();
            when(streams.state()).thenReturn(KafkaStreams.State.RUNNING);
            assertThat(health.health().getStatus()).isEqualTo(Status.UP);
            when(streams.state()).thenReturn(KafkaStreams.State.ERROR);
            assertThat(health.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
            lifecycle.stop();
        } finally {
            registry.close();
        }
    }

    private KafkaStreams streams() {
        KafkaStreams streams = mock(KafkaStreams.class);
        when(streams.metrics()).thenReturn(Map.of());
        return streams;
    }
}
