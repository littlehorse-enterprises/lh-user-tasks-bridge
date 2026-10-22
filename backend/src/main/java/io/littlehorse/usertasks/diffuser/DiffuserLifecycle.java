package io.littlehorse.usertasks.diffuser;

import io.littlehorse.streamsense.diffuser.WorkflowDiffuserRuntime;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.apache.kafka.streams.KafkaStreams;
import org.springframework.context.SmartLifecycle;

/** Starts after infrastructure and stops before the application's shared registry is destroyed. */
public final class DiffuserLifecycle implements SmartLifecycle {
    private final Supplier<WorkflowDiffuserRuntime> factory;
    private final MeterRegistry registry;
    private WorkflowDiffuserRuntime runtime;

    public DiffuserLifecycle(Supplier<WorkflowDiffuserRuntime> factory, MeterRegistry registry) {
        this.factory = factory;
        this.registry = registry;
    }

    @Override
    public synchronized void start() {
        if (runtime != null) return;
        WorkflowDiffuserRuntime candidate = factory.get();
        candidate.start(registry);
        runtime = candidate;
    }

    @Override
    public synchronized void stop() {
        if (runtime == null) return;
        try {
            runtime.close();
        } finally {
            runtime = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return runtime != null;
    }

    public synchronized KafkaStreams.State state() {
        return runtime == null ? KafkaStreams.State.NOT_RUNNING : runtime.state();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
