package io.littlehorse.streamsense.diffuser.topology;

import io.littlehorse.streamsense.diffuser.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds regex patterns for subscribing to input topics based on configuration.
 */
public class InputTopicPatternBuilder {

    private static final Logger log = LoggerFactory.getLogger(InputTopicPatternBuilder.class);

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
        Pattern pattern = Pattern.compile(lhClusterId + "_(" + tenantAlternation + ")_execution");
        log.debug("Execution topic pattern: {}", pattern);
        return pattern;
    }

    /**
     * Builds a regex pattern matching all metadata topics for configured tenants.
     * Pattern: {@code {lhClusterId}_(tenant1|tenant2)_metadata}
     */
    public Pattern metadataTopicPattern() {
        String tenantAlternation = tenantNames.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        Pattern pattern = Pattern.compile(lhClusterId + "_(" + tenantAlternation + ")_metadata");
        log.debug("Metadata topic pattern: {}", pattern);
        return pattern;
    }
}
