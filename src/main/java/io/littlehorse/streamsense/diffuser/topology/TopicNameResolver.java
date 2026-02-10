package io.littlehorse.streamsense.diffuser.topology;

/**
 * Resolves output topic names following the convention:
 * {@code {lhClusterId}_{tenant}_streamlet_{type-prefix}-{specName}}
 */
public class TopicNameResolver {

    private final String lhClusterId;

    public TopicNameResolver(String lhClusterId) {
        this.lhClusterId = lhClusterId;
    }

    public String resolve(String tenant, StreamletType type, String specName) {
        return "%s_%s_streamlet_%s-%s".formatted(lhClusterId, tenant, type.prefix(), specName);
    }
}
