package io.littlehorse.streamsense.diffuser.config;

import io.smallrye.config.EnvConfigSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Environment overrides restricted to the {@code LH_} namespace, at ordinal 400.
 *
 * <p>After removing the prefix, SmallRye applies its native environment naming rules.
 * For example, {@code LH_APP_LH_CLUSTER_ID} overrides {@code app.lh-cluster-id},
 * {@code LH_APP_TENANTS_0__WF_SPECS} configures {@code app.tenants[0].wf-specs}, and
 * {@code LH_KAFKA_STREAMS_BOOTSTRAP_SERVERS} overrides {@code kafka.streams.bootstrap.servers}.
 * Keeping this an {@link EnvConfigSource} also lets config mappings discover indexed
 * properties and resolve underscores against their declared kebab-case names.</p>
 */
public class LhEnvConfigSource extends EnvConfigSource {

    private static final String PREFIX = "LH_";
    private static final int ORDINAL = 400;

    public LhEnvConfigSource() {
        this(System.getenv());
    }

    // Visible for testing
    LhEnvConfigSource(Map<String, String> env) {
        super(withoutPrefix(env), ORDINAL);
    }

    private static Map<String, String> withoutPrefix(Map<String, String> env) {
        Map<String, String> result = new HashMap<>();
        env.forEach((key, value) -> {
            if (key.startsWith(PREFIX)) {
                result.put(key.substring(PREFIX.length()), value);
            }
        });
        return result;
    }

    @Override
    public String getName() {
        return "LhEnvConfigSource";
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }
}
