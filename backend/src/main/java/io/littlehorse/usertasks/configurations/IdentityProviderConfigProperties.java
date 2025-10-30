package io.littlehorse.usertasks.configurations;

import java.util.List;
import lombok.Data;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@AutoConfiguration
@ConfigurationProperties(prefix = "com.c4-soft.springaddons.oidc")
public class IdentityProviderConfigProperties {
    private List<CustomIdentityProviderProperties> ops = List.of();
}
