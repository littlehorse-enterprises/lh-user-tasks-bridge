package io.littlehorse.usertasks.configurations;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfiguration {

    @Bean
    public OpenAPI apiDocConfig() {
        return new OpenAPI()
                .info(new Info()
                        .title("UserTasks Bridge Backend")
                        .description("This is LittleHorse's custom API to handle UserTaskRuns existing in LittleHorse "
                                + "Kernel and provide a seamless experience between LittleHorse Kernel and any OIDC-compliant "
                                + "Identity Provider when working with LH UserTasks.")
                        .version("0.0.1"))
                .externalDocs(new ExternalDocumentation()
                        .description("Documentation")
                        .url("https://littlehorse.io/docs/server/concepts/user-tasks"));
    }
}
