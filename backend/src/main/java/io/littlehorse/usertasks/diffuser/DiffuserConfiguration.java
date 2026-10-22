package io.littlehorse.usertasks.diffuser;

import io.littlehorse.streamsense.diffuser.WorkflowDiffuserRuntime;
import io.littlehorse.streamsense.diffuser.config.ConfigFactory;
import io.littlehorse.streamsense.diffuser.server.HealthHandler;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.streams.KafkaStreams;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "business-console.diffuser.enabled", havingValue = "true")
@PropertySource("classpath:diffuser-embedded.properties")
public class DiffuserConfiguration {
    @Bean
    public DiffuserLifecycle diffuserLifecycle(MeterRegistry registry) {
        return new DiffuserLifecycle(() -> WorkflowDiffuserRuntime.create(ConfigFactory.build()), registry);
    }

    @Bean
    public HealthIndicator diffuserHealthIndicator(DiffuserLifecycle lifecycle) {
        return () -> {
            KafkaStreams.State state = lifecycle.state();
            Health.Builder health = HealthHandler.isReady(state) ? Health.up() : Health.outOfService();
            return health.withDetail("state", state.name()).build();
        };
    }
}
