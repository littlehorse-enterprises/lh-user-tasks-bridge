package io.littlehorse.streamsense.diffuser.config;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;

/**
 * Builds the SmallRye configuration from multiple sources in priority order:
 * <ol>
 *   <li>{@code LH_}-prefixed environment variables (ordinal 400, via {@link LhEnvConfigSource})</li>
 *   <li>External YAML files specified in {@code LH_CONFIG_FILES} env var (ordinal 300)</li>
 *   <li>Classpath {@code diffuser-defaults.yaml} (ordinal 250)</li>
 *   <li>Default MicroProfile Config sources (system properties, env vars)</li>
 * </ol>
 */
public class ConfigFactory {

    public static SmallRyeConfig build() {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .addDefaultSources()
                .withSources(new LhEnvConfigSource())
                .withMapping(AppConfig.class)
                .withValidateUnknown(false);

        // Add classpath YAML sources
        addClasspathYaml(builder, "diffuser-defaults.yaml", 250);

        // Add external config files from LH_CONFIG_FILES
        addExternalConfigSources(builder);

        return builder.build();
    }

    private static void addClasspathYaml(SmallRyeConfigBuilder builder, String name, int ordinal) {
        try {
            Enumeration<URL> resources = Thread.currentThread()
                    .getContextClassLoader()
                    .getResources(name);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                builder.withSources(new YamlConfigSource(url, ordinal));
            }
        } catch (IOException e) {
            // Classpath resource not found — that's fine
        }
    }

    private static void addExternalConfigSources(SmallRyeConfigBuilder builder) {
        String configFiles = System.getenv("LH_CONFIG_FILES");
        if (configFiles == null || configFiles.isBlank()) {
            return;
        }
        for (String filePath : configFiles.split(",")) {
            String trimmed = filePath.trim();
            if (trimmed.isEmpty()) continue;
            Path path = Path.of(trimmed);
            if (Files.exists(path)) {
                try {
                    builder.withSources(new YamlConfigSource(path.toUri().toURL(), 300));
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to load external config: " + trimmed, e);
                }
            }
        }
    }
}
