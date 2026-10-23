package io.littlehorse.streamsense.diffuser.topology;

/**
 * Resolves the tenant identifier from an input topic name.
 *
 * <p>Topic naming convention: {@code {lhClusterId}_{tenant}_{suffix}}
 * where suffix is either "execution" or "metadata".</p>
 */
public class TenantResolver {

    private final String lhClusterId;

    public TenantResolver(String lhClusterId) {
        this.lhClusterId = lhClusterId;
    }

    /**
     * Extracts the tenant name from a topic name.
     *
     * @param topicName the full topic name, e.g. "cluster1_tenant1_execution"
     * @return the tenant name, e.g. "tenant1"
     * @throws IllegalArgumentException if the topic name does not match the expected pattern
     */
    public String resolve(String topicName) {
        String prefix = lhClusterId + "_";
        if (!topicName.startsWith(prefix)) {
            throw new IllegalArgumentException(
                    "Topic '%s' does not start with expected prefix '%s'".formatted(topicName, prefix));
        }

        String rest = topicName.substring(prefix.length());
        int lastUnderscore = rest.lastIndexOf('_');
        if (lastUnderscore <= 0) {
            throw new IllegalArgumentException(
                    "Topic '%s' does not match expected pattern '{clusterId}_{tenant}_{suffix}'".formatted(topicName));
        }

        return rest.substring(0, lastUnderscore);
    }
}
