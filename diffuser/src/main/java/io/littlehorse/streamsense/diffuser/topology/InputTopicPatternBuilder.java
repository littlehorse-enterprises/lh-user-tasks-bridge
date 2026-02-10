package io.littlehorse.streamsense.diffuser.topology;

import io.littlehorse.streamsense.diffuser.config.AppConfig;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds regex patterns for subscribing to input topics based on configuration.
 */
public class InputTopicPatternBuilder {

    private final String lhClusterId;
    private final List<String> tenantNames;

    public InputTopicPatternBuilder(AppConfig appConfig) {
        this.lhClusterId = Pattern.quote(appConfig.lhClusterId());
        this.tenantNames = appConfig.tenants().stream()
                .map(AppConfig.TenantConfig::name)
                .collect(Collectors.toList());
    }

    /**
     * Builds a regex pattern matching all execution topics for configured tenants.
     * Pattern: {@code {lhClusterId}_(tenant1|tenant2)_execution}
     */
    public Pattern executionTopicPattern() {
        String tenantAlternation = tenantNames.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile(lhClusterId + "_(" + tenantAlternation + ")_execution");
    }

    /**
     * Builds a regex pattern matching all metadata topics for configured tenants.
     * Pattern: {@code {lhClusterId}_(tenant1|tenant2)_metadata}
     */
    public Pattern metadataTopicPattern() {
        String tenantAlternation = tenantNames.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile(lhClusterId + "_(" + tenantAlternation + ")_metadata");
    }
}
