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
        return new OpenAPI().info(new Info()
                        .title("UserTasks API")
                        .description("LittleHorse's custom API to manage UserTaskRuns existing in LittleHorse's Server.")
                        .version("0.0.1"))
                .externalDocs(new ExternalDocumentation()
                        .description("Documentation")
                        .url("https://littlehorse.io/docs/concepts/user-tasks/"));
    }
}
